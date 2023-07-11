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
package org.sonarsource.sonarlint.core.tracking;

import java.util.UUID;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.LineWithHashDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TextRangeWithHashDto;

public class LocalOnlyIssue {
  private final UUID id;
  private final String serverRelativePath;
  private final TextRangeWithHashDto textRangeWithHash;
  private final LineWithHashDto lineWithHash;
  private final String ruleKey;
  private final String message;
  private final LocalOnlyIssueResolution resolution;

  /**
   * @param resolution is null when the issue is not resolved
   */
  public LocalOnlyIssue(UUID id, String serverRelativePath, TextRangeWithHashDto textRangeWithHash, LineWithHashDto lineWithHash, String ruleKey, String message,
    @Nullable LocalOnlyIssueResolution resolution) {
    this.id = id;
    this.serverRelativePath = serverRelativePath;
    this.textRangeWithHash = textRangeWithHash;
    this.lineWithHash = lineWithHash;
    this.ruleKey = ruleKey;
    this.message = message;
    this.resolution = resolution;
  }

  public UUID getId() {
    return id;
  }

  public String getServerRelativePath() {
    return serverRelativePath;
  }

  public TextRangeWithHashDto getTextRangeWithHash() {
    return textRangeWithHash;
  }

  public LineWithHashDto getLineWithHash() {
    return lineWithHash;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public String getMessage() {
    return message;
  }

  public LocalOnlyIssueResolution getResolution() {
    return resolution;
  }
}
