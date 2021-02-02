/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.model;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analyzer.issue.TextRangeLocation;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssueLocation;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue.TextRange;

public class DefaultServerLocation extends TextRangeLocation implements ServerIssueLocation {
  private final String message;
  private final String filePath;
  private final String codeSnippet;

  public DefaultServerLocation(@Nullable String filePath, @Nullable TextRange textRange, @Nullable String message, @Nullable String codeSnippet) {
    super(textRange);
    this.filePath = filePath;
    this.message = message;
    this.codeSnippet = codeSnippet;
  }

  @Override
  public String getFilePath() {
    return filePath;
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public String getCodeSnippet() {
    return codeSnippet;
  }
}
