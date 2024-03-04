/*
 * SonarLint Plugin API
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
package org.sonarsource.sonarlint.plugin.api.issue;

import org.sonar.api.batch.fs.TextRange;

/**
 * Describe a text edit for a {@link NewInputFileEdit} as a replacement text for a given {@link TextRange}
 * @since 6.3
 */
public interface NewTextEdit {

  /**
   * @param range the range on which to apply this edit
   * @return the modified edit
   */
  NewTextEdit at(TextRange range);

  /**
   * Prior to 6.4, line returns had to be represented with the '\n' character.
   * From 6.4 on, analyzers can use any EOL character they see fit, SonarLint takes care of adapting this to the one
   * expected by the IDE.
   * @param newText the replacement text.
   * @return the modified edit
   */
  NewTextEdit withNewText(String newText);
}
