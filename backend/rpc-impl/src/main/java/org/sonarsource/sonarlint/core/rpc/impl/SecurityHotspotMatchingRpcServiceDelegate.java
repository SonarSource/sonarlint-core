/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.MatchWithServerSecurityHotspotsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.MatchWithServerSecurityHotspotsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.SecurityHotspotMatchingRpcService;
import org.sonarsource.sonarlint.core.tracking.SecurityHotspotMatchingService;

public class SecurityHotspotMatchingRpcServiceDelegate extends AbstractRpcServiceDelegate implements SecurityHotspotMatchingRpcService {

  public SecurityHotspotMatchingRpcServiceDelegate(SonarLintRpcServerImpl server) {
    super(server);
  }

  @Override
  public CompletableFuture<MatchWithServerSecurityHotspotsResponse> matchWithServerSecurityHotspots(MatchWithServerSecurityHotspotsParams params) {
    return requestAsync(
      cancelMonitor -> new MatchWithServerSecurityHotspotsResponse(getBean(SecurityHotspotMatchingService.class).matchWithServerSecurityHotspots(params.getConfigurationScopeId(),
        params.getClientTrackedHotspotsByIdeRelativePath(), params.shouldFetchHotspotsFromServer(), cancelMonitor)),
      params.getConfigurationScopeId());
  }
}
