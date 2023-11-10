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
 * Extension interface to add {@link NewQuickFix}es to a {@link org.sonar.api.batch.sensor.issue.NewIssue}
 * @since 6.3
 * @deprecated use org.sonar.api.batch.sensor.issue.NewIssue from the sonar-plugin-api instead
 */
@Deprecated(since = "8.12")
public interface NewSonarLintIssue {

  /**
   * Create a new quick fix
   * @return a new uninitialized instance of a quick fix for a given issue
   */
  NewQuickFix newQuickFix();

  /**
   * Add a new quick fix to this issue
   * @param newQuickFix the quick fix to add
   * @return this object
   */
  NewSonarLintIssue addQuickFix(NewQuickFix newQuickFix);
}
