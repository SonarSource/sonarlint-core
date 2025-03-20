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
package org.sonarsource.sonarlint.core.commons.util.git;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ProcessWrapperFactory {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public ProcessWrapperFactory() {
    // nothing to do
  }

  public ProcessWrapper create(@Nullable Path baseDir, String... command) {
    return new ProcessWrapper(baseDir, Map.of(), command);
  }

  public ProcessWrapper create(@Nullable Path baseDir, Map<String, String> envVariables, String... command) {
    return new ProcessWrapper(baseDir, envVariables, command);
  }

  static class ProcessWrapper {

    private final Path baseDir;
    private final String[] command;
    private final Map<String, String> envVariables = new HashMap<>();

    ProcessWrapper(@Nullable Path baseDir, Map<String, String> envVariables, String... command) {
      this.baseDir = baseDir;
      this.envVariables.putAll(envVariables);
      this.command = command;
    }

    void processInputStream(InputStream inputStream, Consumer<String> stringConsumer) {
      try (var scanner = new Scanner(new InputStreamReader(inputStream, UTF_8))) {
        scanner.useDelimiter("\n");
        while (scanner.hasNext()) {
          stringConsumer.accept(scanner.next());
        }
      }
    }

    public ProcessExecutionResult execute() {
      Process p;
      var output = new LinkedList<String>();
      try {
        p = createProcess();
      } catch (IOException e) {
        LOG.warn(format("Could not execute command: [%s]", join(" ", command)), e);
        return new ProcessExecutionResult(-2, join(System.lineSeparator(), output));
      }
      try {
        return runProcessAndGetOutput(p, output);
      } catch (InterruptedException e) {
        LOG.warn(format("Command [%s] interrupted", join(" ", command)), e);
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        LOG.warn(format("Command failed: [%s]", join(" ", command)), e);
      } finally {
        p.destroy();
      }
      return new ProcessExecutionResult(-1, join(System.lineSeparator(), output));
    }

    Process createProcess() throws IOException {
      var processBuilder = new ProcessBuilder()
        .command(command)
        .directory(baseDir != null ? baseDir.toFile() : null);
      envVariables.forEach(processBuilder.environment()::put);
      processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
      processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
      processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);

      return processBuilder.start();
    }

    ProcessExecutionResult runProcessAndGetOutput(Process p, LinkedList<String> output) throws InterruptedException {
      processInputStream(p.getInputStream(), output::add);
      processInputStream(p.getErrorStream(), line -> {
        if (!line.isBlank()) {
          output.add(line);
          LOG.debug(line);
        }
      });
      int exit = p.waitFor();
      var commandOutput = join(System.lineSeparator(), output);
      return new ProcessExecutionResult(exit, join(System.lineSeparator(), commandOutput));
    }
  }

  public record ProcessExecutionResult(int exitCode, String output) {
  }
}
