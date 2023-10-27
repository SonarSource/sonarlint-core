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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerTaintVulnerabilityChangedOrClosedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerTaintVulnerabilityRaisedEvent;
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
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotClosedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotRaisedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.ServerHotspotEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SonarProjectEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityClosedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.event.EventListener;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

public class ServerEventsService {
  private final SonarLintRpcClient client;
  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final StorageService storageService;
  private final ServerApiProvider serverApiProvider;
  private final LanguageSupportRepository languageSupportRepository;
  private final boolean shouldManageServerSentEvents;
  private final Map<String, SonarQubeEventStream> streamsPerConnectionId = new ConcurrentHashMap<>();

  public ServerEventsService(SonarLintRpcClient client, ConfigurationRepository configurationRepository, ConnectionConfigurationRepository connectionConfigurationRepository,
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

  @EventListener
  public void handle(ConfigurationScopesAddedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    subscribeAll(event.getAddedConfigurationScopeIds());
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
      unsubscribe(removedBindingConfiguration);
    }
  }

  @EventListener
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

  @EventListener
  public void handle(ConnectionConfigurationAddedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    // This is only to handle the case where binding was invalid (connection did not exist) and became valid (matching connection was created)
    var connectionId = event.getAddedConnectionId();
    var boundScopes = configurationRepository.getBoundScopesByConnection(connectionId);
    subscribe(connectionId, boundScopes.stream().map(BoundScope::getSonarProjectKey).collect(toSet()));
  }

  @EventListener
  public void handle(ConnectionConfigurationRemovedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    var stream = streamsPerConnectionId.remove(event.getRemovedConnectionId());
    if (stream != null) {
      stream.stop();
    }
  }

  @EventListener
  public void handle(ConnectionConfigurationUpdatedEvent event) {
    if (!shouldManageServerSentEvents) {
      return;
    }
    // URL might have changed, in doubt resubscribe
    resubscribe(event.getUpdatedConnectionId());
  }

  @EventListener
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
    if (serverEvent instanceof TaintVulnerabilityRaisedEvent) {
      var projectKey = ((TaintVulnerabilityRaisedEvent) serverEvent).getProjectKey();
      var taintEvent = (TaintVulnerabilityRaisedEvent) serverEvent;
      client.didReceiveServerTaintVulnerabilityRaisedEvent(new DidReceiveServerTaintVulnerabilityRaisedEvent(connectionId, projectKey,
        taintEvent.getMainLocation().getFilePath(), taintEvent.getBranchName(), taintEvent.getKey()));
    } else if (serverEvent instanceof TaintVulnerabilityClosedEvent ||
      ((serverEvent instanceof IssueChangedEvent) && !((IssueChangedEvent) serverEvent).getImpactedTaintIssueKeys().isEmpty())) {
        var projectKey = ((SonarProjectEvent) serverEvent).getProjectKey();
        client.didReceiveServerTaintVulnerabilityChangedOrClosedEvent(new DidReceiveServerTaintVulnerabilityChangedOrClosedEvent(connectionId, projectKey));
      } else if (serverEvent instanceof SecurityHotspotChangedEvent ||
        serverEvent instanceof SecurityHotspotClosedEvent ||
        serverEvent instanceof SecurityHotspotRaisedEvent) {
          var projectKey = ((SonarProjectEvent) serverEvent).getProjectKey();
          client.didReceiveServerHotspotEvent(new DidReceiveServerHotspotEvent(connectionId, projectKey, ((ServerHotspotEvent) serverEvent).getFilePath()));
        }
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
