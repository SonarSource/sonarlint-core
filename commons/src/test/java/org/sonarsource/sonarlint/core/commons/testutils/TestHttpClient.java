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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.http.HttpConnectionListener;

public class TestHttpClient implements HttpClient {

  private static final OkHttpClient SHARED_CLIENT = new OkHttpClient();
  private final OkHttpClient okClient = SHARED_CLIENT.newBuilder().build();
  private final Map<String, String> headers = new HashMap<>();

  public TestHttpClient withHeader(String name, String value) {
    headers.put(name, value);
    return this;
  }

  @Override
  public Response post(String url, String contentType, String bodyContent) {
    var body = RequestBody.create(MediaType.get(contentType), bodyContent);
    var request = requestBuilder(url)
      .post(body)
      .build();
    return executeRequest(request);
  }

  @Override
  public Response get(String url) {
    var request = requestBuilder(url)
      .build();
    return executeRequest(request);
  }

  @Override
  public CompletableFuture<Response> getAsync(String url) {
    var request = requestBuilder(url)
      .build();
    return executeRequestAsync(request);
  }

  @Override
  public AsyncRequest getEventStream(String url, HttpConnectionListener connectionListener, Consumer<String> messageConsumer) {
    var request = requestBuilder(url)
      .build();
    var call = okClient.newCall(request);
    var asyncRequest = new OkHttpAsyncRequest(call);
    try {
      // use a sync approach to ease testing
      var response = call.execute();
      if (response.isSuccessful()) {
        connectionListener.onConnected();
        // simplified reading, for tests we assume the event comes full, never chunked
        messageConsumer.accept(wrap(response).bodyAsString());
      } else {
        connectionListener.onError(response.code());
      }
    } catch (IOException e) {
      connectionListener.onError(null);
    }
    return asyncRequest;
  }

  @Override
  public Response delete(String url, String contentType, String bodyContent) {
    var body = RequestBody.create(MediaType.get(contentType), bodyContent);
    var request = requestBuilder(url)
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

  private Request.Builder requestBuilder(String url) {
    var builder = new Request.Builder()
      .url(url);
    headers.forEach(builder::addHeader);
    return builder;
  }

  public static class OkHttpAsyncRequest implements HttpClient.AsyncRequest {
    private final Call call;

    private OkHttpAsyncRequest(Call call) {
      this.call = call;
    }

    @Override
    public void cancel() {
      call.cancel();
    }

    public void await() {
      var beginTime = System.currentTimeMillis();
      while (!call.isExecuted() || beginTime > System.currentTimeMillis() - 5000L)
        ;
    }
  }

}
