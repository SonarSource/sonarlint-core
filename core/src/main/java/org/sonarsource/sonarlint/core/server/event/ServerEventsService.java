/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.server.event;

import com.google.common.eventbus.Subscribe;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.client.event.DidReceiveServerEventParams;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.push.ServerEvent;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionCredentialsChangedEvent;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverconnection.StorageService;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

public class ServerEventsService {
  private final SonarLintClient client;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final StorageService storageService;
  private final ServerApiProvider serverApiProvider;
  private final LanguageSupportRepository languageSupportRepository;
  private final boolean shouldManageServerSentEvents;
  private final Map<String, SonarQubeEventStream> streamsPerConnectionId = new ConcurrentHashMap<>();

  public ServerEventsService(SonarLintClient client, ConfigurationRepository configurationRepository, ConnectionConfigurationRepository connectionConfigurationRepository,
    StorageService storageService,
    ServerApiProvider serverApiProvider, LanguageSupportRepository languageSupportRepository, InitializeParams initializeParams) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.storageService = storageService;
    this.serverApiProvider = serverApiProvider;
    this.languageSupportRepository = languageSupportRepository;
    this.shouldManageServerSentEvents = initializeParams.getFeatureFlags().shouldManageServerSentEvents();
  }

  @Subscribe
  public void handle(ConfigurationScopesAddedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    subscribeAll(event.getAddedConfigurationScopeIds());
  }

  @Subscribe
  public void handle(ConfigurationScopeRemovedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    var removedScope = event.getRemovedConfigurationScope();
    var removedBindingConfiguration = event.getRemovedBindingConfiguration();
    var bindingConfigurationFromRepository = configurationRepository.getBindingConfiguration(removedScope.getId());
    if (bindingConfigurationFromRepository == null
      || isBindingDifferent(removedBindingConfiguration, bindingConfigurationFromRepository)) {
      // it has not been re-added in the meantime, or re-added with different binding
      unsubscribe(removedBindingConfiguration);
    }
  }

  @Subscribe
  public void handle(BindingConfigChangedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    var previousBinding = event.getPreviousConfig();
    if (isBindingDifferent(previousBinding, event.getNewConfig())) {
      unsubscribe(previousBinding);
      subscribe(event.getConfigScopeId());
    }
  }

  @Subscribe
  public void handle(ConnectionConfigurationAddedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    // This is only to handle the case where binding was invalid (connection did not exist) and became valid (matching connection was created)
    var connectionId = event.getAddedConnectionId();
    var boundScopes = configurationRepository.getBoundScopesByConnection(connectionId);
    subscribe(connectionId, boundScopes.stream().map(BoundScope::getSonarProjectKey).collect(toSet()));
  }

  @Subscribe
  public void handle(ConnectionConfigurationRemovedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    var stream = streamsPerConnectionId.remove(event.getRemovedConnectionId());
    if (stream != null) {
      stream.stop();
    }
  }

  @Subscribe
  public void handle(ConnectionConfigurationUpdatedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    // URL might have changed, in doubt resubscribe
    resubscribe(event.getUpdatedConnectionId());
  }

  @Subscribe
  public void handle(ConnectionCredentialsChangedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    resubscribe(event.getConnectionId());
  }

  private static boolean isBindingDifferent(BindingConfiguration previousConfig, BindingConfiguration newConfig) {
    return !Objects.equals(previousConfig.getSonarProjectKey(), newConfig.getSonarProjectKey())
      || !Objects.equals(previousConfig.getConnectionId(), newConfig.getConnectionId());
  }

  private void subscribeAll(Set<String> configurationScopeIds) {
    configurationScopeIds.stream()
      .map(configurationRepository::getConfiguredBinding)
      .flatMap(Optional::stream)
      .collect(Collectors.groupingBy(Binding::getConnectionId, mapping(Binding::getSonarProjectKey, toSet())))
      .forEach(this::subscribe);
  }

  private void subscribe(String scopeId) {
    configurationRepository.getConfiguredBinding(scopeId)
      .ifPresent(binding -> subscribe(binding.getConnectionId(), Set.of(binding.getSonarProjectKey())));
  }

  private void subscribe(String connectionId, Set<String> possiblyNewProjectKeys) {
    if (supportsServerSentEvents(connectionId)) {
      var stream = streamsPerConnectionId.computeIfAbsent(connectionId, k -> openStream(connectionId));
      stream.subscribeNew(possiblyNewProjectKeys);
    }
  }

  private SonarQubeEventStream openStream(String connectionId) {
    return new SonarQubeEventStream(storageService.connection(connectionId), languageSupportRepository.getEnabledLanguagesInConnectedMode(), connectionId, serverApiProvider,
      e -> notifyClient(connectionId, e));
  }

  private void notifyClient(String connectionId, ServerEvent serverEvent) {
    client.didReceiveServerEvent(new DidReceiveServerEventParams(connectionId, serverEvent));
  }

  private boolean supportsServerSentEvents(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    return connection != null && connection.getKind() == ConnectionKind.SONARQUBE;
  }

  private void unsubscribe(BindingConfiguration previousBindingConfiguration) {
    if (previousBindingConfiguration.isBound()) {
      var connectionId = requireNonNull(previousBindingConfiguration.getConnectionId());
      var projectKey = requireNonNull(previousBindingConfiguration.getSonarProjectKey());
      if (supportsServerSentEvents(connectionId) && streamsPerConnectionId.containsKey(connectionId)
        && configurationRepository.getBoundScopesByConnection(connectionId).stream().noneMatch(scope -> scope.getSonarProjectKey().equals(projectKey))) {
        streamsPerConnectionId.get(connectionId).unsubscribe(projectKey);
      }
    }
  }

  private void resubscribe(String connectionId) {
    if (supportsServerSentEvents(connectionId) && streamsPerConnectionId.containsKey(connectionId)) {
      streamsPerConnectionId.get(connectionId).resubscribe();
    }
  }

  @PreDestroy
  public void shutdown() {
    streamsPerConnectionId.values().forEach(SonarQubeEventStream::stop);
    streamsPerConnectionId.clear();
  }
}
