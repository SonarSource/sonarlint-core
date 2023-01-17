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
package org.sonarsource.sonarlint.core.analysis.container.analysis.sensor;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.sensor.code.NewSignificantCode;
import org.sonar.api.batch.sensor.coverage.NewCoverage;
import org.sonar.api.batch.sensor.cpd.NewCpdTokens;
import org.sonar.api.batch.sensor.error.AnalysisError;
import org.sonar.api.batch.sensor.highlighting.NewHighlighting;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.batch.sensor.issue.ExternalIssue;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.issue.Issue.Flow;
import org.sonar.api.batch.sensor.issue.fix.QuickFix;
import org.sonar.api.batch.sensor.measure.Measure;
import org.sonar.api.batch.sensor.rule.AdHocRule;
import org.sonar.api.batch.sensor.symbol.NewSymbolTable;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFileEdit;
import org.sonarsource.sonarlint.core.analysis.api.TextEdit;
import org.sonarsource.sonarlint.core.analysis.container.analysis.IssueListenerHolder;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.IssueFilters;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.TextRangeUtils;
import org.sonarsource.sonarlint.core.analysis.sonarapi.ActiveRuleAdapter;
import org.sonarsource.sonarlint.core.analysis.sonarapi.DefaultSonarLintIssue;

import static java.util.stream.Collectors.toList;

public class SonarLintSensorStorage implements SensorStorage {

  private final ActiveRules activeRules;
  private final IssueFilters filters;
  private final IssueListenerHolder issueListener;
  private final AnalysisResults analysisResult;

  public SonarLintSensorStorage(ActiveRules activeRules, IssueFilters filters, IssueListenerHolder issueListener, AnalysisResults analysisResult) {
    this.activeRules = activeRules;
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
    var sonarLintIssue = (DefaultSonarLintIssue) issue;
    var inputComponent = sonarLintIssue.primaryLocation().inputComponent();

    var activeRule = (ActiveRuleAdapter) activeRules.find(sonarLintIssue.ruleKey());
    if ((activeRule == null) || noSonar(inputComponent, sonarLintIssue)) {
      return;
    }

    var primaryMessage = sonarLintIssue.primaryLocation().message();
    var flows = mapFlows(sonarLintIssue.flows());
    var quickFixes = transform(sonarLintIssue.quickFixes());

    var newIssue = new org.sonarsource.sonarlint.core.analysis.api.Issue(activeRule, primaryMessage,
      issue.primaryLocation().textRange(),
      inputComponent.isFile() ? ((SonarLintInputFile) inputComponent).getClientInputFile() : null, flows, quickFixes, sonarLintIssue.ruleDescriptionContextKey());
    if (filters.accept(inputComponent, newIssue)) {
      issueListener.handle(newIssue);
    }
  }

  private static List<org.sonarsource.sonarlint.core.analysis.api.QuickFix> transform(List<QuickFix> quickFixes) {
    return quickFixes.stream().map(SonarLintSensorStorage::transform).collect(toList());
  }

  private static org.sonarsource.sonarlint.core.analysis.api.QuickFix transform(QuickFix qf) {
    return new org.sonarsource.sonarlint.core.analysis.api.QuickFix(
      qf.inputFileEdits().stream().map(edit -> new ClientInputFileEdit(
        ((SonarLintInputFile) edit.target()).getClientInputFile(),
        edit.textEdits().stream().map(textEdit -> new TextEdit(TextRangeUtils.convert(textEdit.range()), textEdit.newText())).collect(toList()))).collect(toList()),
      qf.message());
  }

  private static boolean noSonar(InputComponent inputComponent, Issue issue) {
    var textRange = issue.primaryLocation().textRange();
    return inputComponent.isFile()
      && textRange != null
      && ((SonarLintInputFile) inputComponent).hasNoSonarAt(textRange.start().line())
      && !StringUtils.containsIgnoreCase(issue.ruleKey().rule(), "nosonar");
  }

  private static List<org.sonarsource.sonarlint.core.analysis.api.Flow> mapFlows(List<Flow> flows) {
    return flows.stream()
      .map(f -> new org.sonarsource.sonarlint.core.analysis.api.Flow(new ArrayList<>(f.locations())))
      .filter(f -> !f.locations().isEmpty())
      .collect(toList());
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
    var clientInputFile = ((SonarLintInputFile) analysisError.inputFile()).getClientInputFile();
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
