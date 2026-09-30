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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionResponse.Status;

/** Runs CLI login without exposing the token through process arguments or diagnostic output. */
class CliTokenAuthenticationRunner {

  private static final String FAILURE_MESSAGE = "SonarQube CLI authentication failed. Check the saved token, server connection, and CLI environment.";
  private final Duration timeout;

  CliTokenAuthenticationRunner() {
    this(Duration.ofSeconds(60));
  }

  CliTokenAuthenticationRunner(Duration timeout) {
    this.timeout = timeout;
  }

  AuthenticateCliWithConnectionResponse authenticate(Path executable, String serverUrl, @Nullable String organization,
    String token, SonarLintCancelMonitor cancelMonitor) {
    cancelMonitor.checkCanceled();
    var arguments = new ArrayList<String>();
    arguments.add(executable.toString());
    arguments.add("auth");
    arguments.add("login");
    arguments.add("--with-token");
    arguments.add("--server");
    arguments.add(serverUrl);
    if (organization != null && !organization.isBlank()) {
      arguments.add("--org");
      arguments.add(organization);
    }

    final Process process;
    try {
      process = new ProcessBuilder(arguments)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start();
    } catch (IOException e) {
      return failed("Could not start SonarQube CLI authentication.");
    }

    cancelMonitor.onCancel(process::destroyForcibly);
    var inputFailed = new AtomicBoolean();
    var stdin = Thread.ofVirtual().start(() -> {
      try (var output = process.getOutputStream()) {
        output.write(token.getBytes(StandardCharsets.UTF_8));
        output.write('\n');
      } catch (IOException e) {
        inputFailed.set(true);
      }
    });

    try {
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        return failed("SonarQube CLI authentication timed out after " + timeout.toSeconds() + " seconds.");
      }
      stdin.join(1_000);
      cancelMonitor.checkCanceled();
      if (process.exitValue() == 0 && !inputFailed.get() && !stdin.isAlive()) {
        return new AuthenticateCliWithConnectionResponse(Status.AUTHENTICATED, null);
      }
      return failed(FAILURE_MESSAGE + " Exit code: " + process.exitValue() + ".");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return failed("SonarQube CLI authentication was interrupted.");
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
      }
      try {
        process.waitFor(2, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      closeQuietly(process.getOutputStream());
      stdin.interrupt();
    }
  }

  private static AuthenticateCliWithConnectionResponse failed(String message) {
    return new AuthenticateCliWithConnectionResponse(Status.FAILED, message);
  }

  private static void closeQuietly(AutoCloseable closeable) {
    try {
      closeable.close();
    } catch (Exception ignored) {
      // The process has already exited or been stopped.
    }
  }
}
