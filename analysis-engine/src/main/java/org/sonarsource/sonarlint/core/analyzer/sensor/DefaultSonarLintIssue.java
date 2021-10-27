/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analyzer.sensor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.PathUtils;
import org.sonarsource.sonarlint.core.analyzer.issue.DefaultQuickFix;
import org.sonarsource.sonarlint.core.client.api.common.QuickFix;
import org.sonarsource.sonarlint.core.client.api.common.QuickFixable;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputProject;
import org.sonarsource.sonarlint.plugin.api.issue.NewQuickFix;
import org.sonarsource.sonarlint.plugin.api.issue.NewSonarLintIssue;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

public class DefaultSonarLintIssue extends DefaultStorable implements Issue, NewIssue, NewSonarLintIssue, QuickFixable {

  private final SonarLintInputProject project;
  private final Path baseDir;
  protected DefaultSonarLintIssueLocation primaryLocation;
  protected List<List<IssueLocation>> flows = new ArrayList<>();
  private RuleKey ruleKey;
  private Severity overriddenSeverity;
  private final List<QuickFix> quickFixes;

  public DefaultSonarLintIssue(SonarLintInputProject project, Path baseDir, @Nullable SensorStorage storage) {
    super(storage);
    this.project = project;
    this.baseDir = baseDir;
    this.quickFixes = new ArrayList<>();
  }

  @Override
  public NewIssueLocation newLocation() {
    return new DefaultSonarLintIssueLocation();
  }

  @Override
  public DefaultSonarLintIssue forRule(RuleKey ruleKey) {
    this.ruleKey = ruleKey;
    return this;
  }

  @Override
  public RuleKey ruleKey() {
    return this.ruleKey;
  }

  @Override
  public DefaultSonarLintIssue gap(@Nullable Double gap) {
    // Gap not used in SonarLint
    return this;
  }

  @Override
  public DefaultSonarLintIssue overrideSeverity(@Nullable Severity severity) {
    this.overriddenSeverity = severity;
    return this;
  }

  @Override
  public Severity overriddenSeverity() {
    return this.overriddenSeverity;
  }

  @Override
  public Double gap() {
    throw new UnsupportedOperationException("No gap in SonarLint");
  }

  @Override
  public IssueLocation primaryLocation() {
    return primaryLocation;
  }

  @Override
  public List<Flow> flows() {
    return this.flows.stream()
      .<Flow>map(l -> () -> unmodifiableList(new ArrayList<>(l)))
      .collect(toList());
  }

  @Override
  public DefaultSonarLintIssue at(NewIssueLocation primaryLocation) {
    this.primaryLocation = rewriteLocation((DefaultSonarLintIssueLocation) primaryLocation);
    return this;
  }

  @Override
  public DefaultSonarLintIssue addLocation(NewIssueLocation secondaryLocation) {
    flows.add(Collections.singletonList(rewriteLocation((DefaultSonarLintIssueLocation) secondaryLocation)));
    return this;
  }

  @Override
  public DefaultSonarLintIssue addFlow(Iterable<NewIssueLocation> locations) {
    List<IssueLocation> flowAsList = new ArrayList<>();
    for (NewIssueLocation issueLocation : locations) {
      flowAsList.add(rewriteLocation((DefaultSonarLintIssueLocation) issueLocation));
    }
    flows.add(flowAsList);
    return this;
  }

  private DefaultSonarLintIssueLocation rewriteLocation(DefaultSonarLintIssueLocation location) {
    InputComponent component = location.inputComponent();
    Optional<Path> dirOrModulePath = Optional.empty();

    if (component instanceof InputDir) {
      InputDir dirComponent = (InputDir) component;
      dirOrModulePath = Optional.of(baseDir.relativize(dirComponent.path()));
    }

    if (dirOrModulePath.isPresent()) {
      String path = PathUtils.sanitize(dirOrModulePath.get().toString());
      DefaultSonarLintIssueLocation fixedLocation = new DefaultSonarLintIssueLocation();
      fixedLocation.on(project);
      StringBuilder fullMessage = new StringBuilder();
      if (!StringUtils.isBlank(path)) {
        fullMessage.append("[").append(path).append("] ");
      }
      fullMessage.append(location.message());
      fixedLocation.message(fullMessage.toString());
      return fixedLocation;
    } else {
      return location;
    }
  }

  @Override
  public void doSave() {
    requireNonNull(this.ruleKey, "ruleKey is mandatory on issue");
    storage.store(this);
  }

  @Override
  public NewQuickFix newQuickFix() {
    return new DefaultQuickFix();
  }

  @Override
  public NewSonarLintIssue addQuickFix(NewQuickFix newQuickFix) {
    quickFixes.add((DefaultQuickFix) newQuickFix);
    return this;
  }

  @Override
  public List<QuickFix> quickFixes() {
    return Collections.unmodifiableList(quickFixes);
  }
}
