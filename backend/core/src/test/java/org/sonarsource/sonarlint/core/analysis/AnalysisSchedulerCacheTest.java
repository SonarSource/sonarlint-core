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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.plugin.PluginLifecycleService;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.plugin.source.binaries.OmnisharpDistributionDownloader;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalysisSchedulerCacheTest {

  @Test
  void reloadStandalonePlugins_evicts_caches_when_no_scheduler_started(@TempDir Path tempDir) {
    var userPaths = mockUserPaths(tempDir);
    var pluginLifecycleService = mock(PluginLifecycleService.class);

    var cache = new AnalysisSchedulerCache(mock(OmnisharpDistributionDownloader.class), userPaths,
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

    var cache = new AnalysisSchedulerCache(mock(OmnisharpDistributionDownloader.class), userPaths,
      mock(ConfigurationRepository.class), mock(NodeJsService.class),
      mock(PluginsService.class), pluginLifecycleService,
      mock(ClientFileSystemService.class));

    cache.reloadPlugins("conn1");

    verify(pluginLifecycleService).unloadPluginsAndEvictCaches("conn1");
  }

  @Test
  void reloadAllConnectedPlugins_is_no_op_when_no_connected_schedulers_exist(@TempDir Path tempDir) {
    var userPaths = mockUserPaths(tempDir);
    var pluginLifecycleService = mock(PluginLifecycleService.class);

    var cache = new AnalysisSchedulerCache(mock(OmnisharpDistributionDownloader.class), userPaths,
      mock(ConfigurationRepository.class), mock(NodeJsService.class),
      mock(PluginsService.class), pluginLifecycleService,
      mock(ClientFileSystemService.class));

    cache.reloadAllConnectedPlugins();

    verifyNoInteractions(pluginLifecycleService);
  }

  private static UserPaths mockUserPaths(Path tempDir) {
    var userPaths = mock(UserPaths.class);
    when(userPaths.getWorkDir()).thenReturn(tempDir.resolve("work"));
    return userPaths;
  }

}
