/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking;

public class ListAllParams {
  private final String configurationScopeId;
  /**
   * Set to true to fetch server taint issues
   */
  private final boolean shouldRefresh;

  public ListAllParams(String configurationScopeId) {
    this(configurationScopeId, false);
  }

  public ListAllParams(String configurationScopeId, boolean shouldRefresh) {
    this.configurationScopeId = configurationScopeId;
    this.shouldRefresh = shouldRefresh;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public boolean shouldRefresh() {
    return shouldRefresh;
  }
}
