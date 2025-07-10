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
package org.sonarsource.sonarlint.core.sync;

import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.branch.MatchedSonarProjectBranchChangedEvent;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ExecutorServiceShutdownWatchable;
import org.sonarsource.sonarlint.core.commons.progress.ProgressIndicator;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.progress.TaskManager;
import org.sonarsource.sonarlint.core.commons.util.FailSafeExecutors;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedWithBindingEvent;
import org.sonarsource.sonarlint.core.event.ConnectionCredentialsChangedEvent;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarlint.core.serverapi.exception.UnauthorizedException;
import org.sonarsource.sonarlint.core.serverconnection.AiCodeFixSettingsSynchronizer;
import org.sonarsource.sonarlint.core.serverconnection.LocalStorageSynchronizer;
import org.sonarsource.sonarlint.core.serverconnection.OrganizationSynchronizer;
import org.sonarsource.sonarlint.core.serverconnection.ServerInfoSynchronizer;
import org.sonarsource.sonarlint.core.serverconnection.SonarServerSettingsChangedEvent;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.PROJECT_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.SECURITY_HOTSPOTS;

public class SynchronizationService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintRpcClient client;
  private final ConfigurationRepository configurationRepository;
  private final LanguageSupportRepository languageSupportRepository;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final TaskManager taskManager;
  private final StorageService storageService;
  private final Set<String> connectedModeEmbeddedPluginKeys;
  private final boolean branchSpecificSynchronizationEnabled;
  private final boolean fullSynchronizationEnabled;
  private final SynchronizationTimestampRepository<String> scopeSynchronizationTimestampRepository = new SynchronizationTimestampRepository<>();
  private final SynchronizationTimestampRepository<Binding> bindingSynchronizationTimestampRepository = new SynchronizationTimestampRepository<>();
  private final SynchronizationTimestampRepository<BranchBinding> branchSynchronizationTimestampRepository = new SynchronizationTimestampRepository<>();
  private final TaintSynchronizationService taintSynchronizationService;
  private final IssueSynchronizationService issueSynchronizationService;
  private final HotspotSynchronizationService hotspotSynchronizationService;
  private final SonarProjectBranchesSynchronizationService sonarProjectBranchesSynchronizationService;
  private final SonarProjectBranchTrackingService sonarProjectBranchTrackingService;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final ExecutorServiceShutdownWatchable<ScheduledExecutorService> scheduledSynchronizer = new ExecutorServiceShutdownWatchable<>(
    FailSafeExecutors.newSingleThreadScheduledExecutor("SonarLint Local Storage Synchronizer"));
  private final Set<String> ignoreBranchEventForScopes = ConcurrentHashMap.newKeySet();
  private final boolean shouldSynchronizeHotspots;

  public SynchronizationService(SonarLintRpcClient client, ConfigurationRepository configurationRepository, LanguageSupportRepository languageSupportRepository,
    SonarQubeClientManager sonarQubeClientManager, TaskManager taskManager, StorageService storageService, InitializeParams params,
    TaintSynchronizationService taintSynchronizationService, IssueSynchronizationService issueSynchronizationService, HotspotSynchronizationService hotspotSynchronizationService,
    SonarProjectBranchesSynchronizationService sonarProjectBranchesSynchronizationService, SonarProjectBranchTrackingService sonarProjectBranchTrackingService,
    ApplicationEventPublisher applicationEventPublisher) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.languageSupportRepository = languageSupportRepository;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.taskManager = taskManager;
    this.storageService = storageService;
    this.connectedModeEmbeddedPluginKeys = params.getConnectedModeEmbeddedPluginPathsByKey().keySet();
    this.branchSpecificSynchronizationEnabled = params.getBackendCapabilities().contains(PROJECT_SYNCHRONIZATION);
    this.shouldSynchronizeHotspots = params.getBackendCapabilities().contains(SECURITY_HOTSPOTS);
    this.fullSynchronizationEnabled = params.getBackendCapabilities().contains(FULL_SYNCHRONIZATION);
    this.taintSynchronizationService = taintSynchronizationService;
    this.issueSynchronizationService = issueSynchronizationService;
    this.hotspotSynchronizationService = hotspotSynchronizationService;
    this.sonarProjectBranchesSynchronizationService = sonarProjectBranchesSynchronizationService;
    this.sonarProjectBranchTrackingService = sonarProjectBranchTrackingService;
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
    scheduledSynchronizer.execute(() -> synchronizeProjectsSync(boundScopeByConnectionAndSonarProject, cancelMonitor));
  }

  private void synchronizeProjectsSync(Map<String, Map<String, Collection<BoundScope>>> boundScopeByConnectionAndSonarProject, SonarLintCancelMonitor cancelMonitor) {
    if (boundScopeByConnectionAndSonarProject.isEmpty()) {
      return;
    }
    taskManager.createAndRunTask(null, UUID.randomUUID(), "Synchronizing projects...", null, false, false, progressIndicator -> {
      var connectionsCount = boundScopeByConnectionAndSonarProject.size();
      var progressGap = 100f / connectionsCount;
      var progress = 0f;
      var synchronizedConfScopeIds = new HashSet<String>();
      for (var entry : boundScopeByConnectionAndSonarProject.entrySet()) {
        var connectionId = entry.getKey();
        progressIndicator.notifyProgress("Synchronizing with '" + connectionId + "'...", Math.round(progress));
        synchronizeProjectsOfTheSameConnection(connectionId, entry.getValue(), progressIndicator, synchronizedConfScopeIds, progress, progressGap, cancelMonitor);
        progress += progressGap;
      }
      if (!synchronizedConfScopeIds.isEmpty()) {
        applicationEventPublisher.publishEvent(new ConfigurationScopesSynchronizedEvent(synchronizedConfScopeIds));
        client.didSynchronizeConfigurationScopes(new DidSynchronizeConfigurationScopeParams(synchronizedConfScopeIds));
      }
    }, cancelMonitor);
  }

  private void synchronizeProjectsOfTheSameConnection(String connectionId, Map<String, Collection<BoundScope>> boundScopeBySonarProject, ProgressIndicator progressIndicator,
    Set<String> synchronizedConfScopeIds, float progress, float progressGap, SonarLintCancelMonitor cancelMonitor) {
    if (boundScopeBySonarProject.isEmpty()) {
      return;
    }
    sonarQubeClientManager.withActiveClient(connectionId, serverApi -> {
      var subProgressGap = progressGap / boundScopeBySonarProject.size();
      var subProgress = progress;
      for (var entry : boundScopeBySonarProject.entrySet()) {
        synchronizeProjectWithProgress(serverApi, connectionId, entry.getKey(), entry.getValue(), progressIndicator, cancelMonitor, synchronizedConfScopeIds, subProgress);
        subProgress += subProgressGap;
      }
    });
  }

  private void synchronizeProjectWithProgress(ServerApi serverApi, String connectionId, String sonarProjectKey, Collection<BoundScope> boundScopes,
    ProgressIndicator progressIndicator, SonarLintCancelMonitor cancelMonitor, Set<String> synchronizedConfigScopeIds, float subProgress) {
    var allScopes = configurationRepository.getBoundScopesToConnectionAndSonarProject(connectionId, sonarProjectKey);
    var allScopesByOptBranch = allScopes.stream()
      .collect(groupingBy(b -> sonarProjectBranchTrackingService.awaitEffectiveSonarProjectBranch(b.getConfigScopeId())));
    allScopesByOptBranch
      .forEach((branchNameOpt, scopes) -> branchNameOpt.ifPresent(branchName -> {
        var branchBinding = new BranchBinding(new Binding(connectionId, sonarProjectKey), branchName);
        if (shouldSynchronizeBranch(branchBinding)) {
          branchSynchronizationTimestampRepository.setLastSynchronizationTimestampToNow(branchBinding);
          progressIndicator.notifyProgress("Synchronizing project '" + sonarProjectKey + "'...", (int) subProgress);
          issueSynchronizationService.syncServerIssuesForProject(serverApi, connectionId, sonarProjectKey, branchName, cancelMonitor);
          taintSynchronizationService.synchronizeTaintVulnerabilities(serverApi, connectionId, sonarProjectKey, branchName, cancelMonitor);
          if (shouldSynchronizeHotspots) {
            hotspotSynchronizationService.syncServerHotspotsForProject(serverApi, connectionId, sonarProjectKey, branchName, cancelMonitor);
          }
          synchronizedConfigScopeIds.addAll(boundScopes.stream().map(BoundScope::getConfigScopeId).collect(toSet()));
        }
      }));
  }

  public Version readOrSynchronizeServerVersion(String connectionId, ServerApi serverApi, SonarLintCancelMonitor cancelMonitor) {
    var serverInfoSynchronizer = new ServerInfoSynchronizer(storageService.connection(connectionId));
    return serverInfoSynchronizer.readOrSynchronizeServerInfo(serverApi, cancelMonitor).version();
  }

  @EventListener
  public void onConfigurationsScopeAdded(ConfigurationScopesAddedWithBindingEvent event) {
    if (!fullSynchronizationEnabled) {
      return;
    }
    LOG.debug("Synchronizing new configuration scopes: {}", event.getConfigScopeIds());
    var scopesToSynchronize = event.getConfigScopeIds()
      .stream().map(configurationRepository::getBoundScope)
      .filter(Objects::nonNull)
      .collect(groupingBy(BoundScope::getConnectionId));
    scopesToSynchronize.forEach(this::synchronizeConnectionAndProjectsIfNeededAsync);
  }

  @EventListener
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent event) {
    var scopeId = event.getRemovedConfigurationScopeId();
    LOG.debug("Config scope {} removed, managing caches", scopeId);
    scopeSynchronizationTimestampRepository.clearLastSynchronizationTimestamp(scopeId);
    var previousBinding = event.getRemovedBindingConfiguration();
    if (previousBinding.isBound()) {
      var connectionId = requireNonNull(previousBinding.connectionId());
      var projectKey = requireNonNull(previousBinding.sonarProjectKey());
      var scopes = configurationRepository.getBoundScopesToConnectionAndSonarProject(connectionId, projectKey);
      if (scopes.isEmpty()) {
        // no remaining scope bound to this connection and project, clear the cache
        LOG.debug("Clearing the synchronization cache for {}, binding={}", scopeId, previousBinding);
        var binding = new Binding(connectionId, projectKey);
        bindingSynchronizationTimestampRepository.clearLastSynchronizationTimestamp(binding);
        branchSynchronizationTimestampRepository.clearLastSynchronizationTimestampIf(branchBinding -> branchBinding.getBinding().equals(binding));
      } else {
        LOG.debug("Other config scopes are still bound to {}, see {}, keeping the cache", previousBinding, scopes);
      }
    } else {
      LOG.debug("Removed config scope was not bound, {}, keeping the cache", previousBinding);
    }
  }

  @EventListener
  public void onBindingChanged(BindingConfigChangedEvent event) {
    if (!fullSynchronizationEnabled) {
      return;
    }
    var configScopeId = event.configScopeId();
    scopeSynchronizationTimestampRepository.clearLastSynchronizationTimestamp(configScopeId);
    if (event.previousConfig().isBound()) {
      // when unbinding, we want to let future rebinds trigger a sync
      var previousBinding = new Binding(requireNonNull(event.previousConfig().connectionId()), requireNonNull(event.previousConfig().sonarProjectKey()));
      bindingSynchronizationTimestampRepository.clearLastSynchronizationTimestamp(previousBinding);
      branchSynchronizationTimestampRepository.clearLastSynchronizationTimestampIf(branchBinding -> branchBinding.getBinding().equals(previousBinding));
    }
    var newConnectionId = event.newConfig().connectionId();
    if (newConnectionId != null) {
      synchronizeConnectionAndProjectsIfNeededAsync(
        newConnectionId,
        List.of(new BoundScope(configScopeId, newConnectionId, requireNonNull(event.newConfig().sonarProjectKey()))));
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
    bindingsForUpdatedConnection.forEach(boundScope -> {
      scopeSynchronizationTimestampRepository.clearLastSynchronizationTimestamp(boundScope.getConfigScopeId());
      var binding = new Binding(connectionId, boundScope.getSonarProjectKey());
      bindingSynchronizationTimestampRepository.clearLastSynchronizationTimestamp(binding);
      branchSynchronizationTimestampRepository.clearLastSynchronizationTimestampIf(branchBinding -> branchBinding.getBinding().equals(binding));
    });
    synchronizeConnectionAndProjectsIfNeededAsync(connectionId, bindingsForUpdatedConnection);
  }

  private void synchronizeConnectionAndProjectsIfNeededAsync(String connectionId, Collection<BoundScope> boundScopes) {
    var cancelMonitor = new SonarLintCancelMonitor();
    cancelMonitor.watchForShutdown(scheduledSynchronizer);
    scheduledSynchronizer.execute(
      () -> sonarQubeClientManager.withActiveClient(connectionId, serverApi -> synchronizeConnectionAndProjectsIfNeededSync(connectionId, serverApi, boundScopes, cancelMonitor)));
  }

  private void synchronizeConnectionAndProjectsIfNeededSync(String connectionId, ServerApi serverApi, Collection<BoundScope> boundScopes, SonarLintCancelMonitor cancelMonitor) {
    var scopesToSync = boundScopes.stream().filter(this::shouldSynchronizeScope).toList();
    if (scopesToSync.isEmpty()) {
      return;
    }
    scopesToSync.forEach(scope -> scopeSynchronizationTimestampRepository.setLastSynchronizationTimestampToNow(scope.getConfigScopeId()));
    // We will already trigger a sync of the project storage so we can temporarily ignore branch changed event for these config scopes
    ignoreBranchEventForScopes.addAll(scopesToSync.stream().map(BoundScope::getConfigScopeId).collect(toSet()));
    var enabledLanguagesToSync = languageSupportRepository.getEnabledLanguagesInConnectedMode().stream()
      .filter(SonarLanguage::shouldSyncInConnectedMode).collect(Collectors.toCollection(LinkedHashSet::new));
    var storage = storageService.connection(connectionId);
    var serverInfoSynchronizer = new ServerInfoSynchronizer(storage);
    var storageSynchronizer = new LocalStorageSynchronizer(enabledLanguagesToSync, connectedModeEmbeddedPluginKeys, serverInfoSynchronizer, storage);
    var aiCodeFixSynchronizer = new AiCodeFixSettingsSynchronizer(storage, new OrganizationSynchronizer(storage));
    try {
      LOG.debug("Synchronizing storage of connection '{}'", connectionId);
      var summary = storageSynchronizer.synchronizeServerInfosAndPlugins(serverApi, cancelMonitor);
      if (summary.anyPluginSynchronized()) {
        applicationEventPublisher.publishEvent(new PluginsSynchronizedEvent(connectionId));
      }
      scopesToSync = scopesToSync.stream()
        .filter(boundScope -> shouldSynchronizeBinding(new Binding(connectionId, boundScope.getSonarProjectKey()))).toList();
      var scopesPerProjectKey = scopesToSync.stream()
        .collect(groupingBy(BoundScope::getSonarProjectKey, mapping(BoundScope::getConfigScopeId, toSet())));
      aiCodeFixSynchronizer.synchronize(serverApi, summary.version(), scopesPerProjectKey.keySet(), cancelMonitor);
      scopesPerProjectKey.forEach((projectKey, configScopeIds) -> {
        bindingSynchronizationTimestampRepository.setLastSynchronizationTimestampToNow(new Binding(connectionId, projectKey));
        LOG.debug("Synchronizing storage of Sonar project '{}' for connection '{}'", projectKey, connectionId);
        var analyzerConfigUpdateSummary = storageSynchronizer.synchronizeAnalyzerConfig(serverApi, projectKey, cancelMonitor);
        // XXX we might want to group those 2 events under one
        if (!analyzerConfigUpdateSummary.getUpdatedSettingsValueByKey().isEmpty()) {
          applicationEventPublisher.publishEvent(
            new SonarServerSettingsChangedEvent(configScopeIds, analyzerConfigUpdateSummary.getUpdatedSettingsValueByKey()));
        }
        applicationEventPublisher.publishEvent(new AnalyzerConfigurationSynchronized(configScopeIds));
        sonarProjectBranchesSynchronizationService.sync(connectionId, projectKey, cancelMonitor);
      });
      synchronizeProjectsSync(
        Map.of(connectionId, scopesToSync.stream().map(scope -> new BoundScope(scope.getConfigScopeId(), connectionId, scope.getSonarProjectKey()))
          .collect(groupingBy(BoundScope::getSonarProjectKey, toCollection(ArrayList::new)))),
        cancelMonitor);
    } catch (Exception e) {
      LOG.error("Error during synchronization", e);
      if (e instanceof UnauthorizedException || e instanceof ForbiddenException) {
        throw e;
      }
    } finally {
      ignoreBranchEventForScopes.removeAll(scopesToSync.stream().map(BoundScope::getConfigScopeId).collect(toSet()));
    }
  }

  private boolean shouldSynchronizeBinding(Binding binding) {
    boolean result = bindingSynchronizationTimestampRepository.getLastSynchronizationDate(binding)
      .map(lastSync -> lastSync.isBefore(Instant.now().minus(getSyncPeriod(), ChronoUnit.SECONDS)))
      .orElse(true);
    if (!result) {
      LOG.debug("Skipping synchronization of binding '{}' because it was synchronized recently", binding);
    }
    return result;
  }

  private boolean shouldSynchronizeScope(BoundScope configScope) {
    boolean result = scopeSynchronizationTimestampRepository.getLastSynchronizationDate(configScope.getConfigScopeId())
      .map(lastSync -> lastSync.isBefore(Instant.now().minus(getSyncPeriod(), ChronoUnit.SECONDS)))
      .orElse(true);
    if (!result) {
      LOG.debug("Skipping synchronization of configuration scope '{}' because it was synchronized recently", configScope.getConfigScopeId());
    }
    return result;
  }

  private boolean shouldSynchronizeBranch(BranchBinding branchBinding) {
    boolean result = branchSynchronizationTimestampRepository.getLastSynchronizationDate(branchBinding)
      .map(lastSync -> lastSync.isBefore(Instant.now().minus(getSyncPeriod(), ChronoUnit.SECONDS)))
      .orElse(true);
    if (!result) {
      LOG.debug("Skipping synchronization of branch '{}' because it was synchronized recently", branchBinding.getBranchName());
    }
    return result;
  }

  private static long getSyncPeriod() {
    return Long.parseLong(System.getProperty("sonarlint.internal.synchronization.scope.period", "300"));
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
    configurationRepository.getEffectiveBinding(configurationScopeId).ifPresent(binding -> synchronizeProjectsAsync(Map.of(requireNonNull(binding.connectionId()),
      Map.of(binding.sonarProjectKey(), List.of(new BoundScope(configurationScopeId, binding))))));
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(scheduledSynchronizer, 5, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop synchronizer executor service in a timely manner");
    }
  }
}
