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

package org.sonarsource.sonarlint.core.tracking;

import java.util.Optional;
import org.sonarsource.sonarlint.core.analysis.RawIssue;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.issue.matching.MatchingAttributesMapper;

public class RawIssueFindingMatchingAttributeMapper implements MatchingAttributesMapper<RawIssue> {

  @Override
  public String getRuleKey(RawIssue issue) {
    return issue.getRuleKey();
  }

  @Override
  public Optional<Integer> getLine(RawIssue issue) {
    var textRange = issue.getTextRange();
    if (textRange == null) {
      return Optional.empty();
    }
    return Optional.of(textRange.getStartLine());
  }

  @Override
  public Optional<String> getTextRangeHash(RawIssue issue) {
    return Optional.ofNullable(TextRangeUtils.getTextRangeWithHash(issue.getTextRange(), issue.getClientInputFile()))
      .map(TextRangeWithHash::getHash);
  }

  @Override
  public Optional<String> getLineHash(RawIssue issue) {
    return Optional.ofNullable(TextRangeUtils.getLineWithHash(issue.getTextRange(), issue.getClientInputFile()))
      .map(LineWithHash::getHash);
  }

  @Override
  public String getMessage(RawIssue issue) {
    return issue.getMessage();
  }

  @Override
  public Optional<String> getServerIssueKey(RawIssue issue) {
    return Optional.empty();
  }
}
