/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.api;

import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.commons.TextRange;

public interface WithTextRange {

  /**
   * @return null for file level issues
   */
  @CheckForNull
  TextRange getTextRange();

  @CheckForNull
  default Integer getStartLine() {
    var textRange = getTextRange();
    return textRange != null ? textRange.getStartLine() : null;
  }

  @CheckForNull
  default Integer getStartLineOffset() {
    var textRange = getTextRange();
    return textRange != null ? textRange.getStartLineOffset() : null;
  }

  @CheckForNull
  default Integer getEndLine() {
    var textRange = getTextRange();
    return textRange != null ? textRange.getEndLine() : null;
  }

  @CheckForNull
  default Integer getEndLineOffset() {
    var textRange = getTextRange();
    return textRange != null ? textRange.getEndLineOffset() : null;
  }

  static TextRange convert(org.sonar.api.batch.fs.TextRange analyzerTextRange) {
    return new TextRange(
      analyzerTextRange.start().line(),
      analyzerTextRange.start().lineOffset(),
      analyzerTextRange.end().line(),
      analyzerTextRange.end().lineOffset());
  }
}
