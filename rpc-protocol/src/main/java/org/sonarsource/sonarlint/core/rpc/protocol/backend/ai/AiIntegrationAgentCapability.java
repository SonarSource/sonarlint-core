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

public class AiIntegrationAgentCapability {
  private final AiAgent agent;
  private final List<AiAgentDetectionSource> detectionSources;
  private final boolean cliIntegrationSupported;
  private final boolean standaloneMcpSupported;
  private final boolean hookSupported;
  private final boolean skillSupported;

  public AiIntegrationAgentCapability(AiAgent agent, List<AiAgentDetectionSource> detectionSources,
    boolean cliIntegrationSupported, boolean standaloneMcpSupported, boolean hookSupported, boolean skillSupported) {
    this.agent = agent;
    this.detectionSources = List.copyOf(detectionSources);
    this.cliIntegrationSupported = cliIntegrationSupported;
    this.standaloneMcpSupported = standaloneMcpSupported;
    this.hookSupported = hookSupported;
    this.skillSupported = skillSupported;
  }

  public AiAgent getAgent() {
    return agent;
  }

  public List<AiAgentDetectionSource> getDetectionSources() {
    return detectionSources;
  }

  public boolean isCliIntegrationSupported() {
    return cliIntegrationSupported;
  }

  public boolean isStandaloneMcpSupported() {
    return standaloneMcpSupported;
  }

  public boolean isHookSupported() {
    return hookSupported;
  }

  public boolean isSkillSupported() {
    return skillSupported;
  }
}
