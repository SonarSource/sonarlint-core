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

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.active.rules.ActiveRulesService;
import org.sonarsource.sonarlint.core.commons.progress.TaskManager;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.fs.FileExclusionService;
import org.sonarsource.sonarlint.core.fs.OpenFilesRepository;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.monitoring.MonitoringService;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.StaticResourceStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisServicePluginsSynchronizedTest {

  private ConfigurationRepository configurationRepository;
  private StorageService storageService;
  private AnalysisSchedulerCache schedulerCache;
  private ConnectionStorage connectionStorage;
  private PluginsStorage pluginsStorage;
  private StaticResourceStorage staticResources;
  private AnalysisService underTest;

  @BeforeEach
  void setUp() {
    configurationRepository = mock(ConfigurationRepository.class);
    storageService = mock(StorageService.class);
    schedulerCache = mock(AnalysisSchedulerCache.class);
    connectionStorage = mock(ConnectionStorage.class);
    pluginsStorage = mock(PluginsStorage.class);
    staticResources = mock(StaticResourceStorage.class);
    var initializeParams = mock(InitializeParams.class);
    when(initializeParams.isAutomaticAnalysisEnabled()).thenReturn(false);
    underTest = new AnalysisService(mock(SonarLintRpcClient.class), configurationRepository, mock(LanguageSupportRepository.class), storageService,
      mock(PluginsService.class), mock(ActiveRulesService.class), mock(ClientFileSystemService.class), mock(FileExclusionService.class), mock(MonitoringService.class),
      mock(TaskManager.class), initializeParams, mock(NodeJsService.class), schedulerCache, mock(ApplicationEventPublisher.class), mock(UserAnalysisPropertiesRepository.class),
      mock(OpenFilesRepository.class), mock(ClientFileSystemService.class));
  }

  @Test
  void purges_static_resources_after_the_previous_container_is_unloaded() {
    var stored = List.<StoredPlugin>of();
    when(configurationRepository.getBoundScopesToConnection("conn")).thenReturn(List.of());
    when(storageService.connection("conn")).thenReturn(connectionStorage);
    when(connectionStorage.plugins()).thenReturn(pluginsStorage);
    when(connectionStorage.staticResources()).thenReturn(staticResources);
    when(pluginsStorage.getStoredPlugins()).thenReturn(stored);

    underTest.onPluginsSynchronized(new PluginsSynchronizedEvent("conn"));

    var order = inOrder(schedulerCache, staticResources);
    order.verify(schedulerCache).reloadPlugins("conn");
    order.verify(staticResources).purgeExcept(stored);
  }

  @Test
  void does_not_purge_connected_resources_for_a_standalone_synchronization() {
    underTest.onPluginsSynchronized(new PluginsSynchronizedEvent(null));

    verify(schedulerCache).reloadStandalonePlugins();
    verify(storageService, never()).connection(any());
  }

}
