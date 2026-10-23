/*
 * SonarLint Core - Medium Tests
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
package mediumtest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SonarLintTestHarnessTest {

  private SonarLintTestHarness harness;
  private TestLogHandler logHandler;

  @BeforeEach
  void setUp() {
    harness = new SonarLintTestHarness();

    var testLogger = Logger.getLogger(SonarLintTestHarness.class.getName());
    logHandler = new TestLogHandler();
    testLogger.addHandler(logHandler);
    testLogger.setLevel(Level.ALL);
  }

  @Test
  void should_shutdown_normally() {
    var backend = backendShuttingDownWith(CompletableFuture.completedFuture(null));
    harness.addBackend(backend);
    TestServer server = new TestServer();
    harness.addServer(server);

    harness.afterEach(emptyContext());

    assertThat(harness.getBackends()).isEmpty();
    assertThat(harness.getServers()).isEmpty();
    assertThat(server.isShutdownCalled()).isTrue();
  }

  @Test
  void should_handle_exceptionally_callback() {
    CompletableFuture<Void> failingFuture = new CompletableFuture<>();
    failingFuture.completeExceptionally(new RuntimeException("Simulated exception"));
    var backend = backendShuttingDownWith(failingFuture);
    harness.addBackend(backend);
    TestServer server = new TestServer();
    harness.addServer(server);

    harness.afterEach(emptyContext());

    assertThat(harness.getBackends()).isEmpty();
    assertThat(harness.getServers()).isEmpty();
    assertThat(server.isShutdownCalled()).isTrue();
    assertThat(logHandler.getRecords()).anySatisfy(logRecord -> {
      assertThat(logRecord.getLevel()).isEqualTo(Level.WARNING);
      assertThat(logRecord.getMessage()).contains("Error shutting down backend");
      assertThat(logRecord.getThrown()).isNotNull();
    });
  }

  @Test
  void should_handle_catch_block_exceptions() {
    var backend1 = backendFailingToShutdownWith(new CompletionException("Simulated completion exception", new RuntimeException()));
    var backend2 = backendFailingToShutdownWith(new IllegalStateException("Simulated illegal state exception"));
    harness.addBackend(backend1);
    harness.addBackend(backend2);
    TestServer server = new TestServer();
    harness.addServer(server);

    harness.afterEach(emptyContext());

    assertThat(harness.getBackends()).isEmpty();
    assertThat(harness.getServers()).isEmpty();
    assertThat(server.isShutdownCalled()).isTrue();
    assertThat(logHandler.getRecords()).anySatisfy(logRecord -> {
      assertThat(logRecord.getLevel()).isEqualTo(Level.WARNING);
      assertThat(logRecord.getMessage()).contains("Failed to shutdown backend");
      assertThat(logRecord.getThrown()).isInstanceOf(CompletionException.class);
    });
    assertThat(logHandler.getRecords()).anySatisfy(logRecord -> {
      assertThat(logRecord.getLevel()).isEqualTo(Level.WARNING);
      assertThat(logRecord.getMessage()).contains("Failed to shutdown backend");
      assertThat(logRecord.getThrown()).isInstanceOf(IllegalStateException.class);
    });
  }

  @Test
  void should_handle_server_exceptions() {
    var testBackend = backendShuttingDownWith(CompletableFuture.completedFuture(null));
    harness.addBackend(testBackend);
    ServerFixture.Server throwingServer1 = new ThrowingTestServer(new RuntimeException("Server 1 shutdown error"));
    ServerFixture.Server throwingServer2 = new ThrowingTestServer(new RuntimeException("Server 2 shutdown error"));
    harness.addServer(throwingServer1);
    harness.addServer(throwingServer2);

    harness.afterEach(emptyContext());

    assertThat(harness.getBackends()).isEmpty();
    assertThat(harness.getServers()).isEmpty();
    assertThat(logHandler.getRecords()).anySatisfy(logRecord -> {
      assertThat(logRecord.getLevel()).isEqualTo(Level.WARNING);
      assertThat(logRecord.getMessage()).contains("Failed to shutdown server");
      assertThat(logRecord.getThrown()).isInstanceOf(RuntimeException.class);
      assertThat(logRecord.getThrown().getMessage()).contains("Server 1 shutdown error");
    });
    assertThat(logHandler.getRecords()).anySatisfy(logRecord -> {
      assertThat(logRecord.getLevel()).isEqualTo(Level.WARNING);
      assertThat(logRecord.getMessage()).contains("Failed to shutdown server");
      assertThat(logRecord.getThrown()).isInstanceOf(RuntimeException.class);
      assertThat(logRecord.getThrown().getMessage()).contains("Server 2 shutdown error");
    });
  }

  @Test
  void should_handle_multiple_backends_and_servers() {
    var backend1 = backendShuttingDownWith(CompletableFuture.completedFuture(null));
    CompletableFuture<Void> failingFuture = new CompletableFuture<>();
    failingFuture.completeExceptionally(new RuntimeException("Backend 2 error"));
    var backend2 = backendShuttingDownWith(failingFuture);
    var backend3 = backendFailingToShutdownWith(new IllegalStateException("Backend 3 error"));
    harness.addBackend(backend1);
    harness.addBackend(backend2);
    harness.addBackend(backend3);
    TestServer server1 = new TestServer();
    ServerFixture.Server server2 = new ThrowingTestServer(new RuntimeException("Server 2 error"));
    harness.addServer(server1);
    harness.addServer(server2);

    harness.afterEach(emptyContext());

    assertThat(harness.getBackends()).isEmpty();
    assertThat(harness.getServers()).isEmpty();
    assertThat(server1.isShutdownCalled()).isTrue();
    assertThat(logHandler.getRecords()).anySatisfy(logRecord -> assertThat(logRecord.getMessage()).contains("Error shutting down backend"));
    assertThat(logHandler.getRecords()).anySatisfy(logRecord -> assertThat(logRecord.getMessage()).contains("Failed to shutdown backend"));
    assertThat(logHandler.getRecords()).anySatisfy(logRecord -> assertThat(logRecord.getMessage()).contains("Failed to shutdown server"));
  }

  private static SonarLintTestRpcServer backendShuttingDownWith(CompletableFuture<Void> shutdownFuture) {
    var backend = mock(SonarLintTestRpcServer.class);
    when(backend.shutdown()).thenReturn(shutdownFuture);
    return backend;
  }

  private static SonarLintTestRpcServer backendFailingToShutdownWith(RuntimeException exceptionToThrow) {
    var backend = mock(SonarLintTestRpcServer.class);
    when(backend.shutdown()).thenThrow(exceptionToThrow);
    return backend;
  }

  private static ExtensionContext emptyContext() {
    var context = mock(ExtensionContext.class);
    when(context.getTestMethod()).thenReturn(Optional.empty());
    return context;
  }

  static class TestLogHandler extends Handler {
    private final List<LogRecord> logRecords = new java.util.ArrayList<>();

    @Override
    public void publish(LogRecord logRecord) {
      logRecords.add(logRecord);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
    }

    public List<LogRecord> getRecords() {
      return logRecords;
    }
  }

  static class TestServer extends ServerFixture.Server {
    private boolean shutdownCalled = false;

    public TestServer() {
      super(null, null, null, null, null, null, null, null, null, false, null, null, null, null);
    }

    @Override
    public void shutdown() {
      shutdownCalled = true;
    }

    public boolean isShutdownCalled() {
      return shutdownCalled;
    }
  }

  static class ThrowingTestServer extends ServerFixture.Server {
    private final RuntimeException exceptionToThrow;

    ThrowingTestServer(RuntimeException exceptionToThrow) {
      super(null, null, null, null, null, null, null, null, null, false, null, null, null, null);
      this.exceptionToThrow = exceptionToThrow;
    }

    @Override
    public void shutdown() {
      throw exceptionToThrow;
    }
  }

}
