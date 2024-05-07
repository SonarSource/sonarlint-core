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

public class LinesRange {
  private final int startLine;
  private final int endLine;

  /**
   * @param startLine The numbering starts from 0. I.e. the first line of a file would be `1`
   * @param endLine   The numbering starts from 0. I.e. the last line of a file with 10 lines would be `10`
   */
  public LinesRange(int startLine, int endLine) {
    if (endLine < startLine) {
      throw new IllegalArgumentException("Range is not valid. Start line must be smaller or equal to end line.");
    } else if (startLine < 1) {
      throw new IllegalArgumentException("Range is not valid. Start line must be larger than 0. The numbering starts from 1 (i.e. the " +
        "second line of a file should be `2`)");
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
}
