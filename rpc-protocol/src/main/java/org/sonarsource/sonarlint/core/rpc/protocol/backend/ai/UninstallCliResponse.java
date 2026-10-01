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

import java.util.Objects;
import javax.annotation.Nullable;

public class UninstallCliResponse {
  public enum Status {
    UNINSTALLED,
    NOT_AVAILABLE,
    FAILED
  }

  private final Status status;
  private final String stdout;
  private final String stderr;
  @Nullable
  private final String message;

  public UninstallCliResponse(Status status, String stdout, String stderr, @Nullable String message) {
    this.status = Objects.requireNonNull(status);
    this.stdout = Objects.requireNonNull(stdout);
    this.stderr = Objects.requireNonNull(stderr);
    this.message = message;
  }

  public Status getStatus() {
    return status;
  }

  public String getStdout() {
    return stdout;
  }

  public String getStderr() {
    return stderr;
  }

  @Nullable
  public String getMessage() {
    return message;
  }
}
