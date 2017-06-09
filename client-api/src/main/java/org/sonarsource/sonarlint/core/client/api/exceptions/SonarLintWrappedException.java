/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.exceptions;

import javax.annotation.Nullable;

/**
 * Client should not depend on this technical class
 */
public class SonarLintWrappedException extends SonarLintException {

  private final String originalClassToString;

  private SonarLintWrappedException(String originalClassToString, String msg, Throwable cause) {
    super(msg, cause);
    this.originalClassToString = originalClassToString;
  }

  public static SonarLintException wrap(@Nullable Throwable t) {
    if (t == null) {
      return null;
    }

    if (t instanceof MessageException) {
      return (SonarLintException) t;
    }

    if (t.getCause() == null && t instanceof SonarLintException) {
      return (SonarLintException) t;
    }

    Throwable cause = wrap(t.getCause());
    if (cause instanceof MessageException) {
      return (SonarLintException) cause;
    }

    SonarLintWrappedException sonarLintException = new SonarLintWrappedException(t.toString(), t.getMessage(), cause);
    sonarLintException.setStackTrace(t.getStackTrace());
    return sonarLintException;
  }

  @Override
  public String toString() {
    return originalClassToString;
  }

}
