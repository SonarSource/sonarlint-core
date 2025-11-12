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
import org.sonarsource.sonarlint.core.ai.ide.AiAgentService;
import org.sonarsource.sonarlint.core.ai.ide.AiHookService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetHookScriptContentParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetHookScriptContentResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetRuleFileContentParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetRuleFileContentResponse;

public class AiAgentRpcServiceDelegate extends AbstractRpcServiceDelegate implements AiAgentRpcService {
  public AiAgentRpcServiceDelegate(SonarLintRpcServerImpl sonarLintRpcServer) {
    super(sonarLintRpcServer);
  }

  @Override
  public CompletableFuture<GetRuleFileContentResponse> getRuleFileContent(GetRuleFileContentParams params) {
    return requestAsync(cancelMonitor -> getBean(AiAgentService.class).getRuleFileContent(params.getAiAgent()));
  }

  @Override
  public CompletableFuture<GetHookScriptContentResponse> getHookScriptContent(GetHookScriptContentParams params) {
    return requestAsync(cancelMonitor -> getBean(AiHookService.class).getHookScriptContent(params.getAiAgent()));
  }
}
