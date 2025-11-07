/*
 * SonarLint Core - RPC Java Client
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SloopLauncherTests {
  private Process mockProcess;
  private SloopLauncher underTest;
  private Sloop sloop;
  private Function<List<String>, ProcessBuilder> mockPbFactory;
  private SonarLintRpcClientDelegate rpcClient;
  private String osName = "Linux";
  private Path fakeJreHomePath;
  private Path fakeJreJavaLinuxPath;
  private Path fakeJreJavaWindowsPath;

  @BeforeEach
  void prepare(@TempDir Path fakeJreHomePath) throws IOException {
    this.fakeJreHomePath = fakeJreHomePath;
    var fakeJreBinFolder = this.fakeJreHomePath.resolve("bin");
    Files.createDirectories(fakeJreBinFolder);
    fakeJreJavaLinuxPath = fakeJreBinFolder.resolve("java");
    Files.createFile(fakeJreJavaLinuxPath);
    fakeJreJavaWindowsPath = fakeJreBinFolder.resolve("java.exe");
    Files.createFile(fakeJreJavaWindowsPath);
    mockPbFactory = mock();
    var mockProcessBuilder = mock(ProcessBuilder.class);
    when(mockPbFactory.apply(any())).thenReturn(mockProcessBuilder);
    mockProcess = mock(Process.class);
    when(mockProcess.onExit()).thenReturn(new CompletableFuture<>());
    doReturn(mockProcess).when(mockProcessBuilder).start();

    when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
    when(mockProcess.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
    when(mockProcess.getOutputStream()).thenReturn(new ByteArrayOutputStream());

    rpcClient = mock(SonarLintRpcClientDelegate.class);
    underTest = new SloopLauncher(rpcClient, mockPbFactory, () -> osName);
  }

  @Test
  void test_command_with_embedded_jre(@TempDir Path distPath) throws IOException {
    var bundledJreBinPath = distPath.resolve("jre").resolve("bin");
    Files.createDirectories(bundledJreBinPath);
    var bundledJrejavaPath = bundledJreBinPath.resolve("java");
    Files.createFile(bundledJrejavaPath);

    sloop = underTest.start(distPath);

    verify(mockPbFactory).apply(List.of(bundledJrejavaPath.toString(), "-Djava.awt.headless=true",
      "-classpath", distPath.resolve("lib") + File.separator + '*', "org.sonarsource.sonarlint.core.backend.cli.SonarLintServerCli"));
    assertThat(sloop.getRpcServer()).isNotNull();
  }

  @Test
  void test_command_with_custom_jre_on_linux(@TempDir Path distPath) {
    sloop = underTest.start(distPath, fakeJreHomePath);

    verify(mockPbFactory)
      .apply(List.of(fakeJreJavaLinuxPath.toString(), "-Djava.awt.headless=true",
        "-classpath", distPath.resolve("lib") + File.separator + '*', "org.sonarsource.sonarlint.core.backend.cli.SonarLintServerCli"));
    assertThat(sloop.getRpcServer()).isNotNull();
  }

  @Test
  void test_command_with_custom_jre_on_windows(@TempDir Path distPath) {
    osName = "Windows";
    sloop = underTest.start(distPath, fakeJreHomePath);

    verify(mockPbFactory)
      .apply(List.of(fakeJreJavaWindowsPath.toString(), "-Djava.awt.headless=true",
        "-classpath", distPath.resolve("lib") + File.separator + '*', "org.sonarsource.sonarlint.core.backend.cli.SonarLintServerCli"));
    assertThat(sloop.getRpcServer()).isNotNull();
  }

  @Test
  void test_redirect_stderr_to_client(@TempDir Path distPath) {
    when(mockProcess.getErrorStream()).thenReturn(new ByteArrayInputStream("Some errors\nSome other error".getBytes()));

    sloop = underTest.start(distPath, fakeJreHomePath);

    ArgumentCaptor<LogParams> captor = ArgumentCaptor.captor();
    verify(rpcClient, timeout(1000).times(3)).log(captor.capture());

    assertThat(captor.getAllValues())
      .filteredOn(m -> m.getLevel() == LogLevel.ERROR)
      .extracting(LogParams::getMessage)
      .containsExactly("StdErr: Some errors", "StdErr: Some other error");
  }

  @Test
  void test_log_stacktrace(@TempDir Path distPath) {
    doThrow(new IllegalStateException("Some error")).when(mockProcess).getInputStream();

    assertThrows(IllegalStateException.class, () -> sloop = underTest.start(distPath, fakeJreHomePath));

    ArgumentCaptor<LogParams> captor = ArgumentCaptor.captor();
    verify(rpcClient, times(2)).log(captor.capture());

    var log = captor.getValue();

    assertThat(log.getMessage()).isEqualTo("Unable to start the SonarLint backend");
    assertThat(log.getStackTrace()).startsWith("java.lang.IllegalStateException: Some error");
  }

  @Test
  void test_throw_error_if_java_path_does_not_exist(@TempDir Path distPath) {
    var wrongPath = Paths.get("wrongPath");
    assertThrows(IllegalStateException.class, () -> sloop = underTest.start(distPath, wrongPath));
  }

  @Test
  void test_command_with_custom_jre_on_linux_and_jvm_option(@TempDir Path distPath) {
    sloop = underTest.start(distPath, fakeJreHomePath, "-XX:+UseG1GC -XX:MaxHeapFreeRatio=50");

    verify(mockPbFactory)
      .apply(List.of(fakeJreJavaLinuxPath.toString(), "-XX:+UseG1GC", "-XX:MaxHeapFreeRatio=50", "-Djava.awt.headless=true",
        "-classpath", distPath.resolve("lib") + File.separator + '*', "org.sonarsource.sonarlint.core.backend.cli.SonarLintServerCli"));
    assertThat(sloop.getRpcServer()).isNotNull();
  }
}
