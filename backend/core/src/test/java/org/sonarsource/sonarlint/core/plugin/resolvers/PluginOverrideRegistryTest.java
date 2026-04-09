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

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginOverrideRegistryTest {

  private ConnectionConfigurationRepository connectionRepo;
  private StorageService storageService;
  private ConnectionStorage connectionStorage;
  private ServerInfoStorage serverInfoStorage;
  private PluginOverrideRegistry registry;

  @BeforeEach
  void setUp() {
    connectionRepo = mock(ConnectionConfigurationRepository.class);
    storageService = mock(StorageService.class);
    connectionStorage = mock(ConnectionStorage.class);
    serverInfoStorage = mock(ServerInfoStorage.class);
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    registry = new PluginOverrideRegistry(connectionRepo, storageService);
  }

  @Test
  void should_return_textenterprise_on_sonarcloud() {
    mockConnection("cloud", ConnectionKind.SONARCLOUD);

    assertThat(registry.getEnterpriseOverrideKey("text", "cloud")).contains("textenterprise");
  }

  @Test
  void should_return_textenterprise_on_sq_10_4() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockServerVersion(Version.create("10.4"));

    assertThat(registry.getEnterpriseOverrideKey("text", "conn")).contains("textenterprise");
  }

  @Test
  void should_return_empty_for_text_on_sq_below_10_4() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockServerVersion(Version.create("10.3"));

    assertThat(registry.getEnterpriseOverrideKey("text", "conn")).isEmpty();
  }

  @Test
  void should_return_iacenterprise_on_sq_2025_1() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockServerVersion(Version.create("2025.1"));

    assertThat(registry.getEnterpriseOverrideKey("iac", "conn")).contains("iacenterprise");
  }

  @Test
  void should_return_goenterprise_on_sq_2025_2() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockServerVersion(Version.create("2025.2"));

    assertThat(registry.getEnterpriseOverrideKey("go", "conn")).contains("goenterprise");
  }

  @Test
  void should_return_empty_for_unknown_base_key() {
    mockConnection("cloud", ConnectionKind.SONARCLOUD);

    assertThat(registry.getEnterpriseOverrideKey("java", "cloud")).isEmpty();
  }

  @Test
  void should_identify_textenterprise_as_language_override_on_sonarcloud() {
    mockConnection("cloud", ConnectionKind.SONARCLOUD);

    assertThat(registry.isLanguageOverride("textenterprise", "cloud")).isTrue();
  }

  @Test
  void should_not_identify_textenterprise_as_language_override_on_old_sq() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockServerVersion(Version.create("10.3"));

    assertThat(registry.isLanguageOverride("textenterprise", "conn")).isFalse();
  }

  @Test
  void should_not_identify_unknown_key_as_language_override() {
    mockConnection("cloud", ConnectionKind.SONARCLOUD);

    assertThat(registry.isLanguageOverride("someplugin", "cloud")).isFalse();
  }

  @Test
  void should_return_empty_when_connection_not_found() {
    when(connectionRepo.getConnectionById("unknown")).thenReturn(null);

    assertThat(registry.getEnterpriseOverrideKey("text", "unknown")).isEmpty();
    assertThat(registry.isLanguageOverride("textenterprise", "unknown")).isFalse();
  }

  private void mockConnection(String connectionId, ConnectionKind kind) {
    var connection = mock(AbstractConnectionConfiguration.class);
    when(connection.getKind()).thenReturn(kind);
    when(connectionRepo.getConnectionById(connectionId)).thenReturn(connection);
    when(storageService.connection(connectionId)).thenReturn(connectionStorage);
  }

  private void mockServerVersion(Version version) {
    var serverInfo = mock(StoredServerInfo.class);
    when(serverInfo.version()).thenReturn(version);
    when(serverInfoStorage.read()).thenReturn(Optional.of(serverInfo));
  }
}
