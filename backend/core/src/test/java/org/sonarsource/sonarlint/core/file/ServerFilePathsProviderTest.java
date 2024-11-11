/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.file;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.ConnectionManager;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiErrorHandlingWrapper;
import org.sonarsource.sonarlint.core.serverapi.component.ComponentApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerFilePathsProviderTest {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String CONNECTION_A = "connection_A";
  private static final String CONNECTION_B = "connection_B";
  public static final String PROJECT_KEY = "projectKey";

  private Path cacheDirectory;
  private final ConnectionManager connectionManager = mock(ConnectionManager.class);
  private final ServerApi serverApi_A = mock(ServerApi.class);
  private final ServerApi serverApi_B = mock(ServerApi.class);
  private final SonarLintCancelMonitor cancelMonitor = mock(SonarLintCancelMonitor.class);
  private final ComponentApi componentApi_A = mock(ComponentApi.class);
  private final ComponentApi componentApi_B = mock(ComponentApi.class);
  private ServerFilePathsProvider underTest;

  @BeforeEach
  void before(@TempDir Path storageDir) throws IOException {
    cacheDirectory = storageDir.resolve("cache");
    Files.createDirectories(cacheDirectory);
    when(connectionManager.getServerApiWrapperOrThrow(CONNECTION_A)).thenReturn(new ServerApiErrorHandlingWrapper(serverApi_A, () -> {}));
    when(connectionManager.getServerApiWrapperOrThrow(CONNECTION_B)).thenReturn(new ServerApiErrorHandlingWrapper(serverApi_B, () -> {}));
    when(connectionManager.hasConnection(CONNECTION_A)).thenReturn(true);
    when(connectionManager.hasConnection(CONNECTION_B)).thenReturn(true);

    when(serverApi_A.component()).thenReturn(componentApi_A);
    when(serverApi_B.component()).thenReturn(componentApi_B);
    mockServerFilePaths(componentApi_A, "pathA", "pathB");
    mockServerFilePaths(componentApi_B, "pathC", "pathD");

    underTest = new ServerFilePathsProvider(connectionManager, storageDir);

    cacheDirectory = storageDir.resolve("cache");
  }

  @Test
  void clear_cache_directory_after_initialization(@TempDir Path storageDir) throws IOException {
    cacheDirectory = storageDir.resolve("cache");
    Files.createDirectories(cacheDirectory);
    assertThat(cacheDirectory.toFile()).exists();

    new ServerFilePathsProvider(null, storageDir);

    assertThat(cacheDirectory.toFile()).doesNotExist();
  }

  @Test
  void log_when_connection_not_exist() {
    when(connectionManager.hasConnection("conId")).thenReturn(false);

    underTest.getServerPaths(new Binding("conId", null), cancelMonitor);

    assertThat(logTester.getSlf4jLogs()).extracting(ILoggingEvent::getFormattedMessage)
      .containsExactly("Connection 'conId' does not exist");
  }

  @Test
  void write_to_cache_file_after_fetch() throws IOException {
    underTest.getServerPaths(new Binding(CONNECTION_A, PROJECT_KEY), cancelMonitor);

    assertThat(cacheDirectory.toFile().listFiles()).hasSize(1);
    File file = Objects.requireNonNull(cacheDirectory.toFile().listFiles())[0];
    List<String> paths = FileUtils.readLines(file, Charset.defaultCharset());
    assertThat(paths).hasSize(2);
    assertThat(paths.get(0)).isEqualTo("pathA");
    assertThat(paths.get(1)).isEqualTo("pathB");
  }

  @Test
  void fetch_from_in_memory_for_the_second_attempt() throws IOException {
    underTest.getServerPaths(new Binding(CONNECTION_A, PROJECT_KEY), cancelMonitor);
    FileUtils.deleteDirectory(cacheDirectory.toFile());

    underTest.getServerPaths(new Binding(CONNECTION_A, PROJECT_KEY), cancelMonitor);

    assertThat(cacheDirectory.toFile()).doesNotExist();
  }

  @Test
  void fetch_from_file_when_cache_timeout() throws IOException {
    underTest.getServerPaths(new Binding(CONNECTION_A, PROJECT_KEY), cancelMonitor);

    File file = Objects.requireNonNull(cacheDirectory.toFile().listFiles())[0];
    BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file, true));
    bufferedWriter.write("NewPath");
    bufferedWriter.newLine();
    bufferedWriter.close();

    underTest.clearInMemoryCache();

    List<Path> paths = underTest.getServerPaths(new Binding(CONNECTION_A, PROJECT_KEY), cancelMonitor).get();
    assertThat(paths).hasSize(3);
    assertThat(paths.get(0)).hasToString("pathA");
    assertThat(paths.get(1)).hasToString("pathB");
    assertThat(paths.get(2)).hasToString("NewPath");
  }

  @Test
  void write_to_two_cache_files_for_different_request() {
    underTest.getServerPaths(new Binding(CONNECTION_A, PROJECT_KEY), cancelMonitor);
    underTest.getServerPaths(new Binding(CONNECTION_B, PROJECT_KEY), cancelMonitor);

    assertThat(cacheDirectory.toFile().listFiles()).hasSize(2);
  }

  @Test
  void shouldLogAndIgnoreOtherErrors() {
    when(serverApi_A.component()).thenAnswer(invocation -> {
      throw new IllegalStateException();
    });

    underTest.getServerPaths(new Binding(CONNECTION_A, PROJECT_KEY), cancelMonitor);

    assertThat(logTester.getSlf4jLogs()).extracting(ILoggingEvent::getFormattedMessage)
      .contains("Error while getting server file paths for project 'projectKey'");
  }

  private void mockServerFilePaths(ComponentApi componentApi, String... paths) {
    doReturn(Arrays.stream(paths).map(path -> PROJECT_KEY + ":" + path).collect(Collectors.toList()))
      .when(componentApi)
      .getAllFileKeys(PROJECT_KEY, cancelMonitor);
  }
}
