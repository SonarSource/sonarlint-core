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

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.aicontext.AiContextRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.aicontext.AskCodebaseQuestionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.aicontext.AskCodebaseQuestionResponse;

public class AiContextRpcServiceDelegate extends AbstractRpcServiceDelegate implements AiContextRpcService {
  
  private static final Logger LOG = LoggerFactory.getLogger(AiContextRpcServiceDelegate.class);
  
  public AiContextRpcServiceDelegate(SonarLintRpcServerImpl sonarLintRpcServer) {
    super(sonarLintRpcServer);
  }

  @Override
  public CompletableFuture<AskCodebaseQuestionResponse> askCodebaseQuestion(AskCodebaseQuestionParams params) {
    LOG.info("AI Context: Received codebase question for scope '{}': '{}'", 
             params.getConfigurationScopeId(), params.getQuestion());
             
    return requestAsync(cancelMonitor -> {
      LOG.debug("AI Context: Processing question for scope '{}'", params.getConfigurationScopeId());
      
      // TODO: Implement actual AI Context service
      // For now, return empty results as a placeholder
      var response = new AskCodebaseQuestionResponse(Collections.emptyList());
      
      LOG.info("AI Context: Returning {} location(s) for scope '{}'", 
               response.getLocations().size(), params.getConfigurationScopeId());
      
      return response;
    }, params.getConfigurationScopeId());
  }
}
