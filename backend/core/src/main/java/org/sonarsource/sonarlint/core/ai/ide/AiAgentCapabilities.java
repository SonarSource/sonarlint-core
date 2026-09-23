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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationScope;

final class AiAgentCapabilities {

  private AiAgentCapabilities() {
  }

  static AiIntegrationAgentCapability of(AiIntegrationHost host, AiAgent agent, AiIntegrationScope scope) {
    return new AiIntegrationAgentCapability(
      agent,
      supportsCliIntegration(host, agent, scope),
      supportsStandaloneMcp(host, agent),
      supportsHook(host, agent, scope),
      supportsSkills(host, agent, scope));
  }

  static Optional<String> jsonSectionName(AiAgent agent) {
    return switch (agent) {
      case GITHUB_COPILOT -> Optional.of("servers");
      case CURSOR, WINDSURF, KIRO, CLAUDE_CODE -> Optional.of("mcpServers");
      // Codex stores MCP servers in config.toml ([mcp_servers.sonarqube]), so there is no JSON section to edit.
      case CODEX -> Optional.empty();
    };
  }

  static boolean supportsCliIntegration(AiAgent agent) {
    return switch (agent) {
      case CURSOR, CLAUDE_CODE, CODEX -> true;
      case WINDSURF, KIRO, GITHUB_COPILOT -> false;
    };
  }

  private static boolean supportsCliIntegration(AiIntegrationHost host, AiAgent agent, AiIntegrationScope scope) {
    return scope == AiIntegrationScope.GLOBAL && compatibleWith(host, agent) && supportsCliIntegration(agent);
  }

  private static boolean supportsSkills(AiIntegrationHost host, AiAgent agent, AiIntegrationScope scope) {
    return supportsCliIntegration(host, agent, scope);
  }

  private static boolean supportsStandaloneMcp(AiIntegrationHost host, AiAgent agent) {
    return jsonSectionName(agent).isPresent() && compatibleWith(host, agent);
  }

  private static boolean supportsHook(AiIntegrationHost host, AiAgent agent, AiIntegrationScope scope) {
    return scope == AiIntegrationScope.GLOBAL
      && onNativeHost(host, agent)
      && hookSupport(agent) == HookSupport.CONFIGURED;
  }

  private static boolean compatibleWith(AiIntegrationHost host, AiAgent agent) {
    return host == AiIntegrationHost.OTHER || onNativeHost(host, agent);
  }

  private static boolean onNativeHost(AiIntegrationHost host, AiAgent agent) {
    return switch (agent) {
      case CURSOR -> host == AiIntegrationHost.CURSOR;
      case GITHUB_COPILOT -> host == AiIntegrationHost.VSCODE || host == AiIntegrationHost.INTELLIJ
        || host == AiIntegrationHost.VISUAL_STUDIO;
      case KIRO -> host == AiIntegrationHost.KIRO;
      case WINDSURF -> host == AiIntegrationHost.WINDSURF;
      case CLAUDE_CODE, CODEX -> true;
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
