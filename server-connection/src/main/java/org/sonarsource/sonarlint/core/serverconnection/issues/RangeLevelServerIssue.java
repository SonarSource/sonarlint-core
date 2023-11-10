/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.issues;

import java.time.Instant;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;

/**
 * Issues with precise location (from api/issues/pull, SQ >= 9.6)
 */
public class RangeLevelServerIssue extends ServerIssue<RangeLevelServerIssue> {
  private TextRangeWithHash textRange;

  public RangeLevelServerIssue(String key, boolean resolved, String ruleKey, String message, String filePath, Instant creationDate,
    @Nullable IssueSeverity userSeverity, RuleType type, TextRangeWithHash textRange) {
    super(key, resolved, ruleKey, message, filePath, creationDate, userSeverity, type);
    this.textRange = textRange;
  }

  public TextRangeWithHash getTextRange() {
    return textRange;
  }

  public RangeLevelServerIssue setTextRange(TextRangeWithHash textRange) {
    this.textRange = textRange;
    return this;
  }

}
