/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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

/**
 * Issues with line level precision (from old /batch/issues WS, in SQ < 9.5 and SC)
 */
public class LineLevelServerIssue extends ServerIssue<LineLevelServerIssue> {
  private int line;
  private String lineHash;

  public LineLevelServerIssue(String key, boolean resolved, String ruleKey, String message, String lineHash, String filePath, Instant creationDate, @Nullable String userSeverity,
    String type, int line) {
    super(key, resolved, ruleKey, message, filePath, creationDate, userSeverity, type);
    this.lineHash = lineHash;
    this.line = line;
  }

  public String getLineHash() {
    return lineHash;
  }

  public Integer getLine() {
    return line;
  }

  public LineLevelServerIssue setLineHash(String lineHash) {
    this.lineHash = lineHash;
    return this;
  }

  public LineLevelServerIssue setLine(int line) {
    this.line = line;
    return this;
  }

}
