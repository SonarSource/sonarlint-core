/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.batch.sensor.issue.IssueResolution;
import org.sonar.api.batch.sensor.issue.NewIssueResolution;
import org.sonar.api.rule.RuleKey;

import static java.util.Objects.requireNonNull;
import static org.sonar.api.utils.Preconditions.checkArgument;
import static org.sonar.api.utils.Preconditions.checkState;

public class DefaultSonarLintIssueResolution extends DefaultStorable implements NewIssueResolution, IssueResolution {

  private InputFile inputFile;
  private TextRange textRange;
  private Status status = Status.DEFAULT;
  private final Set<RuleKey> ruleKeys = new LinkedHashSet<>();
  private String comment;

  public DefaultSonarLintIssueResolution(SensorStorage storage) {
    super(storage);
  }

  @Override
  public DefaultSonarLintIssueResolution on(InputFile inputFile) {
    checkArgument(inputFile != null, "Cannot use an inputFile that is null");
    checkState(this.inputFile == null, "on() already called");
    this.inputFile = inputFile;
    return this;
  }

  @Override
  public DefaultSonarLintIssueResolution at(TextRange textRange) {
    checkArgument(textRange != null, "Cannot use a textRange that is null");
    checkState(this.textRange == null, "at() already called");
    this.textRange = textRange;
    return this;
  }

  @Override
  public DefaultSonarLintIssueResolution status(Status status) {
    this.status = requireNonNull(status, "status is mandatory on issue resolution");
    return this;
  }

  @Override
  public DefaultSonarLintIssueResolution forRules(Collection<RuleKey> ruleKeys) {
    this.ruleKeys.clear();
    this.ruleKeys.addAll(requireNonNull(ruleKeys, "ruleKeys is mandatory on issue resolution"));
    return this;
  }

  @Override
  public DefaultSonarLintIssueResolution comment(String comment) {
    this.comment = requireNonNull(comment, "comment is mandatory on issue resolution");
    return this;
  }

  @Override
  public InputFile inputFile() {
    return inputFile;
  }

  @Override
  public TextRange textRange() {
    return textRange;
  }

  @Override
  public Status status() {
    return status;
  }

  @Override
  public Set<RuleKey> ruleKeys() {
    return Set.copyOf(ruleKeys);
  }

  @Override
  public String comment() {
    return comment;
  }

  @Override
  protected void doSave() {
    requireNonNull(inputFile, "inputFile is mandatory on issue resolution");
    requireNonNull(textRange, "textRange is mandatory on issue resolution");
    requireNonNull(comment, "comment is mandatory on issue resolution");
    checkState(!ruleKeys.isEmpty(), "At least one rule key is required on issue resolution");
    storage.store(this);
  }
}
