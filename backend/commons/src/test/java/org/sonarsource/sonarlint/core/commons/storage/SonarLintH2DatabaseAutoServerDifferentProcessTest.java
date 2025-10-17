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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

@Disabled("Flaky across environments due to H2 AUTO_SERVER cross-process networking; kept for documentation and manual verification.")
class SonarLintH2DatabaseAutoServerDifferentProcessTest {

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  @Test
  void auto_server_allows_second_connection_from_different_java_process() throws Exception {
    var init = new StorageInitParams(tempDir);

    // First DB instance opens the file DB and creates a table + a row, then shuts down to simulate another process opening it
    var db1 = new SonarLintH2Database(init);
    try (var c1 = db1.getConnection(); var st1 = c1.createStatement()) {
      st1.execute("CREATE TABLE IF NOT EXISTS T(ID INT PRIMARY KEY, VAL VARCHAR(100))");
      st1.executeUpdate("MERGE INTO T KEY(ID) VALUES (1, 'from-db1')");
    }
    db1.shutdown();

    // Launch a separate JVM that connects to the same DB and writes a new row
    var result = runExternalProcess(tempDir);
    assertThat(result.exitCode).withFailMessage(() -> "External process failed with code " + result.exitCode + "\nOutput:\n" + result.output).isZero();

    // Verify from a fresh DB instance that the change written by the external process is persisted
    var db2 = new SonarLintH2Database(init);
    try (var c2 = db2.getConnection(); var ps = c2.prepareStatement("SELECT COUNT(*) FROM T"); ResultSet rs = ps.executeQuery()) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1)).isEqualTo(2);
    }
    db2.shutdown();
  }

  private static ProcessResult runExternalProcess(Path storageRoot) throws Exception {
    var javaBin = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
    var classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    var mainClass = "org.sonarsource.sonarlint.core.commons.storage.H2ExternalProcessMain";

    List<String> cmd = new ArrayList<>();
    cmd.add(javaBin);
    cmd.add("-cp");
    cmd.add(classpath);
    cmd.add(mainClass);
    cmd.add(storageRoot.toString());

    var pb = new ProcessBuilder(cmd);
    pb.redirectErrorStream(true);
    var process = pb.start();

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
