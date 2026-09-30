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
package org.sonarsource.sonarlint.core;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarlint.core.ai.ide.McpConfigurationService;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationUpdateParams;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MCPServerConfigurationProviderTests {
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private final ConnectionConfigurationRepository repository = mock(ConnectionConfigurationRepository.class);
  private final EmbeddedServer embeddedServer = mock(EmbeddedServer.class);
  private final MCPServerConfigurationProvider provider = new MCPServerConfigurationProvider(repository, mock(TelemetryService.class), embeddedServer);

  @ParameterizedTest
  @CsvSource({"false, 0", "false, -1", "false, 65536", "true, 0", "true, -1", "true, 65536"})
  void should_omit_unavailable_ports_but_keep_forwarding_arguments(boolean cloud, int port) throws IOException {
    configureConnection(cloud);
    when(embeddedServer.getPort()).thenReturn(port);

    var result = MAPPER.readTree(provider.getMCPServerConfigurationJSON("connection", "test-token"));

    assertThat(result.path("env").has("SONARQUBE_IDE_PORT")).isFalse();
    assertThat(result.path("args").toString()).contains("\"-e\",\"SONARQUBE_IDE_PORT\"");
    assertThat(result.path("env").path("SONARQUBE_TOKEN").asText()).isEqualTo("test-token");
    assertThat(result.path("env").path("SONARQUBE_URL").asText()).isEqualTo("https://example.com");
    if (cloud) {
      assertThat(result.path("env").path("SONARQUBE_ORG").asText()).isEqualTo("my-org");
    }
  }

  @ParameterizedTest
  @CsvSource({"false, 1", "false, 64120", "false, 65535", "true, 1", "true, 64120", "true, 65535"})
  void should_include_valid_ports(boolean cloud, int port) throws IOException {
    configureConnection(cloud);
    when(embeddedServer.getPort()).thenReturn(port);

    var configuration = provider.getMCPServerConfigurationJSON("connection", "test-token");
    var result = MAPPER.readTree(configuration);

    assertThat(configuration).doesNotContain("\r");
    assertThat(result.path("env").path("SONARQUBE_IDE_PORT").asText()).isEqualTo(Integer.toString(port));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void should_not_add_the_ide_port_later_when_creation_precedes_server_start(boolean cloud) throws IOException {
    configureConnection(cloud);
    when(embeddedServer.getPort()).thenReturn(0);
    var generated = provider.getMCPServerConfigurationJSON("connection", "test-token");
    var service = new McpConfigurationService();
    var created = service.planUpdate(new McpConfigurationUpdateParams(AiAgent.CURSOR, null, generated));

    assertThat(created.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    assertThat(created.getUpdatedContent()).isNotNull();
    assertThat(MAPPER.readTree(created.getUpdatedContent()).path("mcpServers").path("sonarqube")).isEqualTo(MAPPER.readTree(generated));

    var updated = service.planUpdate(new McpConfigurationUpdateParams(AiAgent.CURSOR, created.getUpdatedContent(), "{\"env\":{\"SONARQUBE_IDE_PORT\":\"64120\"}}"));

    assertThat(updated.getState()).isEqualTo(McpConfigurationState.STANDALONE);
    assertThat(updated.getUpdatedContent()).isNull();
    assertThat(updated.getDiagnostics()).singleElement().asString().contains("no SONARQUBE_IDE_PORT");
  }

  private void configureConnection(boolean cloud) {
    var connection = cloud
      ? new SonarCloudConnectionConfiguration(URI.create("https://example.com"), URI.create("https://api.example.com"), "connection", "my-org", SonarCloudRegion.EU, false)
      : new SonarQubeConnectionConfiguration("connection", "https://example.com", false);
    when(repository.getConnectionById("connection")).thenReturn(connection);
  }
}
