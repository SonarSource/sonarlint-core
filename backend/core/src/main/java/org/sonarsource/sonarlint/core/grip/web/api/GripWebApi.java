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
import org.sonarsource.sonarlint.core.grip.web.api.payload.SuggestFixRequestPayload;
import org.sonarsource.sonarlint.core.grip.web.api.payload.SuggestFixResponsePayload;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestFixParams;

public class GripWebApi {
  private final HttpClientProvider httpClientProvider;

  public GripWebApi(HttpClientProvider httpClientProvider) {
    this.httpClientProvider = httpClientProvider;
  }

  public SuggestFixResponsePayload suggestFix(SuggestFixParams params, String fileContent) {
    var requestBody = serializeRequestBody(params, fileContent);
    try (var response = httpClientProvider.getHttpClientWithPreemptiveAuth(params.getAuthenticationToken()).postWithBearer(
      ensureTrailingSlash(params.getServiceURI()) + "api/suggest/", "application/json",
      requestBody)) {
      return deserializeResponseBody(response.bodyAsString());
    }
  }

  private static String serializeRequestBody(SuggestFixParams params, String fileContent) {
    var issueRange = params.getIssueRange();
    var textRangePayload = new SuggestFixRequestPayload.TextRangePayload(issueRange.getStartLine(), issueRange.getStartLineOffset(), issueRange.getEndLine(),
      issueRange.getEndLineOffset());
    var requestPayload = new SuggestFixRequestPayload(fileContent, params.getIssueMessage(), params.getRuleKey(), textRangePayload);
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
