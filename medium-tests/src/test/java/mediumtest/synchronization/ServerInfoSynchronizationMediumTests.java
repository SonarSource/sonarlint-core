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
package mediumtest.synchronization;

import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;
import static org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.ServerStatus.DOWN;

class ServerInfoSynchronizationMediumTests {

  @SonarLintTest
  void it_should_pull_server_info_when_bound_configuration_scope_is_added(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withFullSynchronization()
      .build();

    addConfigurationScope(backend, "configScopeId", "connectionId", "projectKey");

    waitAtMost(3, SECONDS).untilAsserted(() -> assertThat(getServerInfoFile(backend))
      .exists()
      .extracting(this::readServerVersion, this::readServerMode)
      .containsExactly("10.3", null));
  }

  @SonarLintTest
  void it_should_pull_old_server_info_and_mode_should_be_missing(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.1")
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withFullSynchronization()
      .build();

    addConfigurationScope(backend, "configScopeId", "connectionId", "projectKey");

    waitAtMost(3, SECONDS).untilAsserted(() -> assertThat(getServerInfoFile(backend))
      .exists()
      .extracting(this::readServerVersion, this::readServerMode)
      .containsExactly("10.1", null));
  }

  @SonarLintTest
  void it_should_synchronize_with_sonarcloud_and_mode_should_be_missing(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarCloudConnection("connectionId", "test")
      .withFullSynchronization()
      .build();

    addConfigurationScope(backend, "configScopeId", "connectionId", "projectKey");

    waitAtMost(3, SECONDS).untilAsserted(() -> assertThat(getServerInfoFile(backend))
      .exists()
      .extracting(this::readServerMode)
      .isNull());
  }

  @SonarLintTest
  void it_should_synchronize_with_recent_sonarqube_and_return_mode(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.8")
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withFullSynchronization()
      .build();

    addConfigurationScope(backend, "configScopeId", "connectionId", "projectKey");

    waitAtMost(3, SECONDS).untilAsserted(() -> assertThat(getServerInfoFile(backend))
      .exists()
      .extracting(this::readServerMode)
      .isEqualTo(true));
  }

  @SonarLintTest
  void it_should_stop_synchronization_if_server_is_down(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withStatus(DOWN)
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withFullSynchronization()
      .build(client);

    addConfigurationScope(backend, "configScopeId", "connectionId", "projectKey");

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getServerInfoFile(backend)).doesNotExist();
      assertThat(client.getLogMessages()).contains("Error during synchronization");
    });
  }

  @SonarLintTest
  void it_should_stop_synchronization_if_server_version_is_unsupported(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("7.8")
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withFullSynchronization()
      .build(client);

    addConfigurationScope(backend, "configScopeId", "connectionId", "projectKey");

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getServerInfoFile(backend)).doesNotExist();
      assertThat(client.getLogMessages()).contains("Error during synchronization");
    });
  }

  private void addConfigurationScope(SonarLintTestRpcServer backend, String configScopeId, String connectionId, String projectKey) {
    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto(configScopeId, null, true, "name", new BindingConfigurationDto(connectionId, projectKey, true)))));
  }

  private Path getServerInfoFile(SonarLintTestRpcServer backend) {
    return backend.getStorageRoot().resolve(encodeForFs("connectionId")).resolve("server_info.pb");
  }

  private String readServerVersion(Path protoFilePath) {
    return ProtobufFileUtil.readFile(protoFilePath, Sonarlint.ServerInfo.parser()).getVersion();
  }

  @Nullable
  private Boolean readServerMode(Path protoFilePath) {
    var serverInfo = ProtobufFileUtil.readFile(protoFilePath, Sonarlint.ServerInfo.parser());
    return serverInfo.hasIsMqrMode() ? serverInfo.getIsMqrMode() : null;
  }
}
