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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileResponse;

import static org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.newSonarCloudServer;
import static org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.newSonarQubeServer;
import static org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture.newBackend;
import static org.apache.commons.lang.StringUtils.removeEnd;
import static org.assertj.core.api.Assertions.assertThat;

class SharedConnectedModeSettingsMediumTests {
  private SonarLintTestRpcServer backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void should_throw_when_not_bound() {
    var configScopeId = "file:///my/folder";
    backend = newBackend()
      .build();

    var fileContents = getFileContents(configScopeId);

    assertThat(fileContents).failsWithin(1, TimeUnit.SECONDS);
  }

  @Test
  void should_return_sc_config_when_bound_to_sonarcloud() throws ExecutionException, InterruptedException {
    var configScopeId = "file:///my/workspace/folder";
    var connectionId = "scConnection";
    var organizationKey = "myOrg";
    var projectKey = "projectKey";

    var expectedFileContent = String.format("{\n" +
      "    \"sonarCloudOrganization\": \"%s\",\n" +
      "    \"projectKey\": \"%s\"\n" +
      "}", organizationKey, projectKey);

    var server = newSonarCloudServer(organizationKey).start();

    backend = newBackend()
      .withSonarCloudUrl(server.baseUrl())
      .withSonarCloudConnection(connectionId, organizationKey)
      .withBoundConfigScope(configScopeId, connectionId, projectKey)
      .withTelemetryEnabled()
      .build();

    var result = getFileContents(configScopeId);

    assertThat(result).succeedsWithin(3, TimeUnit.SECONDS);
    assertThat(result.get().getJsonFileContent()).isEqualTo(expectedFileContent);
    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"exportedConnectedModeCount\":1");
  }

  @Test
  void should_return_sq_config_when_bound_to_sonarqube() throws ExecutionException, InterruptedException {
    var configScopeId = "file:///my/workspace/folder";
    var connectionId = "scConnection";
    var projectKey = "projectKey";

    var server = newSonarQubeServer().start();

    var expectedFileContent = String.format("{\n" +
      "    \"sonarQubeUri\": \"%s\",\n" +
      "    \"projectKey\": \"%s\"\n" +
      "}", removeEnd(server.baseUrl(), "/"), projectKey);

    backend = newBackend()
      .withSonarQubeConnection(connectionId, server)
      .withBoundConfigScope(configScopeId, connectionId, projectKey)
      .withTelemetryEnabled()
      .build();

    var result = getFileContents(configScopeId);

    assertThat(result).succeedsWithin(3, TimeUnit.SECONDS);
    assertThat(result.get().getJsonFileContent()).isEqualTo(expectedFileContent);
    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"exportedConnectedModeCount\":1");
  }

  private CompletableFuture<GetSharedConnectedModeConfigFileResponse> getFileContents(String configScopeId) {
    return backend.getBindingService()
      .getSharedConnectedModeConfigFileContents(
        new GetSharedConnectedModeConfigFileParams(configScopeId));
  }

}
