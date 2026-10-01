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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Timeout(15)
class CliResetRunnerTests {
  @TempDir
  Path tempDir;

  @Test
  void passes_exact_arguments_and_environment_and_closes_standard_input() throws Exception {
    var process = completedProcess(0, "warning\n", "avertissement\n");
    var input = new ByteArrayOutputStream();
    when(process.getOutputStream()).thenReturn(input);
    var arguments = List.of(tempDir.resolve("space ' $(hostile)/sonar").toString(), "system", "reset", "--force");
    var runner = new CliResetRunner((command, environment) -> {
      assertThat(command).containsExactlyElementsOf(arguments);
      assertThat(environment).containsExactlyEntriesOf(Map.of("KEY", "value"));
      return process;
    });

    var result = runner.run(arguments, Map.of("KEY", "value"), Duration.ofSeconds(1), new SonarLintCancelMonitor());

    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).isEqualTo("warning\n");
    assertThat(result.stderr()).isEqualTo("avertissement\n");
    assertThat(result.lifecycleFailed()).isFalse();
    assertThat(result.shutdownConfirmed()).isTrue();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  void captures_only_first_megabyte_per_stream_and_drains_the_remainder() throws Exception {
    var output = new byte[CliResetRunner.OUTPUT_LIMIT + 20_000];
    Arrays.fill(output, (byte) 'a');
    var process = completedProcess(0, "", "");
    var stdout = new ByteArrayInputStream(output);
    var stderr = new ByteArrayInputStream(output);
    when(process.getInputStream()).thenReturn(stdout);
    when(process.getErrorStream()).thenReturn(stderr);

    var result = new CliResetRunner((command, environment) -> process).run(List.of("unused"), Map.of(),
      Duration.ofSeconds(1), new SonarLintCancelMonitor());

    assertThat(result.stdout()).hasSize(CliResetRunner.OUTPUT_LIMIT);
    assertThat(result.stderr()).hasSize(CliResetRunner.OUTPUT_LIMIT);
    assertThat(result.diagnostics()).hasSize(2).allMatch(message -> message.contains("truncated"));
    assertThat(stdout.available()).isZero();
    assertThat(stderr.available()).isZero();
    assertThat(result.lifecycleFailed()).isFalse();
  }

  @Test
  void reports_start_failure_without_disclosing_exception_details() {
    var runner = new CliResetRunner((command, environment) -> { throw new IOException("sensitive environment"); });

    var result = runner.run(List.of("unused"), Map.of(), Duration.ofSeconds(1), new SonarLintCancelMonitor());

    assertThat(result.exitCode()).isNull();
    assertThat(result.lifecycleFailed()).isTrue();
    assertThat(result.shutdownConfirmed()).isTrue();
    assertThat(result.diagnostics()).noneMatch(message -> message.contains("sensitive"));
  }

  @Test
  void retains_nonzero_exit_and_both_untrimmed_streams() throws Exception {
    var process = completedProcess(2, " unsupported\n", " commande inconnue ");

    var result = new CliResetRunner((command, environment) -> process).run(List.of("unused"), Map.of(),
      Duration.ofSeconds(1), new SonarLintCancelMonitor());

    assertThat(result.exitCode()).isEqualTo(2);
    assertThat(result.stdout()).isEqualTo(" unsupported\n");
    assertThat(result.stderr()).isEqualTo(" commande inconnue ");
    assertThat(result.lifecycleFailed()).isFalse();
  }

  @Test
  void cancels_and_confirms_shutdown_before_returning() throws Exception {
    var process = completedProcess(0, "", "");
    var started = new CountDownLatch(1);
    var alive = new java.util.concurrent.atomic.AtomicBoolean(true);
    when(process.isAlive()).thenAnswer(invocation -> alive.get());
    doAnswer(invocation -> { alive.set(false); return null; }).when(process).destroy();
    when(process.waitFor(anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
      started.countDown();
      Thread.sleep(5);
      return !alive.get();
    });
    var monitor = new SonarLintCancelMonitor();
    var operation = CompletableFuture.supplyAsync(() -> new CliResetRunner((command, environment) -> process)
      .run(List.of("unused"), Map.of(), Duration.ofSeconds(10), monitor));
    assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
    monitor.cancel();

    var result = operation.get(3, TimeUnit.SECONDS);

    assertThat(result.lifecycleFailed()).isTrue();
    assertThat(result.shutdownConfirmed()).isTrue();
    assertThat(result.diagnostics()).anyMatch(message -> message.contains("cancelled"));
    verify(process).destroy();
  }

  @Test
  void restores_thread_interruption_after_shutdown() throws Exception {
    var process = completedProcess(0, "", "");
    var started = new CountDownLatch(1);
    when(process.waitFor(anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
      started.countDown();
      Thread.sleep(10_000);
      return true;
    });
    var result = new AtomicReference<CliResetRunner.Result>();
    var restored = new java.util.concurrent.atomic.AtomicBoolean();
    var operation = Thread.ofPlatform().start(() -> {
      result.set(new CliResetRunner((command, environment) -> process).run(List.of("unused"), Map.of(),
        Duration.ofSeconds(10), new SonarLintCancelMonitor()));
      restored.set(Thread.currentThread().isInterrupted());
    });
    assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
    operation.interrupt();
    operation.join(3_000);

    assertThat(operation.isAlive()).isFalse();
    assertThat(restored).isTrue();
    assertThat(result.get().lifecycleFailed()).isTrue();
  }

  @Test
  void treats_output_drain_failure_as_lifecycle_failure() throws Exception {
    var process = completedProcess(0, "", "");
    when(process.getInputStream()).thenReturn(new InputStream() {
      @Override
      public int read() throws IOException { throw new IOException("drain failed"); }
    });

    var result = new CliResetRunner((command, environment) -> process).run(List.of("unused"), Map.of(),
      Duration.ofSeconds(1), new SonarLintCancelMonitor());

    assertThat(result.lifecycleFailed()).isTrue();
    assertThat(result.diagnostics()).anyMatch(message -> message.contains("drain"));
  }

  @Test
  void bounds_forced_shutdown_and_reports_a_process_that_cannot_be_stopped() throws Exception {
    var process = completedProcess(0, "", "");
    when(process.isAlive()).thenReturn(true);
    when(process.waitFor(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false);
    var start = System.nanoTime();

    var result = new CliResetRunner((command, environment) -> process).run(List.of("unused"), Map.of(),
      Duration.ofMillis(20), new SonarLintCancelMonitor());

    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(8));
    assertThat(result.shutdownConfirmed()).isFalse();
    assertThat(result.lifecycleFailed()).isTrue();
    assertThat(result.diagnostics()).anyMatch(message -> message.contains("Restart the backend"));
    verify(process).destroyForcibly();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void real_process_drains_saturated_output_without_deadlock() throws Exception {
    var script = script("""
      #!/bin/sh
      head -c 1100000 /dev/zero
      head -c 1100000 /dev/zero >&2
      """);

    var result = new CliResetRunner().run(List.of(script.toString(), "system", "reset", "--force"), Map.of(),
      Duration.ofSeconds(5), new SonarLintCancelMonitor());

    assertThat(result.exitCode()).isZero();
    assertThat(result.lifecycleFailed()).isFalse();
    assertThat(result.stdout()).hasSize(CliResetRunner.OUTPUT_LIMIT);
    assertThat(result.stderr()).hasSize(CliResetRunner.OUTPUT_LIMIT);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void real_timeout_stops_root_and_observed_child() throws Exception {
    var script = script("""
      #!/bin/sh
      sleep 30 &
      wait
      """);
    var process = new AtomicReference<Process>();
    var runner = new CliResetRunner((command, environment) -> {
      var started = new ProcessBuilder(command).start();
      process.set(started);
      return started;
    });

    var result = runner.run(List.of(script.toString()), Map.of(), Duration.ofMillis(200), new SonarLintCancelMonitor());

    assertThat(result.lifecycleFailed()).isTrue();
    assertThat(result.diagnostics()).anyMatch(message -> message.contains("timed out"));
    assertThat(process.get().isAlive()).isFalse();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void inherited_pipes_after_root_exit_fail_without_unbounded_drainer_wait() throws Exception {
    var script = script("""
      #!/bin/sh
      sleep 30 &
      sleep 0.1
      exit 0
      """);
    var started = System.nanoTime();

    var result = new CliResetRunner().run(List.of(script.toString()), Map.of(), Duration.ofSeconds(5), new SonarLintCancelMonitor());

    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(11));
    assertThat(result.lifecycleFailed()).isTrue();
    assertThat(result.diagnostics()).anyMatch(message -> message.contains("descendants"));
  }

  private Path script(String contents) throws IOException {
    var path = Files.writeString(tempDir.resolve("fake-cli"), contents);
    assertThat(path.toFile().setExecutable(true)).isTrue();
    return path;
  }

  private static Process completedProcess(int exitCode, String stdout, String stderr) throws Exception {
    var process = mock(Process.class);
    when(process.getInputStream()).thenReturn(new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8)));
    when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8)));
    when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
    when(process.descendants()).thenAnswer(invocation -> Stream.empty());
    when(process.waitFor(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
    when(process.exitValue()).thenReturn(exitCode);
    return process;
  }
}
