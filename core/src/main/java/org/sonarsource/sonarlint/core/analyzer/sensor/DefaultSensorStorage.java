/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import com.google.common.base.Strings;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.Rule;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.batch.sensor.coverage.internal.DefaultCoverage;
import org.sonar.api.batch.sensor.highlighting.internal.DefaultHighlighting;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.measure.Measure;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.source.Symbol;
import org.sonar.api.utils.MessageException;
import org.sonarsource.sonarlint.core.analyzer.issue.IssueFilters;
import org.sonarsource.sonarlint.core.client.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.IssueListener;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputFile;

public class DefaultSensorStorage implements SensorStorage {

  private final class DefaultClientIssue implements org.sonarsource.sonarlint.core.client.api.Issue {
    private final String severity;
    private final ActiveRule activeRule;
    private final String primaryMessage;
    private final TextRange textRange;
    private final ClientInputFile clientInputFile;

    private DefaultClientIssue(String severity, ActiveRule activeRule, String primaryMessage, @Nullable TextRange textRange, @Nullable ClientInputFile clientInputFile) {
      this.severity = severity;
      this.activeRule = activeRule;
      this.primaryMessage = primaryMessage;
      this.textRange = textRange;
      this.clientInputFile = clientInputFile;
    }

    @Override
    public Integer getStartLineOffset() {
      return textRange != null ? textRange.start().lineOffset() : null;
    }

    @Override
    public Integer getStartLine() {
      return textRange != null ? textRange.start().line() : null;
    }

    @Override
    public String getSeverity() {
      return severity;
    }

    @Override
    public String getRuleName() {
      return getRuleName(activeRule.ruleKey());
    }

    @Override
    public String getRuleKey() {
      return activeRule.ruleKey().toString();
    }

    @Override
    public String getMessage() {
      return primaryMessage;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ClientInputFile getInputFile() {
      return clientInputFile;
    }

    @Override
    public Integer getEndLineOffset() {
      return textRange != null ? textRange.end().lineOffset() : null;
    }

    @Override
    public Integer getEndLine() {
      return textRange != null ? textRange.end().line() : null;
    }

    private String getRuleName(RuleKey ruleKey) {
      Rule rule = rules.find(ruleKey);
      return rule != null ? rule.name() : null;
    }
  }

  private final ActiveRules activeRules;
  private final Rules rules;
  private final IssueFilters filters;
  private final IssueListener issueListener;

  public DefaultSensorStorage(ActiveRules activeRules, Rules rules, IssueFilters filters, IssueListener issueListener) {
    this.activeRules = activeRules;
    this.rules = rules;
    this.filters = filters;
    this.issueListener = issueListener;
  }

  @Override
  public void store(Measure newMeasure) {
    // NO-OP
  }

  @Override
  public void store(Issue issue) {
    InputComponent inputComponent = issue.primaryLocation().inputComponent();

    Rule rule = validateRule(issue);
    final ActiveRule activeRule = activeRules.find(issue.ruleKey());
    if (activeRule == null) {
      // rule does not exist or is not enabled -> ignore the issue
      return;
    }

    final String primaryMessage = Strings.isNullOrEmpty(issue.primaryLocation().message()) ? rule.name() : issue.primaryLocation().message();
    org.sonar.api.batch.rule.Severity overriddenSeverity = issue.overriddenSeverity();
    final String severity = overriddenSeverity != null ? overriddenSeverity.name() : activeRule.severity();

    if (filters.accept(inputComponent.key(), issue)) {
      org.sonarsource.sonarlint.core.client.api.Issue newIssue = new DefaultClientIssue(severity, activeRule, primaryMessage, issue.primaryLocation().textRange(),
        inputComponent.isFile() ? ((SonarLintInputFile) inputComponent).getClientInputFile() : null);
      issueListener.handle(newIssue);
    }
  }

  private Rule validateRule(Issue issue) {
    RuleKey ruleKey = issue.ruleKey();
    Rule rule = rules.find(ruleKey);
    if (rule == null) {
      throw MessageException.of(String.format("The rule '%s' does not exist.", ruleKey));
    }
    if (Strings.isNullOrEmpty(rule.name()) && Strings.isNullOrEmpty(issue.primaryLocation().message())) {
      throw MessageException.of(String.format("The rule '%s' has no name and the related issue has no message.", ruleKey));
    }
    return rule;
  }

  @Override
  public void store(DefaultHighlighting highlighting) {
    // NO-OP
  }

  public void store(DefaultInputFile inputFile, Map<Symbol, Set<TextRange>> referencesBySymbol) {
    // NO-OP
  }

  @Override
  public void store(DefaultCoverage defaultCoverage) {
    // NO-OP
  }

}
