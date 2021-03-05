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
package org.sonarsource.sonarlint.core.analyzer.issue;

import javax.annotation.Nullable;
import org.sonar.api.batch.fs.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation;

public class DefaultLocation extends TextRangeLocation implements IssueLocation {
  private final String message;
  private final ClientInputFile inputFile;

  public DefaultLocation(@Nullable ClientInputFile inputFile, @Nullable TextRange textRange, @Nullable String message) {
    super(textRange);
    this.inputFile = inputFile;
    this.message = message;
  }

  public DefaultLocation(ClientInputFile inputFile, org.sonarsource.sonarlint.core.client.api.common.TextRange textRange, String message) {
    super(textRange);
    this.inputFile = inputFile;
    this.message = message;

  }

  @Override
  public ClientInputFile getInputFile() {
    return inputFile;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
