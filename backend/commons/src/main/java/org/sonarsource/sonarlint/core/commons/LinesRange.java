/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.commons;

import java.util.Objects;

public class LinesRange {
  private final int startLine;
  private final int endLine;

  public LinesRange(int startLine, int endLine) {
    if (endLine < startLine) {
      throw new IllegalArgumentException("Range is not valid. Start line must be smaller or equal to end line.");
    } else if (startLine < 0) {
      throw new IllegalArgumentException("Range is not valid. Start line cannot be a negative number");
    }
    this.startLine = startLine;
    this.endLine = endLine;
  }

  public int getStartLine() {
    return startLine;
  }

  public int getEndLine() {
    return endLine;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    var that = (LinesRange) o;
    return startLine == that.startLine && endLine == that.endLine;
  }

  @Override
  public int hashCode() {
    return Objects.hash(startLine, endLine);
  }
}
