/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.ai.ide;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent.ANTIGRAVITY;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent.CLAUDE_CODE;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent.CODEX;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent.CURSOR;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent.GITHUB_COPILOT;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent.GITHUB_COPILOT_CLI;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent.KIRO;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent.WINDSURF;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost.INTELLIJ;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost.OTHER;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost.VISUAL_STUDIO;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost.VSCODE;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationScope.GLOBAL;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationScope.PROJECT;

class AiAgentCapabilitiesTests {

  static Stream<Arguments> hostAwareCapabilities() {
    return Stream.of(
      Arguments.of(OTHER, CURSOR, GLOBAL, true, true),
      Arguments.of(OTHER, GITHUB_COPILOT, GLOBAL, false, true),
      Arguments.of(OTHER, CODEX, GLOBAL, true, false),
      Arguments.of(OTHER, CLAUDE_CODE, GLOBAL, true, true),
      Arguments.of(OTHER, WINDSURF, GLOBAL, false, true),
      Arguments.of(OTHER, KIRO, GLOBAL, false, true),
      Arguments.of(VSCODE, CURSOR, GLOBAL, false, false),
      Arguments.of(AiIntegrationHost.CURSOR, CURSOR, GLOBAL, true, true),
      Arguments.of(AiIntegrationHost.CURSOR, CURSOR, PROJECT, false, true),
      Arguments.of(INTELLIJ, GITHUB_COPILOT, GLOBAL, false, true),
      Arguments.of(VISUAL_STUDIO, GITHUB_COPILOT, GLOBAL, false, true),
      Arguments.of(VSCODE, GITHUB_COPILOT, GLOBAL, false, true),
      Arguments.of(AiIntegrationHost.CURSOR, GITHUB_COPILOT, GLOBAL, false, false),
      Arguments.of(AiIntegrationHost.WINDSURF, WINDSURF, GLOBAL, false, true),
      Arguments.of(AiIntegrationHost.WINDSURF, WINDSURF, PROJECT, false, true),
      Arguments.of(VSCODE, WINDSURF, GLOBAL, false, false),
      Arguments.of(VSCODE, CLAUDE_CODE, GLOBAL, true, true),
      Arguments.of(VSCODE, CLAUDE_CODE, PROJECT, false, true),
      Arguments.of(AiIntegrationHost.KIRO, KIRO, GLOBAL, false, true),
      Arguments.of(VSCODE, KIRO, GLOBAL, false, false),
      Arguments.of(OTHER, GITHUB_COPILOT_CLI, GLOBAL, true, false),
      Arguments.of(OTHER, ANTIGRAVITY, GLOBAL, true, false)
    );
  }

  @ParameterizedTest
  @MethodSource("hostAwareCapabilities")
  void should_report_host_and_scope_capabilities(AiIntegrationHost host, AiAgent agent, AiIntegrationScope scope,
    boolean cli, boolean mcp) {
    var capability = AiAgentCapabilities.of(host, agent, scope);

    assertThat(capability.isCliIntegrationSupported()).isEqualTo(cli);
    assertThat(capability.isStandaloneMcpSupported()).isEqualTo(mcp);
  }

  @Test
  void should_enable_cursor_cli_and_mcp_when_the_agent_cli_was_discovered() {
    var capability = AiAgentCapabilities.of(VSCODE, CURSOR, GLOBAL, List.of(AiAgentDetectionSource.CLI));

    assertThat(capability.isCliIntegrationSupported()).isTrue();
    assertThat(capability.isStandaloneMcpSupported()).isTrue();
  }
}
