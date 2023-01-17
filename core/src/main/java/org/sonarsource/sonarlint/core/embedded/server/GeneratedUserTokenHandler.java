/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.embedded.server;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.sonarsource.sonarlint.core.clientapi.backend.authentication.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static java.util.function.Predicate.not;

public class GeneratedUserTokenHandler implements HttpRequestHandler {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final AwaitingUserTokenFutureRepository awaitingUserTokenFutureRepository;

  public GeneratedUserTokenHandler(AwaitingUserTokenFutureRepository awaitingUserTokenFutureRepository) {
    this.awaitingUserTokenFutureRepository = awaitingUserTokenFutureRepository;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    if (!Method.POST.isSame(request.getMethod())) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }

    String token = extractAndValidateToken(request);
    if (token == null) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }

    awaitingUserTokenFutureRepository.consumeFutureResponse()
      .filter(not(CompletableFuture::isCancelled))
      .ifPresent(pendingFuture -> pendingFuture.complete(new HelpGenerateUserTokenResponse(token)));
    response.setCode(HttpStatus.SC_OK);
    response.setEntity(new StringEntity("OK"));
  }

  private static String extractAndValidateToken(ClassicHttpRequest request) throws IOException, ParseException {
    var requestEntityString = EntityUtils.toString(request.getEntity(), "UTF-8");
    String token = null;
    try {
      token = new Gson().fromJson(requestEntityString, TokenPayload.class).token;
    } catch (Exception e) {
      // will be converted to HTTP response later
      LOG.error("Could not deserialize user token", e);
    }
    return token;
  }

  private static class TokenPayload {
    private String token;
  }
}
