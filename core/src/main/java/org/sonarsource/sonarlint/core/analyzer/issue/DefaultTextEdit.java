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

import org.sonarsource.sonarlint.core.analysis.api.TextEdit;
import org.sonarsource.sonarlint.core.analysis.api.TextRange;
import org.sonarsource.sonarlint.plugin.api.issue.NewTextEdit;

public class DefaultTextEdit implements TextEdit, NewTextEdit {

  private TextRange range;

  private String newText;

  @Override
  public NewTextEdit at(org.sonar.api.batch.fs.TextRange range) {
    this.range = TextRangeUtils.convert(range);
    return this;
  }

  @Override
  public NewTextEdit withNewText(String newText) {
    this.newText = newText;
    return this;
  }

  @Override
  public TextRange range() {
    return range;
  }

  @Override
  public String newText() {
    return newText;
  }
}
