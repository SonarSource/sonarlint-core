/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.binding;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.GetBindingSuggestionsResponse;

@JsonSegment("binding")
public interface BindingRpcService {

  /**
   * Calculates a suggested binding for a 'configScopeId' and 'connectionId' specified in the {@link GetBindingSuggestionParams}
   * @return {@link GetBindingSuggestionsResponse} containing binding suggestions
   */
  @JsonRequest
  CompletableFuture<GetBindingSuggestionsResponse> getBindingSuggestions(GetBindingSuggestionParams params);

  /**
   * <p> It gets file contents for a shared Connected Mode configuration file.
   * It returns file contents that look like either
   <li>{
   "sonarCloudOrganization": "$organization",
   "projectKey": "$projectKey"
   }</li>
   OR
   <li>   {
   "sonarQubeUri": "$serverUrl",
   "projectKey": "$projectKey"
   }</li>

   <p>Fails if there is no binding found for the configScopeId</p>
   */
  @JsonRequest
  CompletableFuture<GetSharedConnectedModeConfigFileResponse> getSharedConnectedModeConfigFileContents(GetSharedConnectedModeConfigFileParams params);
}
