/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2020 SonarSource SA
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
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;

import static java.util.Objects.requireNonNull;

public class DefaultSonarLintIssueLocation implements NewIssueLocation, IssueLocation {

  private InputComponent component;
  private TextRange textRange;
  private String message;

  @Override
  public DefaultSonarLintIssueLocation on(InputComponent component) {
    requireNonNull(component, "Component can't be null");
    this.component = component;
    return this;
  }

  @Override
  public DefaultSonarLintIssueLocation at(TextRange location) {
    Preconditions.checkState(this.inputComponent() != null, "at() should be called after on()");
    Preconditions.checkState(this.inputComponent().isFile(), "at() should be called only for an InputFile.");
    this.textRange = location;
    return this;
  }

  @Override
  public DefaultSonarLintIssueLocation message(String message) {
    this.message = message;
    return this;
  }

  @Override
  public InputComponent inputComponent() {
    return this.component;
  }

  @Override
  public TextRange textRange() {
    return textRange;
  }

  @Override
  public String message() {
    return this.message;
  }

}
