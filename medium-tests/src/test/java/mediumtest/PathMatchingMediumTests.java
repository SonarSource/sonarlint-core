/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2023 SonarSource SA
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

import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.GetPathTranslationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.GetPathTranslationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static java.util.concurrent.TimeUnit.SECONDS;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

class PathMatchingMediumTests {

  @Test
  void it_should_match_without_prefixes_when_no_local_and_main_branch_server_files_exist() {
    var server = newSonarQubeServer("10.3")
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withFullSynchronization()
      .build();

    addConfigurationScope("configScopeId", "connectionId", "projectKey");

    waitAtMost(3, SECONDS).untilAsserted(() -> assertThat(getPathTranslation("configScopeId"))
      .extracting(GetPathTranslationResponse::getIdePathPrefix, GetPathTranslationResponse::getServerPathPrefix)
      .containsExactly("", ""));
  }

  @Test
  void it_should_match_without_prefixes_when_local_and_main_branch_server_paths_are_the_same() {
    var server = newSonarQubeServer("10.3")
      .withProject("projectKey", project -> project.withBranch("main")
        .withFile("relative/path/to/a/file"))
      .start();
    var client = newFakeClient()
      .withInitialFs("configScopeId",
        List.of(new ClientFileDto(URI.create("ftp://relative/path/to/a/file"), Paths.get("relative/path/to/a/file"), "configScopeId", null, null, null, null)))
      .build();
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withFullSynchronization()
      .build(client);

    addConfigurationScope("configScopeId", "connectionId", "projectKey");

    waitAtMost(3, SECONDS).untilAsserted(() -> assertThat(getPathTranslation("configScopeId"))
      .extracting(GetPathTranslationResponse::getIdePathPrefix, GetPathTranslationResponse::getServerPathPrefix)
      .containsExactly("", ""));
  }

  @Test
  void it_should_match_with_prefixes_when_local_and_main_branch_server_paths_differ() {
    var server = newSonarQubeServer("10.3")
      .withProject("projectKey", project -> project.withBranch("main")
        .withFile("server/path/to/a/file"))
      .start();
    var client = newFakeClient()
      .withInitialFs("configScopeId",
        List.of(new ClientFileDto(URI.create("ftp://local/path/to/a/file"), Paths.get("local/path/to/a/file"), "configScopeId", null, null, null, null)))
      .build();
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withFullSynchronization()
      .build(client);

    addConfigurationScope("configScopeId", "connectionId", "projectKey");

    waitAtMost(3, SECONDS).untilAsserted(() -> assertThat(getPathTranslation("configScopeId"))
      .extracting(GetPathTranslationResponse::getIdePathPrefix, GetPathTranslationResponse::getServerPathPrefix)
      .containsExactly("local", "server"));
  }

  private void addConfigurationScope(String configScopeId, String connectionId, String projectKey) {
    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto(configScopeId, null, true, "name", new BindingConfigurationDto(connectionId, projectKey, true)))));
  }

  private GetPathTranslationResponse getPathTranslation(String configScopeId) {
    try {
      return backend.getFileService().getPathTranslation(new GetPathTranslationParams(configScopeId)).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  private SonarLintTestRpcServer backend;
}
