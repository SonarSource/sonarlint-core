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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllScaIssuesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ScaIssueTrackingRpcService;
import org.sonarsource.sonarlint.core.tracking.ScaIssueTrackingService;

public class ScaIssueTrackingRpcServiceDelegate extends AbstractRpcServiceDelegate implements ScaIssueTrackingRpcService {
  public ScaIssueTrackingRpcServiceDelegate(SonarLintRpcServerImpl sonarLintRpcServer) {
    super(sonarLintRpcServer);
  }

  @Override
  public CompletableFuture<ListAllScaIssuesResponse> listAll(ListAllParams params) {
    return requestAsync(cancelMonitor -> new ListAllScaIssuesResponse(getBean(ScaIssueTrackingService.class)
      .listAll(params.getConfigurationScopeId(), params.shouldRefresh(), cancelMonitor)));
  }
}
