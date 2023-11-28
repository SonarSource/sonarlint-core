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
package org.sonarsource.sonarlint.core.sync;

import com.google.common.util.concurrent.MoreExecutors;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.branch.MatchedSonarProjectBranchChangedEvent;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionCredentialsChangedEvent;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.progress.ProgressNotifier;
import org.sonarsource.sonarlint.core.progress.TaskManager;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidUpdatePluginsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.ServerConnection;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.event.EventListener;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Named
@Singleton
public class SynchronizationService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintRpcClient client;
  private final ConfigurationRepository configurationRepository;
  private final LanguageSupportRepository languageSupportRepository;
  private final ServerApiProvider serverApiProvider;
  private final TaskManager taskManager;
  private final StorageService storageService;
  private final Set<String> connectedModeEmbeddedPluginKeys;
  private final boolean branchSpecificSynchronizationEnabled;
  private final boolean fullSynchronizationEnabled;
  private final SynchronizationTimestampRepository synchronizationTimestampRepository;
  private final TaintSynchronizationService taintSynchronizationService;
  private final IssueSynchronizationService issueSynchronizationService;
  private final HotspotSynchronizationService hotspotSynchronizationService;
  private final SonarProjectBranchesSynchronizationService sonarProjectBranchesSynchronizationService;
  private ScheduledExecutorService scheduledSynchronizer;

  public SynchronizationService(SonarLintRpcClient client, ConfigurationRepository configurationRepository, LanguageSupportRepository languageSupportRepository,
    ServerApiProvider serverApiProvider, StorageService storageService, InitializeParams params,
    SynchronizationTimestampRepository synchronizationTimestampRepository, TaintSynchronizationService taintSynchronizationService,
    IssueSynchronizationService issueSynchronizationService, HotspotSynchronizationService hotspotSynchronizationService,
    SonarProjectBranchesSynchronizationService sonarProjectBranchesSynchronizationService) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.languageSupportRepository = languageSupportRepository;
    this.serverApiProvider = serverApiProvider;
    this.taskManager = new TaskManager(client);
    this.storageService = storageService;
    this.connectedModeEmbeddedPluginKeys = params.getConnectedModeEmbeddedPluginPathsByKey().keySet();
    this.branchSpecificSynchronizationEnabled = params.getFeatureFlags().shouldSynchronizeProjects();
    this.fullSynchronizationEnabled = params.getFeatureFlags().shouldManageFullSynchronization();
    this.synchronizationTimestampRepository = synchronizationTimestampRepository;
    this.taintSynchronizationService = taintSynchronizationService;
    this.issueSynchronizationService = issueSynchronizationService;
    this.hotspotSynchronizationService = hotspotSynchronizationService;
    this.sonarProjectBranchesSynchronizationService = sonarProjectBranchesSynchronizationService;
  }

  @PostConstruct
  public void startScheduledSync() {
    if (!branchSpecificSynchronizationEnabled) {
      return;
    }
    scheduledSynchronizer = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "SonarLint Local Storage Synchronizer"));
    scheduledSynchronizer.scheduleAtFixedRate(this::safeAutoSync, 3600L, 3600L, TimeUnit.SECONDS);
  }

  // we must catch errors for the scheduling to not stop
  private void safeAutoSync() {
    try {
      autoSync();
    } catch (Exception e) {
      LOG.error("Error during the auto-sync", e);
    }
  }

  private void autoSync() {
    synchronizeProjects(configurationRepository.getBoundScopeByConnectionAndSonarProject());
  }

  private void synchronizeProjects(Map<String, Map<String, Collection<BoundScope>>> boundScopeByConnectionAndSonarProject) {
    if (boundScopeByConnectionAndSonarProject.isEmpty()) {
      return;
    }
    taskManager.startTask(null, "Synchronizing projects...", null, false, false, progressNotifier -> {
      var connectionsCount = boundScopeByConnectionAndSonarProject.keySet().size();
      var progressGap = 100f / connectionsCount;
      var progress = 0f;
      var synchronizedConfScopeIds = new HashSet<String>();
      for (var entry : boundScopeByConnectionAndSonarProject.entrySet()) {
        var connectionId = entry.getKey();
        progressNotifier.notify("Synchronizing with '" + connectionId + "'...", Math.round(progress));
        synchronizeProjectsOfTheSameConnection(connectionId, entry.getValue(), progressNotifier, synchronizedConfScopeIds, progress, progressGap);
        progress += progressGap;
      }
      if (!synchronizedConfScopeIds.isEmpty()) {
        client.didSynchronizeConfigurationScopes(new DidSynchronizeConfigurationScopeParams(synchronizedConfScopeIds));
      }
    });
  }

  private void synchronizeProjectsOfTheSameConnection(String connectionId, Map<String, Collection<BoundScope>> boundScopeBySonarProject, ProgressNotifier notifier,
    Set<String> synchronizedConfScopeIds,
    float progress, float progressGap) {
    if (boundScopeBySonarProject.isEmpty()) {
      return;
    }
    serverApiProvider.getServerApi(connectionId).ifPresent(serverApi -> {
      var subProgressGap = progressGap / boundScopeBySonarProject.size();
      var subProgress = progress;
      for (var entry : boundScopeBySonarProject.entrySet()) {
        var sonarProjectKey = entry.getKey();
        notifier.notify("Synchronizing project '" + sonarProjectKey + "'...", Math.round(subProgress));
        issueSynchronizationService.syncServerIssuesForProject(connectionId, sonarProjectKey);
        taintSynchronizationService.synchronizeTaintVulnerabilities(connectionId, sonarProjectKey);
        hotspotSynchronizationService.syncServerHotspotsForProject(connectionId, sonarProjectKey);
        synchronizedConfScopeIds.addAll(entry.getValue().stream().map(BoundScope::getConfigScopeId).collect(toSet()));
        subProgress += subProgressGap;
      }
    });
  }

  @NotNull
  public ServerConnection getServerConnection(String connectionId, ServerApi serverApi) {
    return new ServerConnection(storageService.getStorageFacade(), connectionId, serverApi.isSonarCloud(),
      languageSupportRepository.getEnabledLanguagesInConnectedMode(), connectedModeEmbeddedPluginKeys);
  }

  @EventListener
  public void onConfigurationsScopeAdded(ConfigurationScopesAddedEvent event) {
    if (!fullSynchronizationEnabled) {
      return;
    }
    LOG.debug("Synchronizing new configuration scopes: {}", event.getAddedConfigurationScopeIds());
    var scopesToSynchronize = event.getAddedConfigurationScopeIds()
      .stream().map(configurationRepository::getBoundScope)
      .filter(Objects::nonNull)
      .collect(groupingBy(BoundScope::getConnectionId));
    scopesToSynchronize.forEach(
      (connectionId, boundScopes) -> serverApiProvider.getServerApi(connectionId)
        .ifPresent(serverApi -> synchronizeConnectionAndProjectsIfNeeded(connectionId, serverApi, boundScopes)));
  }

  @EventListener
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent event) {
    synchronizationTimestampRepository.clearLastSynchronizationTimestamp(event.getRemovedConfigurationScopeId());
  }

  @EventListener
  public void onBindingChanged(BindingConfigChangedEvent event) {
    var configScopeId = event.getConfigScopeId();
    synchronizationTimestampRepository.clearLastSynchronizationTimestamp(configScopeId);
    var newConnectionId = event.getNewConfig().getConnectionId();
    if (newConnectionId != null) {
      serverApiProvider.getServerApi(newConnectionId).ifPresent(serverApi -> synchronizeConnectionAndProjectsIfNeeded(newConnectionId, serverApi,
        List.of(new BoundScope(configScopeId, newConnectionId, requireNonNull(event.getNewConfig().getSonarProjectKey())))));
    }
  }

  @EventListener
  public void onConnectionCredentialsChanged(ConnectionCredentialsChangedEvent event) {
    if (!fullSynchronizationEnabled) {
      return;
    }
    var connectionId = event.getConnectionId();
    LOG.debug("Synchronizing connection '{}' after credentials changed", connectionId);
    var bindingsForUpdatedConnection = configurationRepository.getBoundScopesToConnection(connectionId);
    // Clear the synchronization timestamp for all the scopes so that sync is not skipped
    bindingsForUpdatedConnection.forEach(boundScope -> synchronizationTimestampRepository.clearLastSynchronizationTimestamp(boundScope.getConfigScopeId()));
    serverApiProvider.getServerApi(connectionId)
      .ifPresent(serverApi -> synchronizeConnectionAndProjectsIfNeeded(connectionId, serverApi, bindingsForUpdatedConnection));
  }

  private void synchronizeConnectionAndProjectsIfNeeded(String connectionId, ServerApi serverApi, Collection<BoundScope> boundScopes) {
    var scopesToSync = boundScopes.stream().filter(this::shouldSynchronizeScope).collect(toList());
    if (scopesToSync.isEmpty()) {
      return;
    }
    scopesToSync.forEach(scope -> synchronizationTimestampRepository.setLastSynchronizationTimestampToNow(scope.getConfigScopeId()));
    var serverConnection = getServerConnection(connectionId, serverApi);
    try {
      LOG.debug("Synchronizing storage of connection '{}'", connectionId);
      var anyPluginUpdated = serverConnection.sync(serverApi);
      if (anyPluginUpdated) {
        client.didUpdatePlugins(new DidUpdatePluginsParams(connectionId));
      }
      var scopesPerProjectKey = scopesToSync.stream().collect(groupingBy(BoundScope::getSonarProjectKey, mapping(BoundScope::getConfigScopeId, toSet())));
      scopesPerProjectKey.forEach((projectKey, configScopeIds) -> {
        LOG.debug("Synchronizing storage of Sonar project '{}' for connection '{}'", projectKey, connectionId);
        serverConnection.sync(serverApi, projectKey);
        sonarProjectBranchesSynchronizationService.sync(connectionId, projectKey);
      });
      synchronizeProjects(
        Map.of(connectionId, scopesToSync.stream().map(scope -> new BoundScope(scope.getConfigScopeId(), connectionId, scope.getSonarProjectKey()))
          .collect(groupingBy(BoundScope::getSonarProjectKey, toCollection(ArrayList::new)))));
    } catch (Exception e) {
      LOG.error("Error during synchronization", e);
    }
  }

  private boolean shouldSynchronizeScope(BoundScope configScope) {
    var result = synchronizationTimestampRepository.getLastSynchronizationDate(configScope.getConfigScopeId())
      .map(lastSync -> lastSync.isBefore(Instant.now().minus(5, ChronoUnit.MINUTES)))
      .orElse(true);
    if (!result) {
      LOG.debug("Skipping synchronization of configuration scope '{}' because it was synchronized recently", configScope.getConfigScopeId());
    }
    return result;
  }

  @EventListener
  public void onSonarProjectBranchChanged(MatchedSonarProjectBranchChangedEvent changedEvent) {
    if (!branchSpecificSynchronizationEnabled) {
      return;
    }
    var configurationScopeId = changedEvent.getConfigurationScopeId();
    configurationRepository.getEffectiveBinding(configurationScopeId).ifPresent(binding -> synchronizeProjects(Map.of(requireNonNull(binding.getConnectionId()),
      Map.of(binding.getSonarProjectKey(), List.of(new BoundScope(configurationScopeId, binding.getConnectionId(), binding.getSonarProjectKey()))))));
  }

  @PreDestroy
  public void shutdown() {
    if (scheduledSynchronizer != null && !MoreExecutors.shutdownAndAwaitTermination(scheduledSynchronizer, 5, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop synchronizer executor service in a timely manner");
    }
  }
}
