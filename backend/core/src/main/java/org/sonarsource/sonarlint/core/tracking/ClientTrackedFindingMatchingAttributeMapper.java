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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LineWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;

public class ClientTrackedFindingMatchingAttributeMapper implements MatchingAttributesMapper<ClientTrackedFindingDto> {
  @Override
  public String getRuleKey(ClientTrackedFindingDto issue) {
    return issue.getRuleKey();
  }

  @Override
  public Optional<Integer> getLine(ClientTrackedFindingDto issue) {
    var lineWithHash = issue.getLineWithHash();
    return Optional.ofNullable(lineWithHash).map(LineWithHashDto::getNumber);
  }

  @Override
  public Optional<String> getTextRangeHash(ClientTrackedFindingDto issue) {
    var issueRange = issue.getTextRangeWithHash();
    return Optional.ofNullable(issueRange).map(TextRangeWithHashDto::getHash);
  }

  @Override
  public Optional<String> getLineHash(ClientTrackedFindingDto issue) {
    var lineWithHash = issue.getLineWithHash();
    return Optional.ofNullable(lineWithHash).map(LineWithHashDto::getHash);
  }

  @Override
  public String getMessage(ClientTrackedFindingDto issue) {
    return issue.getMessage();
  }

  @Override
  public Optional<String> getServerIssueKey(ClientTrackedFindingDto issue) {
    return Optional.ofNullable(issue.getServerKey());
  }
}
