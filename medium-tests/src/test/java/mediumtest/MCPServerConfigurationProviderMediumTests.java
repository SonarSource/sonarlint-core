/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource SA
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
package mediumtest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.GetMCPServerConfigurationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.GetMCPServerConfigurationResponse;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class MCPServerConfigurationProviderMediumTests {

  @SonarLintTest
  void should_throw_when_connection_does_not_exist(SonarLintTestHarness harness) {
    var connectionId = "nonExistingConnection";
    var token = "nonExistingToken";
    var backend = harness.newBackend()
      .start();

    var fileContents = getSettings(backend, connectionId, token);

    assertThat(fileContents).failsWithin(1, TimeUnit.SECONDS);
  }

  @SonarLintTest
  void should_return_sonarcloud_config_for_sonarcloud_connection(SonarLintTestHarness harness) throws Exception {
    var connectionId = "scConnection";
    var organizationKey = "myOrg";
    var token = "token123";
    var embeddedServerPort = 0;

    var expectedSettings = String.format("""
      {
        "command": "docker",
        "args": [
          "run",
          "-i",
          "--rm",
          "-e",
          "SONARQUBE_TOKEN",
          "-e",
          "SONARQUBE_ORG",
          "-e",
          "SONARQUBE_IDE_PORT",
          "mcp/sonarqube"
        ],
        "env": {
          "SONARQUBE_ORG": "%s",
          "SONARQUBE_TOKEN": "%s",
          "SONARQUBE_IDE_PORT": "%s"
        }
      }
      """, organizationKey, token, embeddedServerPort);

    var server = harness.newFakeSonarCloudServer().start();

    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection(connectionId, organizationKey)
      .withTelemetryEnabled()
      .start();

    var result = getSettings(backend, connectionId, token);

    assertThat(result).succeedsWithin(3, TimeUnit.SECONDS);
    assertThat(result.get().getJsonConfiguration()).isEqualTo(expectedSettings);
    assertThat(backend.telemetryFileContent().getMcpServerConfigurationRequestedCount()).isEqualTo(1);
  }

  @SonarLintTest
  void should_return_sonarqube_config_for_sonarqube_connection(SonarLintTestHarness harness) throws Exception {
    var connectionId = "scConnection";
    var organizationKey = "myOrg";
    var connectionId2 = "sqConnection";
    var serverUrl = "http://my-sonarqube";
    var token = "token123";
    var embeddedServerPort = 0;

    var expectedSettings = String.format("""
      {
        "command": "docker",
        "args": [
          "run",
          "-i",
          "--rm",
          "-e",
          "SONARQUBE_TOKEN",
          "-e",
          "SONARQUBE_URL",
          "-e",
          "SONARQUBE_IDE_PORT",
          "mcp/sonarqube"
        ],
        "env": {
          "SONARQUBE_URL": "%s",
          "SONARQUBE_TOKEN": "%s",
          "SONARQUBE_IDE_PORT": "%s"
        }
      }
      """, serverUrl, token, embeddedServerPort);

    var server = harness.newFakeSonarCloudServer().start();

    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection(connectionId, organizationKey)
      .withSonarQubeConnection(connectionId2, serverUrl)
      .withTelemetryEnabled()
      .start();

    var result = getSettings(backend, connectionId2, token);

    assertThat(result).succeedsWithin(3, TimeUnit.SECONDS);
    assertThat(result.get().getJsonConfiguration()).isEqualTo(expectedSettings);
    assertThat(backend.telemetryFileContent().getMcpServerConfigurationRequestedCount()).isEqualTo(1);
  }

  private CompletableFuture<GetMCPServerConfigurationResponse> getSettings(SonarLintTestRpcServer backend, String connectionId, String token) {
    return backend.getConnectionService().getMCPServerConfiguration(new GetMCPServerConfigurationParams(connectionId, token));
  }

}
