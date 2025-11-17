/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.labs;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

@JsonSegment("labs")
public interface IdeLabsRpcService {
  /**
   * Allows a user to join the IDE Labs program.
   *
   * @param params the parameters containing the user's email address and requesting IDE name
   * @return a CompletableFuture that resolves to a JoinIdeLabsProgramResponse
   * <p>
   * <p>JoinIdeLabsProgramResponse.isSuccess() will be false if email validation failed on the server side, or if an unexpected error occurred.
   * If isSuccess() is false, JoinIdeLabsProgramResponse.getMessage() contains a human-readable explanation.</p>
   */
  @JsonRequest
  CompletableFuture<JoinIdeLabsProgramResponse> joinIdeLabsProgram(JoinIdeLabsProgramParams params);
}
