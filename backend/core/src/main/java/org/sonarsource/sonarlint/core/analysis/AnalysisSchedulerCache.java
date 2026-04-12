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
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisSchedulerConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.command.UnregisterModuleCommand;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.tracing.Trace;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.plugin.PluginLifecycleService;
import org.sonarsource.sonarlint.core.plugin.PluginsConfiguration;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.springframework.context.event.EventListener;

import static org.sonarsource.sonarlint.core.commons.tracing.Trace.startChild;

public class AnalysisSchedulerCache {
  private final Path workDir;
  private final ClientFileSystemService clientFileSystemService;
  private final ConfigurationRepository configurationRepository;
  private final PluginsService pluginsService;
  private final PluginLifecycleService pluginLifecycleService;
  private final NodeJsService nodeJsService;
  private final AtomicReference<AnalysisScheduler> standaloneScheduler = new AtomicReference<>();
  private final ConcurrentHashMap<String, AnalysisScheduler> connectedSchedulerByConnectionId = new ConcurrentHashMap<>();

  public AnalysisSchedulerCache(UserPaths userPaths, ConfigurationRepository configurationRepository,
    NodeJsService nodeJsService, PluginsService pluginsService, PluginLifecycleService pluginLifecycleService, ClientFileSystemService clientFileSystemService) {
    this.configurationRepository = configurationRepository;
    this.pluginsService = pluginsService;
    this.pluginLifecycleService = pluginLifecycleService;
    this.nodeJsService = nodeJsService;
    this.workDir = userPaths.getWorkDir();
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
      k -> createScheduler(pluginsService.getPlugins(connectionId), trace));
  }

  @CheckForNull
  private synchronized AnalysisScheduler getConnectedSchedulerIfStarted(String connectionId) {
    return connectedSchedulerByConnectionId.get(connectionId);
  }

  private synchronized AnalysisScheduler getOrCreateStandaloneScheduler(@Nullable Trace trace) {
    var scheduler = standaloneScheduler.get();
    if (scheduler == null) {
      scheduler = createScheduler(pluginsService.getEmbeddedPlugins(), trace);
      standaloneScheduler.set(scheduler);
    }
    return scheduler;
  }

  @CheckForNull
  private synchronized AnalysisScheduler getStandaloneSchedulerIfStarted() {
    return standaloneScheduler.get();
  }

  private AnalysisScheduler createScheduler(PluginsConfiguration pluginsConfiguration, @Nullable Trace trace) {
    var config = buildSchedulerConfiguration(pluginsConfiguration.extraProperties(), trace);
    return new AnalysisScheduler(config, pluginsConfiguration.plugins(), SonarLintLogger.get().getTargetForCopy());
  }

  private AnalysisSchedulerConfiguration buildSchedulerConfiguration(Map<String, String> extraProperties, @Nullable Trace trace) {
    var activeNodeJs = startChild(trace, "getActiveNodeJs", "createSchedulerConfiguration", nodeJsService::getActiveNodeJs);
    var nodeJsPath = activeNodeJs == null ? null : activeNodeJs.getPath();
    return AnalysisSchedulerConfiguration.builder()
      .setWorkDir(workDir)
      .setClientPid(ProcessHandle.current().pid())
      .setExtraProperties(extraProperties)
      .setNodeJs(nodeJsPath)
      .setFileSystemProvider(this::getFileSystem)
      .build();
  }

  private SchedulerResetConfiguration toSchedulerResetConfiguration(PluginsConfiguration pluginsConfiguration) {
    return new SchedulerResetConfiguration(buildSchedulerConfiguration(pluginsConfiguration.extraProperties(), null), pluginsConfiguration.plugins());
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
      scheduler.reset(() -> toSchedulerResetConfiguration(pluginLifecycleService.reloadPluginsAndEvictCaches(connectionId)));
    } else {
      // Scheduler doesn't exist yet (lazy initialization), but still need to unload old plugins and evict caches
      // This ensures that when the scheduler is eventually created, it won't use stale cached data
      pluginLifecycleService.unloadPluginsAndEvictCaches(connectionId);
    }
  }

  public synchronized void reloadStandalonePlugins() {
    var scheduler = standaloneScheduler.get();
    if (scheduler != null) {
      scheduler.reset(() -> toSchedulerResetConfiguration(pluginLifecycleService.reloadEmbeddedPluginsAndEvictCaches()));
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
      standaloneAnalysisScheduler.reset(() -> toSchedulerResetConfiguration(pluginsService.getEmbeddedPlugins()));
    }
    connectedSchedulerByConnectionId.forEach(
      (connectionId, scheduler) -> scheduler.reset(() -> toSchedulerResetConfiguration(pluginsService.getPlugins(connectionId))));
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
