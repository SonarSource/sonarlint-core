/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.local.only;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.sonarsource.sonarlint.core.commons.IssueStatus;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssueResolution;

public class LocalOnlyIssueFixtures {

  public static LocalOnlyIssue aLocalOnlyIssueResolvedWithoutTextAndLineRange() {
    return new LocalOnlyIssue(
      UUID.randomUUID(),
      "file/path",
      null,
      null,
      "ruleKey",
      "message",
      new LocalOnlyIssueResolution(IssueStatus.WONT_FIX, Instant.now().truncatedTo(ChronoUnit.MILLIS), "comment")
    );
  }

  public static LocalOnlyIssue aLocalOnlyIssueResolved() {
    return aLocalOnlyIssueResolved(UUID.randomUUID());
  }

  public static LocalOnlyIssue aLocalOnlyIssueResolved(Instant resolutionDate) {
    return aLocalOnlyIssueResolved(UUID.randomUUID(), resolutionDate);
  }

  public static LocalOnlyIssue aLocalOnlyIssueResolved(UUID id) {
    return aLocalOnlyIssueResolved(id, Instant.now());
  }

  public static LocalOnlyIssue aLocalOnlyIssueResolved(UUID id, Instant resolutionDate) {
    return new LocalOnlyIssue(
      id,
      "file/path",
      new TextRangeWithHash(1, 2, 3, 4, "ab12"),
      new LineWithHash(1, "linehash"),
      "ruleKey",
      "message",
      new LocalOnlyIssueResolution(IssueStatus.WONT_FIX, resolutionDate.truncatedTo(ChronoUnit.MILLIS), "comment")
    );
  }

}
