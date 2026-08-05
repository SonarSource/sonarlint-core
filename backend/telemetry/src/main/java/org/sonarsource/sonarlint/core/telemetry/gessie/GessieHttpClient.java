/*
 * SonarLint Core - Telemetry
 * Copyright (C) SonarSource Sàrl
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

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.telemetry.InternalDebug;
import org.sonarsource.sonarlint.core.telemetry.MachineIdProvider;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorageManager;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieEvent;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieIdentity;
import org.springframework.beans.factory.annotation.Qualifier;

public class GessieHttpClient {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String EVENT_PAYLOAD_KEY = "event_payload";

  private final Gson gson = configureGson();
  private final HttpClient client;
  private final String endpoint;
  private final MachineIdProvider machineIdProvider;
  private final TelemetryLocalStorageManager telemetryLocalStorageManager;

  public GessieHttpClient(HttpClientProvider httpClientProvider,
    @Qualifier("gessieEndpoint") String gessieEndpoint,
    @Qualifier("gessieApiKey") String gessieApiKey,
    MachineIdProvider machineIdProvider,
    TelemetryLocalStorageManager telemetryLocalStorageManager) {
    this.client = httpClientProvider.getHttpClientWithXApiKeyAndRetries(gessieApiKey);
    this.endpoint = gessieEndpoint;
    this.machineIdProvider = machineIdProvider;
    this.telemetryLocalStorageManager = telemetryLocalStorageManager;
  }

  public void postEvent(GessieEvent event) {
    var json = gson.toJson(mergeIdentity(event));
    logGessiePayload(json);
    var futureResponse = client.postAsync(endpoint + "/ide", HttpClient.JSON_CONTENT_TYPE, json);
    handleGessieResponse(futureResponse);
  }

  private JsonObject mergeIdentity(GessieEvent event) {
    var eventJson = gson.toJsonTree(event).getAsJsonObject();
    var eventPayloadElement = eventJson.get(EVENT_PAYLOAD_KEY);
    if (eventPayloadElement != null && eventPayloadElement.isJsonObject()) {
      var identity = new GessieIdentity(machineIdProvider.getMachineId(), telemetryLocalStorageManager.ideInstallationId());
      var identityJson = gson.toJsonTree(identity).getAsJsonObject();
      mergeObjects(identityJson, eventPayloadElement.getAsJsonObject());
    }
    return eventJson;
  }

  private static JsonObject mergeObjects(JsonObject source, JsonObject target) {
    for (Entry<String, JsonElement> entry : source.entrySet()) {
      var value = entry.getValue();
      if (!target.has(entry.getKey())) {
        target.add(entry.getKey(), value);
      } else if (value.isJsonObject()) {
        mergeObjects((JsonObject) value, target.getAsJsonObject(entry.getKey()));
      }
    }
    return target;
  }

  private void logGessiePayload(String json) {
    if (isTelemetryLogEnabled()) {
      LOG.info("Sending Gessie payload.\n{}", json);
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
        LOG.error("Failed to upload telemetry to Gessie: {} \n{}", response,
          response.bodyAsString());
      }
    }).exceptionally(exception -> {
      if (InternalDebug.isEnabled()) {
        LOG.error("Failed to upload telemetry to Gessie", exception);
      }
      return null;
    });
  }

  @VisibleForTesting
  boolean isTelemetryLogEnabled(){
    return Boolean.parseBoolean(System.getenv("SONARLINT_TELEMETRY_LOG"));
  }

}
