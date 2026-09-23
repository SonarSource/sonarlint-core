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
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;

record CliProbe(List<String> executableNames, List<String> arguments, List<String> outputMarkers) {

  CliProbe {
    executableNames = List.copyOf(executableNames);
    arguments = List.copyOf(arguments);
    outputMarkers = List.copyOf(outputMarkers);
  }
}

record NativeHosts(boolean anyHost, Set<AiIntegrationHost> hosts) {

  NativeHosts {
    hosts = Set.copyOf(hosts);
  }

  static NativeHosts any() {
    return new NativeHosts(true, Set.of());
  }

  static NativeHosts only(AiIntegrationHost... hosts) {
    return new NativeHosts(false, Set.of(hosts));
  }

  boolean matches(AiIntegrationHost host) {
    return anyHost || hosts.contains(host);
  }
}

record AgentProfile(
  AiAgent agent,
  @Nullable CliProbe cliProbe,
  Optional<String> cliTarget,
  Optional<String> mcpJsonSection,
  NativeHosts nativeHosts,
  boolean cliIntegrationSupported,
  boolean ruleFileSupported,
  AiAgentCapabilities.HookSupport hookSupport) {

  boolean onNativeHost(AiIntegrationHost host) {
    return nativeHosts.matches(host);
  }

  boolean compatibleWith(AiIntegrationHost host) {
    return host == AiIntegrationHost.OTHER || onNativeHost(host);
  }
}
