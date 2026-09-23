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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.ai.ide.AiAgentCapabilities.HookSupport;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;

final class AgentProfiles {

  private static final List<String> VERSION_PROBE = List.of("--version");
  private static final List<String> HELP_PROBE = List.of("--help");
  private static final String MCP_SERVERS_SECTION = "mcpServers";
  private static final List<DiscoveredCli> CLI_DISCOVERY = List.of(
    discovered(AiAgent.CLAUDE_CODE, List.of("claude"), VERSION_PROBE, List.of("claude code")),
    discovered(AiAgent.CODEX, List.of("codex"), VERSION_PROBE, List.of("codex-cli")),
    discovered(AiAgent.GITHUB_COPILOT_CLI, List.of("copilot"), VERSION_PROBE, List.of("github copilot cli")),
    discovered(AiAgent.CURSOR, List.of("cursor-agent"), HELP_PROBE, List.of("cursor agent", "cursor-agent")),
    discovered(AiAgent.ANTIGRAVITY, List.of("agy"), HELP_PROBE, List.of("usage of agy:")));
  private static final Map<AiAgent, CliProbe> PROBES_BY_AGENT = CLI_DISCOVERY.stream()
    .collect(Collectors.toUnmodifiableMap(DiscoveredCli::agent, DiscoveredCli::probe));
  private static final Map<AiAgent, AgentProfile> BY_AGENT = createAll();

  private AgentProfiles() {
  }

  static AgentProfile of(AiAgent agent) {
    return BY_AGENT.get(agent);
  }

  static List<AgentProfile> cliDiscoverable() {
    return CLI_DISCOVERY.stream().map(discovered -> of(discovered.agent())).toList();
  }

  private static DiscoveredCli discovered(AiAgent agent, List<String> executableNames, List<String> arguments,
    List<String> outputMarkers) {
    return new DiscoveredCli(agent, new CliProbe(executableNames, arguments, outputMarkers));
  }

  private static Map<AiAgent, AgentProfile> createAll() {
    var profiles = new EnumMap<AiAgent, AgentProfile>(AiAgent.class);
    for (var agent : AiAgent.values()) {
      profiles.put(agent, create(agent));
    }
    return Map.copyOf(profiles);
  }

  private static AgentProfile create(AiAgent agent) {
    return switch (agent) {
      case CURSOR -> profile(agent, Optional.of("cursor"), Optional.of(MCP_SERVERS_SECTION),
        NativeHosts.only(AiIntegrationHost.CURSOR), true, true, HookSupport.NOT_YET_IMPLEMENTED);
      case GITHUB_COPILOT -> profile(agent, Optional.empty(), Optional.of("servers"),
        NativeHosts.only(AiIntegrationHost.VSCODE, AiIntegrationHost.INTELLIJ, AiIntegrationHost.VISUAL_STUDIO),
        false, true, HookSupport.UNSUPPORTED_COPILOT);
      case KIRO -> profile(agent, Optional.empty(), Optional.of(MCP_SERVERS_SECTION),
        NativeHosts.only(AiIntegrationHost.KIRO), false, true, HookSupport.NOT_YET_IMPLEMENTED);
      case WINDSURF -> profile(agent, Optional.empty(), Optional.of(MCP_SERVERS_SECTION),
        NativeHosts.only(AiIntegrationHost.WINDSURF), false, true, HookSupport.CONFIGURED);
      case CLAUDE_CODE -> profile(agent, Optional.of("claude"), Optional.of(MCP_SERVERS_SECTION),
        NativeHosts.any(), true, false, HookSupport.UNSUPPORTED_CLI);
      case CODEX -> profile(agent, Optional.of("codex"), Optional.empty(),
        NativeHosts.any(), true, false, HookSupport.UNSUPPORTED_CLI);
      case GITHUB_COPILOT_CLI -> profile(agent, Optional.of("copilot"), Optional.empty(),
        NativeHosts.any(), true, false, HookSupport.UNSUPPORTED_CLI);
      case ANTIGRAVITY -> profile(agent, Optional.of("antigravity"), Optional.empty(),
        NativeHosts.any(), true, false, HookSupport.UNSUPPORTED_CLI);
    };
  }

  private static AgentProfile profile(AiAgent agent, Optional<String> cliTarget, Optional<String> mcpJsonSection,
    NativeHosts nativeHosts, boolean cliIntegrationSupported, boolean ruleFileSupported, HookSupport hookSupport) {
    return new AgentProfile(agent, PROBES_BY_AGENT.get(agent), cliTarget, mcpJsonSection, nativeHosts,
      cliIntegrationSupported, ruleFileSupported, hookSupport);
  }

  private record DiscoveredCli(AiAgent agent, CliProbe probe) {
  }
}
