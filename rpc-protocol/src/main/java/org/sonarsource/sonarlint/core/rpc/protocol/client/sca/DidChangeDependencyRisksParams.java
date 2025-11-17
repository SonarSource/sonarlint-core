/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.sca;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;

public class DidChangeDependencyRisksParams {
  private final String configurationScopeId;
  private final Set<UUID> closedDependencyRiskIds;
  private final List<DependencyRiskDto> addedDependencyRisks;
  private final List<DependencyRiskDto> updatedDependencyRisks;

  public DidChangeDependencyRisksParams(String configurationScopeId, Set<UUID> closedDependencyRiskIds, List<DependencyRiskDto> addedDependencyRisks,
    List<DependencyRiskDto> updatedDependencyRisks) {
    this.configurationScopeId = configurationScopeId;
    this.closedDependencyRiskIds = closedDependencyRiskIds;
    this.addedDependencyRisks = addedDependencyRisks;
    this.updatedDependencyRisks = updatedDependencyRisks;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public Set<UUID> getClosedDependencyRiskIds() {
    return closedDependencyRiskIds;
  }

  public List<DependencyRiskDto> getAddedDependencyRisks() {
    return addedDependencyRisks;
  }

  public List<DependencyRiskDto> getUpdatedDependencyRisks() {
    return updatedDependencyRisks;
  }
}
