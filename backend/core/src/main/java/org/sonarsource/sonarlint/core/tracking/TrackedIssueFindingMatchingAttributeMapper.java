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
import org.sonarsource.sonarlint.core.issue.matching.MatchingAttributesMapper;

public class TrackedIssueFindingMatchingAttributeMapper implements MatchingAttributesMapper<TrackedIssue> {

  @Override
  public String getRuleKey(TrackedIssue issue) {
    return issue.getRuleKey();
  }

  @Override
  public Optional<Integer> getLine(TrackedIssue issue) {
    var textRange = issue.getTextRangeWithHash();
    if (textRange == null) {
      return Optional.empty();
    }
    return Optional.of(textRange.getStartLine());
  }

  @Override
  public Optional<String> getTextRangeHash(TrackedIssue issue) {
    var textRange = issue.getTextRangeWithHash();
    if (textRange == null) {
      return Optional.empty();
    }
    return Optional.of(textRange.getHash());
  }

  @Override
  public Optional<String> getLineHash(TrackedIssue issue) {
    var lineWithHash = issue.getLineWithHash();
    if (lineWithHash != null) {
      return Optional.of(lineWithHash.getHash());
    }
    return Optional.empty();
  }

  @Override
  public String getMessage(TrackedIssue issue) {
    return issue.getMessage();
  }

  @Override
  public Optional<String> getServerIssueKey(TrackedIssue issue) {
    return Optional.ofNullable(issue.getServerKey());
  }
}
