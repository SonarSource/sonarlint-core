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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.plugin.DownloadBatch;
import org.sonarsource.sonarlint.core.plugin.PluginLifecycleService;
import org.sonarsource.sonarlint.core.plugin.PluginsConfiguration;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ArtifactsLoadingResult;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.StaticResourceStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisSchedulerCacheTest {

  @Test
  void reloadStandalonePlugins_evicts_caches_when_no_scheduler_started(@TempDir Path tempDir) {
    var userPaths = mockUserPaths(tempDir);
    var pluginLifecycleService = mock(PluginLifecycleService.class);

    var cache = new AnalysisSchedulerCache(userPaths,
      mock(ConfigurationRepository.class), mock(NodeJsService.class),
      mock(PluginsService.class), pluginLifecycleService,
      mock(ClientFileSystemService.class), mock(StorageService.class), mock(SonarQubeClientManager.class));

    cache.reloadStandalonePlugins();

    verify(pluginLifecycleService).unloadEmbeddedPluginsAndEvictCaches();
  }

  @Test
  void reloadPlugins_evicts_caches_when_no_connected_scheduler_exists(@TempDir Path tempDir) {
    var userPaths = mockUserPaths(tempDir);
    var pluginLifecycleService = mock(PluginLifecycleService.class);
    var resources = staticResources("conn1");

    var cache = new AnalysisSchedulerCache(userPaths,
      mock(ConfigurationRepository.class), mock(NodeJsService.class),
      mock(PluginsService.class), pluginLifecycleService,
      mock(ClientFileSystemService.class), resources.storage(), mock(SonarQubeClientManager.class));

    cache.reloadPlugins("conn1");

    var order = inOrder(pluginLifecycleService, resources.resources());
    order.verify(pluginLifecycleService).unloadPluginsAndEvictCaches("conn1");
    order.verify(resources.resources()).purgeExcept(List.of());
  }

  @Test
  void reset_supplier_purges_after_the_previous_plugins_are_unloaded(@TempDir Path tempDir) {
    var pluginLifecycleService = mock(PluginLifecycleService.class);
    var resources = staticResources("conn");
    when(pluginLifecycleService.reloadPluginsAndEvictCaches("conn"))
      .thenReturn(new PluginsConfiguration(mock(ArtifactsLoadingResult.class), mock(LoadedPlugins.class), Map.of(), mock(DownloadBatch.class)));

    var cache = new AnalysisSchedulerCache(mockUserPaths(tempDir),
      mock(ConfigurationRepository.class), mock(NodeJsService.class),
      mock(PluginsService.class), pluginLifecycleService,
      mock(ClientFileSystemService.class), resources.storage(), mock(SonarQubeClientManager.class));

    cache.configurationAfterUnload("conn").get();

    var order = inOrder(pluginLifecycleService, resources.resources());
    order.verify(pluginLifecycleService).reloadPluginsAndEvictCaches("conn");
    order.verify(resources.resources()).purgeExcept(List.of());
  }

  private static Storage staticResources(String connectionId) {
    var storageService = mock(StorageService.class);
    var connection = mock(ConnectionStorage.class);
    var plugins = mock(PluginsStorage.class);
    var resources = mock(StaticResourceStorage.class);
    when(storageService.connection(connectionId)).thenReturn(connection);
    when(connection.plugins()).thenReturn(plugins);
    when(connection.staticResources()).thenReturn(resources);
    when(plugins.getStoredPlugins()).thenReturn(List.of());
    return new Storage(storageService, resources);
  }

  private record Storage(StorageService storage, StaticResourceStorage resources) {
  }

  private static UserPaths mockUserPaths(Path tempDir) {
    var userPaths = mock(UserPaths.class);
    when(userPaths.getWorkDir()).thenReturn(tempDir.resolve("work"));
    return userPaths;
  }

}
