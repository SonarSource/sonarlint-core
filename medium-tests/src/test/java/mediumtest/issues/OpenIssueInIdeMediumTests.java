/*
 * SonarLint Core - Medium Tests
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
package mediumtest.issues;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintBackendFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.stubbing.Answer;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class OpenIssueInIdeMediumTests {

  public static final String PROJECT_KEY = "projectKey";
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester(true);

  private static final String ISSUE_KEY = "myIssueKey";
  private static final String PR_ISSUE_KEY = "PRIssueKey";
  private static final String FILE_LEVEL_ISSUE_KEY = "fileLevelIssueKey";
  private static final String CONNECTION_ID = "connectionId";
  private static final String CONFIG_SCOPE_ID = "configScopeId";
  public static final String RULE_KEY = "ruleKey";
  private static final String BRANCH_NAME = "branchName";
  private static final Instant ISSUE_INTRODUCTION_DATE = LocalDateTime.of(2023, 12, 25, 12, 30, 35).toInstant(ZoneOffset.UTC);
  private ServerFixture.Server serverWithIssues = newSonarQubeServer("10.2")
    .withProject(PROJECT_KEY,
      project -> {
        project.withPullRequest("1234",
          pullRequest -> (ServerFixture.ServerBuilder.ServerProjectBuilder.ServerProjectPullRequestBuilder) pullRequest
            .withIssue(PR_ISSUE_KEY, RULE_KEY, "msg", "author", "file/path", "OPEN", "", ISSUE_INTRODUCTION_DATE,
              new TextRange(1, 0, 3, 4))
            .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile\nfive\nlines")));
        return project.withBranch("branchName",
          branch -> {
            branch.withIssue(ISSUE_KEY, RULE_KEY, "msg", "author", "file/path", "OPEN", "", ISSUE_INTRODUCTION_DATE,
              new TextRange(1, 0, 3, 4));
            branch.withIssue(FILE_LEVEL_ISSUE_KEY, RULE_KEY, "msg", "author", "file/path", "OPEN", "", ISSUE_INTRODUCTION_DATE,
              new TextRange(0, 0, 0, 0));
            return branch.withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile\nfive\nlines"));
          });
      })
    .start();
  private SonarLintTestRpcServer backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (serverWithIssues != null) {
      serverWithIssues.shutdown();
      serverWithIssues = null;
    }
  }

  @Test
  void it_should_update_the_telemetry_on_show_issue() throws Exception {
    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithIssues)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .build(fakeClient);

    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"showIssueRequestsCount\":0");

    var statusCode = executeOpenIssueRequest(ISSUE_KEY, PROJECT_KEY, BRANCH_NAME);

    assertThat(statusCode).isEqualTo(200);
    await().atMost(2, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(backend.telemetryFilePath())
        .content().asBase64Decoded().asString()
        .contains("\"showIssueRequestsCount\":1"));
  }

  @Test
  void it_should_open_an_issue_in_ide() throws Exception {
    var issueKey = "myIssueKey";
    var projectKey = PROJECT_KEY;
    var connectionId = "connectionId";
    var configScopeId = "configScopeId";

    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection(connectionId, serverWithIssues)
      .withBoundConfigScope(configScopeId, connectionId, projectKey)
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = executeOpenIssueRequest(ISSUE_KEY, PROJECT_KEY, BRANCH_NAME);
    assertThat(statusCode).isEqualTo(200);

    verify(fakeClient, timeout(2000)).showIssue(any(), any());

    assertThat(fakeClient.getIssueToShowByConfigScopeId()).containsOnlyKeys(configScopeId);
    var issues = fakeClient.getIssueToShowByConfigScopeId().get(configScopeId);
    assertThat(issues).hasSize(1);
    var issueDetails = issues.get(0);
    assertThat(issueDetails.getIssueKey()).isEqualTo(issueKey);
    assertThat(issueDetails.isTaint()).isFalse();
    assertThat(issueDetails.getMessage()).isEqualTo("msg");
    assertThat(issueDetails.getRuleKey()).isEqualTo("ruleKey");
    assertThat(issueDetails.getCreationDate()).isEqualTo("2023-12-25T12:30:35+0000");
    assertThat(issueDetails.getTextRange()).extracting(TextRangeDto::getStartLine, TextRangeDto::getStartLineOffset,
      TextRangeDto::getEndLine, TextRangeDto::getEndLineOffset)
      .contains(1, 0, 3, 4);
    assertThat(issueDetails.getCodeSnippet()).isEqualTo("source\ncode\nfile");
    assertThat(issueDetails.getBranch()).isEqualTo(BRANCH_NAME);
    assertThat(issueDetails.getPullRequest()).isNull();
  }

  @Test
  void it_should_open_pr_issue_in_ide() throws IOException, InterruptedException {
    var projectKey = PROJECT_KEY;
    var connectionId = "connectionId";
    var configScopeId = "configScopeId";

    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection(connectionId, serverWithIssues)
      .withBoundConfigScope(configScopeId, connectionId, projectKey)
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = executeOpenIssueRequest(PR_ISSUE_KEY, PROJECT_KEY, BRANCH_NAME, "1234");
    assertThat(statusCode).isEqualTo(200);

    verify(fakeClient, timeout(2000)).showIssue(any(), any());

    assertThat(fakeClient.getIssueToShowByConfigScopeId()).containsOnlyKeys(configScopeId);
    var issues = fakeClient.getIssueToShowByConfigScopeId().get(configScopeId);
    assertThat(issues).hasSize(1);
    var issueDetails = issues.get(0);
    assertThat(issueDetails.getIssueKey()).isEqualTo(PR_ISSUE_KEY);
    assertThat(issueDetails.isTaint()).isFalse();
    assertThat(issueDetails.getMessage()).isEqualTo("msg");
    assertThat(issueDetails.getRuleKey()).isEqualTo("ruleKey");
    assertThat(issueDetails.getCreationDate()).isEqualTo("2023-12-25T12:30:35+0000");
    assertThat(issueDetails.getTextRange()).extracting(TextRangeDto::getStartLine, TextRangeDto::getStartLineOffset,
      TextRangeDto::getEndLine, TextRangeDto::getEndLineOffset)
      .contains(1, 0, 3, 4);
    assertThat(issueDetails.getCodeSnippet()).isEqualTo("source\ncode\nfile");
    assertThat(issueDetails.getBranch()).isEqualTo(BRANCH_NAME);
    assertThat(issueDetails.getPullRequest()).isEqualTo("1234");
  }

  @Test
  void it_should_open_a_file_level_issue_in_ide() throws Exception {
    var issueKey = FILE_LEVEL_ISSUE_KEY;
    var projectKey = PROJECT_KEY;
    var connectionId = "connectionId";
    var configScopeId = "configScopeId";

    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection(connectionId, serverWithIssues)
      .withBoundConfigScope(configScopeId, connectionId, projectKey)
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = executeOpenIssueRequest(FILE_LEVEL_ISSUE_KEY, PROJECT_KEY, BRANCH_NAME);
    assertThat(statusCode).isEqualTo(200);

    verify(fakeClient, timeout(2000)).showIssue(any(), any());

    assertThat(fakeClient.getIssueToShowByConfigScopeId()).containsOnlyKeys(configScopeId);
    var issues = fakeClient.getIssueToShowByConfigScopeId().get(configScopeId);
    assertThat(issues).hasSize(1);
    var issueDetails = issues.get(0);
    assertThat(issueDetails.getIssueKey()).isEqualTo(issueKey);
    assertThat(issueDetails.isTaint()).isFalse();
    assertThat(issueDetails.getMessage()).isEqualTo("msg");
    assertThat(issueDetails.getRuleKey()).isEqualTo("ruleKey");
    assertThat(issueDetails.getCreationDate()).isEqualTo("2023-12-25T12:30:35+0000");
    assertThat(issueDetails.getTextRange()).extracting(TextRangeDto::getStartLine, TextRangeDto::getStartLineOffset,
      TextRangeDto::getEndLine, TextRangeDto::getEndLineOffset)
      .contains(0, 0, 0, 0);
    assertThat(issueDetails.getCodeSnippet()).isEqualTo("source\ncode\nfile\nfive\nlines");
  }

  @Test
  void it_should_assist_creating_the_binding_if_scope_not_bound() throws Exception {
    var fakeClient = newFakeClient().build();
    mockAssistCreatingConnection(fakeClient, CONNECTION_ID);
    mockAssistBinding(fakeClient, CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY);

    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithIssues)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = executeOpenIssueRequest(ISSUE_KEY, PROJECT_KEY, BRANCH_NAME);
    assertThat(statusCode).isEqualTo(200);

    verify(fakeClient, timeout(2000)).showIssue(eq(CONFIG_SCOPE_ID), any());
    verify(fakeClient, never()).showMessage(any(), any());
  }

  @Test
  void it_should_assist_creating_the_connection_when_server_url_unknown() throws Exception {
    var fakeClient = newFakeClient().build();
    mockAssistCreatingConnection(fakeClient, CONNECTION_ID);
    mockAssistBinding(fakeClient, CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY);

    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = executeOpenIssueRequest(ISSUE_KEY, PROJECT_KEY, BRANCH_NAME);
    assertThat(statusCode).isEqualTo(200);

    verify(fakeClient, timeout(2000)).showIssue(eq(CONFIG_SCOPE_ID), any());
    verify(fakeClient, never()).showMessage(any(), any());
  }

  @Test
  void it_should_fail_request_when_issue_parameter_missing() throws Exception {
    backend = newBackend()
      .withEmbeddedServer()
      .build();

    var statusCode = executeOpenIssueRequest("", PROJECT_KEY, BRANCH_NAME);

    assertThat(statusCode).isEqualTo(400);
  }

  @Test
  void it_should_fail_request_when_project_parameter_missing() throws Exception {
    backend = newBackend()
      .withEmbeddedServer()
      .build();

    var statusCode = executeOpenIssueRequest(ISSUE_KEY, "", "", "");

    assertThat(statusCode).isEqualTo(400);
  }

  private int executeOpenIssueRequest(String issueKey, String projectKey, String branch) throws IOException, InterruptedException {
    HttpRequest request = openIssueWithProjectAndKeyRequest("&issue=" + issueKey, "&project=" + projectKey, "&branch=" + branch);
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    return response.statusCode();
  }

  private int executeOpenIssueRequest(String issueKey, String projectKey, String branch, String pullRequest) throws IOException, InterruptedException {
    HttpRequest request = openIssueWithBranchAndPRRequest("&issue=" + issueKey, "&project=" + projectKey, "&branch=" + branch, "&pullRequest=" + pullRequest);
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    return response.statusCode();
  }

  private HttpRequest openIssueWithProjectAndKeyRequest(String issueParam, String projectParam, String branchParam) {
    return HttpRequest.newBuilder()
      .uri(URI.create(
        "http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/issues/show?server=" + serverWithIssues.baseUrl() + projectParam + issueParam + branchParam))
      .header("Origin", "https://sonar.my")
      .GET().build();
  }

  private HttpRequest openIssueWithBranchAndPRRequest(String issueParam, String projectParam, String branchParam, String pullRequestParam) {
    return HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/issues/show?server=" + serverWithIssues.baseUrl() + projectParam + issueParam
        + branchParam + pullRequestParam))
      .header("Origin", "https://sonar.my")
      .GET().build();
  }

  private void mockAssistBinding(SonarLintBackendFixture.FakeSonarLintRpcClient fakeClient, String configScopeId, String connectionId, String sonarProjectKey) {
    doAnswer((Answer<AssistBindingResponse>) invocation -> {
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId, new BindingConfigurationDto(connectionId, sonarProjectKey, false)));
      return new AssistBindingResponse(configScopeId);
    }).when(fakeClient).assistBinding(any(), any());
  }

  private void mockAssistCreatingConnection(SonarLintBackendFixture.FakeSonarLintRpcClient fakeClient, String connectionId) {
    doAnswer((Answer<AssistCreatingConnectionResponse>) invocation -> {
      backend.getConnectionService().didUpdateConnections(
        new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto(connectionId, serverWithIssues.baseUrl(), true)), Collections.emptyList()));
      return new AssistCreatingConnectionResponse(connectionId);
    }).when(fakeClient).assistCreatingConnection(any(), any());
  }
}
