/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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

abstract class TextRangeLocation implements org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation {

  final TextRange originalTextRange;
  private final org.sonarsource.sonarlint.core.client.api.common.TextRange textRange;

  TextRangeLocation(@Nullable TextRange originalTextRange) {
    this.originalTextRange = originalTextRange;
    this.textRange = originalTextRange != null ?
      new org.sonarsource.sonarlint.core.client.api.common.TextRange(
        originalTextRange.start().line(),
        originalTextRange.start().lineOffset(),
        originalTextRange.end().line(),
        originalTextRange.end().lineOffset())
      : null;
  }

  @Override
  public org.sonarsource.sonarlint.core.client.api.common.TextRange getTextRange() {
    return textRange;
  }
}
