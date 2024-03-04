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

import javax.annotation.Nullable;
import org.sonar.api.batch.fs.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.IssueRangeAndMessage;

public abstract class TextRangeLocation implements IssueRangeAndMessage {

  private final org.sonarsource.sonarlint.core.client.api.common.TextRange textRange;

  protected TextRangeLocation(@Nullable TextRange analyzerTextRange) {
    this.textRange = analyzerTextRange != null ? new org.sonarsource.sonarlint.core.client.api.common.TextRange(
      analyzerTextRange.start().line(),
      analyzerTextRange.start().lineOffset(),
      analyzerTextRange.end().line(),
      analyzerTextRange.end().lineOffset())
      : null;
  }

  protected TextRangeLocation(@Nullable org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue.TextRange serverStorageTextRange) {
    this.textRange = serverStorageTextRange != null ? new org.sonarsource.sonarlint.core.client.api.common.TextRange(
      serverStorageTextRange.getStartLine(),
      serverStorageTextRange.getStartLineOffset(),
      serverStorageTextRange.getEndLine(),
      serverStorageTextRange.getEndLineOffset())
      : null;
  }

  @Override
  public org.sonarsource.sonarlint.core.client.api.common.TextRange getTextRange() {
    return textRange;
  }
}
