/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.grip.web.api.payload;

public class SuggestFixRequestPayload {
  private String promptVersion;
  private String sourceCode;
  private String message;
  private String ruleKey;
  private TextRangePayload textRange;

  public SuggestFixRequestPayload(String promptVersion, String sourceCode, String message, String ruleKey, TextRangePayload textRange) {
    this.sourceCode = sourceCode;
    this.message = message;
    this.ruleKey = ruleKey;
    this.textRange = textRange;
  }

  public static class TextRangePayload {
    private int startLine;
    private int startOffset;
    private int endLine;
    private int endOffset;

    public TextRangePayload(int startLine, int startOffset, int endLine, int endOffset) {
      this.startLine = startLine;
      this.startOffset = startOffset;
      this.endLine = endLine;
      this.endOffset = endOffset;
    }
  }
}
