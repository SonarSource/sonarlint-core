/*
 * SonarLint Plugin API
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
package org.sonarsource.sonarlint.plugin.api.issue;

import org.sonar.api.batch.fs.InputFile;

/**
 * Describe a file edit for a {@link NewQuickFix} as a collection of {@link NewTextEdit}s on a given {@link InputFile}.
 * Text edits are applied in the order they are added, insofar that their ranges do not overlap.
 * @since 6.3
 * @deprecated use org.sonar.api.batch.sensor.issue.fix.NewInputFileEdit from the sonar-plugin-api instead
 */
@Deprecated(since = "8.12")
public interface NewInputFileEdit {

  /**
   * @param inputFile the input file on which to apply this edit
   * @return the modified edit
   */
  NewInputFileEdit on(InputFile inputFile);

  /**
   * Create a new text edit
   * @return a new uninitialized instance of a text edit for a given file edit
   */
  NewTextEdit newTextEdit();

  /**
   * Add a text edit to this input file edit
   * @param newTextEdit the text edit to add
   * @return this instance
   */
  NewInputFileEdit addTextEdit(NewTextEdit newTextEdit);
}
