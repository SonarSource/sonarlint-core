/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.connection;

import java.time.Instant;
import java.time.Period;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.InvalidTokenParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.exception.UnauthorizedException;

public class SonarQubeClient {

  private static final Period WRONG_TOKEN_NOTIFICATION_INTERVAL = Period.ofDays(1);
  private final String connectionId;
  @Nullable
  private final ServerApi serverApi;
  private final SonarLintRpcClient client;
  private SonarQubeClientState state = SonarQubeClientState.ACTIVE;
  @Nullable
  private Instant lastNotificationTime;

  public SonarQubeClient(String connectionId, @Nullable ServerApi serverApi, SonarLintRpcClient client) {
    this.connectionId = connectionId;
    this.serverApi = serverApi;
    this.client = client;
  }

  public boolean isActive() {
    return serverApi != null && state == SonarQubeClientState.ACTIVE;
  }

  public <T> T withClientApiAndReturn(Function<ServerApi, T> serverApiConsumer) {
    try {
      var result = serverApiConsumer.apply(serverApi);
      state = SonarQubeClientState.ACTIVE;
      lastNotificationTime = null;
      return result;
    } catch (UnauthorizedException e) {
      state = SonarQubeClientState.INVALID_CREDENTIALS;
      notifyClientAboutWrongTokenIfNeeded();
    }
    return null;
  }

  public void withClientApi(Consumer<ServerApi> serverApiConsumer) {
    try {
      serverApiConsumer.accept(serverApi);
      state = SonarQubeClientState.ACTIVE;
      lastNotificationTime = null;
    } catch (UnauthorizedException e) {
      state = SonarQubeClientState.INVALID_CREDENTIALS;
      notifyClientAboutWrongTokenIfNeeded();
    }
  }

  private boolean shouldNotifyAboutWrongToken() {
    if (state != SonarQubeClientState.INVALID_CREDENTIALS && state != SonarQubeClientState.MISSING_PERMISSION) {
      return false;
    }
    if (lastNotificationTime == null) {
      return true;
    }
    return lastNotificationTime.plus(WRONG_TOKEN_NOTIFICATION_INTERVAL).isBefore(Instant.now());
  }

  private void notifyClientAboutWrongTokenIfNeeded() {
    if (shouldNotifyAboutWrongToken()) {
      client.invalidToken(new InvalidTokenParams(connectionId));
      lastNotificationTime = Instant.now();
    }
  }
}
