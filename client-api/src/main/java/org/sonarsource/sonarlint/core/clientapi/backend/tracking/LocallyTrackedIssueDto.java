/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.backend.tracking;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class LocallyTrackedIssueDto {
  private final String key;
  private final TextRangeWithHashDto textRangeWithHash;
  private final LineWithHashDto lineWithHash;
  private final String ruleKey;
  private final String message;

  /**
   * @param key null when it's a first-time detected issue, else can be the key coming from the server issue or local tracking
   * @param textRangeWithHash null when it's a file-level issue
   * @param lineWithHash null when it's a file-level issue
   */
  public LocallyTrackedIssueDto(@Nullable String key, @Nullable TextRangeWithHashDto textRangeWithHash, @Nullable LineWithHashDto lineWithHash, String ruleKey, String message) {
    this.key = key;
    this.textRangeWithHash = textRangeWithHash;
    this.lineWithHash = lineWithHash;
    this.ruleKey = ruleKey;
    this.message = message;
  }

  @CheckForNull
  public String getKey() {
    return key;
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
}
