/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarsource.sonarlint.core.analyzer.sensor;

import com.google.common.base.Preconditions;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.sensor.issue.internal.DefaultIssueLocation;

/**
 * Override default implementation to not cast as DefaultInputFile
 */
public class DefaultSonarLintIssueLocation extends DefaultIssueLocation {

  private TextRange textRange;

  @Override
  public DefaultSonarLintIssueLocation at(TextRange location) {
    Preconditions.checkState(this.inputComponent() != null, "at() should be called after on()");
    Preconditions.checkState(this.inputComponent().isFile(), "at() should be called only for an InputFile.");
    this.textRange = location;
    return this;
  }

  @Override
  public TextRange textRange() {
    return textRange;
  }

}
