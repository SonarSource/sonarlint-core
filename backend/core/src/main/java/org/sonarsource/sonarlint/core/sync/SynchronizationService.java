/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.util.concurrent.ConcurrentHashMap;
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
import org.sonarsource.sonarlint.core.commons.progress.ExecutorServiceShutdownWatchable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
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
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.ServerConnection;
import org.sonarsource.sonarlint.core.serverconnection.SonarServerSettingsChangedEvent;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;
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
  private final ApplicationEventPublisher applicationEventPublisher;
  private final ExecutorServiceShutdownWatchable<ScheduledExecutorService> scheduledSynchronizer
    = new ExecutorServiceShutdownWatchable<>(Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "SonarLint Local Storage Synchronizer")));
  private final Set<String> ignoreBranchEventForScopes = ConcurrentHashMap.newKeySet();

  public SynchronizationService(SonarLintRpcClient client, ConfigurationRepository configurationRepository, LanguageSupportRepository languageSupportRepository,
                                ServerApiProvider serverApiProvider, StorageService storageService, InitializeParams params,
                                SynchronizationTimestampRepository synchronizationTimestampRepository, TaintSynchronizationService taintSynchronizationService,
                                IssueSynchronizationService issueSynchronizationService, HotspotSynchronizationService hotspotSynchronizationService,
                                SonarProjectBranchesSynchronizationService sonarProjectBranchesSynchronizationService,
                                ApplicationEventPublisher applicationEventPublisher) {
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
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @PostConstruct
  public void startScheduledSync() {
    if (!branchSpecificSynchronizationEnabled) {
      return;
    }
    var initialDelay = Long.parseLong(System.getProperty("sonarlint.internal.synchronization.initialDelay", "3600"));
    var syncPeriod = Long.parseLong(System.getProperty("sonarlint.internal.synchronization.period", "3600"));
    var cancelMonitor = new SonarLintCancelMonitor();
    cancelMonitor.watchForShutdown(scheduledSynchronizer);
    scheduledSynchronizer.getWrapped().scheduleAtFixedRate(() -> safeSyncAllConfigScopes(cancelMonitor), initialDelay, syncPeriod, TimeUnit.SECONDS);
  }

  // we must catch errors for the scheduling to not stop
  private void safeSyncAllConfigScopes(SonarLintCancelMonitor cancelMonitor) {
    try {
      synchronizeProjectsSync(configurationRepository.getBoundScopeByConnectionAndSonarProject(), cancelMonitor);
    } catch (Exception e) {
      LOG.error("Error during the auto-sync", e);
    }
  }

  private void synchronizeProjectsAsync(Map<String, Map<String, Collection<BoundScope>>> boundScopeByConnectionAndSonarProject) {
    var cancelMonitor = new SonarLintCancelMonitor();
    cancelMonitor.watchForShutdown(scheduledSynchronizer);
    scheduledSynchronizer.submit(() -> synchronizeProjectsSync(boundScopeByConnectionAndSonarProject, cancelMonitor));
  }

  private void synchronizeProjectsSync(Map<String, Map<String, Collection<BoundScope>>> boundScopeByConnectionAndSonarProject, SonarLintCancelMonitor cancelMonitor) {
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
        synchronizeProjectsOfTheSameConnection(connectionId, entry.getValue(), progressNotifier, synchronizedConfScopeIds, progress, progressGap, cancelMonitor);
        progress += progressGap;
      }
      if (!synchronizedConfScopeIds.isEmpty()) {
        applicationEventPublisher.publishEvent(new ConfigurationScopesSynchronizedEvent(synchronizedConfScopeIds));
        client.didSynchronizeConfigurationScopes(new DidSynchronizeConfigurationScopeParams(synchronizedConfScopeIds));
      }
    });
  }

  private void synchronizeProjectsOfTheSameConnection(String connectionId, Map<String, Collection<BoundScope>> boundScopeBySonarProject, ProgressNotifier notifier,
                                                      Set<String> synchronizedConfScopeIds,
                                                      float progress, float progressGap, SonarLintCancelMonitor cancelMonitor) {
    if (boundScopeBySonarProject.isEmpty()) {
      return;
    }
    serverApiProvider.getServerApi(connectionId).ifPresent(serverApi -> {
      var subProgressGap = progressGap / boundScopeBySonarProject.size();
      var subProgress = progress;
      for (var entry : boundScopeBySonarProject.entrySet()) {
        var sonarProjectKey = entry.getKey();
        notifier.notify("Synchronizing project '" + sonarProjectKey + "'...", Math.round(subProgress));
        issueSynchronizationService.syncServerIssuesForProject(connectionId, sonarProjectKey, cancelMonitor);
        taintSynchronizationService.synchronizeTaintVulnerabilities(connectionId, sonarProjectKey, cancelMonitor);
        hotspotSynchronizationService.syncServerHotspotsForProject(connectionId, sonarProjectKey, cancelMonitor);
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
    scopesToSynchronize.forEach(this::synchronizeConnectionAndProjectsIfNeededAsync);
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
      synchronizeConnectionAndProjectsIfNeededAsync(newConnectionId,
        List.of(new BoundScope(configScopeId, newConnectionId, requireNonNull(event.getNewConfig().getSonarProjectKey()))));
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
    synchronizeConnectionAndProjectsIfNeededAsync(connectionId, bindingsForUpdatedConnection);
  }

  private void synchronizeConnectionAndProjectsIfNeededAsync(String connectionId, Collection<BoundScope> boundScopes) {
    var cancelMonitor = new SonarLintCancelMonitor();
    cancelMonitor.watchForShutdown(scheduledSynchronizer);
    scheduledSynchronizer.submit(() ->
      serverApiProvider.getServerApi(connectionId)
        .ifPresent(serverApi -> synchronizeConnectionAndProjectsIfNeededSync(connectionId, serverApi, boundScopes, cancelMonitor)));
  }

  private void synchronizeConnectionAndProjectsIfNeededSync(String connectionId, ServerApi serverApi, Collection<BoundScope> boundScopes, SonarLintCancelMonitor cancelMonitor) {
    var scopesToSync = boundScopes.stream().filter(this::shouldSynchronizeScope).collect(toList());
    if (scopesToSync.isEmpty()) {
      return;
    }
    scopesToSync.forEach(scope -> synchronizationTimestampRepository.setLastSynchronizationTimestampToNow(scope.getConfigScopeId()));
    // We will already trigger a sync of the project storage so we can temporarily ignore branch changed event for these config scopes
    ignoreBranchEventForScopes.addAll(scopesToSync.stream().map(BoundScope::getConfigScopeId).collect(toSet()));
    var serverConnection = getServerConnection(connectionId, serverApi);
    try {
      LOG.debug("Synchronizing storage of connection '{}'", connectionId);
      serverConnection.sync(serverApi, cancelMonitor);
      applicationEventPublisher.publishEvent(new PluginsSynchronizedEvent(connectionId));
      var scopesPerProjectKey = scopesToSync.stream().collect(groupingBy(BoundScope::getSonarProjectKey, mapping(BoundScope::getConfigScopeId, toSet())));
      scopesPerProjectKey.forEach((projectKey, configScopeIds) -> {
        LOG.debug("Synchronizing storage of Sonar project '{}' for connection '{}'", projectKey, connectionId);
        var analyzerConfigUpdateSummary = serverConnection.sync(serverApi, projectKey, cancelMonitor);
        // XXX we might want to group those 2 events under one
        if (!analyzerConfigUpdateSummary.getUpdatedSettingsValueByKey().isEmpty()) {
          applicationEventPublisher.publishEvent(
            new SonarServerSettingsChangedEvent(configScopeIds, analyzerConfigUpdateSummary.getUpdatedSettingsValueByKey())
          );
        }
        applicationEventPublisher.publishEvent(new AnalyzerConfigurationSynchronized(configScopeIds));
        sonarProjectBranchesSynchronizationService.sync(connectionId, projectKey, cancelMonitor);
      });
      synchronizeProjectsSync(
        Map.of(connectionId, scopesToSync.stream().map(scope -> new BoundScope(scope.getConfigScopeId(), connectionId, scope.getSonarProjectKey()))
          .collect(groupingBy(BoundScope::getSonarProjectKey, toCollection(ArrayList::new)))), cancelMonitor);
    } catch (Exception e) {
      LOG.error("Error during synchronization", e);
    } finally {
      ignoreBranchEventForScopes.removeAll(scopesToSync.stream().map(BoundScope::getConfigScopeId).collect(toSet()));
    }
  }

  private boolean shouldSynchronizeScope(BoundScope configScope) {
    var syncPeriod = Long.parseLong(System.getProperty("sonarlint.internal.synchronization.scope.period", "300"));
    boolean result = synchronizationTimestampRepository.getLastSynchronizationDate(configScope.getConfigScopeId())
      .map(lastSync -> lastSync.isBefore(Instant.now().minus(syncPeriod, ChronoUnit.SECONDS)))
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
    if (ignoreBranchEventForScopes.contains(configurationScopeId)) {
      return;
    }
    configurationRepository.getEffectiveBinding(configurationScopeId).ifPresent(binding -> synchronizeProjectsAsync(Map.of(requireNonNull(binding.getConnectionId()),
      Map.of(binding.getSonarProjectKey(), List.of(new BoundScope(configurationScopeId, binding.getConnectionId(), binding.getSonarProjectKey()))))));
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(scheduledSynchronizer, 5, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop synchronizer executor service in a timely manner");
    }
  }
}
