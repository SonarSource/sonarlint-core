/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.usertoken;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

public interface UserTokenRpcService {
  /**
   * <p> It revokes a user token that is existing on the server and was handed over to the client.
   * It silently deals with the following conditions:
   * <ul>
   *   <li>the token provided by name (identified by {@link RevokeTokenParams#getTokenName()} exists</li>
   * </ul>
   * In those cases a completed future will be returned.
   * </p>
   * <p>
   * It returns a failed future if:
   * <ul>
   *   <li>there is a communication problem with the server: network outage, server is down, unauthorized</li>
   * </ul>
   * </p>
   */
  @JsonRequest
  CompletableFuture<Void> revokeToken(RevokeTokenParams params);

}
