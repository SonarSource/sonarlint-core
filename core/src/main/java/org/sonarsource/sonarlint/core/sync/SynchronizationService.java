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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionCredentialsChangedEvent;
import org.sonarsource.sonarlint.core.event.MatchedSonarProjectBranchChangedEvent;
import org.sonarsource.sonarlint.core.event.TaintVulnerabilitiesSynchronizedEvent;
import org.sonarsource.sonarlint.core.file.FilePathTranslation;
import org.sonarsource.sonarlint.core.file.FilePathTranslationRepository;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.progress.ProgressNotifier;
import org.sonarsource.sonarlint.core.progress.TaskManager;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListAllFilePathsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidUpdatePluginsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.ServerConnection;
import org.sonarsource.sonarlint.core.serverconnection.prefix.FileTreeMatcher;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Named
@Singleton
public class SynchronizationService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintRpcClient client;
  private final ConfigurationRepository configurationRepository;
  private final LanguageSupportRepository languageSupportRepository;
  private final SonarProjectBranchTrackingService branchService;
  private final ServerApiProvider serverApiProvider;
  private final TaskManager taskManager;
  private final StorageService storageService;
  private final Set<String> connectedModeEmbeddedPluginKeys;
  private final boolean branchSpecificSynchronizationEnabled;
  private final boolean fullSynchronizationEnabled;
  private final SonarProjectBranchTrackingService branchTrackingService;
  private final FilePathTranslationRepository filePathTranslationRepository;
  private final SynchronizationStatusRepository synchronizationStatusRepository;
  private final ApplicationEventPublisher eventPublisher;
  private ScheduledExecutorService scheduledSynchronizer;

  public SynchronizationService(SonarLintRpcClient client, ConfigurationRepository configurationRepository, LanguageSupportRepository languageSupportRepository,
    SonarProjectBranchTrackingService branchService, ServerApiProvider serverApiProvider, StorageService storageService, InitializeParams params,
    SonarProjectBranchTrackingService branchTrackingService, FilePathTranslationRepository filePathTranslationRepository,
    SynchronizationStatusRepository synchronizationStatusRepository, ApplicationEventPublisher eventPublisher) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.languageSupportRepository = languageSupportRepository;
    this.branchService = branchService;
    this.serverApiProvider = serverApiProvider;
    this.taskManager = new TaskManager(client);
    this.storageService = storageService;
    this.connectedModeEmbeddedPluginKeys = params.getConnectedModeEmbeddedPluginPathsByKey().keySet();
    this.branchSpecificSynchronizationEnabled = params.getFeatureFlags().shouldSynchronizeProjects();
    this.fullSynchronizationEnabled = params.getFeatureFlags().shouldManageFullSynchronization();
    this.branchTrackingService = branchTrackingService;
    this.filePathTranslationRepository = filePathTranslationRepository;
    this.synchronizationStatusRepository = synchronizationStatusRepository;
    this.eventPublisher = eventPublisher;
  }

  @PostConstruct
  public void startScheduledSync() {
    if (!branchSpecificSynchronizationEnabled) {
      return;
    }
    scheduledSynchronizer = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "SonarLint Local Storage Synchronizer"));
    scheduledSynchronizer.scheduleAtFixedRate(this::safeAutoSync, 1L, 3600, TimeUnit.SECONDS);
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
    var bindingsPerConnectionId = configurationRepository.getEffectiveBindingForLeafConfigScopesById()
      .entrySet().stream().collect(
        groupingBy(e -> e.getValue().getConnectionId(),
          mapping(e -> new BoundScope(e.getKey(), e.getValue().getConnectionId(), e.getValue().getSonarProjectKey()), toList())));
    synchronizeProjects(bindingsPerConnectionId);
  }

  private void synchronizeProjects(Map<String, List<BoundScope>> bindingsPerConnectionId) {
    if (bindingsPerConnectionId.isEmpty()) {
      return;
    }
    taskManager.startTask(null, "Synchronizing projects...", null, false, false, progressNotifier -> {
      var connectionsCount = bindingsPerConnectionId.keySet().size();
      var progressGap = 100f / connectionsCount;
      var progress = 0f;
      var synchronizedConfScopeIds = new HashSet<String>();
      for (var entry : bindingsPerConnectionId.entrySet()) {
        var connectionId = entry.getKey();
        progressNotifier.notify("Synchronizing with '" + connectionId + "'...", Math.round(progress));
        synchronizeProjects(connectionId, entry.getValue(), progressNotifier, synchronizedConfScopeIds, progress, progressGap);
        progress += progressGap;
      }
      if (!synchronizedConfScopeIds.isEmpty()) {
        client.didSynchronizeConfigurationScopes(new DidSynchronizeConfigurationScopeParams(synchronizedConfScopeIds));
      }
    });
  }

  private void synchronizeProjects(String connectionId, List<BoundScope> boundConfigurationScopes, ProgressNotifier notifier, Set<String> synchronizedConfScopeIds,
    float progress, float progressGap) {
    if (boundConfigurationScopes.isEmpty()) {
      return;
    }
    serverApiProvider.getServerApi(connectionId).ifPresent(serverApi -> {
      var serverConnection = getServerConnection(connectionId, serverApi);
      var subProgressGap = progressGap / boundConfigurationScopes.size();
      var subProgress = progress;
      for (BoundScope scope : boundConfigurationScopes) {
        notifier.notify("Synchronizing project '" + scope.getSonarProjectKey() + "'...", Math.round(subProgress));
        autoSyncBoundConfigurationScope(scope, serverApi, serverConnection, synchronizedConfScopeIds);
        subProgress += subProgressGap;
      }
    });
  }

  @NotNull
  public ServerConnection getServerConnection(String connectionId, ServerApi serverApi) {
    return new ServerConnection(storageService.getStorageFacade(), connectionId, serverApi.isSonarCloud(),
      languageSupportRepository.getEnabledLanguagesInConnectedMode(), connectedModeEmbeddedPluginKeys);
  }

  private void autoSyncBoundConfigurationScope(BoundScope boundScope, ServerApi serverApi,
    ServerConnection serverConnection, Set<String> synchronizedConfScopeIds) {
    branchService.getEffectiveMatchedSonarProjectBranch(boundScope.getId()).ifPresent(branch -> {
      serverConnection.syncServerIssuesForProject(serverApi, boundScope.getSonarProjectKey(), branch);
      synchronizeTaintVulnerabilities(serverApi, serverConnection, boundScope, branch);
      serverConnection.syncServerHotspotsForProject(serverApi, boundScope.getSonarProjectKey(), branch);
      synchronizedConfScopeIds.add(boundScope.getId());
    });
  }

  public void synchronizeTaintVulnerabilities(String configurationScopeId, Binding binding, String branchName) {
    var connectionId = binding.getConnectionId();
    serverApiProvider.getServerApi(connectionId).ifPresent(serverApi -> {
      var serverConnection = getServerConnection(connectionId, serverApi);
      synchronizeTaintVulnerabilities(serverApi, serverConnection, new BoundScope(configurationScopeId, binding.getConnectionId(), binding.getSonarProjectKey()), branchName);
    });
  }

  private void synchronizeTaintVulnerabilities(ServerApi serverApi, ServerConnection serverConnection, BoundScope boundScope, String branch) {
    if (languageSupportRepository.areTaintVulnerabilitiesSupported()) {
      var projectKey = boundScope.getSonarProjectKey();
      var summary = serverConnection.updateServerTaintIssuesForProject(serverApi, projectKey, branch);
      eventPublisher.publishEvent(new TaintVulnerabilitiesSynchronizedEvent(boundScope, summary));
    }
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
    synchronizationStatusRepository.clearLastSynchronizationNow(event.getRemovedConfigurationScopeId());
  }

  @EventListener
  public void onBindingChanged(BindingConfigChangedEvent event) {
    var configScopeId = event.getConfigScopeId();
    synchronizationStatusRepository.clearLastSynchronizationNow(configScopeId);
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
    LOG.debug("Synchronizing connection '{}' after credential changed", connectionId);
    var bindingsForUpdatedConnection = configurationRepository.getBoundScopesByConnection(connectionId)
      .stream()
      .map(b -> new BoundScope(connectionId, connectionId, b.getSonarProjectKey()))
      .collect(toList());
    serverApiProvider.getServerApi(connectionId)
      .ifPresent(serverApi -> synchronizeConnectionAndProjectsIfNeeded(connectionId, serverApi, bindingsForUpdatedConnection));
  }

  private void synchronizeConnectionAndProjectsIfNeeded(String connectionId, ServerApi serverApi, List<BoundScope> boundScopes) {
    var scopesToSync = boundScopes.stream().filter(this::shouldSynchronizeScope).collect(toList());
    if (scopesToSync.isEmpty()) {
      return;
    }
    scopesToSync.forEach(scope -> synchronizationStatusRepository.setLastSynchronizationNow(scope.getId()));
    var serverConnection = getServerConnection(connectionId, serverApi);
    try {
      var anyPluginUpdated = serverConnection.sync(serverApi);
      if (anyPluginUpdated) {
        client.didUpdatePlugins(new DidUpdatePluginsParams(connectionId));
      }
      var scopesPerProjectKey = scopesToSync.stream().collect(groupingBy(BoundScope::getSonarProjectKey, mapping(BoundScope::getId, toSet())));
      scopesPerProjectKey.forEach((projectKey, configScopeIds) -> {
        serverConnection.sync(serverApi, projectKey);
        matchPaths(serverApi, projectKey, configScopeIds);
        configScopeIds.forEach(branchTrackingService::matchSonarProjectBranch);
      });
      synchronizeProjects(Map.of(connectionId, scopesToSync.stream().map(scope -> new BoundScope(scope.getId(), connectionId, scope.getSonarProjectKey())).collect(toList())));
    } catch (Exception e) {
      LOG.error("Error during synchronization", e);
    }
  }

  private boolean shouldSynchronizeScope(BoundScope configScope) {
    return synchronizationStatusRepository.getLastSynchronizationDate(configScope.getId())
      .map(lastSync -> lastSync.isBefore(Instant.now().minus(5, ChronoUnit.MINUTES)))
      .orElse(true);
  }

  private void matchPaths(ServerApi serverApi, String projectKey, Set<String> configScopeIds) {
    var fileMatcher = new FileTreeMatcher();
    var serverFilePaths = listAllFilePathsFromServer(serverApi, projectKey);
    configScopeIds.forEach(configScopeId -> {
      var localFilePaths = listAllFilePathsFromClient(configScopeId);
      if (localFilePaths == null) {
        return;
      }
      var match = fileMatcher.match(serverFilePaths, localFilePaths);
      filePathTranslationRepository.setPathTranslation(configScopeId, new FilePathTranslation(match.idePrefix(), match.sqPrefix()));
    });
  }

  private static List<Path> listAllFilePathsFromServer(ServerApi serverApi, String projectKey) {
    return serverApi.component().getAllFileKeys(projectKey, new ProgressMonitor(null)).stream()
      .map(fileKey -> fileKey.substring(StringUtils.lastIndexOf(fileKey, ":") + 1))
      .map(Paths::get)
      .collect(toList());
  }

  @CheckForNull
  private List<Path> listAllFilePathsFromClient(String configScopeId) {
    try {
      return client.listAllFilePaths(new ListAllFilePathsParams(configScopeId)).get().getAllFilePaths().stream().map(Paths::get).collect(toList());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted!", e);
    } catch (ExecutionException e) {
      LOG.warn("Unable to list all file paths from the client", e);
    }
    return null;
  }

  @EventListener
  public void onSonarProjectBranchChanged(MatchedSonarProjectBranchChangedEvent changedEvent) {
    if (!branchSpecificSynchronizationEnabled) {
      return;
    }
    var configurationScopeId = changedEvent.getConfigurationScopeId();
    configurationRepository.getEffectiveBinding(configurationScopeId).ifPresent(binding -> synchronizeProjects(Map.of(requireNonNull(binding.getConnectionId()),
      List.of(new BoundScope(configurationScopeId, binding.getConnectionId(), binding.getSonarProjectKey())))));
  }

  public void fetchProjectIssues(Binding binding, String activeBranch) {
    serverApiProvider.getServerApi(binding.getConnectionId()).ifPresent(serverApi -> {
      var serverConnection = getServerConnection(binding.getConnectionId(), serverApi);
      serverConnection.downloadServerIssuesForProject(serverApi, binding.getSonarProjectKey(), activeBranch);
    });
  }

  public void fetchFileIssues(Binding binding, String serverFileRelativePath, String activeBranch) {
    serverApiProvider.getServerApi(binding.getConnectionId()).ifPresent(serverApi -> {
      var serverConnection = getServerConnection(binding.getConnectionId(), serverApi);
      serverConnection.downloadServerIssuesForFile(serverApi, binding.getSonarProjectKey(), serverFileRelativePath, activeBranch);
    });
  }

  public void fetchProjectHotspots(Binding binding, String activeBranch) {
    serverApiProvider.getServerApi(binding.getConnectionId()).ifPresent(serverApi -> {
      var serverConnection = getServerConnection(binding.getConnectionId(), serverApi);
      serverConnection.downloadAllServerHotspots(serverApi, binding.getSonarProjectKey(), activeBranch, new ProgressMonitor(null));
    });
  }

  public void fetchFileHotspots(Binding binding, String activeBranch, String serverFilePath) {
    serverApiProvider.getServerApi(binding.getConnectionId()).ifPresent(serverApi -> {
      var serverConnection = getServerConnection(binding.getConnectionId(), serverApi);
      serverConnection.downloadAllServerHotspotsForFile(serverApi, binding.getSonarProjectKey(), serverFilePath, activeBranch);
    });
  }

  @PreDestroy
  public void shutdown() {
    if (scheduledSynchronizer != null && !MoreExecutors.shutdownAndAwaitTermination(scheduledSynchronizer, 5, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop synchronizer executor service in a timely manner");
    }
  }
}
