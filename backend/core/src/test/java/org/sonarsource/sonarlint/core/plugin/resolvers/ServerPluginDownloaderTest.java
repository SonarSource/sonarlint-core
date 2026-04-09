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
package org.sonarsource.sonarlint.core.plugin.resolvers;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.event.PluginStatusUpdateEvent;
import org.sonarsource.sonarlint.core.plugin.ArtifactSource;
import org.sonarsource.sonarlint.core.plugin.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.PluginStatus;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerPluginDownloaderTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private StorageService storageService;
  private ConnectionConfigurationRepository connectionRepo;
  private SonarQubeClientManager sonarQubeClientManager;
  private ApplicationEventPublisher eventPublisher;
  private ExecutorService downloadExecutor;
  private PluginsStorage pluginsStorage;
  private Path javaJar;

  @BeforeEach
  void setUp() {
    storageService = mock(StorageService.class);
    connectionRepo = mock(ConnectionConfigurationRepository.class);
    sonarQubeClientManager = mock(SonarQubeClientManager.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    var connectionStorage = mock(ConnectionStorage.class);
    pluginsStorage = mock(PluginsStorage.class);

    javaJar = Path.of("sonar-java-plugin.jar");

    when(storageService.connection("conn")).thenReturn(connectionStorage);
    when(connectionStorage.plugins()).thenReturn(pluginsStorage);

    var connection = mock(AbstractConnectionConfiguration.class);
    when(connection.getKind()).thenReturn(ConnectionKind.SONARQUBE);
    when(connectionRepo.getConnectionById("conn")).thenReturn(connection);
  }

  @Test
  void should_publish_synced_event_after_language_plugin_download_succeeds() {
    downloadExecutor = Executors.newSingleThreadExecutor();
    try {
      var downloader = new ServerPluginDownloader(storageService, sonarQubeClientManager, connectionRepo, eventPublisher, downloadExecutor);
      when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarPlugin.JAVA.getKey(), javaJar));

      var serverPlugin = mockServerPlugin(SonarPlugin.JAVA.getKey());
      downloader.scheduleLanguagePluginDownload("conn", serverPlugin, SonarLanguage.JAVA);

      var expectedEvent = new PluginStatusUpdateEvent("conn",
        List.of(PluginStatus.forLanguage(SonarLanguage.JAVA, ArtifactState.SYNCED, ArtifactSource.SONARQUBE_SERVER, null, null, javaJar, null)));

      await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> verify(eventPublisher).publishEvent(expectedEvent));
    } finally {
      downloadExecutor.shutdownNow();
    }
  }

  @Test
  void should_publish_failed_event_when_language_plugin_download_fails() {
    downloadExecutor = Executors.newSingleThreadExecutor();
    try {
      var downloader = new ServerPluginDownloader(storageService, sonarQubeClientManager, connectionRepo, eventPublisher, downloadExecutor);
      var serverPlugin = mockServerPlugin(SonarPlugin.JAVA.getKey());
      
      doThrow(new RuntimeException("Download failed")).when(sonarQubeClientManager).withActiveClient(any(), any());
      
      downloader.scheduleLanguagePluginDownload("conn", serverPlugin, SonarLanguage.JAVA);

      var expectedEvent = new PluginStatusUpdateEvent("conn", List.of(PluginStatus.failed(SonarLanguage.JAVA)));
      await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> verify(eventPublisher).publishEvent(expectedEvent));
    } finally {
      downloadExecutor.shutdownNow();
    }
  }

  @Test
  void should_deduplicate_concurrent_plugin_downloads() {
    var mockedExecutor = mock(ExecutorService.class);
    var downloader = new ServerPluginDownloader(storageService, sonarQubeClientManager, connectionRepo, eventPublisher, mockedExecutor);
    var serverPlugin = mockServerPlugin(SonarPlugin.JAVA.getKey());
    
    // First schedule should submit to executor
    downloader.scheduleLanguagePluginDownload("conn", serverPlugin, SonarLanguage.JAVA);
    
    // Second schedule for the same connection and plugin should be ignored
    downloader.scheduleLanguagePluginDownload("conn", serverPlugin, SonarLanguage.JAVA);
    
    verify(mockedExecutor, times(1)).submit(any(Runnable.class));
  }
  
  @Test
  void should_perform_synchronous_download_and_return_state() {
    downloadExecutor = mock(ExecutorService.class);
    var downloader = new ServerPluginDownloader(storageService, sonarQubeClientManager, connectionRepo, eventPublisher, downloadExecutor);
    var serverPlugin = mockServerPlugin("custom-plugin");
    
    var state = downloader.downloadPluginSync("conn", serverPlugin);
    
    assertThat(state).isEqualTo(ArtifactState.SYNCED);
    verify(sonarQubeClientManager).withActiveClient(any(), any());
  }

  private static ServerPlugin mockServerPlugin(String pluginKey) {
    var plugin = mock(ServerPlugin.class);
    when(plugin.getKey()).thenReturn(pluginKey);
    return plugin;
  }

}
