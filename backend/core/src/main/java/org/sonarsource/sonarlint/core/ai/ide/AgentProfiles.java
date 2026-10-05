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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;

final class AgentProfiles {

  private static final List<String> VERSION_PROBE = List.of("--version");
  private static final List<String> HELP_PROBE = List.of("--help");
  private static final String MCP_SERVERS_SECTION = "mcpServers";
  private static final String CODEX_CLI_NAME = "codex";
  private static final List<DiscoveredCli> CLI_DISCOVERY = List.of(
    discovered(AiAgent.CLAUDE_CODE, List.of("claude"), VERSION_PROBE, List.of("claude code")),
    discovered(AiAgent.CODEX, List.of(CODEX_CLI_NAME), VERSION_PROBE, List.of("codex-cli")),
    discovered(AiAgent.GITHUB_COPILOT_CLI, List.of("copilot"), VERSION_PROBE, List.of("github copilot cli")),
    discovered(AiAgent.CURSOR, List.of("cursor-agent"), HELP_PROBE, List.of("cursor agent", "cursor-agent")),
    discovered(AiAgent.ANTIGRAVITY, List.of("agy"), HELP_PROBE, List.of("usage of agy:")));
  private static final Map<AiAgent, CliProbe> PROBES_BY_AGENT = CLI_DISCOVERY.stream()
    .collect(Collectors.toUnmodifiableMap(DiscoveredCli::agent, DiscoveredCli::probe));
  private static final List<AgentProfile> ALL = List.of(
    profile(AiAgent.CLAUDE_CODE, "claude", "claude-code", MCP_SERVERS_SECTION, NativeHosts.any()),
    profile(AiAgent.GITHUB_COPILOT_CLI, "copilot", "copilot-cli", null, NativeHosts.any()),
    profile(AiAgent.CODEX, CODEX_CLI_NAME, CODEX_CLI_NAME, null, NativeHosts.any()),
    profile(AiAgent.CURSOR, "cursor", "cursor", MCP_SERVERS_SECTION, NativeHosts.only(AiIntegrationHost.CURSOR)),
    profile(AiAgent.ANTIGRAVITY, "antigravity", "antigravity", null, NativeHosts.any()),
    profile(AiAgent.GITHUB_COPILOT, null, null, "servers",
      NativeHosts.only(AiIntegrationHost.VSCODE, AiIntegrationHost.INTELLIJ, AiIntegrationHost.VISUAL_STUDIO)),
    profile(AiAgent.KIRO, null, null, MCP_SERVERS_SECTION, NativeHosts.only(AiIntegrationHost.KIRO)),
    profile(AiAgent.WINDSURF, null, null, MCP_SERVERS_SECTION, NativeHosts.only(AiIntegrationHost.WINDSURF)),
    profile(AiAgent.JUNIE, null, null, MCP_SERVERS_SECTION, NativeHosts.only(AiIntegrationHost.INTELLIJ)),
    profile(AiAgent.JETBRAINS_AI_ASSISTANT, null, null, MCP_SERVERS_SECTION, NativeHosts.only(AiIntegrationHost.INTELLIJ)));
  private static final Map<AiAgent, AgentProfile> BY_AGENT = ALL.stream()
    .collect(Collectors.toUnmodifiableMap(AgentProfile::agent, profile -> profile));
  private static final Map<String, AiAgent> CLI_INTEGRATIONS = createCliIntegrations();

  private AgentProfiles() {
  }

  static AgentProfile of(AiAgent agent) {
    return BY_AGENT.get(agent);
  }

  static List<AgentProfile> cliDiscoverable() {
    return CLI_DISCOVERY.stream().map(discovered -> of(discovered.agent())).toList();
  }

  static Map<String, AiAgent> cliIntegrations() {
    return CLI_INTEGRATIONS;
  }

  private static DiscoveredCli discovered(AiAgent agent, List<String> executableNames, List<String> arguments,
    List<String> outputMarkers) {
    return new DiscoveredCli(agent, new CliProbe(executableNames, arguments, outputMarkers));
  }

  private static Map<String, AiAgent> createCliIntegrations() {
    var integrations = new LinkedHashMap<String, AiAgent>();
    ALL.forEach(profile -> profile.cliIntegrationId().ifPresent(id -> integrations.put(id, profile.agent())));
    return Collections.unmodifiableMap(integrations);
  }

  private static AgentProfile profile(AiAgent agent, @Nullable String cliTarget, @Nullable String cliIntegrationId,
    @Nullable String mcpJsonSection, NativeHosts nativeHosts) {
    return new AgentProfile(agent, PROBES_BY_AGENT.get(agent), Optional.ofNullable(cliTarget),
      Optional.ofNullable(mcpJsonSection), nativeHosts, Optional.ofNullable(cliIntegrationId));
  }

  private record DiscoveredCli(AiAgent agent, CliProbe probe) {
  }
}
