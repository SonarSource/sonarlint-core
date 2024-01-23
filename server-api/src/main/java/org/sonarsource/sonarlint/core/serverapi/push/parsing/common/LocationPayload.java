/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.push.parsing.common;

import static java.util.Objects.isNull;
import static org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils.isBlank;

public class LocationPayload {
  private String filePath;
  private String message;
  private TextRangePayload textRange;

  public String getFilePath() {
    return filePath;
  }

  public String getMessage() {
    return message;
  }

  public TextRangePayload getTextRange() {
    return textRange;
  }

  public boolean isInvalid() {
    return isBlank(filePath) || isBlank(message) || isNull(textRange) || textRange.isInvalid();
  }

  public static class TextRangePayload {
    private int startLine;
    private int startLineOffset;
    private int endLine;
    private int endLineOffset;
    private String hash;

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

    public String getHash() {
      return hash;
    }

    public boolean isInvalid() {
      return isBlank(hash);
    }
  }
}
