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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.internal.DefaultInputDir;
import org.sonar.api.batch.fs.internal.DefaultInputModule;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.internal.DefaultStorable;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.PathUtils;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputProject;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

public class DefaultSonarLintIssue extends DefaultStorable implements Issue, NewIssue {

  private final SonarLintInputProject project;
  private final Path baseDir;
  protected DefaultSonarLintIssueLocation primaryLocation;
  protected List<List<IssueLocation>> flows = new ArrayList<>();
  private RuleKey ruleKey;
  private Severity overriddenSeverity;

  public DefaultSonarLintIssue(SonarLintInputProject project, Path baseDir, @Nullable SensorStorage storage) {
    super(storage);
    this.project = project;
    this.baseDir = baseDir;
  }

  @Override
  public NewIssueLocation newLocation() {
    return new DefaultSonarLintIssueLocation();
  }

  public DefaultSonarLintIssue forRule(RuleKey ruleKey) {
    this.ruleKey = ruleKey;
    return this;
  }

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
    Preconditions.checkArgument(primaryLocation != null, "Cannot use a location that is null");
    checkState(this.primaryLocation == null, "at() already called");
    this.primaryLocation = rewriteLocation((DefaultSonarLintIssueLocation) primaryLocation);
    Preconditions.checkArgument(this.primaryLocation.inputComponent() != null, "Cannot use a location with no input component");
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

    if (component instanceof DefaultInputDir) {
      DefaultInputDir dirComponent = (DefaultInputDir) component;
      dirOrModulePath = Optional.of(baseDir.relativize(dirComponent.path()));
    } else if (component instanceof DefaultInputModule && !Objects.equals(project.key(), component.key())) {
      DefaultInputModule moduleComponent = (DefaultInputModule) component;
      dirOrModulePath = Optional.of(baseDir.relativize(moduleComponent.getBaseDir()));
    }

    if (dirOrModulePath.isPresent()) {
      String path = PathUtils.sanitize(dirOrModulePath.get().toString());
      DefaultSonarLintIssueLocation fixedLocation = new DefaultSonarLintIssueLocation();
      fixedLocation.on(project);
      StringBuilder fullMessage = new StringBuilder();
      if (!isNullOrEmpty(path)) {
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
    checkState(primaryLocation != null, "Primary location is mandatory on every issue");
    storage.store(this);
  }

}
