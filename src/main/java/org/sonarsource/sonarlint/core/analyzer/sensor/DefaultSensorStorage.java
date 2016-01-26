/*
 * SonarLint Core Library
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
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputFile;
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
import org.sonarsource.sonarlint.core.IssueListener;
import org.sonarsource.sonarlint.core.analyzer.issue.IssueFilters;
import org.sonarsource.sonarlint.core.index.BatchComponent;
import org.sonarsource.sonarlint.core.index.BatchComponentCache;

public class DefaultSensorStorage implements SensorStorage {

  private final BatchComponentCache componentCache;
  private final ActiveRules activeRules;
  private final Rules rules;
  private final IssueFilters filters;
  private final IssueListener issueListener;

  public DefaultSensorStorage(BatchComponentCache componentCache, ActiveRules activeRules, Rules rules, IssueFilters filters, IssueListener issueListener) {
    this.componentCache = componentCache;
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
    BatchComponent component = componentCache.get(inputComponent);

    Rule rule = validateRule(issue);
    ActiveRule activeRule = activeRules.find(issue.ruleKey());
    if (activeRule == null) {
      // rule does not exist or is not enabled -> ignore the issue
      return;
    }

    String primaryMessage = Strings.isNullOrEmpty(issue.primaryLocation().message()) ? rule.name() : issue.primaryLocation().message();
    org.sonar.api.batch.rule.Severity overriddenSeverity = issue.overriddenSeverity();
    String severity = overriddenSeverity != null ? overriddenSeverity.name() : activeRule.severity();

    if (filters.accept(inputComponent.key(), issue)) {
      IssueListener.Issue newIssue = new IssueListener.Issue();
      if (inputComponent.isFile()) {
        newIssue.setFilePath(((InputFile) inputComponent).path());
      }
      newIssue.setMessage(primaryMessage);
      newIssue.setRuleKey(activeRule.ruleKey().toString());
      newIssue.setRuleName(getRuleName(activeRule.ruleKey()));
      newIssue.setSeverity(severity);
      if (inputComponent.isFile()) {
        TextRange textRange = issue.primaryLocation().textRange();
        if (textRange != null) {
          newIssue.setStartLine(textRange.start().line());
          newIssue.setStartLineOffset(textRange.start().lineOffset());
          newIssue.setEndLine(textRange.end().line());
          newIssue.setEndLineOffset(textRange.end().lineOffset());
        }
      }
      issueListener.handle(newIssue);
    }
  }

  private String getRuleName(RuleKey ruleKey) {
    Rule rule = rules.find(ruleKey);
    return rule != null ? rule.name() : null;
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
