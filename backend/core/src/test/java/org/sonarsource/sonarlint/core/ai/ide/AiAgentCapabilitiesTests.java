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

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.ai.ide.AiAgentCapabilities.HookSupport;

class AiAgentCapabilitiesTests {

  @Test
  void should_report_cli_and_mcp_capabilities() {
    var cursor = AiAgentCapabilities.of(AiAgent.CURSOR);
    var copilot = AiAgentCapabilities.of(AiAgent.GITHUB_COPILOT);

    assertThat(cursor.isCliIntegrationSupported()).isTrue();
    assertThat(cursor.isStandaloneMcpSupported()).isTrue();
    assertThat(copilot.isCliIntegrationSupported()).isFalse();
    assertThat(copilot.isStandaloneMcpSupported()).isTrue();
  }

  @Test
  void should_classify_rule_file_and_hook_support() {
    assertThat(AiAgentCapabilities.supportsRuleFile(AiAgent.CURSOR)).isTrue();
    assertThat(AiAgentCapabilities.supportsRuleFile(AiAgent.CLAUDE_CODE)).isFalse();
    assertThat(AiAgentCapabilities.hookSupport(AiAgent.WINDSURF)).isEqualTo(HookSupport.CONFIGURED);
    assertThat(AiAgentCapabilities.hookSupport(AiAgent.CURSOR)).isEqualTo(HookSupport.NOT_YET_IMPLEMENTED);
    assertThat(AiAgentCapabilities.hookSupport(AiAgent.GITHUB_COPILOT)).isEqualTo(HookSupport.UNSUPPORTED_COPILOT);
    assertThat(AiAgentCapabilities.hookSupport(AiAgent.CODEX)).isEqualTo(HookSupport.UNSUPPORTED_CLI);
  }

  @Test
  void should_keep_existing_unsupported_messages() {
    assertThat(AiAgentCapabilities.unsupportedRuleFile(AiAgent.CLAUDE_CODE))
      .hasMessage("CLAUDE_CODE rule file generation is not supported");
    assertThat(AiAgentCapabilities.unsupportedHook(AiAgent.CURSOR))
      .hasMessage("CURSOR hook configuration not yet implemented");
    assertThat(AiAgentCapabilities.unsupportedHook(AiAgent.GITHUB_COPILOT))
      .hasMessage("GitHub Copilot does not support hooks");
    assertThat(AiAgentCapabilities.unsupportedHook(AiAgent.CODEX))
      .hasMessage("CODEX hook configuration is not supported");
  }
}
