/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ListAllStandaloneRulesDefinitionsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RulesRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.UpdateStandaloneRulesConfigurationParams;
import org.sonarsource.sonarlint.core.rules.RuleNotFoundException;
import org.sonarsource.sonarlint.core.rules.RulesService;

class RulesRpcServiceDelegate extends AbstractRpcServiceDelegate implements RulesRpcService {

  public RulesRpcServiceDelegate(SonarLintRpcServerImpl server) {
    super(server);
  }

  @Override
  public CompletableFuture<GetEffectiveRuleDetailsResponse> getEffectiveRuleDetails(GetEffectiveRuleDetailsParams params) {
    return requestAsync(cancelMonitor -> {
      try {
        return new GetEffectiveRuleDetailsResponse(
          getBean(RulesService.class).getEffectiveRuleDetails(params.getConfigurationScopeId(), params.getRuleKey(), params.getContextKey(), cancelMonitor));
      } catch (RuleNotFoundException e) {
        var error = new ResponseError(SonarLintRpcErrorCode.RULE_NOT_FOUND, e.getMessage(), e.getRuleKey());
        throw new ResponseErrorException(error);
      }
    }, params.getConfigurationScopeId());
  }

  @Override
  public CompletableFuture<ListAllStandaloneRulesDefinitionsResponse> listAllStandaloneRulesDefinitions() {
    return requestAsync(cancelMonitor -> new ListAllStandaloneRulesDefinitionsResponse(getBean(RulesService.class).listAllStandaloneRulesDefinitions()));
  }

  @Override
  public CompletableFuture<GetStandaloneRuleDescriptionResponse> getStandaloneRuleDetails(GetStandaloneRuleDescriptionParams params) {
    return requestAsync(cancelMonitor -> getBean(RulesService.class).getStandaloneRuleDetails(params.getRuleKey()));
  }

  @Override
  public void updateStandaloneRulesConfiguration(UpdateStandaloneRulesConfigurationParams params) {
    notify(() -> getBean(RulesService.class).updateStandaloneRulesConfiguration(params.getRuleConfigByKey()));
  }
}
