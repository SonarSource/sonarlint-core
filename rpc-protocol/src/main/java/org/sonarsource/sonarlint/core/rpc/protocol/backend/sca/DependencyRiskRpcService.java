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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.sca;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams;

@JsonSegment("dependencyRisk")
public interface DependencyRiskRpcService {

  /**
   * Returns the list of dependency risks detected for the given configuration scopes.
   */
  @JsonRequest
  CompletableFuture<ListAllDependencyRisksResponse> listAll(ListAllParams params);

    /**
   * <p> It changes a status of a Dependency Risk (SCA finding) that exists on the server. In detail, it is responsible for:
   * <ul>
   *   <li>Changes the status of a Dependency Risk (identified by {@link ChangeDependencyRiskStatusParams#getDependencyRiskKey()})</li>
   *   <li>Updates the Dependency Risk status in the local storage</li>
   *   <li>Calls the server to update the Dependency Risk status</li>
   * </ul>
   *</p>
   * It returns a failed future if:
   * <ul>
   *   <li>the provided configuration scope (identified by {@link ChangeDependencyRiskStatusParams#getConfigurationScopeId()} is unknown</li>
   *   <li>the connection bound to the configuration scope is unknown</li>
   *   <li>the issueReleaseKey is not found in the local storage</li>
   *   <li>the Dependency Risk is not found either on the server or in the local storage for issues</li>
   *   <li>there is a communication problem with the server: network outage, server is down, unauthorized</li>
   *   <li>the transition is ACCEPT, FIXED, or SAFE, but no comment is provided</li>
   * </ul>
   * </p>
   */  
  @JsonRequest
  CompletableFuture<Void> changeStatus(ChangeDependencyRiskStatusParams params);

  /**
   * Returns the details of a dependency risk including description and affected packages.
   */
  @JsonRequest
  CompletableFuture<GetDependencyRiskDetailsResponse> getDependencyRiskDetails(GetDependencyRiskDetailsParams params);

  @JsonRequest
  CompletableFuture<Void> openDependencyRiskInBrowser(OpenDependencyRiskInBrowserParams params);

  /**
   * Checks if the Dependency Risk feature is supported for the given configuration scope.
   * Reasons for not being supported include:
   * <ul>
   *   <li></li>
   *   <li>Not using version 2025.4 or higher</li>
   *   <li>Not using edition Enterprise or higher</li>
   *   <li>Not using Advanced Security (SCA not enabled)</li>
   * </ul>
   */
  @JsonRequest
  CompletableFuture<CheckDependencyRiskSupportedResponse> checkSupported(CheckDependencyRiskSupportedParams params);

}
