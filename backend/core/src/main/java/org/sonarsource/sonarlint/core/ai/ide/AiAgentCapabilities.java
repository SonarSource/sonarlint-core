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

import java.util.Optional;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationAgentCapability;

final class AiAgentCapabilities {

  private AiAgentCapabilities() {
  }

  static AiIntegrationAgentCapability of(AiAgent agent) {
    return new AiIntegrationAgentCapability(agent, supportsCliIntegration(agent), jsonSectionName(agent).isPresent());
  }

  static Optional<String> jsonSectionName(AiAgent agent) {
    return switch (agent) {
      case GITHUB_COPILOT -> Optional.of("servers");
      case CURSOR, WINDSURF, KIRO, CLAUDE_CODE -> Optional.of("mcpServers");
      case CODEX -> Optional.empty();
    };
  }

  static boolean supportsCliIntegration(AiAgent agent) {
    return switch (agent) {
      case CURSOR, CLAUDE_CODE, CODEX -> true;
      case WINDSURF, KIRO, GITHUB_COPILOT -> false;
    };
  }

  static boolean supportsRuleFile(AiAgent agent) {
    return switch (agent) {
      case CURSOR, WINDSURF, KIRO, GITHUB_COPILOT -> true;
      case CLAUDE_CODE, CODEX -> false;
    };
  }

  static HookSupport hookSupport(AiAgent agent) {
    return switch (agent) {
      case WINDSURF -> HookSupport.CONFIGURED;
      case CURSOR, KIRO -> HookSupport.NOT_YET_IMPLEMENTED;
      case GITHUB_COPILOT -> HookSupport.UNSUPPORTED_COPILOT;
      case CLAUDE_CODE, CODEX -> HookSupport.UNSUPPORTED_CLI;
    };
  }

  static UnsupportedOperationException unsupportedRuleFile(AiAgent agent) {
    return new UnsupportedOperationException(agent + " rule file generation is not supported");
  }

  static UnsupportedOperationException unsupportedHook(AiAgent agent) {
    return switch (hookSupport(agent)) {
      case NOT_YET_IMPLEMENTED -> new UnsupportedOperationException(agent + " hook configuration not yet implemented");
      case UNSUPPORTED_COPILOT -> new UnsupportedOperationException("GitHub Copilot does not support hooks");
      case UNSUPPORTED_CLI -> new UnsupportedOperationException(agent + " hook configuration is not supported");
      case CONFIGURED -> throw new IllegalStateException(agent + " supports hook configuration");
    };
  }

  enum HookSupport {
    CONFIGURED,
    NOT_YET_IMPLEMENTED,
    UNSUPPORTED_COPILOT,
    UNSUPPORTED_CLI
  }
}
