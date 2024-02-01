/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.analysis;

import java.util.Set;

public class DidChangeAnalysisReadinessParams {
  private final Set<String> configurationScopeIds;
  private final boolean areReadyForAnalysis;

  public DidChangeAnalysisReadinessParams(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {
    this.configurationScopeIds = configurationScopeIds;
    this.areReadyForAnalysis = areReadyForAnalysis;
  }

  public Set<String> getConfigurationScopeIds() {
    return configurationScopeIds;
  }

  public boolean areReadyForAnalysis() {
    return areReadyForAnalysis;
  }
}
