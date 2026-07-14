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
package org.sonarsource.sonarlint.core.plugin.source.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerPluginDownloaderTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private SonarQubeClientManager sonarQubeClientManager;
  private ConnectionConfigurationRepository connectionRepository;
  private ServerPluginDownloader downloader;

  @BeforeEach
  void setUp() {
    sonarQubeClientManager = mock(SonarQubeClientManager.class);
    connectionRepository = mock(ConnectionConfigurationRepository.class);
    downloader = new ServerPluginDownloader(mock(StorageService.class), sonarQubeClientManager, connectionRepository);
  }

  @Test
  void should_perform_blocking_download() {
    var serverPlugin = new ServerPlugin("custom-plugin", "hash", "custom.jar", true);

    downloader.downloadPluginSyncOrThrow("conn", serverPlugin);

    verify(sonarQubeClientManager).withActiveClient(any(), any());
  }

  @Test
  void should_return_sonarqube_cloud_origin_for_cloud_connection() {
    mockConnection(ConnectionKind.SONARCLOUD, "https://sonarcloud.io");

    assertThat(downloader.sourceFor("conn")).isEqualTo(ArtifactOrigin.SONARQUBE_CLOUD);
  }

  @Test
  void should_return_sonarqube_server_origin_for_server_or_unknown_connection() {
    mockConnection(ConnectionKind.SONARQUBE, "https://sonarqube.example");

    assertThat(downloader.sourceFor("conn")).isEqualTo(ArtifactOrigin.SONARQUBE_SERVER);
    assertThat(downloader.sourceFor("unknown")).isEqualTo(ArtifactOrigin.SONARQUBE_SERVER);
  }

  @Test
  void should_build_deduplication_key_from_server_url_plugin_key_and_hash() {
    mockConnection(ConnectionKind.SONARQUBE, "https://sonarqube.example");
    var plugin = new ServerPlugin("java", "plugin-hash", "java.jar", true);

    assertThat(downloader.deduplicationKeyFor("conn", plugin))
      .isEqualTo("https://sonarqube.example/api/plugins/download?plugin=java#plugin-hash");
  }

  private void mockConnection(ConnectionKind kind, String url) {
    var connection = mock(AbstractConnectionConfiguration.class);
    when(connection.getKind()).thenReturn(kind);
    when(connection.getUrl()).thenReturn(url);
    when(connectionRepository.getConnectionById("conn")).thenReturn(connection);
  }
}
