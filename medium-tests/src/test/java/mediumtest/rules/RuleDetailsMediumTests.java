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
package mediumtest.rules;

import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;

class RuleDetailsMediumTests {

  @SonarLintTest
  void it_should_return_details_from_server_when_sonarqube(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey",
        project -> project.withBranch("branchName"))
      .start();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.TEXT)
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withServerVersion("9.9").withProject("projectKey",
          project -> project.withRuleSet("secrets", ruleSet -> ruleSet.withActiveRule("secrets:S6290", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .build();

    var ruleDetails = backend.getAnalysisService().getRuleDetails(new GetRuleDetailsParams("configScope", "secrets:S6290")).join();

    assertThat(ruleDetails.getType())
      .isEqualTo(RuleType.VULNERABILITY);
  }

  @SonarLintTest
  void it_should_return_details_from_server_when_sonarcloud(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarCloudServer()
      .withProject("projectKey",
        project -> project.withBranch("branchName"))
      .start();
    var backend = harness.newBackend()
      .withSonarCloudUrl(server.baseUrl())
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.TEXT)
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .withSonarCloudConnection("connectionId",
        storage -> storage.withPlugin(TestPlugin.TEXT).withProject("projectKey",
          project -> project.withRuleSet("secrets", ruleSet -> ruleSet.withActiveRule("secrets:S6290", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .build();

    var ruleDetails = backend.getAnalysisService().getRuleDetails(new GetRuleDetailsParams("configScope", "secrets:S6290")).join();

    assertThat(ruleDetails.getType()).isEqualTo(RuleType.VULNERABILITY);
  }

  @SonarLintTest
  void it_should_return_details_from_the_embedded_ipython_rules_when_connected(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarCloudServer()
      .withProject("projectKey",
        project -> project.withBranch("branchName"))
      .start();
    var backend = harness.newBackend()
      .withSonarCloudUrl(server.baseUrl())
      .withStandaloneEmbeddedPlugin(TestPlugin.PYTHON)
      .withEnabledLanguageInStandaloneMode(Language.IPYTHON)
      .withSonarCloudConnection("connectionId",
        storage -> storage.withPlugin(TestPlugin.TEXT).withProject("projectKey",
          project -> project.withRuleSet("secrets", ruleSet -> ruleSet.withActiveRule("secrets:S6290", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .build();

    var ruleDetails = backend.getAnalysisService().getRuleDetails(new GetRuleDetailsParams("configScope", "ipython:PrintStatementUsage")).join();

    assertThat(ruleDetails.getType()).isEqualTo(RuleType.CODE_SMELL);
  }
}
