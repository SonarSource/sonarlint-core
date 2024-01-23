/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.backend.tracking;

import java.util.List;
import java.util.Map;

public class MatchWithServerSecurityHotspotsParams {
  private final String configurationScopeId;
  private final Map<String, List<ClientTrackedFindingDto>> clientTrackedHotspotsByServerRelativePath;
  private final boolean shouldFetchHotspotsFromServer;

  public MatchWithServerSecurityHotspotsParams(String configurationScopeId, Map<String, List<ClientTrackedFindingDto>> clientTrackedHotspotsByServerRelativePath,
    boolean shouldFetchHotspotsFromServer) {
    this.configurationScopeId = configurationScopeId;
    this.clientTrackedHotspotsByServerRelativePath = clientTrackedHotspotsByServerRelativePath;
    this.shouldFetchHotspotsFromServer = shouldFetchHotspotsFromServer;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public Map<String, List<ClientTrackedFindingDto>> getClientTrackedHotspotsByServerRelativePath() {
    return clientTrackedHotspotsByServerRelativePath;
  }

  public boolean shouldFetchHotspotsFromServer() {
    return shouldFetchHotspotsFromServer;
  }
}
