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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CircuitBreakingInterceptorTests {
  private static final String URL = "https://example.org/api?key=value";
  private final AtomicLong now = new AtomicLong();
  private final CircuitBreakingInterceptor underTest = new CircuitBreakingInterceptor(new Ticker() {
    @Override
    public long read() {
      return now.get();
    }
  });

  @Test
  void it_should_not_limit_repeated_get_requests() throws Exception {
    var request = new BasicHttpRequest("GET", "https://example.org/api/system/status");
    for (var i = 0; i < 20; i++) {
      assertThat(admitted(request)).isTrue();
    }
  }

  @Test
  void it_should_reject_the_eleventh_matching_request_and_recover_at_the_exact_boundary() throws Exception {
    admitRequests(10);
    assertThat(admitted(request())).isFalse();

    now.set(TimeUnit.SECONDS.toNanos(10) - 1);
    assertThat(admitted(request())).isFalse();
    now.incrementAndGet();

    admitRequests(10);
    assertThat(admitted(request())).isFalse();
  }

  @Test
  void it_should_use_a_rolling_window() throws Exception {
    admitRequests(9);
    now.set(TimeUnit.SECONDS.toNanos(9));
    admitRequests(1);
    assertThat(admitted(request())).isFalse();

    now.set(TimeUnit.SECONDS.toNanos(10));

    admitRequests(9);
    assertThat(admitted(request())).isFalse();
  }

  @Test
  void rejected_requests_should_not_delay_recovery() throws Exception {
    admitRequests(10);
    for (var second = 1; second < 10; second++) {
      now.set(TimeUnit.SECONDS.toNanos(second));
      assertThat(admitted(request())).isFalse();
    }
    now.set(TimeUnit.SECONDS.toNanos(10));

    assertThat(admitted(request())).isTrue();
  }

  @Test
  void it_should_admit_exactly_ten_concurrent_matching_requests() throws Exception {
    assertThat(concurrentAdmissions()).isEqualTo(10);
    now.set(TimeUnit.SECONDS.toNanos(10));
    assertThat(concurrentAdmissions()).isEqualTo(10);
  }

  @Test
  void it_should_distinguish_request_method_complete_url_and_payload() throws Exception {
    admitRequests(10);

    for (var different : List.of(
      request("DELETE", URL, "payload"),
      request("POST", "https://other.org/api?key=value", "payload"),
      request("POST", "http://example.org/api?key=value", "payload"),
      request("POST", "https://example.org:8443/api?key=value", "payload"),
      request("POST", "https://example.org/other?key=value", "payload"),
      request("POST", "https://example.org/api?key=other", "payload"),
      request("POST", URL, "different payload"))) {
      assertThat(admitted(different)).isTrue();
    }
  }

  @Test
  void it_should_distinguish_authentication_accept_and_content_type() throws Exception {
    admitRequests(10);
    for (var header : List.of("Authorization", "x-api-key", "Accept", "Content-Type")) {
      var different = request();
      different.setHeader(header, "different");
      assertThat(admitted(different)).isTrue();
      if (!"Content-Type".equals(header)) {
        different.removeHeaders(header);
        assertThat(admitted(different)).isTrue();
      }
    }
  }

  @Test
  void it_should_match_header_names_case_insensitively_and_frame_values() throws Exception {
    admitRequests(10);
    var same = request();
    same.setHeader("authorization", "Bearer secret");
    same.setHeader("X-API-KEY", "key");
    same.setHeader("accept", "application/json");
    assertThat(admitted(same)).isFalse();

    var first = request();
    first.setHeader("Authorization", "a");
    first.setHeader("x-api-key", "bc");
    var second = request();
    second.setHeader("Authorization", "ab");
    second.setHeader("x-api-key", "c");
    for (var i = 0; i < 10; i++) {
      assertThat(admitted(first)).isTrue();
    }
    assertThat(admitted(second)).isTrue();
  }

  @Test
  void it_should_distinguish_absent_and_empty_bodies() throws Exception {
    var absent = request();
    absent.setBody(null);
    for (var i = 0; i < 10; i++) {
      assertThat(admitted(absent)).isTrue();
    }
    var empty = request();
    empty.setBody(new byte[0], ContentType.APPLICATION_JSON);

    assertThat(admitted(empty)).isTrue();
  }

  @Test
  void it_should_match_text_and_binary_payloads_using_the_content_type_charset() throws Exception {
    for (var charset : List.of(StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1)) {
      for (var length : new int[] {2048, 2049}) {
        var contentType = ContentType.create("text/plain", charset);
        var text = "é".repeat(length);
        var first = request();
        first.setHeader("Content-Type", contentType.toString());
        first.setBody(text, contentType);
        for (var i = 0; i < 10; i++) {
          assertThat(admitted(first)).isTrue();
        }
        var samePayload = request();
        samePayload.setHeader("Content-Type", contentType.toString());
        samePayload.setBody(text.getBytes(charset), contentType);
        var differentPayload = request();
        differentPayload.setHeader("Content-Type", contentType.toString());
        differentPayload.setBody("è".repeat(length), contentType);

        assertThat(admitted(samePayload)).isFalse();
        assertThat(admitted(differentPayload)).isTrue();
        assertThat(first.getBodyText()).isEqualTo(text);
      }
    }
  }

  @Test
  void it_should_use_utf8_for_long_text_without_an_explicit_charset() throws Exception {
    var text = "é".repeat(2049);
    var first = request();
    first.setBody(text, ContentType.create("application/json"));
    for (var i = 0; i < 10; i++) {
      assertThat(admitted(first)).isTrue();
    }
    var samePayload = request();
    samePayload.setBody(text.getBytes(StandardCharsets.UTF_8), ContentType.create("application/json"));

    assertThat(admitted(samePayload)).isFalse();
  }

  @Test
  void it_should_use_producer_content_type_unless_a_header_is_present_without_consuming_the_producer() throws Exception {
    var original = request();
    original.removeHeaders("Content-Type");
    var producer = mock(AsyncEntityProducer.class);
    when(producer.getContentType()).thenReturn(ContentType.APPLICATION_JSON.toString());
    var chain = mock(AsyncExecChain.class);
    execute(original, producer, chain, mock(AsyncExecCallback.class));
    verify(producer).getContentType();
    verifyNoMoreInteractions(producer);

    admitRequests(9);
    assertThat(admitted(request())).isFalse();
    var explicit = request();
    explicit.setHeader("Content-Type", ContentType.APPLICATION_JSON.toString());
    when(producer.getContentType()).thenReturn("text/plain");
    var callback = mock(AsyncExecCallback.class);
    execute(explicit, producer, chain, callback);
    verify(callback).failed(isA(HttpException.class));
    verify(producer).getContentType();
    verifyNoMoreInteractions(producer);
    assertThat(original.getBodyText()).isEqualTo("payload");
  }

  @Test
  void it_should_keep_admissions_after_failure_and_cancellation() throws Exception {
    var calls = new AtomicInteger();
    AsyncExecChain chain = (request, producer, scope, callback) -> {
      if (calls.getAndIncrement() % 2 == 0) {
        callback.failed(new IOException("failure"));
      } else {
        scope.cancellableDependency.cancel();
      }
    };
    for (var i = 0; i < 10; i++) {
      execute(request(), null, chain, mock(AsyncExecCallback.class));
    }

    assertThat(admitted(request())).isFalse();
    assertThat(calls.get()).isEqualTo(10);
  }

  @Test
  void it_should_delegate_and_fail_outside_the_admission_lock() throws Exception {
    AsyncExecChain chain = (request, producer, scope, callback) -> assertThat(Thread.holdsLock(underTest)).isFalse();
    for (var i = 0; i < 10; i++) {
      execute(request(), null, chain, mock(AsyncExecCallback.class));
    }
    var callback = mock(AsyncExecCallback.class);
    doAnswer(invocation -> {
      assertThat(Thread.holdsLock(underTest)).isFalse();
      return null;
    }).when(callback).failed(any());

    execute(request(), null, mock(AsyncExecChain.class), callback);

    var failure = ArgumentCaptor.forClass(Exception.class);
    verify(callback).failed(failure.capture());
    assertThat(failure.getValue()).isExactlyInstanceOf(HttpException.class)
      .hasMessage("Too many matching HTTP requests in a short period");
  }

  @Test
  void it_should_support_generic_bodyless_requests_and_reject_unsupported_bodies() throws Exception {
    var original = new BasicHttpRequest("POST", URL);
    assertThat(admitted(original)).isTrue();
    var producer = mock(AsyncEntityProducer.class);
    var chain = mock(AsyncExecChain.class);
    var callback = mock(AsyncExecCallback.class);

    execute(original, producer, chain, callback);

    verify(callback).failed(isA(HttpException.class));
    verifyNoInteractions(chain, producer);
  }

  private int concurrentAdmissions() throws Exception {
    try (var executor = Executors.newFixedThreadPool(20)) {
      var ready = new CountDownLatch(20);
      var start = new CountDownLatch(1);
      var results = new ArrayList<Future<Boolean>>();
      for (var i = 0; i < 20; i++) {
        results.add(executor.submit(() -> {
          ready.countDown();
          assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
          return admitted(request());
        }));
      }
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      var admitted = 0;
      for (var result : results) {
        if (result.get(5, TimeUnit.SECONDS)) {
          admitted++;
        }
      }
      return admitted;
    }
  }

  private void admitRequests(int count) throws Exception {
    for (var i = 0; i < count; i++) {
      assertThat(admitted(request())).isTrue();
    }
  }

  private boolean admitted(HttpRequest original) throws Exception {
    var delegated = new AtomicBoolean();
    var callback = mock(AsyncExecCallback.class);
    AsyncEntityProducer producer = null;
    if (original instanceof SimpleHttpRequest simple && simple.getBody() != null) {
      producer = mock(AsyncEntityProducer.class);
      when(producer.getContentType()).thenReturn(simple.getContentType().toString());
    }
    execute(original, producer, (request, entity, scope, cb) -> delegated.set(true), callback);
    verify(callback, times(delegated.get() ? 0 : 1)).failed(isA(HttpException.class));
    return delegated.get();
  }

  private void execute(HttpRequest original, AsyncEntityProducer producer, AsyncExecChain chain, AsyncExecCallback callback) throws Exception {
    var scope = new AsyncExecChain.Scope("test", new HttpRoute(new HttpHost("https", "example.org", 443)), original,
      mock(CancellableDependency.class), HttpClientContext.create(), mock(AsyncExecRuntime.class), null, null);
    underTest.execute(BasicRequestBuilder.copy(original).build(), producer, scope, chain, callback);
  }

  private static SimpleHttpRequest request() {
    return request("POST", URL, "payload");
  }

  private static SimpleHttpRequest request(String method, String url, String body) {
    var request = SimpleHttpRequest.create(method, url);
    request.setBody(body, ContentType.APPLICATION_JSON);
    request.setHeader("Authorization", "Bearer secret");
    request.setHeader("x-api-key", "key");
    request.setHeader("Accept", "application/json");
    request.setHeader("Content-Type", ContentType.APPLICATION_JSON.toString());
    return request;
  }
}
