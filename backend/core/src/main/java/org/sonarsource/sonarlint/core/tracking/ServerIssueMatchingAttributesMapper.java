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
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

public class ServerIssueMatchingAttributesMapper implements MatchingAttributesMapper<ServerIssue<?>> {

  @Override
  public String getRuleKey(ServerIssue<?> issue) {
    return issue.getRuleKey();
  }

  @Override
  public Optional<Integer> getLine(ServerIssue<?> issue) {
    if (issue instanceof LineLevelServerIssue) {
      return Optional.of(((LineLevelServerIssue) issue).getLine());
    }
    if (issue instanceof RangeLevelServerIssue) {
      return Optional.of(((RangeLevelServerIssue) issue).getTextRange().getStartLine());
    }
    return Optional.empty();
  }

  @Override
  public Optional<String> getTextRangeHash(ServerIssue<?> issue) {
    if (issue instanceof RangeLevelServerIssue) {
      return Optional.of(((RangeLevelServerIssue) issue).getTextRange().getHash());
    }
    return Optional.empty();
  }

  @Override
  public Optional<String> getLineHash(ServerIssue<?> issue) {
    if (issue instanceof LineLevelServerIssue) {
      return Optional.of(((LineLevelServerIssue) issue).getLineHash());
    }
    return Optional.empty();
  }

  @Override
  public String getMessage(ServerIssue<?> issue) {
    return issue.getMessage();
  }

  @Override
  public Optional<String> getServerIssueKey(ServerIssue<?> issue) {
    return Optional.of(issue.getKey());
  }
}
