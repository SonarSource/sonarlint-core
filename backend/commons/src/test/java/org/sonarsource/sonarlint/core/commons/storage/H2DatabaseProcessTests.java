/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class H2DatabaseProcessTests {
  @TempDir
  Path root;

  @Test
  void shares_a_migrated_database_with_a_running_owner() throws Exception {
    try (var owner = launch("owner", "normal"); var contender = launch("contender", "normal")) {
      owner.awaitGate("ready");
      contender.awaitGate("ready");
      owner.signal("start");
      owner.awaitGate("initialized");
      contender.signal("start");
      verifySharing(owner, contender);
    }
  }

  @Test
  void initializes_simultaneously_in_separate_jvms() throws Exception {
    try (var first = launch("first", "simultaneous"); var second = launch("second", "simultaneous")) {
      first.awaitGate("ready");
      second.awaitGate("ready");
      Files.writeString(root.resolve("simultaneous-start"), "ready");
      verifySharing(first, second);
    }
  }

  @Test
  void recovers_while_original_owner_finishes_starting_auto_server() throws Exception {
    try (var owner = launch("owner", "delayed-owner"); var contender = launch("contender", "normal")) {
      owner.awaitGate("ready");
      contender.awaitGate("ready");
      owner.signal("start");
      owner.awaitGate("locked");
      assertThat(root.resolve("storage/h2/sq-ide.lock.db")).exists();
      contender.signal("start");
      contender.awaitGate("retry");
      assertThat(owner.process.isAlive()).isTrue();
      owner.signal("release");
      verifySharing(owner, contender);
      assertThat(contender.output()).contains("H2 startup contention recovered");
    }
  }

  private static void verifySharing(Child first, Child second) throws Exception {
    first.awaitGate("initialized");
    second.awaitGate("initialized");
    assertThat(first.process.isAlive()).isTrue();
    assertThat(second.process.isAlive()).isTrue();
    first.signal("query");
    second.signal("query");
    first.awaitGate("queried");
    second.awaitGate("queried");
    assertThat(first.process.isAlive()).isTrue();
    assertThat(second.process.isAlive()).isTrue();
  }

  private Child launch(String name, String mode) throws IOException {
    var gates = Files.createDirectories(root.resolve(name));
    var output = root.resolve(name + ".log");
    var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    var classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    var process = new ProcessBuilder(java, "-Djava.security.manager=allow", "-cp", classpath,
      H2DatabaseProcessMain.class.getName(), root.resolve("storage").toString(), gates.toString(), mode)
      .redirectErrorStream(true).redirectOutput(output.toFile()).start();
    return new Child(process, gates, output);
  }

  private record Child(Process process, Path gates, Path outputFile) implements AutoCloseable {
    void signal(String name) throws IOException {
      Files.writeString(gates.resolve(name), "ready");
    }

    void awaitGate(String name) {
      await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(20)).untilAsserted(() -> {
        assertThat(process.isAlive()).withFailMessage("Child exited waiting for %s:%n%s", name, output()).isTrue();
        assertThat(gates.resolve(name)).withFailMessage("Missing gate %s:%n%s", name, output()).exists();
      });
    }

    String output() throws IOException {
      return Files.readString(outputFile);
    }

    @Override
    public void close() throws Exception {
      try {
        signal("release");
        signal("stop");
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
          process.destroy();
          if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
          }
        }
        assertThat(process.exitValue()).withFailMessage("Child failed:%n%s", output()).isZero();
      } finally {
        if (process.isAlive()) {
          process.destroyForcibly();
          process.waitFor(5, TimeUnit.SECONDS);
        }
      }
    }
  }
}
