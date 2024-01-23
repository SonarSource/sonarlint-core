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
package org.sonarsource.sonarlint.core.clientapi.backend.rules;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

public interface RulesService {

  /**
   * Returns the effective details about a rule. "Effective" means that returned rule details will take into account
   * the binding and the finding context.
   * @return a completed future if the rule was found, else a failed future
   */
  @JsonRequest
  CompletableFuture<GetEffectiveRuleDetailsResponse> getEffectiveRuleDetails(GetEffectiveRuleDetailsParams params);

  /**
   * Return list of all available rules for SonarLint standalone mode. Used to build the rules configuration UI.
   * The description is not part of the response, since we usually display description one rule at a time.
   * Use {@link RulesService#getStandaloneRuleDetails(GetStandaloneRuleDescriptionParams)} to get the rule description.
   */
  @JsonRequest
  CompletableFuture<ListAllStandaloneRulesDefinitionsResponse> listAllStandaloneRulesDefinitions();

  /**
   * Get rule details of a single rule. The details will include the rule description.
   */
  @JsonRequest
  CompletableFuture<GetStandaloneRuleDescriptionResponse> getStandaloneRuleDetails(GetStandaloneRuleDescriptionParams params);

  /**
   * Notify the backend about changes to the standalone rule's configuration. This configuration will override defaults rule activation and parameters
   */
  @JsonNotification
  void updateStandaloneRulesConfiguration(UpdateStandaloneRulesConfigurationParams params);
}
