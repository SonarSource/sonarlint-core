/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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

import com.google.common.base.Strings;
import java.util.List;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.Rule;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.batch.rule.internal.DefaultRule;
import org.sonar.api.batch.sensor.code.internal.DefaultSignificantCode;
import org.sonar.api.batch.sensor.coverage.internal.DefaultCoverage;
import org.sonar.api.batch.sensor.cpd.internal.DefaultCpdTokens;
import org.sonar.api.batch.sensor.error.AnalysisError;
import org.sonar.api.batch.sensor.highlighting.internal.DefaultHighlighting;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.batch.sensor.issue.ExternalIssue;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.issue.Issue.Flow;
import org.sonar.api.batch.sensor.measure.Measure;
import org.sonar.api.batch.sensor.symbol.internal.DefaultSymbolTable;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.MessageException;
import org.sonarsource.sonarlint.core.analyzer.issue.DefaultClientIssue;
import org.sonarsource.sonarlint.core.analyzer.issue.DefaultFlow;
import org.sonarsource.sonarlint.core.analyzer.issue.IssueFilters;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.container.model.DefaultAnalysisResult;

import static java.util.stream.Collectors.toList;

public class DefaultSensorStorage implements SensorStorage {

  private final ActiveRules activeRules;
  private final Rules rules;
  private final IssueFilters filters;
  private final IssueListener issueListener;
  private final DefaultAnalysisResult analysisResult;

  public DefaultSensorStorage(ActiveRules activeRules, Rules rules, IssueFilters filters, IssueListener issueListener, DefaultAnalysisResult analysisResult) {
    this.activeRules = activeRules;
    this.rules = rules;
    this.filters = filters;
    this.issueListener = issueListener;
    this.analysisResult = analysisResult;
  }

  @Override
  public void store(Measure newMeasure) {
    // NO-OP
  }

  @Override
  public void store(Issue issue) {
    InputComponent inputComponent = issue.primaryLocation().inputComponent();

    DefaultRule rule = validateRule(issue);
    ActiveRule activeRule = activeRules.find(issue.ruleKey());
    if (activeRule == null) {
      // rule does not exist or is not enabled -> ignore the issue
      return;
    }

    String primaryMessage = Strings.isNullOrEmpty(issue.primaryLocation().message()) ? rule.name() : issue.primaryLocation().message();
    org.sonar.api.batch.rule.Severity overriddenSeverity = issue.overriddenSeverity();
    String severity = overriddenSeverity != null ? overriddenSeverity.name() : activeRule.severity();
    String type = rule.type();

    List<org.sonarsource.sonarlint.core.client.api.common.analysis.Issue.Flow> flows = mapFlows(issue.flows());

    DefaultClientIssue newIssue = new DefaultClientIssue(severity, type, activeRule, rules.find(activeRule.ruleKey()), primaryMessage, issue.primaryLocation().textRange(),
      inputComponent.isFile() ? ((SonarLintInputFile) inputComponent).getClientInputFile() : null, flows);
    if (filters.accept(inputComponent, newIssue)) {
      issueListener.handle(newIssue);
    }
  }

  private static List<org.sonarsource.sonarlint.core.client.api.common.analysis.Issue.Flow> mapFlows(List<Flow> flows) {
    return flows.stream()
      .map(f -> new DefaultFlow(f.locations()
        .stream()
        .collect(toList())))
      .filter(f -> !f.locations().isEmpty())
      .collect(toList());
  }

  private DefaultRule validateRule(Issue issue) {
    RuleKey ruleKey = issue.ruleKey();
    Rule rule = rules.find(ruleKey);
    if (rule == null) {
      throw MessageException.of(String.format("The rule '%s' does not exist.", ruleKey));
    }
    if (Strings.isNullOrEmpty(rule.name()) && Strings.isNullOrEmpty(issue.primaryLocation().message())) {
      throw MessageException.of(String.format("The rule '%s' has no name and the related issue has no message.", ruleKey));
    }
    return (DefaultRule) rule;
  }

  @Override
  public void store(DefaultHighlighting highlighting) {
    // NO-OP
  }

  @Override
  public void store(DefaultCoverage defaultCoverage) {
    // NO-OP
  }

  @Override
  public void store(DefaultCpdTokens defaultCpdTokens) {
    // NO-OP
  }

  @Override
  public void store(DefaultSymbolTable symbolTable) {
    // NO-OP
  }

  @Override
  public void store(AnalysisError analysisError) {
    ClientInputFile clientInputFile = ((SonarLintInputFile) analysisError.inputFile()).getClientInputFile();
    analysisResult.addFailedAnalysisFile(clientInputFile);
  }

  @Override
  public void storeProperty(String key, String value) {
    // NO-OP
  }

  @Override
  public void store(ExternalIssue issue) {
    // NO-OP
  }

  @Override
  public void store(DefaultSignificantCode significantCode) {
    // NO-OP
  }

}
