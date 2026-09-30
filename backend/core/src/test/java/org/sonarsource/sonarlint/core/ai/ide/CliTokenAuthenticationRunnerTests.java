/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.ai.ide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionResponse.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@DisabledOnOs(OS.WINDOWS)
class CliTokenAuthenticationRunnerTests {

  @TempDir
  Path tempDir;

  @Test
  void passes_the_token_only_on_standard_input_and_discards_echoed_output() throws IOException {
    var cli = script("""
      #!/bin/sh
      [ "$1" = auth ] && [ "$2" = login ] && [ "$3" = --with-token ] || exit 2
      [ "$4" = --server ] && [ "$5" = https://sonarqube.us ] || exit 3
      [ "$6" = --org ] && [ "$7" = acme ] || exit 4
      read token
      [ "$token" = saved-secret ] || exit 5
      printf '%s\\n' "$token"
      printf '%s\\n' "$token" >&2
      """);

    var result = new CliTokenAuthenticationRunner().authenticate(cli, "https://sonarqube.us", "acme", "saved-secret", new SonarLintCancelMonitor());

    assertThat(result.getStatus()).isEqualTo(Status.AUTHENTICATED);
    assertThat(result.toString()).doesNotContain("saved-secret");
  }

  @Test
  void returns_a_safe_failure_when_the_cli_rejects_the_token() throws IOException {
    var cli = script("""
      #!/bin/sh
      read token
      printf '%s\\n' "$token" >&2
      exit 7
      """);

    var result = new CliTokenAuthenticationRunner().authenticate(cli, "https://server.example", null, "saved-secret", new SonarLintCancelMonitor());

    assertThat(result.getStatus()).isEqualTo(Status.FAILED);
    assertThat(result.getMessage()).contains("Exit code: 7").doesNotContain("saved-secret");
  }

  @Test
  void times_out_a_cli_that_does_not_exit() throws IOException {
    var started = tempDir.resolve("timed-out-pid");
    var cli = script("""
      #!/bin/sh
      read token
      echo $$ > '%s'
      exec sleep 10
      """.formatted(started));

    var result = new CliTokenAuthenticationRunner(Duration.ofMillis(200))
      .authenticate(cli, "https://server.example", null, "saved-secret", new SonarLintCancelMonitor());

    assertThat(result.getStatus()).isEqualTo(Status.FAILED);
    assertThat(result.getMessage()).contains("timed out");
    assertThat(Files.exists(started)).isTrue();
    assertThat(processIsAlive(started)).isFalse();
  }

  @Test
  void stops_the_cli_when_the_request_is_cancelled() throws Exception {
    var started = tempDir.resolve("started");
    var cli = script("""
      #!/bin/sh
      read token
      echo $$ > '%s'
      exec sleep 10
      """.formatted(started));
    var monitor = new SonarLintCancelMonitor();
    var operation = CompletableFuture.supplyAsync(() -> new CliTokenAuthenticationRunner()
      .authenticate(cli, "https://server.example", null, "saved-secret", monitor));
    try {
      await().atMost(Duration.ofSeconds(2)).until(() -> Files.exists(started));
      monitor.cancel();
      assertThatThrownBy(() -> operation.get(2, TimeUnit.SECONDS)).hasCauseInstanceOf(CancellationException.class);
      assertThat(processIsAlive(started)).isFalse();
    } finally {
      monitor.cancel();
    }
  }

  private Path script(String content) throws IOException {
    var cli = Files.createTempFile(tempDir, "sonar", ".sh");
    Files.writeString(cli, content);
    assertThat(cli.toFile().setExecutable(true)).isTrue();
    return cli;
  }

  private static boolean processIsAlive(Path pidFile) throws IOException {
    var pid = Long.parseLong(Files.readString(pidFile).trim());
    return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
  }
}
