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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.plugin.PluginLifecycleService;
import org.sonarsource.sonarlint.core.plugin.PluginsConfiguration;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisSchedulerCacheTest {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester(true);

  @Test
  void reloadStandalonePlugins_evicts_caches_when_no_scheduler_started(@TempDir Path tempDir) {
    var userPaths = mockUserPaths(tempDir);
    var pluginLifecycleService = mock(PluginLifecycleService.class);

    var cache = new AnalysisSchedulerCache(userPaths,
      mock(ConfigurationRepository.class), mock(NodeJsService.class),
      mock(PluginsService.class), pluginLifecycleService,
      mock(ClientFileSystemService.class));

    cache.reloadStandalonePlugins();

    verify(pluginLifecycleService).unloadEmbeddedPluginsAndEvictCaches();
  }

  @Test
  void reloadPlugins_evicts_caches_when_no_connected_scheduler_exists(@TempDir Path tempDir) {
    var userPaths = mockUserPaths(tempDir);
    var pluginLifecycleService = mock(PluginLifecycleService.class);

    var cache = new AnalysisSchedulerCache(userPaths,
      mock(ConfigurationRepository.class), mock(NodeJsService.class),
      mock(PluginsService.class), pluginLifecycleService,
      mock(ClientFileSystemService.class));

    cache.reloadPlugins("conn1");

    verify(pluginLifecycleService).unloadPluginsAndEvictCaches("conn1");
  }

  @Test
  void node_js_path_change_reloads_embedded_plugins_and_evicts_caches_when_standalone_scheduler_started(@TempDir Path tempDir) {
    var configurationRepository = mock(ConfigurationRepository.class);
    var pluginsService = mock(PluginsService.class);
    var pluginLifecycleService = mock(PluginLifecycleService.class);
    var initialPlugins = emptyPluginsConfiguration();
    var reloadedPlugins = emptyPluginsConfiguration();
    when(configurationRepository.getEffectiveBinding("scope1")).thenReturn(Optional.empty());
    when(pluginsService.getEmbeddedPlugins()).thenReturn(initialPlugins);
    when(pluginLifecycleService.reloadEmbeddedPluginsAndEvictCaches()).thenReturn(reloadedPlugins);
    var cache = new AnalysisSchedulerCache(mockUserPaths(tempDir), configurationRepository, mock(NodeJsService.class),
      pluginsService, pluginLifecycleService, mock(ClientFileSystemService.class));

    try {
      cache.getOrCreateAnalysisScheduler("scope1");

      cache.onClientNodeJsPathChanged(new ClientNodeJsPathChanged());

      verify(pluginLifecycleService, timeout(5_000)).reloadEmbeddedPluginsAndEvictCaches();
      verify(pluginsService).transferOwnershipToAnalysisScheduler(null, initialPlugins);
      verify(pluginsService, timeout(5_000)).transferOwnershipToAnalysisScheduler(null, reloadedPlugins);
    } finally {
      cache.shutdown();
    }
  }

  @Test
  void node_js_path_change_reloads_connected_plugins_and_evicts_caches_when_connected_scheduler_started(@TempDir Path tempDir) {
    var configurationRepository = mock(ConfigurationRepository.class);
    var pluginsService = mock(PluginsService.class);
    var pluginLifecycleService = mock(PluginLifecycleService.class);
    var initialPlugins = emptyPluginsConfiguration();
    var reloadedPlugins = emptyPluginsConfiguration();
    when(configurationRepository.getEffectiveBinding("scope1")).thenReturn(Optional.of(new Binding("conn1", "project1")));
    when(pluginsService.getPlugins("conn1")).thenReturn(initialPlugins);
    when(pluginLifecycleService.reloadPluginsAndEvictCaches("conn1")).thenReturn(reloadedPlugins);
    var cache = new AnalysisSchedulerCache(mockUserPaths(tempDir), configurationRepository, mock(NodeJsService.class),
      pluginsService, pluginLifecycleService, mock(ClientFileSystemService.class));

    try {
      cache.getOrCreateAnalysisScheduler("scope1");

      cache.onClientNodeJsPathChanged(new ClientNodeJsPathChanged());

      verify(pluginLifecycleService, timeout(5_000)).reloadPluginsAndEvictCaches("conn1");
      verify(pluginsService).transferOwnershipToAnalysisScheduler("conn1", initialPlugins);
      verify(pluginsService, timeout(5_000)).transferOwnershipToAnalysisScheduler("conn1", reloadedPlugins);
    } finally {
      cache.shutdown();
    }
  }

  private static PluginsConfiguration emptyPluginsConfiguration() {
    var loadedPlugins = mock(LoadedPlugins.class);
    when(loadedPlugins.getAnalysisPluginInstancesByKeys()).thenReturn(Map.of());
    return new PluginsConfiguration(null, loadedPlugins, Map.of());
  }

  private static UserPaths mockUserPaths(Path tempDir) {
    var userPaths = mock(UserPaths.class);
    when(userPaths.getWorkDir()).thenReturn(tempDir.resolve("work"));
    return userPaths;
  }

}
