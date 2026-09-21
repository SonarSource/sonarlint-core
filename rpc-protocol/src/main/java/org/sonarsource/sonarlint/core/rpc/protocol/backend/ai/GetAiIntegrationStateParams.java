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

public class GetAiIntegrationStateParams {
  private final AiIntegrationHost ideHost;
  private final List<AiAgent> detectedAgents;
  private final AiIntegrationScope scope;
  @Nullable
  private final String configurationScopeId;

  public GetAiIntegrationStateParams(AiIntegrationHost ideHost, List<AiAgent> detectedAgents, AiIntegrationScope scope,
    @Nullable String configurationScopeId) {
    this.ideHost = ideHost;
    this.detectedAgents = List.copyOf(detectedAgents);
    this.scope = scope;
    this.configurationScopeId = configurationScopeId;
  }

  public AiIntegrationHost getIdeHost() {
    return ideHost;
  }

  public List<AiAgent> getDetectedAgents() {
    return detectedAgents;
  }

  public AiIntegrationScope getScope() {
    return scope;
  }

  @Nullable
  public String getConfigurationScopeId() {
    return configurationScopeId;
  }
}
