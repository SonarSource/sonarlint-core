/*
 * SonarLint Core - Medium Tests
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
package mediumtest.ai.ide;

import java.util.List;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationAgentCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliCommandAction;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetRuleFileContentParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareCliCommandParams;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;

class AiAgentMediumTests {

  @SonarLintTest
  void it_should_return_the_rule_file_content_for_cursor(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();

    var response = backend.getAiAgentService().getRuleFileContent(new GetRuleFileContentParams(AiAgent.CURSOR)).join();

    assertThat(response.getContent()).contains("alwaysApply: true");
    assertThat(response.getContent()).contains("IMPORTANT");
    assertThat(response.getContent()).contains("analyze_file_list");
    assertThat(response.getContent()).contains("Important Tool Guidelines");
  }

  @SonarLintTest
  void it_should_return_the_rule_file_content_for_github_copilot(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();

    var response = backend.getAiAgentService().getRuleFileContent(new GetRuleFileContentParams(AiAgent.GITHUB_COPILOT)).join();

    assertThat(response.getContent()).doesNotContain("alwaysApply: true");
    assertThat(response.getContent()).contains("applyTo: \"**/*\"");
    assertThat(response.getContent()).contains("IMPORTANT");
    assertThat(response.getContent()).contains("analyze_file_list");
    assertThat(response.getContent()).contains("Important Tool Guidelines");
  }

  @SonarLintTest
  void it_should_return_the_rule_file_content_for_windsurf(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();

    var response = backend.getAiAgentService().getRuleFileContent(new GetRuleFileContentParams(AiAgent.WINDSURF)).join();

    assertThat(response.getContent()).contains("alwaysApply: true");
    assertThat(response.getContent()).contains("IMPORTANT");
    assertThat(response.getContent()).contains("analyze_file_list");
    assertThat(response.getContent()).contains("Important Tool Guidelines");
  }

  @SonarLintTest
  void it_should_return_the_rule_file_content_for_kiro(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();

    var response = backend.getAiAgentService().getRuleFileContent(new GetRuleFileContentParams(AiAgent.KIRO)).join();

    assertThat(response.getContent()).contains("inclusion: always");
    assertThat(response.getContent()).contains("IMPORTANT");
    assertThat(response.getContent()).contains("analyze_file_list");
    assertThat(response.getContent()).contains("Important Tool Guidelines");
  }

  @SonarLintTest
  void it_should_expose_cli_integration_state_and_install_command_through_rpc(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();

    var state = backend.getAiAgentService().getIntegrationState(
      new GetAiIntegrationStateParams(List.of(AiAgent.CLAUDE_CODE, AiAgent.GITHUB_COPILOT))).join();
    var installCommand = backend.getAiAgentService().prepareCliCommand(
      new PrepareCliCommandParams(CliCommandAction.INSTALL, null, null, null)).join();

    assertThat(state.getCli()).isNotNull();
    assertThat(state.getCli().getInstallationStatus()).isIn(
      CliInstallationStatus.NOT_INSTALLED, CliInstallationStatus.INSTALLED, CliInstallationStatus.UNUSABLE);
    assertThat(state.getAgents()).extracting(AiIntegrationAgentCapability::getAgent)
      .containsExactly(AiAgent.CLAUDE_CODE, AiAgent.GITHUB_COPILOT);
    assertThat(installCommand.getExecutable()).isIn("/bin/bash", "powershell.exe");
    assertThat(installCommand.isInteractive()).isTrue();
  }

}
