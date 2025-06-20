/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource SA
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.impl.SonarLintRpcServerImpl;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SonarLintTestHarnessShutdownTest {

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
    SonarLintTestRpcServer backend = new TestBackend(getMockedBackend(), getMockedClient(), CompletableFuture.completedFuture(null));
    harness.addBackend(backend);
    TestServer server = new TestServer();
    harness.addServer(server);

    harness.afterEach(null);

    assertThat(harness.getBackends()).isEmpty();
    assertThat(harness.getServers()).isEmpty();
    assertThat(server.isShutdownCalled()).isTrue();
  }

  @Test
  void should_handle_exceptionally_callback() {
    CompletableFuture<Void> failingFuture = new CompletableFuture<>();
    failingFuture.completeExceptionally(new RuntimeException("Simulated exception"));
    SonarLintTestRpcServer backend = new TestBackend(getMockedBackend(), getMockedClient(),failingFuture);
    harness.addBackend(backend);
    TestServer server = new TestServer();
    harness.addServer(server);

    harness.afterEach(null);

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
    SonarLintTestRpcServer backend1 = new ThrowingBackend(getMockedBackend(), getMockedClient(), new CompletionException("Simulated completion exception", new RuntimeException()));
    SonarLintTestRpcServer backend2 = new ThrowingBackend(getMockedBackend(), getMockedClient(), new IllegalStateException("Simulated illegal state exception"));
    harness.addBackend(backend1);
    harness.addBackend(backend2);
    TestServer server = new TestServer();
    harness.addServer(server);

    harness.afterEach(null);

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
    SonarLintTestRpcServer testBackend = new TestBackend(getMockedBackend(), getMockedClient(), CompletableFuture.completedFuture(null));
    harness.addBackend(testBackend);
    ServerFixture.Server throwingServer1 = new ThrowingTestServer(new RuntimeException("Server 1 shutdown error"));
    ServerFixture.Server throwingServer2 = new ThrowingTestServer(new RuntimeException("Server 2 shutdown error"));
    harness.addServer(throwingServer1);
    harness.addServer(throwingServer2);

    harness.afterEach(null);

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
    SonarLintTestRpcServer backend1 = new TestBackend(getMockedBackend(), getMockedClient(), CompletableFuture.completedFuture(null));
    CompletableFuture<Void> failingFuture = new CompletableFuture<>();
    failingFuture.completeExceptionally(new RuntimeException("Backend 2 error"));
    SonarLintTestRpcServer backend2 = new TestBackend(getMockedBackend(), getMockedClient(), failingFuture);
    SonarLintTestRpcServer backend3 = new ThrowingBackend(getMockedBackend(), getMockedClient(), new IllegalStateException("Backend 3 error"));
    harness.addBackend(backend1);
    harness.addBackend(backend2);
    harness.addBackend(backend3);
    TestServer server1 = new TestServer();
    ServerFixture.Server server2 = new ThrowingTestServer(new RuntimeException("Server 2 error"));
    harness.addServer(server1);
    harness.addServer(server2);

    harness.afterEach(null);

    assertThat(harness.getBackends()).isEmpty();
    assertThat(harness.getServers()).isEmpty();
    assertThat(server1.isShutdownCalled()).isTrue();
    assertThat(logHandler.getRecords()).anySatisfy(logRecord ->
      assertThat(logRecord.getMessage()).contains("Error shutting down backend"));
    assertThat(logHandler.getRecords()).anySatisfy(logRecord ->
      assertThat(logRecord.getMessage()).contains("Failed to shutdown backend"));
    assertThat(logHandler.getRecords()).anySatisfy(logRecord ->
      assertThat(logRecord.getMessage()).contains("Failed to shutdown server"));
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

  static class TestBackend extends SonarLintTestRpcServer {
    private final CompletableFuture<Void> shutdownFuture;

    TestBackend(BackendJsonRpcLauncher serverLauncher, ClientJsonRpcLauncher clientLauncher, CompletableFuture<Void> shutdownFuture) {
      super(serverLauncher, clientLauncher);
      this.shutdownFuture = shutdownFuture;
    }

    @Override
    public CompletableFuture<Void> shutdown() {
      return shutdownFuture;
    }
  }

  static class ThrowingBackend extends SonarLintTestRpcServer {
    private final RuntimeException exceptionToThrow;

    ThrowingBackend(BackendJsonRpcLauncher serverLauncher, ClientJsonRpcLauncher clientLauncher, RuntimeException exceptionToThrow) {
      super(serverLauncher, clientLauncher);
      this.exceptionToThrow = exceptionToThrow;
    }

    @Override
    public CompletableFuture<Void> shutdown() {
      throw exceptionToThrow;
    }
  }

  static class TestServer extends ServerFixture.Server {
    private boolean shutdownCalled = false;

    public TestServer() {
      super(null,null,null,null,null,null,
        null,null,null,null,false, null,null);
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
      super(null,null,null,null,null,null,
        null,null,null,null,false, null,null);
      this.exceptionToThrow = exceptionToThrow;
    }

    @Override
    public void shutdown() {
      throw exceptionToThrow;
    }
  }

  private BackendJsonRpcLauncher getMockedBackend(){
    var backend = mock(BackendJsonRpcLauncher.class);
    when(backend.getServer()).thenReturn(mock(SonarLintRpcServerImpl.class));
    return backend;
  }

  private ClientJsonRpcLauncher getMockedClient(){
    var client = mock(ClientJsonRpcLauncher.class);
    when(client.getServerProxy()).thenReturn(mock(SonarLintRpcServer.class));
    return client;
  }

}
