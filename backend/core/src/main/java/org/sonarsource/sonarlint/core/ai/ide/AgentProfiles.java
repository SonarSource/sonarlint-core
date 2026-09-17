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

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.ai.ide.AiAgentCapabilities.HookSupport;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;

final class AgentProfiles {

  private static final List<String> VERSION_PROBE = List.of("--version");
  private static final List<String> HELP_PROBE = List.of("--help");
  private static final String MCP_SERVERS_SECTION = "mcpServers";
  private static final List<AiAgent> CLI_DISCOVERY_ORDER = List.of(AiAgent.CLAUDE_CODE, AiAgent.CODEX,
    AiAgent.GITHUB_COPILOT_CLI, AiAgent.CURSOR, AiAgent.ANTIGRAVITY);
  private static final Map<AiAgent, AgentProfile> BY_AGENT = createAll();

  private AgentProfiles() {
  }

  static AgentProfile of(AiAgent agent) {
    return BY_AGENT.get(agent);
  }

  static List<AgentProfile> cliDiscoverable() {
    return CLI_DISCOVERY_ORDER.stream().map(AgentProfiles::of).toList();
  }

  private static Map<AiAgent, AgentProfile> createAll() {
    var profiles = new EnumMap<AiAgent, AgentProfile>(AiAgent.class);
    for (var agent : AiAgent.values()) {
      profiles.put(agent, create(agent));
    }
    var discoverable = profiles.values().stream()
      .filter(AgentProfile::cliDiscoverable)
      .map(AgentProfile::agent)
      .collect(Collectors.toCollection(() -> EnumSet.noneOf(AiAgent.class)));
    if (!discoverable.equals(EnumSet.copyOf(CLI_DISCOVERY_ORDER))) {
      throw new IllegalStateException("CLI discovery order must list every discoverable agent");
    }
    return Map.copyOf(profiles);
  }

  private static AgentProfile create(AiAgent agent) {
    return switch (agent) {
      case CURSOR -> new AgentProfile(agent, List.of("cursor-agent"), HELP_PROBE,
        List.of("cursor agent", "cursor-agent"), Optional.of("cursor"), Optional.of(MCP_SERVERS_SECTION), false,
        Set.of(AiIntegrationHost.CURSOR), true, true, HookSupport.NOT_YET_IMPLEMENTED);
      case GITHUB_COPILOT -> new AgentProfile(agent, List.of(), List.of(), List.of(), Optional.empty(),
        Optional.of("servers"), false,
        Set.of(AiIntegrationHost.VSCODE, AiIntegrationHost.INTELLIJ, AiIntegrationHost.VISUAL_STUDIO), false, true,
        HookSupport.UNSUPPORTED_COPILOT);
      case KIRO -> new AgentProfile(agent, List.of(), List.of(), List.of(), Optional.empty(), Optional.of(MCP_SERVERS_SECTION),
        false, Set.of(AiIntegrationHost.KIRO), false, true, HookSupport.NOT_YET_IMPLEMENTED);
      case WINDSURF -> new AgentProfile(agent, List.of(), List.of(), List.of(), Optional.empty(),
        Optional.of(MCP_SERVERS_SECTION), false, Set.of(AiIntegrationHost.WINDSURF), false, true, HookSupport.CONFIGURED);
      case CLAUDE_CODE -> new AgentProfile(agent, List.of("claude"), VERSION_PROBE, List.of("claude code"),
        Optional.of("claude"), Optional.of(MCP_SERVERS_SECTION), true, Set.of(), true, false, HookSupport.UNSUPPORTED_CLI);
      case CODEX -> new AgentProfile(agent, List.of("codex"), VERSION_PROBE, List.of("codex-cli"), Optional.of("codex"),
        Optional.empty(), true, Set.of(), true, false, HookSupport.UNSUPPORTED_CLI);
      case GITHUB_COPILOT_CLI -> new AgentProfile(agent, List.of("copilot"), VERSION_PROBE,
        List.of("github copilot cli"), Optional.of("copilot"), Optional.empty(), true, Set.of(), true, false,
        HookSupport.UNSUPPORTED_CLI);
      case ANTIGRAVITY -> new AgentProfile(agent, List.of("agy"), HELP_PROBE, List.of("usage of agy:"),
        Optional.of("antigravity"), Optional.empty(), true, Set.of(), true, false, HookSupport.UNSUPPORTED_CLI);
    };
  }
}
