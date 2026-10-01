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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;

/** Bounded process lifecycle and concurrent output capture for reset and installer cleanup helpers. */
class CliResetRunner {
  static final int OUTPUT_LIMIT = 1_048_576;
  private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);
  private final ProcessStarter starter;

  CliResetRunner() {
    this((command, environment) -> {
      var builder = new ProcessBuilder(command);
      builder.environment().putAll(environment);
      return builder.start();
    });
  }

  CliResetRunner(ProcessStarter starter) {
    this.starter = starter;
  }

  Result run(List<String> command, Map<String, String> additionalEnvironment, Duration timeout, SonarLintCancelMonitor monitor) {
    monitor.checkCanceled();
    final Process process;
    try {
      process = starter.start(List.copyOf(command), Map.copyOf(additionalEnvironment));
    } catch (IOException | RuntimeException e) {
      return new Result(null, "", "", List.of("Could not start the CLI cleanup process."), true, true);
    }
    var cancelled = new AtomicBoolean();
    monitor.onCancel(() -> cancelled.set(true));
    var stdout = new Capture(process.getInputStream());
    var stderr = new Capture(process.getErrorStream());
    var outThread = Thread.ofVirtual().start(stdout);
    var errThread = Thread.ofVirtual().start(stderr);
    var descendants = new LinkedHashSet<ProcessHandle>();
    var diagnostics = new ArrayList<String>();
    boolean failed = false;
    boolean interrupted = false;
    boolean shutdownConfirmed = true;
    Integer exitCode = null;
    try {
      process.getOutputStream().close();
      var deadline = deadline(timeout);
      while (true) {
        observeDescendants(process, descendants);
        if (cancelled.get()) {
          diagnostics.add("CLI cleanup was cancelled.");
          failed = true;
          break;
        }
        if (stdout.failed() || stderr.failed()) {
          diagnostics.add("Could not drain CLI cleanup output.");
          failed = true;
          break;
        }
        if (process.waitFor(25, TimeUnit.MILLISECONDS)) {
          exitCode = process.exitValue();
          break;
        }
        if (System.nanoTime() >= deadline) {
          diagnostics.add("CLI cleanup timed out after " + timeout.toSeconds() + " seconds.");
          failed = true;
          break;
        }
      }
      if (!failed && !awaitDrainsAndDescendants(outThread, errThread, descendants, cancelled, DRAIN_TIMEOUT)) {
        diagnostics.add("CLI cleanup left running descendants or open output streams.");
        failed = true;
      }
      if (stdout.failed() || stderr.failed()) {
        diagnostics.add("Could not drain CLI cleanup output.");
        failed = true;
      }
    } catch (InterruptedException e) {
      interrupted = true;
      diagnostics.add("CLI cleanup was interrupted.");
      failed = true;
    } catch (IOException | RuntimeException e) {
      diagnostics.add("Could not complete CLI cleanup process monitoring.");
      failed = true;
    } finally {
      if (failed) {
        // Cleanup must finish even after interrupt/cancellation; the RPC propagates cancellation afterward.
        interrupted |= Thread.interrupted();
        shutdownConfirmed = terminate(process, descendants);
        interrupted |= Thread.interrupted();
        var closeThread = Thread.ofVirtual().start(() -> {
          closeQuietly(process.getInputStream());
          closeQuietly(process.getErrorStream());
          closeQuietly(process.getOutputStream());
        });
        interrupted |= awaitFinalDrains(outThread, errThread, closeThread);
        shutdownConfirmed &= !outThread.isAlive() && !errThread.isAlive() && !closeThread.isAlive();
        if (!shutdownConfirmed) {
          diagnostics.add("CLI cleanup shutdown could not be confirmed. Restart the backend before trying automatic uninstall again.");
        }
      }
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
    stdout.addDiagnostics("stdout", diagnostics);
    stderr.addDiagnostics("stderr", diagnostics);
    return new Result(exitCode, stdout.text(), stderr.text(), diagnostics, failed, shutdownConfirmed);
  }

  private static long deadline(Duration duration) {
    return System.nanoTime() + duration.toNanos();
  }

  private static void observeDescendants(Process process, Set<ProcessHandle> descendants) {
    try (var children = process.descendants()) {
      children.forEach(descendants::add);
    }
    observeKnownDescendants(descendants);
  }

  private static void observeKnownDescendants(Set<ProcessHandle> descendants) {
    for (var parent : List.copyOf(descendants)) {
      try (var children = parent.descendants()) {
        children.forEach(descendants::add);
      }
    }
  }

  private static boolean awaitDrainsAndDescendants(Thread stdout, Thread stderr, Set<ProcessHandle> descendants,
    AtomicBoolean cancelled, Duration timeout) throws InterruptedException {
    var deadline = deadline(timeout);
    while (stdout.isAlive() || stderr.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)) {
      observeKnownDescendants(descendants);
      if (cancelled.get() || System.nanoTime() >= deadline) {
        return false;
      }
      Thread.sleep(10);
    }
    return !cancelled.get();
  }

  private static boolean terminate(Process process, Set<ProcessHandle> descendants) {
    var deadline = deadline(TERMINATION_TIMEOUT);
    try {
      observeDescendants(process, descendants);
      descendants.forEach(handle -> handle.destroy());
      process.destroy();
      awaitTermination(process, descendants, Math.min(deadline, deadline(Duration.ofSeconds(1))));
      descendants.stream().filter(ProcessHandle::isAlive).forEach(handle -> handle.destroyForcibly());
      if (process.isAlive()) {
        process.destroyForcibly();
      }
      awaitTermination(process, descendants, deadline);
      return !process.isAlive() && descendants.stream().noneMatch(ProcessHandle::isAlive);
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static void awaitTermination(Process process, Set<ProcessHandle> descendants, long deadline) {
    while ((process.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)) && System.nanoTime() < deadline) {
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        // The interrupt is restored by the caller after bounded shutdown.
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  private static boolean awaitFinalDrains(Thread stdout, Thread stderr, Thread closer) {
    boolean interrupted = false;
    var deadline = deadline(DRAIN_TIMEOUT);
    while ((stdout.isAlive() || stderr.isAlive() || closer.isAlive()) && System.nanoTime() < deadline) {
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    return interrupted;
  }

  private static void closeQuietly(AutoCloseable stream) {
    try {
      stream.close();
    } catch (Exception ignored) {
      // The process has already been stopped; drain-thread state determines shutdown confirmation.
    }
  }

  @FunctionalInterface
  interface ProcessStarter {
    Process start(List<String> command, Map<String, String> additionalEnvironment) throws IOException;
  }

  record Result(@Nullable Integer exitCode, String stdout, String stderr, List<String> diagnostics,
                boolean lifecycleFailed, boolean shutdownConfirmed) {
    Result {
      diagnostics = List.copyOf(diagnostics);
    }
  }

  private static final class Capture implements Runnable {
    private final InputStream input;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private volatile boolean failed;
    private volatile boolean truncated;

    private Capture(InputStream input) {
      this.input = input;
    }

    @Override
    public void run() {
      try (input) {
        var buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
          synchronized (bytes) {
            var remaining = OUTPUT_LIMIT - bytes.size();
            bytes.write(buffer, 0, Math.min(count, remaining));
            truncated |= count > remaining;
          }
        }
      } catch (IOException | RuntimeException e) {
        failed = true;
      }
    }

    boolean failed() {
      return failed;
    }

    String text() {
      synchronized (bytes) {
        return bytes.toString(StandardCharsets.UTF_8);
      }
    }

    void addDiagnostics(String stream, List<String> diagnostics) {
      if (truncated) {
        diagnostics.add("CLI cleanup " + stream + " was truncated after " + OUTPUT_LIMIT + " bytes; remaining output was drained.");
      }
    }
  }
}
