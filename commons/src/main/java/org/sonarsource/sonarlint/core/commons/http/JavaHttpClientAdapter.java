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
package org.sonarsource.sonarlint.core.commons.http;

import java.net.URISyntaxException;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.async.methods.AbstractCharResponseConsumer;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.util.Timeout;

public class JavaHttpClientAdapter implements HttpClient {

  private static final Timeout STREAM_CONNECTION_REQUEST_TIMEOUT = Timeout.ofSeconds(10);
  private static final Timeout STREAM_CONNECTION_TIMEOUT = Timeout.ofMinutes(1);
  private final CloseableHttpAsyncClient javaClient;
  @Nullable
  private final String usernameOrToken;
  @Nullable
  private final String password;
  private boolean connected = false;

  public JavaHttpClientAdapter(CloseableHttpAsyncClient javaClient, @Nullable String usernameOrToken, @Nullable String password) {
    this.javaClient = javaClient;
    this.usernameOrToken = usernameOrToken;
    this.password = password;
  }

  @Override
  public Response post(String url, String contentType, String bodyContent) {
    try {
      return postAsync(url, contentType, bodyContent).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public CompletableFuture<Response> postAsync(String url, String contentType, String body) {
    var request = SimpleRequestBuilder.post(url)
      .setBody(body, ContentType.parse(contentType))
      .build();
    return executeAsync(request);
  }

  @Override
  public Response get(String url) {
    try {
      return getAsync(url).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public CompletableFuture<Response> getAsync(String url) {
    return executeAsync(SimpleRequestBuilder.get(url).build());
  }

  @Override
  public Response delete(String url, String contentType, String bodyContent) {
    var httpRequest = SimpleRequestBuilder
      .delete(url)
      .setBody(bodyContent, ContentType.parse(contentType))
      .build();
    try {
      return executeAsync(httpRequest).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public AsyncRequest getEventStream(String url, HttpConnectionListener connectionListener, Consumer<String> messageConsumer) {
    var request = SimpleRequestBuilder.get(url).build();
    request.setConfig(RequestConfig.custom()
      .setConnectionRequestTimeout(STREAM_CONNECTION_REQUEST_TIMEOUT)
      .setConnectTimeout(STREAM_CONNECTION_TIMEOUT)
      .setResponseTimeout(Timeout.ZERO_MILLISECONDS)
      .build());

    if (usernameOrToken != null) {
      request.setHeader("Authorization", basic(usernameOrToken, Objects.requireNonNullElse(password, "")));
    }
    request.setHeader("Accept", "text/event-stream");
    connected = false;
    var httpFuture = javaClient.execute(new BasicRequestProducer(request, null),
      new AbstractCharResponseConsumer<>() {
        @Override
        public void releaseResources() {
          // should we close something ?
        }

        @Override
        protected int capacityIncrement() {
          return Integer.MAX_VALUE;
        }

        @Override
        protected void data(CharBuffer src, boolean endOfStream) {
          if (connected) {
            messageConsumer.accept(src.toString());
          }
        }

        @Override
        protected void start(HttpResponse httpResponse, ContentType contentType) {
          if (httpResponse.getCode() < 200 || httpResponse.getCode() >= 300) {
            connectionListener.onError(httpResponse.getCode());
          } else {
            connected = true;
            connectionListener.onConnected();
          }
        }

        @Override
        protected Object buildResult() {
          return null;
        }

        @Override
        public void failed(Exception cause) {
          System.out.println(cause);
        }
      }, new FutureCallback<>() {

        @Override
        public void completed(Object result) {
          if (connected) {
            connectionListener.onClosed();
          }
        }

        @Override
        public void failed(Exception ex) {
          if (connected) {
            // called when disconnected from server
            connectionListener.onClosed();
          } else {
            connectionListener.onError(null);
          }
        }

        @Override
        public void cancelled() {
          // nothing to do, the completable future is already canceled
        }
      });

    return new HttpAsyncRequest(httpFuture);
  }

  private CompletableFuture<Response> executeAsync(SimpleHttpRequest httpRequest) {
    try {
      if (usernameOrToken != null) {
        httpRequest.setHeader("Authorization", basic(usernameOrToken, Objects.requireNonNullElse(password, "")));
      }
      var futureResponse = new CompletableFuture<Response>();
      var httpFuture = javaClient.execute(httpRequest, new FutureCallback<>() {
        @Override
        public void completed(SimpleHttpResponse result) {
          String uri;
          // getRequestUri may be relative, so we prefer getUri
          try {
            uri = httpRequest.getUri().toString();
          } catch (URISyntaxException e) {
            uri = httpRequest.getRequestUri();
          }
          futureResponse.complete(new ApacheHttpResponse(uri, result));
        }

        @Override
        public void failed(Exception ex) {
          futureResponse.completeExceptionally(ex);
        }

        @Override
        public void cancelled() {
          futureResponse.cancel(true);
        }
      });
      return futureResponse.whenComplete((ignore, error) -> {
        if (error instanceof CancellationException) {
          httpFuture.cancel(false);
        }
      });
    } catch (Exception e) {
      throw new IllegalStateException("Unable to execute request: " + e.getMessage(), e);
    }
  }

  private String basic(String username, String password) {
    var usernameAndPassword = String.format("%s:%s", username, password);
    var encoded = Base64.getEncoder().encodeToString(usernameAndPassword.getBytes(StandardCharsets.UTF_8));
    return String.format("Basic %s", encoded);
  }

  public static class HttpAsyncRequest implements AsyncRequest {
    private final Future<?> response;

    private HttpAsyncRequest(Future<?> response) {
      this.response = response;
    }

    @Override
    public void cancel() {
      response.cancel(true);
    }

  }

}
