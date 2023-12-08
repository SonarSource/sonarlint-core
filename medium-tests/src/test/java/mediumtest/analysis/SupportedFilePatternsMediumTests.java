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
package mediumtest.analysis;

import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetSupportedFilePatternsParams;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;

class SupportedFilePatternsMediumTests {

  private SonarLintRpcServer backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void it_should_return_default_supported_file_patterns_in_standalone_mode() throws ExecutionException, InterruptedException {
    backend = newBackend()
      .withEnabledLanguageInStandaloneMode(JAVA)
      .build();

    var patterns = backend.getAnalysisService().getSupportedFilePatterns(new GetSupportedFilePatternsParams("configScopeId")).get().getPatterns();
    assertThat(patterns).containsOnly("**/*.java", "**/*.jav");
  }

  @Test
  void it_should_return_default_supported_file_patterns_in_connected_mode_when_not_override_on_server() throws ExecutionException, InterruptedException {
    var client = newFakeClient().withMatchedBranch("configScopeId", "branchName").build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", storage -> storage.withPlugin(TestPlugin.JAVA)
        .withProject("projectKey"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .build(client);

    var patterns = backend.getAnalysisService().getSupportedFilePatterns(new GetSupportedFilePatternsParams("configScopeId")).get().getPatterns();
    assertThat(patterns).containsOnly("**/*.java", "**/*.jav");
  }

  @Test
  void it_should_return_supported_file_patterns_with_server_defined_file_suffixes() throws ExecutionException, InterruptedException {
    var client = newFakeClient().withMatchedBranch("configScopeId", "branchName").build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", storage -> storage.withPlugin(TestPlugin.JAVA)
        .withProject("projectKey", (projectStorageBuilder -> {
          projectStorageBuilder.withSetting("sonar.java.file.suffixes", ".foo, .bar");
        })))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withEnabledLanguageInStandaloneMode(JAVA)
      .build(client);

    var patterns = backend.getAnalysisService().getSupportedFilePatterns(new GetSupportedFilePatternsParams("configScopeId")).get().getPatterns();
    assertThat(patterns).containsOnly("**/*.foo", "**/*.bar");
  }

}
