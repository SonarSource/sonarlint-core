/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.embedded.server;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.analysis.AnalysisResult;
import org.sonarsource.sonarlint.core.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.analysis.RawIssue;
import org.sonarsource.sonarlint.core.analysis.api.TriggerType;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class AnalyzeListFilesRequestHandlerTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester(true);
  private AnalyzeListFilesRequestHandler analyzeListFilesRequestHandler;
  private AnalysisService analysisService;
  private ClientFileSystemService clientFileSystemService;

  @BeforeEach
  void setup() {
    analysisService = mock(AnalysisService.class);
    clientFileSystemService = mock(ClientFileSystemService.class);
    analyzeListFilesRequestHandler = spy(new AnalyzeListFilesRequestHandler(analysisService, clientFileSystemService));
  }

  @Test
  void should_reject_non_post_requests() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest(Method.GET, "/analyze");
    var response = new BasicClassicHttpResponse(200);
    var context = mock(HttpContext.class);

    analyzeListFilesRequestHandler.handle(request, response, context);

    assertThat(response.getCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  void should_reject_invalid_json_request() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest(Method.POST, "/analyze");
    request.setEntity(new StringEntity("invalid json", StandardCharsets.UTF_8));
    var response = new BasicClassicHttpResponse(200);
    var context = mock(HttpContext.class);

    analyzeListFilesRequestHandler.handle(request, response, context);

    assertThat(response.getCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
    assertThat(logTester.logs()).contains("Failed to parse MCP analysis request");
  }

  @Test
  void should_reject_null_request_body() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest(Method.POST, "/analyze");
    request.setEntity(new StringEntity("null", StandardCharsets.UTF_8));
    var response = new BasicClassicHttpResponse(200);
    var context = mock(HttpContext.class);

    analyzeListFilesRequestHandler.handle(request, response, context);

    assertThat(response.getCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  void should_reject_empty_file_list() throws HttpException, IOException {
    var requestJson = new Gson().toJson(new AnalyzeListFilesRequestHandler.AnalysisRequest(Collections.emptyList()));
    var request = new BasicClassicHttpRequest(Method.POST, "/analyze");
    request.setEntity(new StringEntity(requestJson, StandardCharsets.UTF_8));
    var response = new BasicClassicHttpResponse(200);
    var context = mock(HttpContext.class);

    analyzeListFilesRequestHandler.handle(request, response, context);

    assertThat(response.getCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  void should_successfully_analyze_files_with_single_config_scope() throws HttpException, IOException {
    var analysisRequest = new AnalyzeListFilesRequestHandler.AnalysisRequest(List.of("/path/to/file1.java", "/path/to/file2.java"));
    var requestJson = new Gson().toJson(analysisRequest);
    var request = new BasicClassicHttpRequest(Method.POST, "/analyze");
    request.setEntity(new StringEntity(requestJson, StandardCharsets.UTF_8));
    var response = new BasicClassicHttpResponse(200);
    var context = mock(HttpContext.class);
    var configScopeId = "scope1";
    var fileUris = Set.of(
      URI.create("file:///path/to/file1.java"),
      URI.create("file:///path/to/file2.java")
    );
    var filesByScope = Map.of(configScopeId, fileUris);
    when(clientFileSystemService.groupFilesByConfigScope(anySet())).thenReturn(filesByScope);
    var mockIssue = createMockRawIssue("java:S123", "Test message", IssueSeverity.MAJOR,
      URI.create("file:///path/to/file1.java"), new TextRange(1, 0, 1, 10));
    var scanResults = mock(AnalysisResult.class);
    when(scanResults.rawIssues()).thenReturn(List.of(mockIssue));
    when(analysisService.scheduleAnalysis(anyString(), any(UUID.class), anySet(), anyMap(), 
      anyBoolean(), eq(TriggerType.FORCED), any())).thenReturn(CompletableFuture.completedFuture(scanResults));

    analyzeListFilesRequestHandler.handle(request, response, context);

    assertThat(response.getCode()).isEqualTo(200);
    assertThat(response.getEntity()).isNotNull();
    var responseContent = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
    var analysisResult = new Gson().fromJson(responseContent, AnalyzeListFilesRequestHandler.AnalysisResult.class);
    assertThat(analysisResult.issues()).hasSize(1);
    var issue = analysisResult.issues().get(0);
    assertThat(issue.ruleKey()).isEqualTo("java:S123");
    assertThat(issue.message()).isEqualTo("Test message");
    assertThat(issue.severity()).isEqualTo("MAJOR");
    assertThat(issue.filePath()).isEqualTo("/path/to/file1.java");
    assertThat(issue.textRange()).isEqualTo(new TextRange(1, 0, 1, 10));
  }

  @Test
  void should_successfully_analyze_files_with_multiple_config_scopes() throws HttpException, IOException {
    var analysisRequest = new AnalyzeListFilesRequestHandler.AnalysisRequest(List.of("/scope1/file1.java", "/scope2/file2.java", "/scope1/file3.java"));
    var requestJson = new Gson().toJson(analysisRequest);
    var request = new BasicClassicHttpRequest(Method.POST, "/analyze");
    request.setEntity(new StringEntity(requestJson, StandardCharsets.UTF_8));
    var response = new BasicClassicHttpResponse(200);
    var context = mock(HttpContext.class);
    var filesByScope = Map.of(
      "scope1", Set.of(URI.create("file:///scope1/file1.java"), URI.create("file:///scope1/file3.java")),
      "scope2", Set.of(URI.create("file:///scope2/file2.java"))
    );
    when(clientFileSystemService.groupFilesByConfigScope(anySet())).thenReturn(filesByScope);
    var mockIssue1 = createMockRawIssue("java:S123", "Issue in scope1", IssueSeverity.MAJOR,
      URI.create("file:///scope1/file1.java"), new TextRange(1, 0, 1, 10));
    var mockIssue2 = createMockRawIssue("java:S456", "Issue in scope2", IssueSeverity.MINOR, 
      URI.create("file:///scope2/file2.java"), new TextRange(2, 5, 2, 15));
    var scanResults1 = mock(AnalysisResult.class);
    when(scanResults1.rawIssues()).thenReturn(List.of(mockIssue1));
    var scanResults2 = mock(AnalysisResult.class);
    when(scanResults2.rawIssues()).thenReturn(List.of(mockIssue2));
    when(analysisService.scheduleAnalysis(eq("scope1"), any(UUID.class), anySet(), anyMap(), 
      anyBoolean(), eq(TriggerType.FORCED), any())).thenReturn(CompletableFuture.completedFuture(scanResults1));
    when(analysisService.scheduleAnalysis(eq("scope2"), any(UUID.class), anySet(), anyMap(), 
      anyBoolean(), eq(TriggerType.FORCED), any())).thenReturn(CompletableFuture.completedFuture(scanResults2));

    analyzeListFilesRequestHandler.handle(request, response, context);

    assertThat(response.getCode()).isEqualTo(200);
    var responseContent = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
    var analysisResult = new Gson().fromJson(responseContent, AnalyzeListFilesRequestHandler.AnalysisResult.class);
    assertThat(analysisResult.issues()).hasSize(2);
    assertThat(analysisResult.issues())
      .extracting(AnalyzeListFilesRequestHandler.RawIssueResponse::ruleKey)
      .containsExactlyInAnyOrder("java:S123", "java:S456");
  }

  @Test
  void should_handle_issues_with_null_severity_and_file_path() throws HttpException, IOException {
    var analysisRequest = new AnalyzeListFilesRequestHandler.AnalysisRequest(List.of("/path/to/file.java"));
    var requestJson = new Gson().toJson(analysisRequest);
    var request = new BasicClassicHttpRequest(Method.POST, "/analyze");
    request.setEntity(new StringEntity(requestJson, StandardCharsets.UTF_8));
    var response = new BasicClassicHttpResponse(200);
    var context = mock(HttpContext.class);
    var filesByScope = Map.of("scope1", Set.of(URI.create("file:///path/to/file.java")));
    when(clientFileSystemService.groupFilesByConfigScope(anySet())).thenReturn(filesByScope);
    var mockIssue = createMockRawIssue("java:S123", "Test message", null, null, null);
    var scanResults = mock(AnalysisResult.class);
    when(scanResults.rawIssues()).thenReturn(List.of(mockIssue));
    when(analysisService.scheduleAnalysis(anyString(), any(UUID.class), anySet(), anyMap(), 
      anyBoolean(), eq(TriggerType.FORCED), any())).thenReturn(CompletableFuture.completedFuture(scanResults));

    analyzeListFilesRequestHandler.handle(request, response, context);

    assertThat(response.getCode()).isEqualTo(200);
    var responseContent = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
    var analysisResult = new Gson().fromJson(responseContent, AnalyzeListFilesRequestHandler.AnalysisResult.class);
    assertThat(analysisResult.issues()).hasSize(1);
    var issue = analysisResult.issues().get(0);
    assertThat(issue.ruleKey()).isEqualTo("java:S123");
    assertThat(issue.severity()).isNull();
    assertThat(issue.filePath()).isNull();
    assertThat(issue.textRange()).isNull();
  }

  private RawIssue createMockRawIssue(String ruleKey, String message, IssueSeverity severity, 
                                     URI fileUri, TextRange textRange) {
    var mockIssue = mock(RawIssue.class);
    when(mockIssue.getRuleKey()).thenReturn(ruleKey);
    when(mockIssue.getMessage()).thenReturn(message);
    when(mockIssue.getSeverity()).thenReturn(severity);
    when(mockIssue.getFileUri()).thenReturn(fileUri);
    when(mockIssue.getTextRange()).thenReturn(textRange);
    return mockIssue;
  }

}
