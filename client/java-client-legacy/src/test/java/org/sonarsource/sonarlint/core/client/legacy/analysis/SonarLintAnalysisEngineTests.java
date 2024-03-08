/*
 * SonarLint Core - Java Client Legacy
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.legacy.analysis;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.analysis.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.command.Command;
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.api.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.ActiveRuleDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetAnalysisConfigResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetGlobalConfigurationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetRuleDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarLintAnalysisEngineTests {


  @TempDir
  private Path basedir;
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  private static final String CONNECTION_ID = "connectionId";
  boolean issueWasReported = false;
  GetAnalysisConfigResponse getAnalysisConfigResponse;
  GetGlobalConfigurationResponse getGlobalConfigurationResponse;
  AnalysisRpcService analysisRpcService;
  SonarLintAnalysisEngine underTest;
  SonarLintRpcServer backend;
  AnalysisEngine analysisEngine;

  @BeforeEach
  void init() {
    var engineConfiguration = mock(EngineConfiguration.class);
    var logOutput = mock(ClientLogOutput.class);
    when(engineConfiguration.getLogOutput()).thenReturn(logOutput);
    when(engineConfiguration.getWorkDir()).thenReturn(basedir.resolve("workDir"));
    backend = mock(SonarLintRpcServer.class);
    analysisRpcService = mock(AnalysisRpcService.class);
    getGlobalConfigurationResponse = mock(GetGlobalConfigurationResponse.class);
    when(getGlobalConfigurationResponse.getEnabledLanguages()).thenReturn(List.of(Language.JAVA));
    getAnalysisConfigResponse = mock(GetAnalysisConfigResponse.class);
    when(getAnalysisConfigResponse.getActiveRules())
      .thenReturn(List.of(new ActiveRuleDto("java:S1481",
        "java", Map.of(), "java")));
    when(analysisRpcService.getGlobalConnectedConfiguration(any()))
      .thenReturn(CompletableFuture.completedFuture(getGlobalConfigurationResponse));
    when(analysisRpcService.getAnalysisConfig(any()))
      .thenReturn(CompletableFuture.completedFuture(getAnalysisConfigResponse));
    when(analysisRpcService.getRuleDetails(any()))
      .thenReturn(CompletableFuture.completedFuture(new GetRuleDetailsResponse(IssueSeverity.BLOCKER,
        RuleType.BUG, CleanCodeAttribute.CLEAR, List.of(), VulnerabilityProbability.HIGH)));
    when(backend.getAnalysisService()).thenReturn(analysisRpcService);
    underTest = new SonarLintAnalysisEngine(engineConfiguration, backend, CONNECTION_ID);
    analysisEngine = mock(AnalysisEngine.class);
    when(analysisEngine.post(any(), any()))
      .thenReturn(CompletableFuture.completedFuture(new AnalysisResults()));
    underTest.analysisEngine.set(analysisEngine);
  }

  @Test
  void shouldSkipIssueReportingIfRuleWasDisabledDuringAnalysis() throws IOException {
    var configScopeId = "configScopeId";
    var analysisConfiguration = mock(AnalysisConfiguration.class);
    var logOutput = mock(ClientLogOutput.class);
    var progressMonitor = mock(ClientProgressMonitor.class);
    when(analysisConfiguration.baseDir()).thenReturn(basedir);
    var file = mock(ClientInputFile.class);
    when(file.isTest()).thenReturn(false);
    when(file.uri()).thenReturn(basedir.resolve("workDir").resolve("FileUri.java").toUri());
    when(file.contents()).thenReturn("package devoxx;\n" +
      "\n" +
      "public class FileUri {\n" +
      "  public static void main(String[] args) {\n" +
      "    int i = 0;\n" +
      "  }\n" +
      "}");
    when(file.relativePath()).thenReturn("FileUri.java");
    when(analysisConfiguration.inputFiles())
      .thenReturn(List.of(file));
    var issue = mock(org.sonarsource.sonarlint.core.analysis.api.Issue.class);
    when(issue.getRuleKey()).thenReturn("java:S1481");
    when(issue.getMessage()).thenReturn("message");
    when(issue.getTextRange()).thenReturn(mock(TextRange.class));
    when(issue.flows()).thenReturn(List.of());
    var captor = ArgumentCaptor.forClass(Command.class);

    underTest.analyze(analysisConfiguration, rawIssue -> issueWasReported = true, logOutput, progressMonitor, configScopeId);
    verify(analysisEngine).post(captor.capture(), any());
    ((AnalyzeCommand) captor.getValue()).getIssueListener().accept(issue);

    assertTrue(issueWasReported);
  }

  @Test
  void shouldSkipIssueReportingIfRuleWasDisabledDuringAnalysisFoo() throws IOException {
    var configScopeId = "configScopeId";
    var analysisConfiguration = mock(AnalysisConfiguration.class);
    var logOutput = mock(ClientLogOutput.class);
    var progressMonitor = mock(ClientProgressMonitor.class);
    when(analysisConfiguration.baseDir()).thenReturn(basedir);
    var file = mock(ClientInputFile.class);
    when(file.isTest()).thenReturn(false);
    when(file.uri()).thenReturn(basedir.resolve("workDir").resolve("FileUri.java").toUri());
    when(file.contents()).thenReturn("package devoxx;\n" +
      "\n" +
      "public class FileUri {\n" +
      "  public static void main(String[] args) {\n" +
      "    int i = 0;\n" +
      "  }\n" +
      "}");
    when(file.relativePath()).thenReturn("FileUri.java");
    when(analysisConfiguration.inputFiles())
      .thenReturn(List.of(file));
    var issue = mock(org.sonarsource.sonarlint.core.analysis.api.Issue.class);
    when(issue.getRuleKey()).thenReturn("java:S1481");
    when(issue.getMessage()).thenReturn("message");
    when(issue.getTextRange()).thenReturn(mock(TextRange.class));
    when(issue.flows()).thenReturn(List.of());
    when(analysisRpcService.getRuleDetails(any()))
      .thenThrow(new RuntimeException());
    var captor = ArgumentCaptor.forClass(Command.class);

    underTest.analyze(analysisConfiguration, rawIssue -> issueWasReported = true, logOutput, progressMonitor, configScopeId);
    verify(analysisEngine).post(captor.capture(), any());
    ((AnalyzeCommand) captor.getValue()).getIssueListener().accept(issue);

    assertFalse(issueWasReported);
  }
}
