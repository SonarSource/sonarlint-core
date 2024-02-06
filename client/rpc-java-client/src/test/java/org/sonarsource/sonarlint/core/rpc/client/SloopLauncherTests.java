/*
 * SonarLint Core - RPC Java Client
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
package org.sonarsource.sonarlint.core.rpc.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SloopLauncherTests {

  private Process mockProcess;
  private SloopLauncher underTest;
  private Function<List<String>, ProcessBuilder> mockPbFactory;
  private SonarLintRpcClientDelegate rpcClient;

  private String osName = "Linux";

  @BeforeEach
  void prepare() throws IOException {
    mockPbFactory = mock();
    var mockProcessBuilder = mock(ProcessBuilder.class);
    when(mockPbFactory.apply(any())).thenReturn(mockProcessBuilder);
    mockProcess = mock(Process.class);
    doReturn(mockProcess).when(mockProcessBuilder).start();

    when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
    when(mockProcess.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
    when(mockProcess.getOutputStream()).thenReturn(new ByteArrayOutputStream());

    rpcClient = mock(SonarLintRpcClientDelegate.class);
    underTest = new SloopLauncher(rpcClient, mockPbFactory, () -> osName);
  }

  @Test
  void test_command_on_linux(@TempDir Path distPath) {
    underTest.start(distPath);

    verify(mockPbFactory).apply(List.of("sh", "sonarlint-backend"));
    assertThat(underTest.getServerProxy()).isNotNull();
  }

  @Test
  void test_command_on_windows(@TempDir Path distPath) {
    osName = "Windows";

    underTest.start(distPath);

    verify(mockPbFactory).apply(List.of("cmd.exe", "/c", "sonarlint-backend.bat"));
    assertThat(underTest.getServerProxy()).isNotNull();
  }

  @Test
  void test_command_on_linux_and_provide_jre(@TempDir Path distPath, @TempDir Path jreDir) {
    underTest.start(distPath, jreDir);

    verify(mockPbFactory).apply(List.of("sh", "sonarlint-backend", "-j", jreDir.toString()));
    assertThat(underTest.getServerProxy()).isNotNull();
  }

  @Test
  void test_command_on_windows_and_provide_jre(@TempDir Path distPath, @TempDir Path jreDir) {
    osName = "Windows";

    underTest.start(distPath, jreDir);

    verify(mockPbFactory).apply(List.of("cmd.exe", "/c", "sonarlint-backend.bat", "-j", jreDir.toString()));
    assertThat(underTest.getServerProxy()).isNotNull();
  }

  @Test
  void test_redirect_stderr_to_client(@TempDir Path distPath) {
    when(mockProcess.getErrorStream()).thenReturn(new ByteArrayInputStream("Some errors\nSome other error".getBytes()));

    underTest.start(distPath);

    ArgumentCaptor<LogParams> captor = ArgumentCaptor.captor();
    verify(rpcClient, timeout(1000).times(2)).log(captor.capture());

    assertThat(captor.getAllValues())
      .extracting(LogParams::getMessage)
      .containsExactly("StdErr: Some errors", "StdErr: Some other error");
  }

  @Test
  void test_wait_for_successful(@TempDir Path distPath) throws InterruptedException {
    when(mockProcess.exitValue()).thenReturn(0);
    doReturn(true).when(mockProcess).waitFor(anyLong(), any());
    underTest.start(distPath);

    assertThat(underTest.waitFor()).isZero();

    verifyNoInteractions(rpcClient);
  }

  @Test
  void test_wait_for_timed_out(@TempDir Path distPath) throws InterruptedException {
    doReturn(false).when(mockProcess).waitFor(anyLong(), any());
    underTest.start(distPath);

    assertThat(underTest.waitFor()).isEqualTo(-1);

    ArgumentCaptor<LogParams> captor = ArgumentCaptor.captor();
    verify(rpcClient, times(1)).log(captor.capture());

    assertThat(captor.getAllValues())
      .extracting(LogParams::getMessage)
      .containsExactly("Unable to stop the SonarLint process in a timely manner. Killing it.");
    verify(mockProcess).destroyForcibly();
  }

  @Test
  void test_log_stacktrace(@TempDir Path distPath) {
    doThrow(new IllegalStateException("Some error")).when(mockProcess).getInputStream();

    assertThrows(IllegalStateException.class, () -> underTest.start(distPath));

    ArgumentCaptor<LogParams> captor = ArgumentCaptor.captor();
    verify(rpcClient).log(captor.capture());

    var log = captor.getValue();

    assertThat(log.getMessage()).isEqualTo("Unable to start the SonarLint backend");
    assertThat(log.getStackTrace()).startsWith("java.lang.IllegalStateException: Some error");
  }
}