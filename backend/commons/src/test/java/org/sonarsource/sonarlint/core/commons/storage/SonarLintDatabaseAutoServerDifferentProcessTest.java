/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.commons.testutils.H2Utils.ensureTestTableExists;
import static org.sonarsource.sonarlint.core.commons.testutils.H2Utils.insertRecords;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

class SonarLintDatabaseAutoServerDifferentProcessTest {

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @DisplayName("Cross-process concurrent access to H2: green with AUTO_SERVER=TRUE, fail with AUTO_SERVER=FALSE")
  void auto_server_allows_second_connection_from_different_java_process(boolean autoServer) throws Exception {
    var init = new SonarLintDatabaseInitParams(tempDir, SonarLintDatabaseMode.FILE, autoServer);

    // First DB instance opens the file DB and creates a table + a row
    var db1 = new SonarLintDatabase(init);
    ensureTestTableExists(db1);

    AtomicReference<Process> processRef = new AtomicReference<>();
    // Start external process while db1 is still open
    var externalProcessStarter = new Thread(() -> {
      try {
        System.out.println("Main process PID: " + ProcessHandle.current().pid());
        System.out.println("External process starting. ");
        var process = startExternalProcess(tempDir, autoServer);
        // Wait for external process to complete
        processRef.set(process);
        System.out.println("External process launched");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    externalProcessStarter.start();

    Thread.sleep(100);
    // Insert records in parallel with the external process
    var insertThread = new Thread(() -> {
      try {
        System.out.println("Main process starting inserts, PID: " + ProcessHandle.current().pid());
        insertRecords(db1, 10000);
        System.out.println("Main process finished inserts");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    insertThread.start();

    while (processRef.get() == null) {
      Thread.sleep(100);
    }
    var processResult = waitForProcess(processRef.get());

    if( autoServer ) {
      assertThat(processResult.exitCode).isZero();
    } else {
      assertThat(processResult.exitCode).isNotZero();
    }
  }

  private static Process startExternalProcess(Path storageRoot, boolean autoServer) throws Exception {
    var javaBin = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
    var classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    var mainClass = "org.sonarsource.sonarlint.core.commons.testutils.H2ExternalProcessMain";

    List<String> cmd = new ArrayList<>();
    cmd.add(javaBin);
    cmd.add("-cp");
    cmd.add(classpath);
    cmd.add(mainClass);
    cmd.add(storageRoot.toString());
    cmd.add(String.valueOf(autoServer));
    cmd.add(storageRoot.toString());

    var pb = new ProcessBuilder(cmd);
    pb.redirectErrorStream(true);
    return pb.start();
  }

  private static ProcessResult waitForProcess(Process process) throws Exception {
    var sb = new StringBuilder();
    try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append('\n');
        // forward output to help debugging on CI in case of failures
        System.out.println("[external] " + line);
      }
    }

    int code = process.waitFor();
    return new ProcessResult(code, sb.toString());
  }

  private static boolean isWindows() {
    String os = System.getProperty("os.name");
    return os != null && os.toLowerCase().contains("win");
  }

  private static final class ProcessResult {
    final int exitCode;
    final String output;
    private ProcessResult(int exitCode, String output) {
      this.exitCode = exitCode;
      this.output = output;
    }
  }
}
