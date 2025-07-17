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
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.ChangeScaIssueStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.GetDependencyRiskDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.GetDependencyRiskDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.ScaRpcService;
import org.sonarsource.sonarlint.core.sca.ScaService;

public class ScaRpcServiceDelegate extends AbstractRpcServiceDelegate implements ScaRpcService {

  public ScaRpcServiceDelegate(SonarLintRpcServerImpl server) {
    super(server);
  }

  @Override
  public CompletableFuture<Void> changeStatus(ChangeScaIssueStatusParams params) {
    return runAsync(cancelMonitor -> {
      try {
        getBean(ScaService.class).changeStatus(
          params.getConfigurationScopeId(),
          params.getIssueReleaseKey(),
          params.getTransition(),
          params.getComment(),
          cancelMonitor);
      } catch (ScaService.ScaIssueNotFoundException e) {
        var error = new ResponseError(SonarLintRpcErrorCode.ISSUE_NOT_FOUND,
          "Dependency Risk with key " + e.getIssueKey() + " was not found", e.getIssueKey());
        throw new ResponseErrorException(error);
      } catch (IllegalArgumentException e) {
        var error = new ResponseError(SonarLintRpcErrorCode.INVALID_ARGUMENT, e.getMessage(), null);
        throw new ResponseErrorException(error);
      }
    }, params.getConfigurationScopeId());
  }

  @Override
  public CompletableFuture<GetDependencyRiskDetailsResponse> getDependencyRiskDetails(GetDependencyRiskDetailsParams params) {
    return requestAsync(cancelMonitor -> getBean(ScaService.class)
      .getDependencyRiskDetails(params.getConfigurationScopeId(), params.getDependencyRiskKey(), cancelMonitor), params.getConfigurationScopeId());
  }
}
