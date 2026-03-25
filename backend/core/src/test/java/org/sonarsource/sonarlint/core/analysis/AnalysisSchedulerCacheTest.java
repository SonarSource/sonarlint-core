/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.plugin.PluginLifecycleService;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class AnalysisSchedulerCacheTest {

  @Test
  void reloadStandalonePlugins_evicts_caches_when_no_scheduler_started(@TempDir Path tempDir) {
    var initializeParams = stubInitializeParams();
    var userPaths = mockUserPaths(tempDir);
    var pluginLifecycleService = mock(PluginLifecycleService.class);

    var cache = new AnalysisSchedulerCache(initializeParams, userPaths,
      mock(ConfigurationRepository.class), mock(NodeJsService.class),
      mock(PluginsService.class), pluginLifecycleService,
      mock(ClientFileSystemService.class));

    cache.reloadStandalonePlugins();

    verify(pluginLifecycleService).unloadEmbeddedPluginsAndEvictCaches();
  }

  @Test
  void reloadPlugins_evicts_caches_when_no_connected_scheduler_exists(@TempDir Path tempDir) {
    var initializeParams = stubInitializeParams();
    var userPaths = mockUserPaths(tempDir);
    var pluginLifecycleService = mock(PluginLifecycleService.class);

    var cache = new AnalysisSchedulerCache(initializeParams, userPaths,
      mock(ConfigurationRepository.class), mock(NodeJsService.class),
      mock(PluginsService.class), pluginLifecycleService,
      mock(ClientFileSystemService.class));

    cache.reloadPlugins("conn1");

    verify(pluginLifecycleService).unloadPluginsAndEvictCaches("conn1");
  }

  @Test
  void reloadAllConnectedPlugins_is_no_op_when_no_connected_schedulers_exist(@TempDir Path tempDir) {
    var initializeParams = stubInitializeParams();
    var userPaths = mockUserPaths(tempDir);
    var pluginLifecycleService = mock(PluginLifecycleService.class);

    var cache = new AnalysisSchedulerCache(initializeParams, userPaths,
      mock(ConfigurationRepository.class), mock(NodeJsService.class),
      mock(PluginsService.class), pluginLifecycleService,
      mock(ClientFileSystemService.class));

    cache.reloadAllConnectedPlugins();

    verifyNoInteractions(pluginLifecycleService);
  }

  private static InitializeParams stubInitializeParams() {
    var params = mock(InitializeParams.class);
    when(params.getEnabledLanguagesInStandaloneMode()).thenReturn(Set.of());
    when(params.getLanguageSpecificRequirements()).thenReturn(null);
    return params;
  }

  private static UserPaths mockUserPaths(Path tempDir) {
    var userPaths = mock(UserPaths.class);
    when(userPaths.getWorkDir()).thenReturn(tempDir.resolve("work"));
    return userPaths;
  }

}
