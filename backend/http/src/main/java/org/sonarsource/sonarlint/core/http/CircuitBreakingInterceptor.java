/*
 * SonarLint Core - HTTP
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
package org.sonarsource.sonarlint.core.http;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.async.methods.SimpleBody;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;

class CircuitBreakingInterceptor implements AsyncExecChainHandler {
  private static final int MAX_REQUESTS = 10;
  private static final int MAX_SIGNATURES = 10_000;
  private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(10);

  // Monotonic time source for the rolling window and cache expiry; replaceable in tests.
  private final Ticker ticker;
  // Bounded cache of admitted timestamps per request fingerprint, oldest first.
  private final Cache<String, ArrayDeque<Long>> admissions;

  CircuitBreakingInterceptor() {
    this(Ticker.systemTicker());
  }

  CircuitBreakingInterceptor(Ticker ticker) {
    this.ticker = ticker;
    admissions = CacheBuilder.newBuilder()
      .maximumSize(MAX_SIGNATURES)
      .expireAfterAccess(Duration.ofNanos(WINDOW_NANOS))
      .concurrencyLevel(1)
      .ticker(ticker)
      .build();
  }

  @Override
  public void execute(HttpRequest request, @Nullable AsyncEntityProducer entityProducer, AsyncExecChain.Scope scope, AsyncExecChain chain,
    AsyncExecCallback callback) throws HttpException, IOException {
    // Multi-project synchronization legitimately repeats the same GET requests.
    if (Method.GET.isSame(request.getMethod())) {
      chain.proceed(request, entityProducer, scope, callback);
      return;
    }
    String signature;
    try {
      signature = fingerprint(request, entityProducer, scope.originalRequest);
    } catch (HttpException e) {
      callback.failed(e);
      return;
    }
    if (admit(signature)) {
      chain.proceed(request, entityProducer, scope, callback);
    } else {
      callback.failed(new HttpException("Too many matching HTTP requests in a short period"));
    }
  }

  // Checks and records admission atomically; rejected requests do not extend the window.
  private synchronized boolean admit(String signature) {
    var now = ticker.read();
    var history = admissions.getIfPresent(signature);
    if (history == null) {
      history = new ArrayDeque<>(MAX_REQUESTS);
    }
    while (!history.isEmpty() && (now - history.peekFirst()) >= WINDOW_NANOS) {
      history.removeFirst();
    }
    if (history.size() == MAX_REQUESTS) {
      return false;
    }
    history.addLast(now);
    admissions.put(signature, history);
    return true;
  }

  // Identifies matching requests without retaining their URLs, credentials, or bodies.
  private static String fingerprint(HttpRequest request, @Nullable AsyncEntityProducer producer, HttpRequest originalRequest) throws HttpException {
    var body = originalRequest instanceof SimpleHttpRequest simpleRequest ? simpleRequest.getBody() : null;
    if (body == null && producer != null) {
      throw new HttpException("Cannot check HTTP request frequency for an unsupported request body");
    }
    var hash = sha256();
    frame(hash, request.getMethod());
    try {
      frame(hash, request.getUri().toString());
    } catch (URISyntaxException e) {
      throw new HttpException("Cannot check HTTP request frequency for an invalid URI");
    }
    hashHeaders(hash, request);
    frame(hash, contentType(request, producer));
    hashBody(hash, body);
    return HexFormat.of().formatHex(hash.digest());
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private static void hashHeaders(MessageDigest hash, HttpRequest request) {
    for (var headerName : new String[] {"Authorization", "x-api-key", "Accept"}) {
      var headers = request.getHeaders(headerName);
      frame(hash, headers.length);
      for (var header : headers) {
        frame(hash, header.getValue());
      }
    }
  }

  @Nullable
  private static String contentType(HttpRequest request, @Nullable AsyncEntityProducer producer) {
    var header = request.getFirstHeader("Content-Type");
    if (header != null) {
      return header.getValue();
    }
    return producer != null ? producer.getContentType() : null;
  }

  private static void hashBody(MessageDigest hash, @Nullable SimpleBody body) {
    if (body == null) {
      hash.update((byte) 0);
    } else {
      hash.update((byte) 1);
      var bytes = body.isText()
        ? body.getBodyText().getBytes(ContentType.getCharset(body.getContentType(), StandardCharsets.UTF_8))
        : body.getBodyBytes();
      frame(hash, bytes.length);
      hash.update(bytes);
    }
  }

  // Prefixes each value with its byte length to keep request components unambiguous.
  private static void frame(MessageDigest hash, @Nullable String value) {
    if (value == null) {
      frame(hash, -1);
    } else {
      var bytes = value.getBytes(StandardCharsets.UTF_8);
      frame(hash, bytes.length);
      hash.update(bytes);
    }
  }

  private static void frame(MessageDigest hash, int value) {
    hash.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
  }
}
