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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.ai;

import java.util.List;
import javax.annotation.Nullable;

public class GetAiIntegrationStateResponse {
  private final SonarQubeCliState cli;
  private final List<AiIntegrationAgentCapability> agents;
  private final List<AiIntegrationConnection> connectionChoices;
  @Nullable
  private final String recommendedConnectionId;

  public GetAiIntegrationStateResponse(SonarQubeCliState cli, List<AiIntegrationAgentCapability> agents,
    List<AiIntegrationConnection> connectionChoices, @Nullable String recommendedConnectionId) {
    this.cli = cli;
    this.agents = List.copyOf(agents);
    this.connectionChoices = List.copyOf(connectionChoices);
    this.recommendedConnectionId = recommendedConnectionId;
  }

  public SonarQubeCliState getCli() {
    return cli;
  }

  public List<AiIntegrationAgentCapability> getAgents() {
    return agents;
  }

  /** Connections the client may use to prefill an interactive CLI login. */
  public List<AiIntegrationConnection> getConnectionChoices() {
    return connectionChoices;
  }

  /** The connection selected from the current configuration scope, when one is available. */
  @Nullable
  public String getRecommendedConnectionId() {
    return recommendedConnectionId;
  }
}
