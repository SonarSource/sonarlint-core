/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2023 SonarSource SA
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

public class TextRange {

  private final int startLine;
  private final int startLineOffset;
  private final int endLine;
  private final int endLineOffset;

  public TextRange(int startLine, int startLineOffset, int endLine, int endLineOffset) {
    this.startLine = startLine;
    this.startLineOffset = startLineOffset;
    this.endLine = endLine;
    this.endLineOffset = endLineOffset;
  }

  public int getStartLine() {
    return startLine;
  }

  public int getStartLineOffset() {
    return startLineOffset;
  }

  public int getEndLine() {
    return endLine;
  }

  public int getEndLineOffset() {
    return endLineOffset;
  }

  @Override
  public int hashCode() {
    return Objects.hash(endLine, endLineOffset, startLine, startLineOffset);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof TextRange)) {
      return false;
    }
    TextRange other = (TextRange) obj;
    return endLine == other.endLine && endLineOffset == other.endLineOffset && startLine == other.startLine && startLineOffset == other.startLineOffset;
  }

}
