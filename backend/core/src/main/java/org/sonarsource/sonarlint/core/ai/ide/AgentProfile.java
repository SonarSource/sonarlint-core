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
import java.util.Optional;
import java.util.Set;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;

record AgentProfile(
  AiAgent agent,
  List<String> executableNames,
  List<String> probeArguments,
  List<String> outputMarkers,
  Optional<String> cliTarget,
  Optional<String> mcpJsonSection,
  boolean nativeOnAnyHost,
  Set<AiIntegrationHost> nativeHosts,
  boolean cliIntegrationSupported,
  boolean ruleFileSupported,
  AiAgentCapabilities.HookSupport hookSupport) {

  AgentProfile {
    executableNames = List.copyOf(executableNames);
    probeArguments = List.copyOf(probeArguments);
    outputMarkers = List.copyOf(outputMarkers);
    nativeHosts = Set.copyOf(nativeHosts);
  }

  boolean cliDiscoverable() {
    return !executableNames.isEmpty();
  }

  boolean onNativeHost(AiIntegrationHost host) {
    return nativeOnAnyHost || nativeHosts.contains(host);
  }

  boolean compatibleWith(AiIntegrationHost host) {
    return host == AiIntegrationHost.OTHER || onNativeHost(host);
  }
}
