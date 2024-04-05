/*
 * SonarLint Core - Backend CLI
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.backend.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.impl.SonarLintRpcServerImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstructionWithAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class SonarLintServerCliTest {
  @Test
  void it_should_return_success_exit_code_when_parent_stream_ends() {
    var exitCode = new SonarLintServerCli().run(new ByteArrayInputStream(new byte[0]), new PrintStream(new ByteArrayOutputStream()));

    assertThat(exitCode).isZero();
  }

  @Test
  void log_when_client_is_closed() throws IOException {
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    System.setErr(new PrintStream(outContent));

    InputStream inputStream = spy(new ByteArrayInputStream(new byte[0]));
    when(inputStream.available()).thenReturn(1);
    var exitCode = new SonarLintServerCli().run(inputStream, new PrintStream(new ByteArrayOutputStream()));

    assertThat(outContent.toString()).isEqualToIgnoringNewLines("Input stream has closed, exiting...");

    assertThat(exitCode).isZero();
    outContent.close();
  }

  @Test
  void log_when_connection_canceled() {
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    System.setErr(new PrintStream(outContent));

    SonarLintRpcServerImpl mockServer = mock(SonarLintRpcServerImpl.class);
    doThrow(CancellationException.class).when(mockServer).getClientListener();
    try (MockedConstruction<BackendJsonRpcLauncher> ignored = mockConstructionWithAnswer(BackendJsonRpcLauncher.class, invocationOnMock -> mockServer)) {
      var exitCode = new SonarLintServerCli().run(new ByteArrayInputStream(new byte[0]), new PrintStream(new ByteArrayOutputStream()));

      assertThat(outContent.toString()).isEqualToIgnoringNewLines("Server is shutting down...");
      assertThat(exitCode).isZero();
    }
  }

  @Test
  void log_interrupted_exception() throws ExecutionException, InterruptedException {
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    System.setErr(new PrintStream(outContent));

    SonarLintRpcServerImpl mockServer = mock(SonarLintRpcServerImpl.class);
    Future<Void> mockFuture = mock(Future.class);
    when(mockServer.getClientListener()).thenReturn(mockFuture);
    doThrow(new InterruptedException("interrupted exc")).when(mockFuture).get();
    try (MockedConstruction<BackendJsonRpcLauncher> ignored = mockConstructionWithAnswer(BackendJsonRpcLauncher.class, invocationOnMock -> mockServer)) {
      var exitCode = new SonarLintServerCli().run(new ByteArrayInputStream(new byte[0]), new PrintStream(new ByteArrayOutputStream()));

      assertThat(outContent.toString()).contains("java.lang.InterruptedException: interrupted exc");
      assertThat(exitCode).isEqualTo(-1);
    }
  }

  @Test
  void log_other_exceptions() {
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    System.setErr(new PrintStream(outContent));

    SonarLintRpcServerImpl mockServer = mock(SonarLintRpcServerImpl.class);
    doThrow(new RuntimeException("an exc")).when(mockServer).getClientListener();
    try (MockedConstruction<BackendJsonRpcLauncher> ignored = mockConstructionWithAnswer(BackendJsonRpcLauncher.class, invocationOnMock -> mockServer)) {
      var exitCode = new SonarLintServerCli().run(new ByteArrayInputStream(new byte[0]), new PrintStream(new ByteArrayOutputStream()));

      assertThat(outContent.toString()).contains("java.lang.RuntimeException: an exc");
      assertThat(exitCode).isEqualTo(-1);
    }
  }

}