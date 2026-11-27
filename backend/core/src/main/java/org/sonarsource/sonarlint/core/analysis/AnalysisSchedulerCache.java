/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.analysis;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import org.sonar.api.scanner.ResourceFetcher;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisSchedulerConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.command.UnregisterModuleCommand;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.tracing.Trace;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.plugin.PluginLifecycleService;
import org.sonarsource.sonarlint.core.plugin.PluginsConfiguration;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.plugin.resources.ConnectedResourceFetcher;
import org.sonarsource.sonarlint.core.plugin.resources.StandaloneResourceFetcher;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.springframework.context.event.EventListener;

import static org.sonarsource.sonarlint.core.commons.tracing.Trace.startChild;

public class AnalysisSchedulerCache {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Path workDir;
  private final Path storageRoot;
  private final ClientFileSystemService clientFileSystemService;
  private final ConfigurationRepository configurationRepository;
  private final PluginsService pluginsService;
  private final PluginLifecycleService pluginLifecycleService;
  private final NodeJsService nodeJsService;
  private final StorageService storageService;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final HttpClientProvider httpClientProvider;
  private final AtomicReference<AnalysisScheduler> standaloneScheduler = new AtomicReference<>();
  private final ConcurrentHashMap<String, AnalysisScheduler> connectedSchedulerByConnectionId = new ConcurrentHashMap<>();

  public AnalysisSchedulerCache(UserPaths userPaths, ConfigurationRepository configurationRepository,
    NodeJsService nodeJsService, PluginsService pluginsService, PluginLifecycleService pluginLifecycleService, ClientFileSystemService clientFileSystemService,
    StorageService storageService, SonarQubeClientManager sonarQubeClientManager, HttpClientProvider httpClientProvider) {
    this.configurationRepository = configurationRepository;
    this.pluginsService = pluginsService;
    this.pluginLifecycleService = pluginLifecycleService;
    this.nodeJsService = nodeJsService;
    this.storageService = storageService;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.httpClientProvider = httpClientProvider;
    this.workDir = userPaths.getWorkDir();
    this.storageRoot = userPaths.getStorageRoot();
    this.clientFileSystemService = clientFileSystemService;
  }

  @CheckForNull
  public AnalysisScheduler getAnalysisSchedulerIfStarted(String configurationScopeId) {
    return configurationRepository.getEffectiveBinding(configurationScopeId)
      .map(binding -> getConnectedSchedulerIfStarted(binding.connectionId()))
      .orElseGet(this::getStandaloneSchedulerIfStarted);
  }

  public AnalysisScheduler getOrCreateAnalysisScheduler(String configurationScopeId) {
    return getOrCreateAnalysisScheduler(configurationScopeId, null);
  }

  public AnalysisScheduler getOrCreateAnalysisScheduler(String configurationScopeId, @Nullable Trace trace) {
    return configurationRepository.getEffectiveBinding(configurationScopeId)
      .map(binding -> getOrCreateConnectedScheduler(binding.connectionId(), trace))
      .orElseGet(() -> getOrCreateStandaloneScheduler(trace));
  }

  private synchronized AnalysisScheduler getOrCreateConnectedScheduler(String connectionId, @Nullable Trace trace) {
    return connectedSchedulerByConnectionId.computeIfAbsent(connectionId,
      k -> createScheduler(pluginsService.getPlugins(connectionId), connectedResourceFetcher(connectionId), trace));
  }

  @CheckForNull
  private synchronized AnalysisScheduler getConnectedSchedulerIfStarted(String connectionId) {
    return connectedSchedulerByConnectionId.get(connectionId);
  }

  private synchronized AnalysisScheduler getOrCreateStandaloneScheduler(@Nullable Trace trace) {
    var scheduler = standaloneScheduler.get();
    if (scheduler == null) {
      scheduler = createScheduler(pluginsService.getEmbeddedPlugins(), standaloneResourceFetcher(), trace);
      standaloneScheduler.set(scheduler);
    }
    return scheduler;
  }

  @CheckForNull
  private synchronized AnalysisScheduler getStandaloneSchedulerIfStarted() {
    return standaloneScheduler.get();
  }

  private AnalysisScheduler createScheduler(PluginsConfiguration pluginsConfiguration, @Nullable ResourceFetcher resourceFetcher, @Nullable Trace trace) {
    var config = buildSchedulerConfiguration(pluginsConfiguration.extraProperties(), resourceFetcher, trace);
    return new AnalysisScheduler(config, pluginsConfiguration.plugins(), SonarLintLogger.get().getTargetForCopy());
  }

  private AnalysisSchedulerConfiguration buildSchedulerConfiguration(Map<String, String> extraProperties, @Nullable ResourceFetcher resourceFetcher, @Nullable Trace trace) {
    var activeNodeJs = startChild(trace, "getActiveNodeJs", "createSchedulerConfiguration", nodeJsService::getActiveNodeJs);
    var nodeJsPath = activeNodeJs == null ? null : activeNodeJs.getPath();
    return AnalysisSchedulerConfiguration.builder()
      .setWorkDir(workDir)
      .setClientPid(ProcessHandle.current().pid())
      .setExtraProperties(extraProperties)
      .setNodeJs(nodeJsPath)
      .setFileSystemProvider(this::getFileSystem)
      .setResourceFetcher(resourceFetcher)
      .build();
  }

  private ResourceFetcher connectedResourceFetcher(String connectionId) {
    var connection = storageService.connection(connectionId);
    return new ConnectedResourceFetcher(connectionId, connection.plugins().getStoredPluginsByKey(), connection.staticResources(), sonarQubeClientManager);
  }

  private ResourceFetcher standaloneResourceFetcher() {
    return new StandaloneResourceFetcher(storageRoot, httpClientProvider);
  }

  SchedulerResetConfiguration toSchedulerResetConfiguration(PluginsConfiguration pluginsConfiguration, @Nullable String connectionId) {
    var resourceFetcher = connectionId == null ? standaloneResourceFetcher() : connectedResourceFetcher(connectionId);
    return new SchedulerResetConfiguration(buildSchedulerConfiguration(pluginsConfiguration.extraProperties(), resourceFetcher, null), pluginsConfiguration.plugins());
  }

  private ClientModuleFileSystem getFileSystem(String configurationScopeId) {
    return new BackendModuleFileSystem(clientFileSystemService, configurationScopeId);
  }

  @EventListener
  public void onConnectionRemoved(ConnectionConfigurationRemovedEvent event) {
    stop(event.removedConnectionId());
  }

  public synchronized void reloadPlugins(String connectionId) {
    var scheduler = connectedSchedulerByConnectionId.get(connectionId);
    if (scheduler != null) {
      // ResetPluginsCommand stops the previous container before calling this supplier.
      // Reloading plugins then runs ServerPluginSource.load, which purges only when the server is reachable and nothing is downloading.
      scheduler.reset(configurationAfterUnload(connectionId));
    } else {
      // No scheduler exists yet, so this branch only unloads the cached plugins. The download that published the event has finished,
      // and load() skipped its purge while that download was pending. Purge the stored inventory here; loading again would schedule downloads.
      pluginLifecycleService.unloadPluginsAndEvictCaches(connectionId);
      purgeStaticResources(connectionId);
    }
  }

  Supplier<SchedulerResetConfiguration> configurationAfterUnload(String connectionId) {
    return () -> toSchedulerResetConfiguration(pluginLifecycleService.reloadPluginsAndEvictCaches(connectionId), connectionId);
  }

  private void purgeStaticResources(String connectionId) {
    try {
      var connection = storageService.connection(connectionId);
      connection.staticResources().purgeExcept(connection.plugins().getStoredPlugins());
    } catch (RuntimeException e) {
      LOG.debug("Cannot purge cached static resources for connection {}", connectionId, e);
    }
  }

  public synchronized void reloadStandalonePlugins() {
    var scheduler = standaloneScheduler.get();
    if (scheduler != null) {
      scheduler.reset(() -> toSchedulerResetConfiguration(pluginLifecycleService.reloadEmbeddedPluginsAndEvictCaches(), null));
    } else {
      pluginLifecycleService.unloadEmbeddedPluginsAndEvictCaches();
    }
  }

  @EventListener
  public void onClientNodeJsPathChanged(ClientNodeJsPathChanged event) {
    resetStartedSchedulers();
  }

  @EventListener
  public void onBindingConfigurationChanged(BindingConfigChangedEvent event) {
    var schedulerBeforeBindingChange = event.previousConfig().isBound() ? getConnectedSchedulerIfStarted(Objects.requireNonNull(event.previousConfig().connectionId()))
      : getStandaloneSchedulerIfStarted();
    var schedulerAfterBindingChange = getAnalysisSchedulerIfStarted(event.configScopeId());
    if (schedulerBeforeBindingChange != null && schedulerAfterBindingChange != schedulerBeforeBindingChange) {
      schedulerBeforeBindingChange.post(new UnregisterModuleCommand(event.configScopeId()));
      configurationRepository.getChildrenWithInheritedBinding(event.configScopeId())
        .forEach(childId -> schedulerBeforeBindingChange.post(new UnregisterModuleCommand(childId)));
    }
  }

  @PreDestroy
  public void shutdown() {
    try {
      stopAll();
    } catch (Exception e) {
      SonarLintLogger.get().error("Error shutting down analysis scheduler cache", e);
    }
  }

  private synchronized void resetStartedSchedulers() {
    var standaloneAnalysisScheduler = this.standaloneScheduler.get();
    if (standaloneAnalysisScheduler != null) {
      standaloneAnalysisScheduler.reset(() -> toSchedulerResetConfiguration(pluginsService.getEmbeddedPlugins(), null));
    }
    connectedSchedulerByConnectionId.forEach(
      (connectionId, scheduler) -> scheduler.reset(() -> toSchedulerResetConfiguration(pluginsService.getPlugins(connectionId), connectionId)));
  }

  private synchronized void stopAll() {
    var standaloneAnalysisScheduler = this.standaloneScheduler.getAndSet(null);
    if (standaloneAnalysisScheduler != null) {
      standaloneAnalysisScheduler.stop();
    }
    connectedSchedulerByConnectionId.forEach((connectionId, scheduler) -> scheduler.stop());
    connectedSchedulerByConnectionId.clear();
  }

  private synchronized void stop(String connectionId) {
    var scheduler = connectedSchedulerByConnectionId.remove(connectionId);
    if (scheduler != null) {
      scheduler.stop();
    }
    pluginLifecycleService.unloadPluginsAndEvictCaches(connectionId);
  }

  public void unregisterModule(String scopeId, @Nullable String connectionId) {
    var analysisScheduler = connectionId == null ? getStandaloneSchedulerIfStarted() : getConnectedSchedulerIfStarted(connectionId);
    if (analysisScheduler != null) {
      if (connectionId != null && !configurationRepository.hasScopesBoundToConnection(connectionId)) {
        stop(connectionId);
      } else {
        analysisScheduler.post(new UnregisterModuleCommand(scopeId));
      }
    } else if (connectionId != null && !configurationRepository.hasScopesBoundToConnection(connectionId)) {
      pluginLifecycleService.unloadPluginsAndEvictCaches(connectionId);
    }
  }
}
