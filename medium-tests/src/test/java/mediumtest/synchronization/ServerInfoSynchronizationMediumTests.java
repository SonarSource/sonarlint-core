/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2024 SonarSource SA
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
package mediumtest.synchronization;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;

import static java.util.concurrent.TimeUnit.SECONDS;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.ServerFixture.ServerStatus.DOWN;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

class ServerInfoSynchronizationMediumTests {

  @Test
  void it_should_pull_server_info_when_bound_configuration_scope_is_added() {
    var server = newSonarQubeServer("10.3")
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withFullSynchronization()
      .build();

    addConfigurationScope("configScopeId", "connectionId", "projectKey");

    waitAtMost(3, SECONDS).untilAsserted(() -> assertThat(getServerInfoFile())
      .exists()
      .extracting(this::readServerVersion)
      .isEqualTo("10.3"));
  }

  @Test
  void it_should_stop_synchronization_if_server_is_down() {
    var server = newSonarQubeServer("10.3")
      .withStatus(DOWN)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = newFakeClient().build();
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withFullSynchronization()
      .build(client);

    addConfigurationScope("configScopeId", "connectionId", "projectKey");

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getServerInfoFile()).doesNotExist();
      assertThat(client.getLogMessages()).contains("Error during synchronization");
    });
  }

  @Test
  void it_should_stop_synchronization_if_server_version_is_unsupported() {
    var server = newSonarQubeServer("7.8")
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = newFakeClient().build();
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withFullSynchronization()
      .build(client);

    addConfigurationScope("configScopeId", "connectionId", "projectKey");

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getServerInfoFile()).doesNotExist();
      assertThat(client.getLogMessages()).contains("Error during synchronization");
    });
  }

  private void addConfigurationScope(String configScopeId, String connectionId, String projectKey) {
    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto(configScopeId, null, true, "name", new BindingConfigurationDto(connectionId, projectKey, true)))));
  }

  private Path getServerInfoFile() {
    return backend.getStorageRoot().resolve(encodeForFs("connectionId")).resolve("server_info.pb");
  }

  private String readServerVersion(Path protoFilePath) {
    return ProtobufFileUtil.readFile(protoFilePath, Sonarlint.ServerInfo.parser()).getVersion();
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  private SonarLintTestRpcServer backend;
}
