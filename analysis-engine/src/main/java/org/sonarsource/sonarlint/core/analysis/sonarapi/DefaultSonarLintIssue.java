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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.batch.sensor.issue.fix.QuickFix;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.PathUtils;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputProject;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.SensorQuickFix;
import org.sonarsource.sonarlint.plugin.api.issue.NewQuickFix;
import org.sonarsource.sonarlint.plugin.api.issue.NewSonarLintIssue;

import static java.util.Objects.requireNonNull;

public class DefaultSonarLintIssue extends DefaultStorable implements Issue, NewIssue, NewSonarLintIssue {

  private final SonarLintInputProject project;
  private final Path baseDir;
  protected DefaultSonarLintIssueLocation primaryLocation;
  protected List<Flow> flows = new ArrayList<>();
  private RuleKey ruleKey;
  private Severity overriddenSeverity;
  private final List<QuickFix> quickFixes;
  private Optional<String> ruleDescriptionContextKey = Optional.empty();

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
  public NewIssue setRuleDescriptionContextKey(@Nullable String ruleDescriptionContextKey) {
    this.ruleDescriptionContextKey = Optional.ofNullable(ruleDescriptionContextKey);
    return this;
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
    return this.flows;
  }

  @Override
  public DefaultSonarLintIssue at(NewIssueLocation primaryLocation) {
    this.primaryLocation = rewriteLocation((DefaultSonarLintIssueLocation) primaryLocation);
    return this;
  }

  @Override
  public NewIssue addLocation(NewIssueLocation secondaryLocation) {
    return addFlow(List.of(secondaryLocation));
  }

  @Override
  public NewIssue addFlow(Iterable<NewIssueLocation> locations) {
    return addFlow(locations, FlowType.UNDEFINED, null);
  }

  @Override
  public NewIssue addFlow(Iterable<NewIssueLocation> flowLocations, FlowType flowType, @Nullable String flowDescription) {
    List<IssueLocation> flowAsList = new ArrayList<>();
    for (NewIssueLocation issueLocation : flowLocations) {
      flowAsList.add(rewriteLocation((DefaultSonarLintIssueLocation) issueLocation));
    }
    flows.add(new DefaultFlow(flowAsList, flowDescription, flowType));
    return this;
  }

  private DefaultSonarLintIssueLocation rewriteLocation(DefaultSonarLintIssueLocation location) {
    var component = location.inputComponent();
    Optional<Path> dirOrModulePath = Optional.empty();

    if (component instanceof InputDir) {
      var dirComponent = (InputDir) component;
      dirOrModulePath = Optional.of(baseDir.relativize(dirComponent.path()));
    }

    if (dirOrModulePath.isPresent()) {
      var path = PathUtils.sanitize(dirOrModulePath.get().toString());
      var fixedLocation = new DefaultSonarLintIssueLocation();
      fixedLocation.on(project);
      var fullMessage = new StringBuilder();
      if (!StringUtils.isEmpty(path)) {
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
  public SensorQuickFix newQuickFix() {
    return new SensorQuickFix();
  }

  @Override
  public DefaultSonarLintIssue addQuickFix(NewQuickFix newQuickFix) {
    // legacy method from sonarlint-plugin-api, keep for backward compatibility and remove later
    quickFixes.add((QuickFix) newQuickFix);
    return this;
  }

  @Override
  public DefaultSonarLintIssue addQuickFix(org.sonar.api.batch.sensor.issue.fix.NewQuickFix newQuickFix) {
    quickFixes.add((QuickFix) newQuickFix);
    return this;
  }

  @Override
  public List<QuickFix> quickFixes() {
    return Collections.unmodifiableList(quickFixes);
  }

  @Override
  public NewIssue setQuickFixAvailable(boolean qfAvailable) {
    // not relevant in SonarLint
    return this;
  }

  @Override
  public boolean isQuickFixAvailable() {
    return !quickFixes.isEmpty();
  }

  @Override
  public Optional<String> ruleDescriptionContextKey() {
    return ruleDescriptionContextKey;
  }
}
