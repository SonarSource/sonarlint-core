/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetSupportedFilePatternsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetSupportedFilePatternsResponse;

class AnalysisRpcServiceDelegate extends AbstractRpcServiceDelegate implements AnalysisRpcService {

  public AnalysisRpcServiceDelegate(SonarLintRpcServerImpl server) {
    super(server);
  }

  @Override
  public CompletableFuture<GetSupportedFilePatternsResponse> getSupportedFilePatterns(GetSupportedFilePatternsParams params) {
    return requestAsync(
      cancelChecker -> new GetSupportedFilePatternsResponse(getBean(AnalysisService.class).getSupportedFilePatterns(params.getConfigScopeId())),
      params.getConfigScopeId());
  }
}