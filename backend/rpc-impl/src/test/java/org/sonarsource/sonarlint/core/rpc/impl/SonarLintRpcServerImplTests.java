/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.spring.SpringApplicationContextInitializer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SonarLintRpcServerImplTests {

  @Test
  void it_should_fail_to_use_services_if_the_backend_is_not_initialized() {
    var in = new ByteArrayInputStream(new byte[0]);
    var out = new ByteArrayOutputStream();
    var backend = new SonarLintRpcServerImpl(in, out);

    assertThat(backend.getTelemetryService().getStatus())
      .failsWithin(1, TimeUnit.MINUTES)
      .withThrowableOfType(ExecutionException.class)
      .withCauseInstanceOf(IllegalStateException.class)
      .withStackTraceContaining("Backend is not initialized");
  }

  @Test
  void it_should_silently_shutdown_the_backend_if_it_was_not_initialized() {
    var in = new ByteArrayInputStream(new byte[0]);
    var out = new ByteArrayOutputStream();
    var backend = new SonarLintRpcServerImpl(in, out);

    var future = backend.shutdown();

    assertThat(future)
      .succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  void it_should_let_in_flight_requests_finish_before_clearing_initialized() throws Exception {
    var backend = new SonarLintRpcServerImpl(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
    var initialized = initializedFlag(backend);
    initialized.set(true);
    var contextInitializer = mock(SpringApplicationContextInitializer.class);
    contextInitializerField().set(backend, contextInitializer);

    var requestStarted = new CountDownLatch(1);
    var releaseRequest = new CountDownLatch(1);
    var sawInitializedContext = new AtomicBoolean(false);
    backend.getRequestsExecutor().execute(() -> {
      requestStarted.countDown();
      try {
        assertThat(releaseRequest.await(10, TimeUnit.SECONDS)).isTrue();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      try {
        backend.getInitializedApplicationContext();
        sawInitializedContext.set(true);
      } catch (IllegalStateException e) {
        sawInitializedContext.set(false);
      }
    });

    assertThat(requestStarted.await(10, TimeUnit.SECONDS)).isTrue();
    var shutdown = backend.shutdown();
    await().atMost(5, SECONDS).until(() -> backend.getRequestsExecutor().isShutdown());
    // Client responses are still delivered while the request pool drains.
    assertThat(backend.isInboundDispatcherShutdown()).isFalse();
    assertThat(backend.isReaderShutdown()).isFalse();
    releaseRequest.countDown();

    assertThat(shutdown).succeedsWithin(Duration.ofSeconds(5));
    assertThat(sawInitializedContext).isTrue();
    assertThat(initialized).isFalse();
    verify(contextInitializer).close();
  }

  @Test
  void shutdown_stops_inbound_dispatch_before_the_outbound_writer() {
    var backend = new SonarLintRpcServerImpl(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());

    var shutdown = backend.shutdown();

    await().atMost(5, SECONDS).until(backend::isInboundDispatcherShutdown);
    assertThat(backend.isReaderShutdown()).isTrue();
    assertThat(backend.isOutboundWriterShutdown()).isFalse();
    assertThat(shutdown).succeedsWithin(Duration.ofSeconds(5));
    await().atMost(5, SECONDS).until(backend::isOutboundWriterShutdown);
  }

  private static AtomicBoolean initializedFlag(SonarLintRpcServerImpl backend) throws Exception {
    var field = SonarLintRpcServerImpl.class.getDeclaredField("initialized");
    field.setAccessible(true);
    return (AtomicBoolean) field.get(backend);
  }

  private static Field contextInitializerField() throws Exception {
    var field = SonarLintRpcServerImpl.class.getDeclaredField("springApplicationContextInitializer");
    field.setAccessible(true);
    return field;
  }

}
