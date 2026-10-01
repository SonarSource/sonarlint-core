/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.ai;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** Success describes executable and installer cleanup, not complete CLI reset cleanup. */
public class UninstallCliResponse {
  public enum Status {
    UNINSTALLED,
    UNINSTALLED_WITH_REMAINING_CONFIGURATION,
    NOT_AVAILABLE,
    RESET_FAILED,
    EXECUTABLE_REMOVAL_FAILED,
    IN_PROGRESS
  }

  private final Status status;
  @Nullable
  private final String executablePath;
  @Nullable
  private final Integer resetExitCode;
  private final String stdout;
  private final String stderr;
  private final List<String> diagnostics;

  public UninstallCliResponse(Status status, @Nullable String executablePath, @Nullable Integer resetExitCode,
    String stdout, String stderr, List<String> diagnostics) {
    this.status = Objects.requireNonNull(status);
    this.executablePath = executablePath;
    this.resetExitCode = resetExitCode;
    this.stdout = Objects.requireNonNull(stdout);
    this.stderr = Objects.requireNonNull(stderr);
    this.diagnostics = List.copyOf(diagnostics);
  }

  public Status getStatus() {
    return status;
  }

  @Nullable
  public String getExecutablePath() {
    return executablePath;
  }

  @Nullable
  public Integer getResetExitCode() {
    return resetExitCode;
  }

  public String getStdout() {
    return stdout;
  }

  public String getStderr() {
    return stderr;
  }

  public List<String> getDiagnostics() {
    return List.copyOf(diagnostics);
  }
}
