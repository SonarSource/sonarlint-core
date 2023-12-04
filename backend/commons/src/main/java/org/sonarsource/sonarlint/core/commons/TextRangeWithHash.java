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

public class TextRangeWithHash extends TextRange {

  private final String hash;

  public TextRangeWithHash(int startLine, int startLineOffset, int endLine, int endLineOffset, String hash) {
    super(startLine, startLineOffset, endLine, endLineOffset);
    this.hash = hash;
  }

  public String getHash() {
    return hash;
  }

  @Override
  public int hashCode() {
    final var prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(hash);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (!(obj instanceof TextRangeWithHash)) {
      return false;
    }
    TextRangeWithHash other = (TextRangeWithHash) obj;
    return Objects.equals(hash, other.hash);
  }

}
