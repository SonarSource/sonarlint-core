/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected;

import com.google.common.base.Joiner;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.utils.System2;
import org.sonarqube.ws.client.GetRequest;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.HttpWsClient;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;

public class SonarLintWsClient {

  private static final Logger LOG = LoggerFactory.getLogger(SonarLintWsClient.class);

  private final HttpWsClient client;
  private final String userAgent;

  public SonarLintWsClient(ServerConfiguration serverConfig) {
    this.userAgent = serverConfig.getUserAgent();
    client = buildClient(serverConfig);
  }

  private static HttpWsClient buildClient(ServerConfiguration serverConfig) {
    HttpConnector connector = new HttpConnector.Builder().url(serverConfig.getUrl())
      .userAgent(serverConfig.getUserAgent())
      .credentials(serverConfig.getLogin(), serverConfig.getPassword())
      .proxy(serverConfig.getProxy())
      .proxyCredentials(serverConfig.getProxyLogin(), serverConfig.getProxyPassword())
      .readTimeoutMilliseconds(serverConfig.getReadTimeoutMs())
      .connectTimeoutMilliseconds(serverConfig.getConnectTimeoutMs())
      .build();
    return new HttpWsClient(connector);
  }

  public CloseableWsResponse get(String path) {
    CloseableWsResponse response = rawGet(path);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  public CloseableWsResponse post(String path) {
    CloseableWsResponse response = rawPost(path);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  /**
   * Execute POST and don't check response
   */
  public CloseableWsResponse rawPost(String path) {
    long startTime = System2.INSTANCE.now();
    PostRequest request = new PostRequest(path);
    WsResponse response = client.wsConnector().call(request);
    long duration = System2.INSTANCE.now() - startTime;
    LOG.debug("{} {} {} | time={}ms", request.getMethod(), response.code(), response.requestUrl(), duration);
    return new CloseableWsResponse(response);
  }

  /**
   * Execute GET and don't check response
   */
  public CloseableWsResponse rawGet(String path) {
    long startTime = System2.INSTANCE.now();
    GetRequest request = new GetRequest(path);
    WsResponse response = client.wsConnector().call(request);
    long duration = System2.INSTANCE.now() - startTime;
    LOG.debug("{} {} {} | time={}ms", request.getMethod(), response.code(), response.requestUrl(), duration);
    return new CloseableWsResponse(response);
  }

  public static RuntimeException handleError(CloseableWsResponse toBeClosed) {
    try (CloseableWsResponse failedResponse = toBeClosed) {
      if (failedResponse.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
        return new IllegalStateException("Not authorized. Please check server credentials.");
      }
      if (failedResponse.code() == HttpURLConnection.HTTP_FORBIDDEN) {
        // Details are in response content
        return new IllegalStateException(tryParseAsJsonError(failedResponse.content()));
      }
      return new IllegalStateException(
        "Error " + failedResponse.code() + " on " + failedResponse.requestUrl() + (failedResponse.hasContent() ? (": " + tryParseAsJsonError(failedResponse.content())) : ""));
    }
  }

  private static String tryParseAsJsonError(String responseContent) {
    try {
      JsonParser parser = new JsonParser();
      JsonObject obj = parser.parse(responseContent).getAsJsonObject();
      JsonArray errors = obj.getAsJsonArray("errors");
      List<String> errorMessages = new ArrayList<>();
      for (JsonElement e : errors) {
        errorMessages.add(e.getAsJsonObject().get("msg").getAsString());
      }
      return Joiner.on(", ").join(errorMessages);
    } catch (Exception e) {
      return responseContent;
    }
  }

  public String getUserAgent() {
    return userAgent;
  }

}
