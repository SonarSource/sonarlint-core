package org.sonarsource.sonarlint.core.telemetry.gessie;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieEvent;

public class GessieHttpClient {

  private final Gson gson = configureGson();
  private final HttpClient client;
  private final String endpoint;

  public GessieHttpClient(HttpClientProvider httpClientProvider, String endpoint) {
    this.client = httpClientProvider.getHttpClient();
    this.endpoint = endpoint;
  }

  public void postEvent(GessieEvent event) {
    client.postAsync(endpoint, HttpClient.JSON_CONTENT_TYPE, gson.toJson(event))
      .join();
  }

  private static Gson configureGson() {
    return new GsonBuilder()
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
      .serializeNulls()
      .create();
  }
}
