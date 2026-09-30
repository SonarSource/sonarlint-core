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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationScope;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationInspectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationUpdateParams;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;

class AiAgentMediumTests {

  @SonarLintTest
  void it_should_expose_cli_integration_state_through_rpc(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();

    var state = backend.getAiAgentService().getIntegrationState(
      new GetAiIntegrationStateParams(AiIntegrationHost.OTHER, List.of(AiAgent.CLAUDE_CODE, AiAgent.GITHUB_COPILOT),
        AiIntegrationScope.GLOBAL, null)).join();

    assertThat(state.getCli()).isNotNull();
    assertThat(state.getCli().getInstallationStatus()).isNotNull();
    assertThat(state.getAgents()).extracting(AiIntegrationAgentCapability::getAgent)
      .containsExactly(AiAgent.CLAUDE_CODE, AiAgent.GITHUB_COPILOT);
  }

  @SonarLintTest
  void it_should_prepare_cli_install_command_through_rpc(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();

    var installCommand = backend.getAiAgentService().prepareInstallCommand().join();

    assertThat(installCommand.getExecutable()).isIn("/bin/bash", "powershell.exe");
    assertThat(installCommand.isInteractive()).isTrue();
  }

  @SonarLintTest
  void it_should_inspect_and_plan_an_mcp_configuration_update(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();

    for (var agent : new AiAgent[] {AiAgent.CURSOR, AiAgent.JUNIE, AiAgent.JETBRAINS_AI_ASSISTANT}) {
      var inspection = backend.getAiAgentService()
        .inspectMcpConfiguration(new McpConfigurationInspectionParams(agent, null))
        .join();
      var update = backend.getAiAgentService()
        .planMcpConfigurationUpdate(new McpConfigurationUpdateParams(agent, null,
          "{\"command\":\"docker\",\"args\":[\"sonarsource/sonarqube-mcp\"]}"))
        .join();

      assertThat(inspection.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
      assertThat(update.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
      assertThat(update.getUpdatedContent()).contains("mcpServers", "sonarqube");
    }
  }

}
