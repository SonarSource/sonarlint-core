/*
 * SonarLint Core - Implementation
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

import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.Rule;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.batch.sensor.code.NewSignificantCode;
import org.sonar.api.batch.sensor.coverage.NewCoverage;
import org.sonar.api.batch.sensor.cpd.NewCpdTokens;
import org.sonar.api.batch.sensor.error.AnalysisError;
import org.sonar.api.batch.sensor.highlighting.NewHighlighting;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.batch.sensor.issue.ExternalIssue;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.issue.Issue.Flow;
import org.sonar.api.batch.sensor.measure.Measure;
import org.sonar.api.batch.sensor.rule.AdHocRule;
import org.sonar.api.batch.sensor.symbol.NewSymbolTable;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.MessageException;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analyzer.issue.DefaultClientIssue;
import org.sonarsource.sonarlint.core.analyzer.issue.DefaultFlow;
import org.sonarsource.sonarlint.core.analyzer.issue.IssueFilters;
import org.sonarsource.sonarlint.core.client.api.common.QuickFix;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.container.analysis.SonarLintRule;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.container.model.DefaultAnalysisResult;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang.StringUtils.isEmpty;

public class SonarLintSensorStorage implements SensorStorage {

  private final ActiveRules activeRules;
  private final Rules rules;
  private final IssueFilters filters;
  private final IssueListener issueListener;
  private final DefaultAnalysisResult analysisResult;

  public SonarLintSensorStorage(ActiveRules activeRules, Rules rules, IssueFilters filters, IssueListener issueListener, DefaultAnalysisResult analysisResult) {
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
    if (!(issue instanceof DefaultSonarLintIssue)) {
      throw new IllegalArgumentException("Trying to store a non-SonarLint issue?");
    }
    DefaultSonarLintIssue sonarLintIssue = (DefaultSonarLintIssue) issue;
    InputComponent inputComponent = sonarLintIssue.primaryLocation().inputComponent();

    SonarLintRule rule = validateRule(sonarLintIssue);
    ActiveRule activeRule = activeRules.find(sonarLintIssue.ruleKey());
    if (activeRule == null) {
      // rule does not exist or is not enabled -> ignore the issue
      return;
    }

    if (noSonar(inputComponent, sonarLintIssue)) {
      return;
    }

    String primaryMessage = defaultIfEmpty(sonarLintIssue.primaryLocation().message(), rule.name());
    org.sonar.api.batch.rule.Severity overriddenSeverity = sonarLintIssue.overriddenSeverity();
    String severity = overriddenSeverity != null ? overriddenSeverity.name() : activeRule.severity();
    String type = rule.type().name();

    List<org.sonarsource.sonarlint.core.client.api.common.analysis.Issue.Flow> flows = mapFlows(sonarLintIssue.flows());
    List<QuickFix> quickFixes = sonarLintIssue.quickFixes();

    DefaultClientIssue newIssue = new DefaultClientIssue(severity, type, activeRule, rules.find(activeRule.ruleKey()), primaryMessage, issue.primaryLocation().textRange(),
      inputComponent.isFile() ? ((SonarLintInputFile) inputComponent).getClientInputFile() : null, flows, quickFixes);
    if (filters.accept(inputComponent, newIssue)) {
      issueListener.handle(newIssue);
    }
  }

  private static boolean noSonar(InputComponent inputComponent, Issue issue) {
    TextRange textRange = issue.primaryLocation().textRange();
    return inputComponent.isFile()
      && textRange != null
      && ((SonarLintInputFile) inputComponent).hasNoSonarAt(textRange.start().line())
      && !StringUtils.containsIgnoreCase(issue.ruleKey().rule(), "nosonar");
  }

  private static List<org.sonarsource.sonarlint.core.client.api.common.analysis.Issue.Flow> mapFlows(List<Flow> flows) {
    return flows.stream()
      .map(f -> new DefaultFlow(f.locations()
        .stream()
        .collect(toList())))
      .filter(f -> !f.locations().isEmpty())
      .collect(toList());
  }

  private SonarLintRule validateRule(Issue issue) {
    RuleKey ruleKey = issue.ruleKey();
    Rule rule = rules.find(ruleKey);
    if (rule == null) {
      throw MessageException.of(String.format("The rule '%s' does not exist.", ruleKey));
    }
    if (isEmpty(rule.name()) && isEmpty(issue.primaryLocation().message())) {
      throw MessageException.of(String.format("The rule '%s' has no name and the related issue has no message.", ruleKey));
    }
    return (SonarLintRule) rule;
  }

  @Override
  public void store(NewHighlighting highlighting) {
    // NO-OP
  }

  @Override
  public void store(NewCoverage defaultCoverage) {
    // NO-OP
  }

  @Override
  public void store(NewCpdTokens defaultCpdTokens) {
    // NO-OP
  }

  @Override
  public void store(NewSymbolTable symbolTable) {
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
  public void store(NewSignificantCode significantCode) {
    // NO-OP
  }

  @Override
  public void store(AdHocRule adHocRule) {
    // NO-OP
  }

}
