/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.util.git;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class GitBlameReader {
  private static final String COMMITTER_MAIL = "committer-mail ";
  private static final String COMMITTER_TIME = "committer-time ";
  private static final String NOT_COMMITTED = "<not.committed.yet>";

  private final List<Instant> commitDates = new ArrayList<>();
  private boolean isCurrentLineCommitted;

  public void readLine(String line) {
    // committer-mail comes before committer-time
    if (line.startsWith(COMMITTER_MAIL)) {
      var committerEmail = line.substring(COMMITTER_MAIL.length());
      isCurrentLineCommitted = !committerEmail.equals(NOT_COMMITTED);
    } else if (line.startsWith(COMMITTER_TIME)) {
      commitDates.add(isCurrentLineCommitted ? Instant.ofEpochSecond(Long.parseLong(line.substring(COMMITTER_TIME.length()))).truncatedTo(ChronoUnit.SECONDS) : null);
    }
  }

  public BlameResult getResult() {
    return new BlameResult(commitDates);
  }
}
