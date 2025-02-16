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

  public ProcessWrapper create(@Nullable Path baseDir, Consumer<String> stdOutLineConsumer, String... command) {
    return new ProcessWrapper(baseDir, stdOutLineConsumer, Map.of(), command);
  }

  public ProcessWrapper create(@Nullable Path baseDir, Consumer<String> stdOutLineConsumer, Map<String, String> envVariables, String... command) {
    return new ProcessWrapper(baseDir, stdOutLineConsumer, envVariables, command);
  }

  static class ProcessWrapper {

    private final Path baseDir;
    private final Consumer<String> stdOutLineConsumer;
    private final String[] command;
    private final Map<String, String> envVariables = new HashMap<>();

    ProcessWrapper(@Nullable Path baseDir, Consumer<String> stdOutLineConsumer, Map<String, String> envVariables, String... command) {
      this.baseDir = baseDir;
      this.stdOutLineConsumer = stdOutLineConsumer;
      this.envVariables.putAll(envVariables);
      this.command = command;
    }

    private static void processInputStream(InputStream inputStream, Consumer<String> stringConsumer) {
      try (var scanner = new Scanner(new InputStreamReader(inputStream, UTF_8))) {
        scanner.useDelimiter("\n");
        while (scanner.hasNext()) {
          stringConsumer.accept(scanner.next());
        }
      }
    }

    private static boolean isNotAGitRepo(LinkedList<String> output) {
      return output.stream().anyMatch(line -> line.contains("not a git repository"));
    }

    public void execute() throws IOException {
      var output = new LinkedList<String>();
      var processBuilder = new ProcessBuilder()
        .command(command)
        .directory(baseDir != null ? baseDir.toFile() : null);
      envVariables.forEach(processBuilder.environment()::put);
      processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
      processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
      processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);

      var p = processBuilder.start();
      try {
        processInputStream(p.getInputStream(), line -> {
          output.add(line);
          stdOutLineConsumer.accept(line);
        });

        processInputStream(p.getErrorStream(), line -> {
          if (!line.isBlank()) {
            output.add(line);
            LOG.debug(line);
          }
        });

        int exit = p.waitFor();
        if (exit != 0) {
          if (isNotAGitRepo(output)) {
            var dirStr = baseDir != null ? baseDir.toString() : "null";
            throw new GitRepoNotFoundException(dirStr);
          }
          throw new IllegalStateException(format("Command execution exited with code: %d", exit));
        }
      } catch (InterruptedException e) {
        LOG.warn(format("Command [%s] interrupted", join(" ", command)), e);
        Thread.currentThread().interrupt();
      } finally {
        p.destroy();
      }
    }
  }

}
