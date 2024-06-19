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
package org.sonarsource.sonarlint.core.grip.web.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.URI;
import java.util.UUID;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.grip.FixSuggestion;
import org.sonarsource.sonarlint.core.grip.web.api.payload.ProvideFeedbackRequestPayload;
import org.sonarsource.sonarlint.core.grip.web.api.payload.SuggestFixRequestPayload;
import org.sonarsource.sonarlint.core.grip.web.api.payload.SuggestFixResponsePayload;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.ProvideFeedbackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestFixParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

public class GripWebApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final HttpClientProvider httpClientProvider;

  public GripWebApi(HttpClientProvider httpClientProvider) {
    this.httpClientProvider = httpClientProvider;
  }

  public Either<String, SuggestFixWebApiResponse> suggestFix(SuggestFixParams params, String fileContent) {
    var requestBody = serializeFixRequestBody(params, fileContent);
    LOG.info("Requesting suggestion from GRIP service: {}", requestBody);
    try (var response = httpClientProvider.getHttpClientWithPreemptiveAuth(params.getAuthenticationToken()).postWithBearer(
      ensureTrailingSlash(params.getServiceURI()) + "api/suggest/", "application/json",
      requestBody)) {
      var code = response.code();
      var responseBodyString = response.bodyAsString();
      if (code >= 200 && response.code() < 400) {
        try {
          LOG.info("Received response from the GRIP service: {}", responseBodyString);
          var responseBody = deserializeResponseBody(responseBodyString);
          return Either.forRight(
            new SuggestFixWebApiResponse(UUID.fromString(response.header("X-Correlation-Id")), responseBody.choices.get(responseBody.choices.size() - 1).message.content));
        } catch (Exception e) {
          LOG.error("Unexpected response received from the suggestion service", e);
          return Either.forLeft("Unexpected response received from the suggestion service");
        }
      } else {
        LOG.info("Error response received: code={}, body={}", code, responseBodyString);
        return Either.forLeft("An error occurred when requesting the suggestion. See logs for more details");
      }
    } catch (Exception e) {
      LOG.error("Error requesting suggestions from the GRIP service", e);
      return Either.forLeft("Error requesting suggestions from the GRIP service. See logs for more details");
    }
  }

  public void provideFeedback(ProvideFeedbackParams params, FixSuggestion fixSuggestion) {
    var requestBody = serializeFeedbackRequestBody(params, fixSuggestion);
    LOG.info("Providing feedback to GRIP service: {}", requestBody);
    try (var ignored = httpClientProvider.getHttpClientWithPreemptiveAuth(params.getAuthenticationToken()).postWithBearer(
      ensureTrailingSlash(params.getServiceURI()) + "api/feedback/", "application/json",
      requestBody)) {
    }
  }

  private static String serializeFixRequestBody(SuggestFixParams params, String fileContent) {
    var issueRange = params.getIssueRange();
    var textRangePayload = new SuggestFixRequestPayload.TextRangePayload(issueRange.getStartLine(), issueRange.getStartLineOffset(), issueRange.getEndLine(),
      issueRange.getEndLineOffset());
    var requestPayload = new SuggestFixRequestPayload(fileContent, params.getIssueMessage(), params.getRuleKey(), textRangePayload);
    return new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create().toJson(requestPayload);
  }

  private static String serializeFeedbackRequestBody(ProvideFeedbackParams params, FixSuggestion fixSuggestion) {
    var rating = params.getRating();
    var requestPayload = new ProvideFeedbackRequestPayload(fixSuggestion.getRuleKey(), params.isAccepted(), rating == null ? null : rating.name(), params.getComment(),
      new ProvideFeedbackRequestPayload.ContextPayload(params.getCorrelationId(), fixSuggestion.getResponseTime(), fixSuggestion.getBefore(), fixSuggestion.getAfter()));
    return new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create().toJson(requestPayload);
  }

  private static SuggestFixResponsePayload deserializeResponseBody(String body) {
    return new Gson().fromJson(body, SuggestFixResponsePayload.class);
  }

  private static String ensureTrailingSlash(URI uri) {
    var uriString = uri.toString();
    return uriString.endsWith("/") ? uriString : (uriString + "/");
  }
}
