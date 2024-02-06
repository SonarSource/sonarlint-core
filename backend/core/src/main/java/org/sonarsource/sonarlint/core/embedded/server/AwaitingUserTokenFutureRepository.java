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
package org.sonarsource.sonarlint.core.embedded.server;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;

import static org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository.haveSameOrigin;

@Named
@Singleton
public class AwaitingUserTokenFutureRepository {
  private final ConcurrentHashMap<String, CompletableFuture<HelpGenerateUserTokenResponse>> awaitingFuturesByServerUrl = new ConcurrentHashMap<>();

  public void addExpectedResponse(String serverBaseUrl, CompletableFuture<HelpGenerateUserTokenResponse> futureResponse) {
    var previousFuture = awaitingFuturesByServerUrl.put(serverBaseUrl, futureResponse);
    if (previousFuture != null) {
      previousFuture.cancel(false);
    }
    futureResponse.whenComplete((r, e) -> awaitingFuturesByServerUrl.remove(serverBaseUrl, futureResponse));
  }

  public Optional<CompletableFuture<HelpGenerateUserTokenResponse>> consumeFutureResponse(String serverOrigin) {
    for (var iterator = awaitingFuturesByServerUrl.entrySet().iterator(); iterator.hasNext();) {
      var entry = iterator.next();
      if (haveSameOrigin(entry.getKey(), serverOrigin)) {
        iterator.remove();
        return Optional.of(entry.getValue());
      }
    }
    return Optional.empty();
  }

}
