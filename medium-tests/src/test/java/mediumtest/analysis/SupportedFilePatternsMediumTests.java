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
package mediumtest.analysis;

import java.util.concurrent.ExecutionException;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetSupportedFilePatternsParams;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;

class SupportedFilePatternsMediumTests {

  @SonarLintTest
  void it_should_return_default_supported_file_patterns_in_standalone_mode(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(JAVA)
      .start();

    var patterns = backend.getAnalysisService().getSupportedFilePatterns(new GetSupportedFilePatternsParams("configScopeId")).get().getPatterns();
    assertThat(patterns).containsOnly("**/*.java", "**/*.jav");
  }

  @SonarLintTest
  void it_should_return_default_supported_file_patterns_in_connected_mode_when_not_override_on_server(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var client = harness.newFakeClient().withMatchedBranch("configScopeId", "branchName").build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", storage -> storage.withPlugin(TestPlugin.JAVA)
        .withProject("projectKey"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .start(client);

    var patterns = backend.getAnalysisService().getSupportedFilePatterns(new GetSupportedFilePatternsParams("configScopeId")).get().getPatterns();
    assertThat(patterns).containsOnly("**/*.java", "**/*.jav");
  }

  @SonarLintTest
  void it_should_return_supported_file_patterns_with_server_defined_file_suffixes(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var client = harness.newFakeClient().withMatchedBranch("configScopeId", "branchName").build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", storage -> storage.withPlugin(TestPlugin.JAVA)
        .withProject("projectKey", project -> project.withSetting("sonar.java.file.suffixes", ".foo, .bar")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withEnabledLanguageInStandaloneMode(JAVA)
      .start(client);

    var patterns = backend.getAnalysisService().getSupportedFilePatterns(new GetSupportedFilePatternsParams("configScopeId")).get().getPatterns();
    assertThat(patterns).containsOnly("**/*.foo", "**/*.bar");
  }

}
