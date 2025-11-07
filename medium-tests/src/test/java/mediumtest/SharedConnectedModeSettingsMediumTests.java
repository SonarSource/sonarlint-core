/*
 * SonarLint Core - Medium Tests
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
package mediumtest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.Strings;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileResponse;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;

class SharedConnectedModeSettingsMediumTests {

  @SonarLintTest
  void should_throw_when_not_bound(SonarLintTestHarness harness) {
    var configScopeId = "file:///my/folder";
    var backend = harness.newBackend()
      .start();

    var fileContents = getFileContents(backend, configScopeId);

    assertThat(fileContents).failsWithin(1, TimeUnit.SECONDS);
  }

  @SonarLintTest
  void should_return_sc_config_when_bound_to_sonarcloud(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var configScopeId = "file:///my/workspace/folder";
    var connectionId = "scConnection";
    var organizationKey = "myOrg";
    var projectKey = "projectKey";

    var expectedFileContent = String.format("""
      {
          "sonarCloudOrganization": "%s",
          "projectKey": "%s",
          "region": "EU"
      }""", organizationKey, projectKey);

    var server = harness.newFakeSonarCloudServer().start();

    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection(connectionId, organizationKey)
      .withBoundConfigScope(configScopeId, connectionId, projectKey)
      .withTelemetryEnabled()
      .start();

    var result = getFileContents(backend, configScopeId);

    assertThat(result).succeedsWithin(3, TimeUnit.SECONDS);
    assertThat(result.get().getJsonFileContent()).isEqualTo(expectedFileContent);
    assertThat(backend.telemetryFileContent().getExportedConnectedModeCount()).isEqualTo(1);
  }

  @SonarLintTest
  void should_return_wrong_sc_config_when_bound_to_sonarcloud_us(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var configScopeId = "file:///my/workspace/folder";
    var connectionId = "scConnection";
    var organizationKey = "myOrg";
    var projectKey = "projectKey";

    var expectedFileContent = String.format("""
      {
          "sonarCloudOrganization": "%s",
          "projectKey": "%s",
          "region": "US"
      }""", organizationKey, projectKey);

    var server = harness.newFakeSonarCloudServer().start();

    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection(connectionId, organizationKey, "US")
      .withBoundConfigScope(configScopeId, connectionId, projectKey)
      .withTelemetryEnabled()
      .start();

    var result = getFileContents(backend, configScopeId);

    assertThat(result).succeedsWithin(3, TimeUnit.SECONDS);
    assertThat(result.get().getJsonFileContent()).isEqualTo(expectedFileContent);
    assertThat(backend.telemetryFileContent().getExportedConnectedModeCount()).isEqualTo(1);
  }

  @SonarLintTest
  void should_return_sq_config_when_bound_to_sonarqube(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var configScopeId = "file:///my/workspace/folder";
    var connectionId = "scConnection";
    var projectKey = "projectKey";

    var server = harness.newFakeSonarQubeServer().start();

    var expectedFileContent = String.format("""
      {
          "sonarQubeUri": "%s",
          "projectKey": "%s"
      }""", Strings.CS.removeEnd(server.baseUrl(), "/"), projectKey);

    var backend = harness.newBackend()
      .withSonarQubeConnection(connectionId, server)
      .withBoundConfigScope(configScopeId, connectionId, projectKey)
      .withTelemetryEnabled()
      .start();

    var result = getFileContents(backend, configScopeId);

    assertThat(result).succeedsWithin(3, TimeUnit.SECONDS);
    assertThat(result.get().getJsonFileContent()).isEqualTo(expectedFileContent);
    assertThat(backend.telemetryFileContent().getExportedConnectedModeCount()).isEqualTo(1);
  }

  private CompletableFuture<GetSharedConnectedModeConfigFileResponse> getFileContents(SonarLintTestRpcServer backend, String configScopeId) {
    return backend.getBindingService()
      .getSharedConnectedModeConfigFileContents(
        new GetSharedConnectedModeConfigFileParams(configScopeId));
  }

}
