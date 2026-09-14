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
package org.sonarsource.sonarlint.core.analysis.container.analysis.sensor;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.sensor.code.NewSignificantCode;
import org.sonar.api.batch.sensor.coverage.NewCoverage;
import org.sonar.api.batch.sensor.cpd.NewCpdTokens;
import org.sonar.api.batch.sensor.error.AnalysisError;
import org.sonar.api.batch.sensor.highlighting.NewHighlighting;
import org.sonar.api.batch.sensor.issue.ExternalIssue;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.measure.Measure;
import org.sonar.api.batch.sensor.rule.AdHocRule;
import org.sonar.api.batch.sensor.symbol.NewSymbolTable;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.container.analysis.IssueListenerHolder;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputProject;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.IssueFilters;
import org.sonarsource.sonarlint.core.analysis.sonarapi.DefaultSonarLintIssue;
import org.sonarsource.sonarlint.core.analysis.sonarapi.DefaultSonarLintIssueLocation;
import org.sonarsource.sonarlint.core.analysis.sonarapi.DefaultSonarLintIssueResolution;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import testutils.TestInputFileBuilder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SonarLintSensorStorageTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @Mock
  private ActiveRules activeRules;
  @Mock
  private IssueFilters filters;
  @Mock
  private IssueListenerHolder issueListener;
  @Mock
  private AnalysisResults analysisResult;
  @Mock
  private SonarLintInputFile inputFile;
  @Mock
  private ClientInputFile clientInputFile;
  @Mock
  private ActiveRule activeRule;

  private SonarLintSensorStorage underTest;
  private SonarLintInputFile analyzedFile;
  private final SonarLintInputProject project = new SonarLintInputProject();
  private final RuleKey ruleKey = RuleKey.of("repo", "rule");

  @BeforeEach
  void setUp() {
    underTest = new SonarLintSensorStorage(activeRules, filters, issueListener, analysisResult);
    analyzedFile = new TestInputFileBuilder("src/Foo.java")
      .initMetadata("Foo\nBar\nBaz\n")
      .build();
  }

  @Test
  void store_Measure_doesnt_interact_with_its_param() {
    var measure = mock(Measure.class);
    underTest.store(measure);
    verifyNoInteractions(measure);
  }

  @Test
  void store_ExternalIssue_doesnt_interact_with_its_param() {
    var externalIssue = mock(ExternalIssue.class);
    underTest.store(externalIssue);
    verifyNoInteractions(externalIssue);
  }

  @Test
  void store_DefaultSignificantCode_doesnt_interact_with_its_param() {
    var significantCode = mock(NewSignificantCode.class);
    underTest.store(significantCode);
    verifyNoInteractions(significantCode);
  }

  @Test
  void store_DefaultHighlighting_doesnt_interact_with_its_param() {
    var highlighting = mock(NewHighlighting.class);
    underTest.store(highlighting);
    verifyNoInteractions(highlighting);
  }

  @Test
  void store_DefaultCoverage_doesnt_interact_with_its_param() {
    var coverage = mock(NewCoverage.class);
    underTest.store(coverage);
    verifyNoInteractions(coverage);
  }

  @Test
  void store_DefaultCpdTokens_doesnt_interact_with_its_param() {
    var cpdTokens = mock(NewCpdTokens.class);
    underTest.store(cpdTokens);
    verifyNoInteractions(cpdTokens);
  }

  @Test
  void store_DefaultSymbolTable_doesnt_interact_with_its_param() {
    var symbolTable = mock(NewSymbolTable.class);
    underTest.store(symbolTable);
    verifyNoInteractions(symbolTable);
  }

  @Test
  void store_AdHocRule_doesnt_interact_with_its_param() {
    var adHocRule = mock(AdHocRule.class);
    underTest.store(adHocRule);
    verifyNoInteractions(adHocRule);
  }

  @Test
  void store_IssueResolution_skips_matching_issues() {
    when(activeRules.find(ruleKey)).thenReturn(activeRule);
    storeResolution(analyzedFile, 1, ruleKey);

    storeIssue(analyzedFile, 1, ruleKey);

    verify(issueListener, never()).handle(any());
  }

  @Test
  void store_IssueResolution_does_not_skip_issues_for_other_rules() {
    var otherRule = RuleKey.of("repo", "other");
    when(activeRules.find(otherRule)).thenReturn(activeRule);
    when(filters.accept(any(), any())).thenReturn(true);
    storeResolution(analyzedFile, 1, ruleKey);

    storeIssue(analyzedFile, 1, otherRule);

    verify(issueListener).handle(any());
  }

  @Test
  void store_IssueResolution_does_not_skip_issues_on_other_lines() {
    when(activeRules.find(ruleKey)).thenReturn(activeRule);
    when(filters.accept(any(), any())).thenReturn(true);
    storeResolution(analyzedFile, 1, ruleKey);

    storeIssue(analyzedFile, 2, ruleKey);

    verify(issueListener).handle(any());
  }

  @Test
  void store_should_throw_exception_for_non_sonarlint_issue() {
    var issue = mock(Issue.class);
    
    assertThatThrownBy(() -> underTest.store(issue))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Trying to store a non-SonarLint issue?");
  }

  @Test
  void store_AnalysisError_should_add_failed_analysis_file() {
    var analysisError = mock(AnalysisError.class);
    when(analysisError.inputFile()).thenReturn(inputFile);
    when(inputFile.getClientInputFile()).thenReturn(clientInputFile);
    
    underTest.store(analysisError);
    
    verify(analysisResult).addFailedAnalysisFile(clientInputFile);
  }

  private void storeResolution(SonarLintInputFile file, int line, RuleKey resolvedRule) {
    new DefaultSonarLintIssueResolution(underTest)
      .on(file)
      .at(file.selectLine(line))
      .forRules(List.of(resolvedRule))
      .comment("resolved")
      .save();
  }

  private void storeIssue(SonarLintInputFile file, int line, RuleKey issueRule) {
    var issue = new DefaultSonarLintIssue(project, Path.of("."), underTest)
      .at(new DefaultSonarLintIssueLocation()
        .on(file)
        .at(file.selectLine(line))
        .message("Wrong way!"))
      .forRule(issueRule);
    underTest.store(issue);
  }

}
