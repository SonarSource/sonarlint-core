/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.connection;

import java.util.function.Consumer;
import java.util.function.Function;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.InvalidTokenParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarlint.core.serverapi.exception.UnauthorizedException;

public class ServerConnectionWrapper {

  private final String connectionId;
  private final ServerApi serverApi;
  private final SonarLintRpcClient client;
  private ConnectionState state = ConnectionState.NEVER_USED;

  public ServerConnectionWrapper(String connectionId, ServerApi serverApi, SonarLintRpcClient client) {
    this.connectionId = connectionId;
    this.serverApi = serverApi;
    this.client = client;
  }

  public boolean isSonarCloud() {
    return serverApi.isSonarCloud();
  }

  public boolean isValid() {
    return state == ConnectionState.NEVER_USED || state == ConnectionState.ACTIVE;
  }

  public <T> T withClientApiAndReturn(Function<ServerApi, T> serverApiConsumer) {
    try {
      var result = serverApiConsumer.apply(serverApi);
      state = ConnectionState.ACTIVE;
      return result;
    } catch (ForbiddenException e) {
      state = ConnectionState.INVALID_CREDENTIALS;
      client.invalidToken(new InvalidTokenParams(connectionId));
    } catch (UnauthorizedException e) {
      state = ConnectionState.MISSING_PERMISSION;
      client.invalidToken(new InvalidTokenParams(connectionId));
    }
    return null;
  }

  public void withClientApi(Consumer<ServerApi> serverApiConsumer) {
    try {
      serverApiConsumer.accept(serverApi);
      state = ConnectionState.ACTIVE;
    } catch (ForbiddenException e) {
      state = ConnectionState.INVALID_CREDENTIALS;
      client.invalidToken(new InvalidTokenParams(connectionId));
    } catch (UnauthorizedException e) {
      state = ConnectionState.MISSING_PERMISSION;
      client.invalidToken(new InvalidTokenParams(connectionId));
    }
  }

  void withClientApiRethrowing(Consumer<ServerApi> serverApiConsumer) {
    try {
      serverApiConsumer.accept(serverApi);
      state = ConnectionState.ACTIVE;
    } catch (ForbiddenException e) {
      state = ConnectionState.INVALID_CREDENTIALS;
      client.invalidToken(new InvalidTokenParams(connectionId));
      throw e;
    } catch (UnauthorizedException e) {
      state = ConnectionState.MISSING_PERMISSION;
      client.invalidToken(new InvalidTokenParams(connectionId));
      throw e;
    }
  }

  public ConnectionState getState() {
    return state;
  }

  public void setState(ConnectionState state) {
    this.state = state;
  }
}
