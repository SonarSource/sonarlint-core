/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
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

  public ProcessWrapper create(@Nullable Path baseDir, Consumer<String> lineConsumer, String... command) {
    return new ProcessWrapper(baseDir, lineConsumer, command);
  }

  public static class ProcessWrapper {

    private final Path baseDir;
    private final Consumer<String> lineConsumer;
    private final String[] command;

    ProcessWrapper(@Nullable Path baseDir, Consumer<String> lineConsumer, String... command) {
      this.baseDir = baseDir;
      this.lineConsumer = lineConsumer;
      this.command = command;
    }

    void processInputStream(InputStream inputStream, Consumer<String> stringConsumer) throws IOException {
      try (var reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          stringConsumer.accept(line);
        }
      }
    }

    public ProcessExecutionResult execute() {
      Process p;
      try {
        p = createProcess();
      } catch (IOException e) {
        LOG.warn(format("Could not execute command: [%s]", join(" ", command)), e);
        return new ProcessExecutionResult(-2);
      }
      try {
        return runProcessAndGetOutput(p);
      } catch (InterruptedException e) {
        LOG.warn(format("Command [%s] interrupted", join(" ", command)), e);
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        LOG.warn(format("Command failed: [%s]", join(" ", command)), e);
      } finally {
        p.destroy();
      }
      return new ProcessExecutionResult(-1);
    }

    Process createProcess() throws IOException {
      return new ProcessBuilder()
        .command(command)
        .directory(baseDir != null ? baseDir.toFile() : null)
        .start();
    }

    ProcessExecutionResult runProcessAndGetOutput(Process p) throws InterruptedException, IOException {
      processInputStream(p.getInputStream(), lineConsumer);
      processInputStream(p.getErrorStream(), line -> {
        if (!line.isBlank()) {
          LOG.debug(line);
        }
      });
      int exit = p.waitFor();
      return new ProcessExecutionResult(exit);
    }
  }

  public record ProcessExecutionResult(int exitCode) {
  }
}
