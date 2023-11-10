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

/**
 * Describe a quick fix for a {@link NewSonarLintIssue}, with a description and a collection of {@link NewInputFileEdit}.
 * Input file edits will be applied in the order they are added, insofar that they are compatible with one another.
 * @since 6.3
 * @deprecated use org.sonar.api.batch.sensor.issue.fix.NewQuickFix from the sonar-plugin-api instead
 */
@Deprecated(since = "8.12")
public interface NewQuickFix {

  /**
   * Define the message for this quick fix, which will be shown to the user as an action item.
   * The fix message may be inspired by the issue message, but the context into which they appear is different,
   * so it might be better to adapt it. A good message should:
   * <ul>
   *   <li>Be short (ideally, not more than 50 characters)</li>
   *   <li>Use sentence capitalization</li>
   *   <li><em>Not</em> end with a full stop (<code>.</code>)</li>
   *   <li>Describe the expected outcome of the change, e.g. <i>Make the constructor explicit</i> instead of <i>Add the "explicit" keyword</i>.
   *   It tells the user how to fix the issue</li>
   *   <li>Focus on the target more than the current situation. For instance, <i>Replace "AAA" with "BBB"</i> would be better phrased <i>Replace with "BBB"</i></li>
   *   <li>Avoid the use of a demonstrative, e.g. <i>this</i>. Prefer the more neutral <i>the</i>.
   *   The message may be used in several contexts, some of which would not work very well with a demonstrative</li>
   * </ul>
   * @param message a description for this quick fix
   * @return the updated quickfix
   */
  NewQuickFix message(String message);

  /**
   * Create a new input file edit
   * @return a new uninitialized instance of a file edit for a given fix
   */
  NewInputFileEdit newInputFileEdit();

  /**
   * Add a new input file edit to this quick fix
   * @param newInputFileEdit the input file edit to add
   * @return this instance
   */
  NewQuickFix addInputFileEdit(NewInputFileEdit newInputFileEdit);
}
