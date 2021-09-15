/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.analyzer.issue;

public class TextRangeUtils {

  private TextRangeUtils() {
  }

  public static org.sonarsource.sonarlint.core.client.api.common.TextRange convert(org.sonar.api.batch.fs.TextRange analyzerTextRange) {
    return new org.sonarsource.sonarlint.core.client.api.common.TextRange(
      analyzerTextRange.start().line(),
      analyzerTextRange.start().lineOffset(),
      analyzerTextRange.end().line(),
      analyzerTextRange.end().lineOffset());
  }

  public static org.sonarsource.sonarlint.core.client.api.common.TextRange convert(org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue.TextRange serverStorageTextRange) {
    return new org.sonarsource.sonarlint.core.client.api.common.TextRange(
      serverStorageTextRange.getStartLine(),
      serverStorageTextRange.getStartLineOffset(),
      serverStorageTextRange.getEndLine(),
      serverStorageTextRange.getEndLineOffset());
  }

}
