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
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisServicePluginsSynchronizedTest {

  private ConfigurationRepository configurationRepository;
  private StorageService storageService;
  private AnalysisSchedulerCache schedulerCache;
  private AnalysisService underTest;

  @BeforeEach
  void setUp() {
    configurationRepository = mock(ConfigurationRepository.class);
    storageService = mock(StorageService.class);
    schedulerCache = mock(AnalysisSchedulerCache.class);
    var initializeParams = mock(InitializeParams.class);
    when(initializeParams.isAutomaticAnalysisEnabled()).thenReturn(false);
    underTest = new AnalysisService(mock(SonarLintRpcClient.class), configurationRepository, mock(LanguageSupportRepository.class), storageService,
      mock(PluginsService.class), mock(ActiveRulesService.class), mock(ClientFileSystemService.class), mock(FileExclusionService.class), mock(MonitoringService.class),
      mock(TaskManager.class), initializeParams, mock(NodeJsService.class), schedulerCache, mock(ApplicationEventPublisher.class), mock(UserAnalysisPropertiesRepository.class),
      mock(OpenFilesRepository.class), mock(ClientFileSystemService.class));
  }

  @Test
  void reloads_plugins_without_purging_before_the_container_is_unloaded() {
    when(configurationRepository.getBoundScopesToConnection("conn")).thenReturn(List.of());

    underTest.onPluginsSynchronized(new PluginsSynchronizedEvent("conn"));

    verify(schedulerCache).reloadPlugins("conn");
    verify(storageService, never()).connection(any());
  }

  @Test
  void does_not_purge_connected_resources_for_a_standalone_synchronization() {
    underTest.onPluginsSynchronized(new PluginsSynchronizedEvent(null));

    verify(schedulerCache).reloadStandalonePlugins();
    verify(storageService, never()).connection(any());
  }

}
