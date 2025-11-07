/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.ChangeDependencyRiskStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.CheckDependencyRiskSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.CheckDependencyRiskSupportedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.GetDependencyRiskDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.GetDependencyRiskDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.ListAllDependencyRisksResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.OpenDependencyRiskInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams;
import org.sonarsource.sonarlint.core.sca.DependencyRiskService;

public class DependencyRiskRpcServiceDelegate extends AbstractRpcServiceDelegate implements DependencyRiskRpcService {

  public DependencyRiskRpcServiceDelegate(SonarLintRpcServerImpl server) {
    super(server);
  }

  @Override
  public CompletableFuture<ListAllDependencyRisksResponse> listAll(ListAllParams params) {
    return requestAsync(cancelMonitor -> new ListAllDependencyRisksResponse(getBean(DependencyRiskService.class)
      .listAll(params.getConfigurationScopeId(), params.shouldRefresh(), cancelMonitor)));
  }

  @Override
  public CompletableFuture<Void> changeStatus(ChangeDependencyRiskStatusParams params) {
    return runAsync(cancelMonitor -> {
      try {
        getBean(DependencyRiskService.class).changeStatus(
          params.getConfigurationScopeId(),
          params.getDependencyRiskKey(),
          params.getTransition(),
          params.getComment(),
          cancelMonitor);
      } catch (DependencyRiskService.DependencyRiskNotFoundException e) {
        var error = new ResponseError(SonarLintRpcErrorCode.ISSUE_NOT_FOUND,
          "Dependency Risk with key " + e.getKey() + " was not found", e.getKey());
        throw new ResponseErrorException(error);
      } catch (IllegalArgumentException e) {
        var error = new ResponseError(SonarLintRpcErrorCode.INVALID_ARGUMENT, e.getMessage(), null);
        throw new ResponseErrorException(error);
      }
    }, params.getConfigurationScopeId());
  }

  @Override
  public CompletableFuture<GetDependencyRiskDetailsResponse> getDependencyRiskDetails(GetDependencyRiskDetailsParams params) {
    return requestAsync(cancelMonitor -> getBean(DependencyRiskService.class)
      .getDependencyRiskDetails(params.getConfigurationScopeId(), params.getDependencyRiskKey(), cancelMonitor), params.getConfigurationScopeId());
  }

  @Override
  public CompletableFuture<Void> openDependencyRiskInBrowser(OpenDependencyRiskInBrowserParams params) {
    return runAsync(cancelMonitor -> {
      try {
        getBean(DependencyRiskService.class).openDependencyRiskInBrowser(
          params.getConfigScopeId(),
          params.getDependencyRiskKey());
      } catch (IllegalArgumentException e) {
        var error = new ResponseError(SonarLintRpcErrorCode.INVALID_ARGUMENT, e.getMessage(), null);
        throw new ResponseErrorException(error);
      }
    }, params.getConfigScopeId());
  }

  @Override
  public CompletableFuture<CheckDependencyRiskSupportedResponse> checkSupported(CheckDependencyRiskSupportedParams params) {
    return requestAsync(cancelMonitor ->
      getBean(DependencyRiskService.class).checkSupported(params.getConfigurationScopeId()), params.getConfigurationScopeId());
  }

}
