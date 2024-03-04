/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.api;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.TextRange;

public class DefaultLocation implements IssueLocation {
  private final String message;
  private final ClientInputFile inputFile;
  private final org.sonarsource.sonarlint.core.commons.TextRange textRange;

  public DefaultLocation(@Nullable ClientInputFile inputFile, @Nullable TextRange textRange, @Nullable String message) {
    this.textRange = textRange != null ? WithTextRange.convert(textRange) : null;
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

  @CheckForNull
  @Override
  public org.sonarsource.sonarlint.core.commons.TextRange getTextRange() {
    return textRange;
  }
}
