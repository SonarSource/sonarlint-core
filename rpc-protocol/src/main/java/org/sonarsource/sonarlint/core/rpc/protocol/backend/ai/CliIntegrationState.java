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

public class CliIntegrationState {
  private final AiAgent agent;
  private final CliIntegrationRecordingStatus recordingStatus;
  private final List<CliIntegrationConfiguration> configurations;

  public CliIntegrationState(AiAgent agent, CliIntegrationRecordingStatus recordingStatus, List<CliIntegrationConfiguration> configurations) {
    this.agent = agent;
    this.recordingStatus = recordingStatus;
    this.configurations = List.copyOf(configurations);
  }

  public AiAgent getAgent() {
    return agent;
  }

  public CliIntegrationRecordingStatus getRecordingStatus() {
    return recordingStatus;
  }

  public List<CliIntegrationConfiguration> getConfigurations() {
    return configurations;
  }
}
