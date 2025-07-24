/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2025 SonarSource SA
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.container.analysis.IssueListenerHolder;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.IssueFilters;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SonarLintSensorStorageTests {

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

  private SonarLintSensorStorage underTest;

  @BeforeEach
  void setUp() {
    underTest = new SonarLintSensorStorage(activeRules, filters, issueListener, analysisResult);
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

}
