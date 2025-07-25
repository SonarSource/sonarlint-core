/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.sca;

import java.util.UUID;
import javax.annotation.Nullable;

public class ChangeDependencyRiskStatusParams {
  private final String configurationScopeId;
  private final UUID dependencyRiskKey;
  private final DependencyRiskTransition transition;
  @Nullable
  private final String comment;

  public ChangeDependencyRiskStatusParams(String configurationScopeId, UUID dependencyRiskKey, DependencyRiskTransition transition, @Nullable String comment) {
    this.configurationScopeId = configurationScopeId;
    this.dependencyRiskKey = dependencyRiskKey;
    this.transition = transition;
    this.comment = comment;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public UUID getDependencyRiskKey() {
    return dependencyRiskKey;
  }

  public DependencyRiskTransition getTransition() {
    return transition;
  }

  @Nullable
  public String getComment() {
    return comment;
  }
}
