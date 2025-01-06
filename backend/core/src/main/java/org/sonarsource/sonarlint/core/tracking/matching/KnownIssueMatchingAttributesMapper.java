/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.tracking.matching;

import java.util.Optional;
import org.sonarsource.sonarlint.core.commons.KnownFinding;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;

public class KnownIssueMatchingAttributesMapper implements MatchingAttributesMapper<KnownFinding> {

  @Override
  public String getRuleKey(KnownFinding issue) {
    return issue.getRuleKey();
  }

  @Override
  public Optional<Integer> getLine(KnownFinding issue) {
    return Optional.ofNullable(issue.getLineWithHash()).map(LineWithHash::getNumber);
  }

  @Override
  public Optional<String> getTextRangeHash(KnownFinding issue) {
    return Optional.ofNullable(issue.getTextRangeWithHash()).map(TextRangeWithHash::getHash);
  }

  @Override
  public Optional<String> getLineHash(KnownFinding issue) {
    return Optional.ofNullable(issue.getLineWithHash()).map(LineWithHash::getHash);
  }

  @Override
  public String getMessage(KnownFinding issue) {
    return issue.getMessage();
  }

  @Override
  public Optional<String> getServerIssueKey(KnownFinding issue) {
    return Optional.empty();
  }
}
