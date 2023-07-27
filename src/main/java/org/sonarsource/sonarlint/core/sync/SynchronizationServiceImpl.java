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

import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchServiceImpl;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.ActiveSonarProjectBranchChanged;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.progress.ProgressNotifier;
import org.sonarsource.sonarlint.core.progress.TaskManager;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.ServerConnection;
import org.sonarsource.sonarlint.core.serverconnection.StorageService;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Named
@Singleton
public class SynchronizationServiceImpl {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintClient client;
  private final ConfigurationRepository configurationRepository;
  private final LanguageSupportRepository languageSupportRepository;
  private final SonarProjectBranchServiceImpl branchService;
  private final ServerApiProvider serverApiProvider;
  private final TaskManager taskManager;
  private final StorageService storageService;
  private final Set<String> connectedModeEmbeddedPluginKeys;
  private final InitializeParams params;
  private ScheduledExecutorService scheduledSynchronizer;

  public SynchronizationServiceImpl(SonarLintClient client, ConfigurationRepository configurationRepository, LanguageSupportRepository languageSupportRepository,
    SonarProjectBranchServiceImpl branchService, ServerApiProvider serverApiProvider, StorageService storageService, InitializeParams params) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.languageSupportRepository = languageSupportRepository;
    this.branchService = branchService;
    this.serverApiProvider = serverApiProvider;
    this.taskManager = new TaskManager(client);
    this.storageService = storageService;
    this.connectedModeEmbeddedPluginKeys = params.getConnectedModeEmbeddedPluginPathsByKey().keySet();
    this.params = params;
  }

  @PostConstruct
  public void startScheduledSync() {
    if (!params.getFeatureFlags().shouldSynchronizeProjects()) {
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
          mapping(e -> new BoundConfigurationScope(e.getKey(), e.getValue().getSonarProjectKey()), toList())));
    autoSync(bindingsPerConnectionId);
  }

  private void autoSync(Map<String, List<BoundConfigurationScope>> bindingsPerConnectionId) {
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
        autoSync(connectionId, entry.getValue(), progressNotifier, synchronizedConfScopeIds, progress, progressGap);
        progress += progressGap;
      }
      if (!synchronizedConfScopeIds.isEmpty()) {
        client.didSynchronizeConfigurationScopes(new DidSynchronizeConfigurationScopeParams(synchronizedConfScopeIds));
      }
    });
  }

  private void autoSync(String connectionId, List<BoundConfigurationScope> boundConfigurationScopes, ProgressNotifier notifier, Set<String> synchronizedConfScopeIds,
    float progress, float progressGap) {
    if (boundConfigurationScopes.isEmpty()) {
      return;
    }
    serverApiProvider.getServerApi(connectionId).ifPresent(serverApi -> {
      var serverConnection = new ServerConnection(storageService.getStorageFacade(), connectionId, serverApi.isSonarCloud(),
        languageSupportRepository.getEnabledLanguagesInConnectedMode(), connectedModeEmbeddedPluginKeys);
      var subProgressGap = progressGap / boundConfigurationScopes.size();
      var subProgress = progress;
      for (BoundConfigurationScope scope : boundConfigurationScopes) {
        notifier.notify("Synchronizing project '" + scope.sonarProjectKey + "'...", Math.round(subProgress));
        autoSyncBoundConfigurationScope(scope, serverApi, serverConnection, synchronizedConfScopeIds);
        subProgress += subProgressGap;
      }
    });
  }

  private void autoSyncBoundConfigurationScope(BoundConfigurationScope boundScope, ServerApi serverApi,
    ServerConnection serverConnection, Set<String> synchronizedConfScopeIds) {
    branchService.getEffectiveActiveSonarProjectBranch(boundScope.configurationScopeId).ifPresent(branch -> {
      serverConnection.syncServerIssuesForProject(serverApi, boundScope.sonarProjectKey, branch);
      if (languageSupportRepository.areTaintVulnerabilitiesSupported()) {
        serverConnection.syncServerTaintIssuesForProject(serverApi, boundScope.sonarProjectKey, branch);
      }
      serverConnection.syncServerHotspotsForProject(serverApi, boundScope.sonarProjectKey, branch);
      synchronizedConfScopeIds.add(boundScope.configurationScopeId);
    });
  }

  @Subscribe
  public void onSonarProjectBranchChanged(ActiveSonarProjectBranchChanged changedEvent) {
    var configurationScopeId = changedEvent.getConfigurationScopeId();
    configurationRepository.getEffectiveBinding(configurationScopeId).ifPresent(binding -> autoSync(Map.of(requireNonNull(binding.getConnectionId()),
      List.of(new BoundConfigurationScope(configurationScopeId, binding.getSonarProjectKey())))));
  }

  @PreDestroy
  public void shutdown() {
    if (scheduledSynchronizer != null && !MoreExecutors.shutdownAndAwaitTermination(scheduledSynchronizer, 5, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop synchronizer executor service in a timely manner");
    }
  }

  private static class BoundConfigurationScope {
    private final String configurationScopeId;
    private final String sonarProjectKey;

    private BoundConfigurationScope(String configurationScopeId, String sonarProjectKey) {
      this.configurationScopeId = configurationScopeId;
      this.sonarProjectKey = sonarProjectKey;
    }
  }
}
