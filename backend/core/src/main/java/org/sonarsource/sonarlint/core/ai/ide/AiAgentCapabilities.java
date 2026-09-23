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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationAgentCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationScope;

final class AiAgentCapabilities {

  private AiAgentCapabilities() {
  }

  static AiIntegrationAgentCapability of(AiIntegrationHost host, AiAgent agent, AiIntegrationScope scope) {
    return of(host, agent, scope, List.of(AiAgentDetectionSource.IDE));
  }

  static AiIntegrationAgentCapability of(AiIntegrationHost host, AiAgent agent, AiIntegrationScope scope,
    List<AiAgentDetectionSource> detectionSources) {
    var profile = AgentProfiles.of(agent);
    var available = profile.compatibleWith(host) || detectionSources.contains(AiAgentDetectionSource.CLI);
    var cliIntegrationSupported = scope == AiIntegrationScope.GLOBAL && profile.cliIntegrationSupported() && available;
    return new AiIntegrationAgentCapability(
      agent,
      detectionSources,
      cliIntegrationSupported,
      profile.mcpJsonSection().isPresent() && available,
      supportsHook(host, profile, scope),
      cliIntegrationSupported);
  }

  static Optional<String> jsonSectionName(AiAgent agent) {
    return AgentProfiles.of(agent).mcpJsonSection();
  }

  static boolean supportsRuleFile(AiAgent agent) {
    return AgentProfiles.of(agent).ruleFileSupported();
  }

  static HookSupport hookSupport(AiAgent agent) {
    return AgentProfiles.of(agent).hookSupport();
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

  private static boolean supportsHook(AiIntegrationHost host, AgentProfile profile, AiIntegrationScope scope) {
    return scope == AiIntegrationScope.GLOBAL
      && profile.onNativeHost(host)
      && profile.hookSupport() == HookSupport.CONFIGURED;
  }

  enum HookSupport {
    CONFIGURED,
    NOT_YET_IMPLEMENTED,
    UNSUPPORTED_COPILOT,
    UNSUPPORTED_CLI
  }
}
