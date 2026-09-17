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

/** Agent CLI integrate targets and standalone JSON MCP section names. */
final class AiAgentSupport {

  private AiAgentSupport() {
  }

  static Optional<String> cliTarget(AiAgent agent) {
    return switch (agent) {
      case CURSOR -> Optional.of("cursor");
      case CLAUDE_CODE -> Optional.of("claude");
      case CODEX -> Optional.of("codex");
      case WINDSURF, KIRO, GITHUB_COPILOT -> Optional.empty();
    };
  }

  static Optional<String> jsonSectionName(AiAgent agent) {
    return switch (agent) {
      case GITHUB_COPILOT -> Optional.of("servers");
      case CURSOR, WINDSURF, KIRO, CLAUDE_CODE -> Optional.of("mcpServers");
      case CODEX -> Optional.empty();
    };
  }
}
