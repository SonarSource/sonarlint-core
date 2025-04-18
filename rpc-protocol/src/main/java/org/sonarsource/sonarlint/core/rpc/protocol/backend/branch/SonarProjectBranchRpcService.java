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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.branch;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

@JsonSegment("branch")
public interface SonarProjectBranchRpcService {

  /**
   * Must be called when any change on the VCS might lead to a different sonar project branch being resolved (could be a different HEAD, a branch checkout).
   */
  @JsonNotification
  void didVcsRepositoryChange(DidVcsRepositoryChangeParams params);

  /**
   * Returns the currently matched Sonar Project branch. Might return a null value in {@link GetMatchedSonarProjectBranchResponse} if no matching happened.
   */
  @JsonRequest
  CompletableFuture<GetMatchedSonarProjectBranchResponse> getMatchedSonarProjectBranch(GetMatchedSonarProjectBranchParams params);
}
