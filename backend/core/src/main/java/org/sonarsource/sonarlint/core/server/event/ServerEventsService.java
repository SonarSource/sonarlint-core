/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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

import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.ConnectionManager;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.util.FailSafeExecutors;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionCredentialsChangedEvent;
import org.sonarsource.sonarlint.core.event.SonarServerEventReceivedEvent;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

public class ServerEventsService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final ConnectionManager connectionManager;
  private final LanguageSupportRepository languageSupportRepository;
  private final boolean shouldManageServerSentEvents;
  private final ApplicationEventPublisher eventPublisher;
  private final Map<String, SonarQubeEventStream> streamsPerConnectionId = new ConcurrentHashMap<>();
  private final ExecutorService executorService = FailSafeExecutors.newSingleThreadExecutor("sonarlint-server-sent-events-subscriber");

  public ServerEventsService(ConfigurationRepository configurationRepository, ConnectionConfigurationRepository connectionConfigurationRepository,
    ConnectionManager connectionManager, LanguageSupportRepository languageSupportRepository, InitializeParams initializeParams, ApplicationEventPublisher eventPublisher) {
    this.configurationRepository = configurationRepository;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.connectionManager = connectionManager;
    this.languageSupportRepository = languageSupportRepository;
    this.shouldManageServerSentEvents = initializeParams.getFeatureFlags().shouldManageServerSentEvents();
    this.eventPublisher = eventPublisher;
  }

  @EventListener
  public void handle(ConfigurationScopesAddedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    executorService.execute(() -> subscribeAll(event.getAddedConfigurationScopeIds()));
  }

  @EventListener
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
      executorService.execute(() -> unsubscribe(removedBindingConfiguration));
    }
  }

  @EventListener
  public void handle(BindingConfigChangedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    var previousBinding = event.getPreviousConfig();
    if (isBindingDifferent(previousBinding, event.getNewConfig())) {
      executorService.execute(() -> {
        unsubscribe(previousBinding);
        subscribe(event.getConfigScopeId());
      });
    }
  }

  @EventListener
  public void handle(ConnectionConfigurationAddedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    // This is only to handle the case where binding was invalid (connection did not exist) and became valid (matching connection was created)
    var connectionId = event.getAddedConnectionId();
    executorService.execute(() -> subscribe(connectionId, configurationRepository.getSonarProjectsUsedForConnection(connectionId)));
  }

  @EventListener
  public void handle(ConnectionConfigurationRemovedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    executorService.execute(() -> {
      var stream = streamsPerConnectionId.remove(event.getRemovedConnectionId());
      if (stream != null) {
        stream.stop();
      }
    });
  }

  @EventListener
  public void handle(ConnectionConfigurationUpdatedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    // URL might have changed, in doubt resubscribe
    executorService.execute(() -> resubscribe(event.getUpdatedConnectionId()));
  }

  @EventListener
  public void handle(ConnectionCredentialsChangedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    executorService.execute(() -> resubscribe(event.getConnectionId()));
  }

  private static boolean isBindingDifferent(BindingConfiguration previousConfig, BindingConfiguration newConfig) {
    return !Objects.equals(previousConfig.getSonarProjectKey(), newConfig.getSonarProjectKey())
      || !Objects.equals(previousConfig.getConnectionId(), newConfig.getConnectionId());
  }

  private void subscribeAll(Set<String> configurationScopeIds) {
    configurationScopeIds.stream()
      .map(configurationRepository::getConfiguredBinding)
      .flatMap(Optional::stream)
      .collect(Collectors.groupingBy(Binding::connectionId, mapping(Binding::sonarProjectKey, toSet())))
      .forEach(this::subscribe);
  }

  private void subscribe(String scopeId) {
    configurationRepository.getConfiguredBinding(scopeId)
      .ifPresent(binding -> subscribe(binding.connectionId(), Set.of(binding.sonarProjectKey())));
  }

  private void subscribe(String connectionId, Set<String> possiblyNewProjectKeys) {
    if (supportsServerSentEvents(connectionId)) {
      var stream = streamsPerConnectionId.computeIfAbsent(connectionId, k -> openStream(connectionId));
      stream.subscribeNew(possiblyNewProjectKeys);
    }
  }

  private SonarQubeEventStream openStream(String connectionId) {
    return new SonarQubeEventStream(languageSupportRepository.getEnabledLanguagesInConnectedMode(), connectionId, connectionManager,
      e -> eventPublisher.publishEvent(new SonarServerEventReceivedEvent(connectionId, e)));
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
        && configurationRepository.getSonarProjectsUsedForConnection(connectionId).stream().noneMatch(usedProjectKey -> usedProjectKey.equals(projectKey))) {
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
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop server-sent events subscriber service in a timely manner");
    }
    streamsPerConnectionId.values().forEach(SonarQubeEventStream::stop);
    streamsPerConnectionId.clear();
  }
}
