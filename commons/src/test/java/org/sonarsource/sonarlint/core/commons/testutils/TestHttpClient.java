/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.testutils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.http.HttpConnectionListener;

public class TestHttpClient implements HttpClient {

  private static final java.net.http.HttpClient SHARED_CLIENT = java.net.http.HttpClient.newBuilder().build();
  private final Map<String, String> headers = new HashMap<>();

  public TestHttpClient withHeader(String name, String value) {
    headers.put(name, value);
    return this;
  }

  @Override
  public Response post(String url, String contentType, String bodyContent) {
    var request = requestBuilder(url)
      .headers("Content-Type", contentType)
      .POST(HttpRequest.BodyPublishers.ofString(bodyContent)).build();
    return executeRequest(request);
  }

  @Override
  public Response get(String url) {
    var request = requestBuilder(url).GET().build();
    return executeRequest(request);
  }

  @Override
  public CompletableFuture<Response> getAsync(String url) {
    var request = requestBuilder(url).GET().build();
    return executeRequestAsync(request);
  }

  @Override
  public AsyncRequest getEventStream(String url, HttpConnectionListener connectionListener, Consumer<String> messageConsumer) {
    var request = requestBuilder(url).GET().build();
    var responseFuture = SHARED_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
    var wrappedFuture = responseFuture.whenComplete((response, ex) -> {
      if (ex != null) {
        connectionListener.onError(null);
      } else {
        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
          connectionListener.onConnected();
          // simplified reading, for tests we assume the event comes full, never chunked
          messageConsumer.accept(wrap(response).bodyAsString());
        } else {
          connectionListener.onError(response.statusCode());
        }
      }
    });
    return new HttpAsyncRequest(wrappedFuture);
  }

  @Override
  public Response delete(String url, String contentType, String bodyContent) {
    var request = requestBuilder(url)
      .headers("Content-Type", contentType)
      .method("DELETE", HttpRequest.BodyPublishers.ofString(bodyContent)).build();
    return executeRequest(request);
  }

  private Response executeRequest(HttpRequest request) {
    try {
      return wrap(SHARED_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream()));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to execute request: " + e.getMessage(), e);
    }
  }

  private CompletableFuture<Response> executeRequestAsync(HttpRequest request) {
    var call = SHARED_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
    return call.thenApply(this::wrap);
  }

  private Response wrap(HttpResponse<InputStream> wrapped) {
    return new Response() {

      @Override
      public String url() {
        return wrapped.request().uri().toString();
      }

      @Override
      public int code() {
        return wrapped.statusCode();
      }

      @Override
      public void close() {
        // Nothing
      }

      @Override
      public String bodyAsString() {
        try (var body = wrapped.body()) {
          return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
          throw new IllegalStateException("Unable to read response body: " + e.getMessage(), e);
        }
      }

      @Override
      public InputStream bodyAsStream() {
        return wrapped.body();
      }

      @Override
      public String toString() {
        return wrapped.toString();
      }
    };
  }

  private HttpRequest.Builder requestBuilder(String url) {
    var builder = HttpRequest.newBuilder().uri(URI.create(url));
    headers.forEach(builder::headers);
    return builder;
  }

  public static class HttpAsyncRequest implements HttpClient.AsyncRequest {
    private final CompletableFuture<HttpResponse<InputStream>> response;

    private HttpAsyncRequest(CompletableFuture<HttpResponse<InputStream>> response) {
      this.response = response;
    }

    @Override
    public void cancel() {
      response.cancel(true);
    }

  }

}
