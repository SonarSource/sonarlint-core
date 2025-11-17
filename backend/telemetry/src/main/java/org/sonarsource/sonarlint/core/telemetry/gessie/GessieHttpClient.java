/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry.gessie;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.telemetry.InternalDebug;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

public class GessieHttpClient {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String X_API_KEY_IDE = "value";

  private final Gson gson = configureGson();
  private final HttpClient client;
  private final String endpoint;
  @Value("${SONARLINT_TELEMETRY_LOG:false}")
  private boolean isTelemetryLogEnabled;

  public GessieHttpClient(HttpClientProvider httpClientProvider, @Qualifier("gessieEndpoint") String gessieEndpoint) {
    this.client = httpClientProvider.getHttpClientWithXApiKeyAndRetries(X_API_KEY_IDE);
    this.endpoint = gessieEndpoint;
  }

  public void postEvent(GessieEvent event) {
    var json = gson.toJson(event);
    logGessiePayload(json);
    var futureResponse = client.postAsync(endpoint + "/ide", HttpClient.JSON_CONTENT_TYPE, json);
    handleGessieResponse(futureResponse);
  }

  private void logGessiePayload(String json) {
    if (isTelemetryLogEnabled) {
      LOG.info("Sending Gessie payload.");
      LOG.info(json);
    }
  }

  private static Gson configureGson() {
    return new GsonBuilder()
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
      .serializeNulls()
      .create();
  }

  private static void handleGessieResponse(CompletableFuture<HttpClient.Response> responseCompletableFuture) {
    responseCompletableFuture.thenAccept(response -> {
      if (!response.isSuccessful() && InternalDebug.isEnabled()) {
        LOG.error("Failed to upload telemetry to Gessie: {}", response);
      }
    }).exceptionally(exception -> {
      if (InternalDebug.isEnabled()) {
        LOG.error("Failed to upload telemetry to Gessie", exception);
      }
      return null;
    });
  }
}
