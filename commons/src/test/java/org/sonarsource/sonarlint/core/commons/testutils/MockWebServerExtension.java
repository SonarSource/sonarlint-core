/*
 * SonarLint Commons
 * Copyright (C) 2016-2022 SonarSource SA
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.fail;

public class MockWebServerExtension implements BeforeEachCallback, AfterEachCallback {

  private static final OkHttpClient SHARED_CLIENT = new OkHttpClient();

  private MockWebServer server;
  protected final Map<String, MockResponse> responsesByPath = new HashMap<>();

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    server = new MockWebServer();
    responsesByPath.clear();
    final Dispatcher dispatcher = new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
        if (responsesByPath.containsKey(request.getPath())) {
          return responsesByPath.get(request.getPath());
        }
        return new MockResponse().setResponseCode(404);
      }
    };
    server.setDispatcher(dispatcher);
    server.start();
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    server.shutdown();
  }

  public void addStringResponse(String path, String body) {
    responsesByPath.put(path, new MockResponse().setBody(body));
  }

  public void removeResponse(String path) {
    responsesByPath.remove(path);
  }

  public void addResponse(String path, MockResponse response) {
    responsesByPath.put(path, response);
  }

  public int getRequestCount() {
    return server.getRequestCount();
  }

  public RecordedRequest takeRequest() {
    try {
      return server.takeRequest();
    } catch (InterruptedException e) {
      fail(e);
      return null; // appeasing the compiler: this line will never be executed.
    }
  }

  public String url(String path) {
    return server.url(path).toString();
  }

  public void addResponseFromResource(String path, String responseResourcePath) {
    try (var b = new Buffer()) {
      responsesByPath.put(path, new MockResponse().setBody(b.readFrom(requireNonNull(MockWebServerExtension.class.getResourceAsStream(responseResourcePath)))));
    } catch (IOException e) {
      fail(e);
    }

  }

  public static HttpClient httpClient() {
    return new HttpClient() {

      private final OkHttpClient okClient = SHARED_CLIENT.newBuilder().build();

      @Override
      public Response post(String url, String contentType, String bodyContent) {
        var body = RequestBody.create(MediaType.get(contentType), bodyContent);
        var request = new Request.Builder()
          .url(url)
          .post(body)
          .build();
        return executeRequest(request);
      }

      @Override
      public Response get(String url) {
        var request = new Request.Builder()
          .url(url)
          .build();
        return executeRequest(request);
      }

      @Override
      public CompletableFuture<Response> getAsync(String url) {
        var request = new Request.Builder()
          .url(url)
          .build();
        return executeRequestAsync(request);
      }

      @Override
      public Response delete(String url, String contentType, String bodyContent) {
        var body = RequestBody.create(MediaType.get(contentType), bodyContent);
        var request = new Request.Builder()
          .url(url)
          .delete(body)
          .build();
        return executeRequest(request);
      }

      private Response executeRequest(Request request) {
        try {
          return wrap(okClient.newCall(request).execute());
        } catch (IOException e) {
          throw new IllegalStateException("Unable to execute request: " + e.getMessage(), e);
        }
      }

      private CompletableFuture<Response> executeRequestAsync(Request request) {
        var call = okClient.newCall(request);
        var futureResponse = new CompletableFuture<Response>()
          .whenComplete((response, error) -> {
            if (error instanceof CancellationException) {
              call.cancel();
            }
          });
        call.enqueue(new Callback() {
          @Override
          public void onFailure(Call call, IOException e) {
            futureResponse.completeExceptionally(e);
          }

          @Override
          public void onResponse(Call call, okhttp3.Response response) {
            futureResponse.complete(wrap(response));
          }
        });
        return futureResponse;
      }

      private Response wrap(okhttp3.Response wrapped) {
        return new Response() {

          @Override
          public String url() {
            return wrapped.request().url().toString();
          }

          @Override
          public int code() {
            return wrapped.code();
          }

          @Override
          public void close() {
            wrapped.close();
          }

          @Override
          public String bodyAsString() {
            try (var body = wrapped.body()) {
              return body.string();
            } catch (IOException e) {
              throw new IllegalStateException("Unable to read response body: " + e.getMessage(), e);
            }
          }

          @Override
          public InputStream bodyAsStream() {
            return wrapped.body().byteStream();
          }

          @Override
          public String toString() {
            return wrapped.toString();
          }
        };
      }

    };
  }

}
