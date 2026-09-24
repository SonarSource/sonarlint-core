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

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationScope;

public class AiIntegrationActionParams {
  private final AiIntegrationAction action;
  private final AiIntegrationActionStatus status;
  @Nullable
  private final AiIntegrationFailureCategory failureCategory;
  @Nullable
  private final AiAgent agent;
  @Nullable
  private final AiIntegrationScope scope;
  private final AiIntegrationHost host;
  private final AiIntegrationEnvironment environment;

  public AiIntegrationActionParams(AiIntegrationAction action,
    AiIntegrationActionStatus status,
    @Nullable AiIntegrationFailureCategory failureCategory,
    @Nullable AiAgent agent,
    @Nullable AiIntegrationScope scope,
    AiIntegrationHost host,
    AiIntegrationEnvironment environment) {
    this.action = action;
    this.status = status;
    this.failureCategory = failureCategory;
    this.agent = agent;
    this.scope = scope;
    this.host = host;
    this.environment = environment;
  }

  public AiIntegrationAction getAction() {
    return action;
  }

  public AiIntegrationActionStatus getStatus() {
    return status;
  }

  @Nullable
  public AiIntegrationFailureCategory getFailureCategory() {
    return failureCategory;
  }

  @Nullable
  public AiAgent getAgent() {
    return agent;
  }

  @Nullable
  public AiIntegrationScope getScope() {
    return scope;
  }

  public AiIntegrationHost getHost() {
    return host;
  }

  public AiIntegrationEnvironment getEnvironment() {
    return environment;
  }
}
