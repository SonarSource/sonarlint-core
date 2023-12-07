/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons;

import java.time.Instant;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class LocalOnlyIssue {
  private final UUID id;
  private final String serverRelativePath;
  private final TextRangeWithHash textRangeWithHash;
  private final LineWithHash lineWithHash;
  private final String ruleKey;
  private final String message;
  private LocalOnlyIssueResolution resolution;

  /**
   * @param resolution is null when the issue is not resolved
   */
  public LocalOnlyIssue(UUID id, String serverRelativePath, @Nullable TextRangeWithHash textRangeWithHash, @Nullable LineWithHash lineWithHash, String ruleKey,
    String message, @Nullable LocalOnlyIssueResolution resolution) {
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

  @CheckForNull
  public TextRangeWithHash getTextRangeWithHash() {
    return textRangeWithHash;
  }

  @CheckForNull
  public LineWithHash getLineWithHash() {
    return lineWithHash;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public String getMessage() {
    return message;
  }

  @CheckForNull
  public LocalOnlyIssueResolution getResolution() {
    return resolution;
  }

  public void resolve(IssueStatus newStatus) {
    resolution = new LocalOnlyIssueResolution(newStatus, Instant.now(), null);
  }

}
