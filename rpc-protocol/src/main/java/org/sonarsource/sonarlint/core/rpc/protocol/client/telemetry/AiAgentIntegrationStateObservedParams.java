/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry;

import java.util.List;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState;

public class AiAgentIntegrationStateObservedParams {
  private final AiIntegrationObservationTrigger trigger;
  private final AiAgent agent;
  private final List<AiAgentDetectionSource> detectionSources;
  private final McpConfigurationState standaloneMcpState;
  private final AiIntegrationHost host;
  private final AiIntegrationEnvironment environment;

  public AiAgentIntegrationStateObservedParams(AiIntegrationObservationTrigger trigger,
    AiAgent agent,
    List<AiAgentDetectionSource> detectionSources,
    McpConfigurationState standaloneMcpState,
    AiIntegrationHost host,
    AiIntegrationEnvironment environment) {
    this.trigger = trigger;
    this.agent = agent;
    this.detectionSources = detectionSources;
    this.standaloneMcpState = standaloneMcpState;
    this.host = host;
    this.environment = environment;
  }

  public AiIntegrationObservationTrigger getTrigger() {
    return trigger;
  }

  public AiAgent getAgent() {
    return agent;
  }

  public List<AiAgentDetectionSource> getDetectionSources() {
    return detectionSources;
  }

  public McpConfigurationState getStandaloneMcpState() {
    return standaloneMcpState;
  }

  public AiIntegrationHost getHost() {
    return host;
  }

  public AiIntegrationEnvironment getEnvironment() {
    return environment;
  }
}
