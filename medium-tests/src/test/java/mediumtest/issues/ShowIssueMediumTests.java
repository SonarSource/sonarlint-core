/*
 * SonarLint Core - Medium Tests
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
package mediumtest.issues;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.clientapi.common.TextRangeDto;
import org.sonarsource.sonarlint.core.commons.TextRange;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ShowIssueMediumTests {

  private static final String ISSUE_KEY = "myIssueKey";
  private static final String PR_ISSUE_KEY = "PRIssueKey";
  private static final String FILE_LEVEL_ISSUE_KEY = "fileLevelIssueKey";
  private static final String PROJECT_KEY = "projectKey";
  private static final String CONNECTION_ID = "connectionId";
  private static final String CONFIG_SCOPE_ID = "configScopeId";
  public static final String RULE_KEY = "ruleKey";
  private static final String BRANCH_NAME = "branchName";
  private ServerFixture.Server serverWithIssues = newSonarQubeServer("10.2")
    .withProject(PROJECT_KEY,
      project -> {
        project.withProjectName("org.sonarsource.sonarlint.intellij:sonarlint-intellij")
          .withPullRequest("1234", pullRequest -> (ServerFixture.ServerBuilder.ServerProjectPullRequestBuilder) pullRequest.withIssue(PR_ISSUE_KEY, RULE_KEY, "msg", "author", "file/path", "OPEN", "", "2023-05-13T17:55:39+0202",
          new TextRange(1, 0, 3, 4))
          .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile\nfive\nlines")));
        return project.withBranch("branchName",
          branch -> {
            branch.withIssue(ISSUE_KEY, RULE_KEY, "msg", "author", "file/path", "OPEN", "", "2023-05-13T17:55:39+0202",
              new TextRange(1, 0, 3, 4));
            branch.withIssue(FILE_LEVEL_ISSUE_KEY, RULE_KEY, "msg", "author", "file/path", "OPEN", "", "2023-05-13T17:55:39+0202",
              new TextRange(0, 0, 0, 0));
            return branch.withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile\nfive\nlines"));
          });
      })
    .start();
  private SonarLintTestBackend backend;

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
    var projectKey = "projectKey";
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
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getIssueParamsToShowByIssueKey()).containsOnlyKeys(issueKey));
    var showIssueParams = fakeClient.getIssueParamsToShowByIssueKey().get(issueKey);
    assertThat(showIssueParams.getIssueKey()).isEqualTo(issueKey);
    assertThat(showIssueParams.isTaint()).isFalse();
    assertThat(showIssueParams.getMessage()).isEqualTo("msg");
    assertThat(showIssueParams.getConfigScopeId()).isEqualTo(configScopeId);
    assertThat(showIssueParams.getRuleKey()).isEqualTo("ruleKey");
    assertThat(showIssueParams.getCreationDate()).isEqualTo("2023-05-13T17:55:39+0202");
    assertThat(showIssueParams.getTextRange()).extracting(TextRangeDto::getStartLine, TextRangeDto::getStartLineOffset,
        TextRangeDto::getEndLine, TextRangeDto::getEndLineOffset)
      .contains(1, 0, 3, 4);
    assertThat(showIssueParams.getCodeSnippet()).isEqualTo("source\ncode\nfile");
    assertThat(showIssueParams.getBranch()).isEqualTo(BRANCH_NAME);
    assertThat(showIssueParams.getPullRequest()).isNull();
  }

  @Test
  void it_should_open_pr_issue_in_ide() throws IOException, InterruptedException {
    var projectKey = "projectKey";
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
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getIssueParamsToShowByIssueKey()).containsOnlyKeys(PR_ISSUE_KEY));
    var showIssueParams = fakeClient.getIssueParamsToShowByIssueKey().get(PR_ISSUE_KEY);
    assertThat(showIssueParams.getIssueKey()).isEqualTo(PR_ISSUE_KEY);
    assertThat(showIssueParams.isTaint()).isFalse();
    assertThat(showIssueParams.getMessage()).isEqualTo("msg");
    assertThat(showIssueParams.getConfigScopeId()).isEqualTo(configScopeId);
    assertThat(showIssueParams.getRuleKey()).isEqualTo("ruleKey");
    assertThat(showIssueParams.getCreationDate()).isEqualTo("2023-05-13T17:55:39+0202");
    assertThat(showIssueParams.getTextRange()).extracting(TextRangeDto::getStartLine, TextRangeDto::getStartLineOffset,
        TextRangeDto::getEndLine, TextRangeDto::getEndLineOffset)
      .contains(1, 0, 3, 4);
    assertThat(showIssueParams.getCodeSnippet()).isEqualTo("source\ncode\nfile");
    assertThat(showIssueParams.getBranch()).isEqualTo(BRANCH_NAME);
    assertThat(showIssueParams.getPullRequest()).isEqualTo("1234");
  }

  @Test
  void it_should_open_a_file_level_issue_in_ide() throws Exception {
    var issueKey = FILE_LEVEL_ISSUE_KEY;
    var projectKey = "projectKey";
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
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getIssueParamsToShowByIssueKey()).containsOnlyKeys(issueKey));
    var showIssueParams = fakeClient.getIssueParamsToShowByIssueKey().get(issueKey);
    assertThat(showIssueParams.getIssueKey()).isEqualTo(issueKey);
    assertThat(showIssueParams.isTaint()).isFalse();
    assertThat(showIssueParams.getMessage()).isEqualTo("msg");
    assertThat(showIssueParams.getConfigScopeId()).isEqualTo(configScopeId);
    assertThat(showIssueParams.getRuleKey()).isEqualTo("ruleKey");
    assertThat(showIssueParams.getCreationDate()).isEqualTo("2023-05-13T17:55:39+0202");
    assertThat(showIssueParams.getTextRange()).extracting(TextRangeDto::getStartLine, TextRangeDto::getStartLineOffset,
        TextRangeDto::getEndLine, TextRangeDto::getEndLineOffset)
      .contains(0, 0, 0, 0);
    assertThat(showIssueParams.getCodeSnippet()).isEqualTo("source\ncode\nfile\nfive\nlines");
  }

  @Test
  void it_should_assist_creating_the_binding_if_scope_not_bound() throws Exception {
    var fakeClient = newFakeClient()
      .withConfigScope("sonarlint-intellij")
      .assistingConnectingAndBindingToSonarQube("sonarlint-intellij", CONNECTION_ID, serverWithIssues.baseUrl(),
      "projectKey").build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithIssues)
      .withUnboundConfigScope("sonarlint-intellij")
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = executeOpenIssueRequest(ISSUE_KEY, PROJECT_KEY, BRANCH_NAME);

    assertThat(statusCode).isEqualTo(200);
    assertThat(fakeClient.getMessagesToShow()).isEmpty();
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getIssueParamsToShowByIssueKey()).containsOnlyKeys(ISSUE_KEY));
    assertThat(fakeClient.getIssueParamsToShowByIssueKey().get(ISSUE_KEY).getRuleKey()).isEqualTo(RULE_KEY);
  }

  @Test
  void it_should_not_assist_binding_if_multiple_suggestions() throws Exception {
    var fakeClient = newFakeClient()
      // Both of the config scope IDs will be a match
      .withConfigScope("intellij")
      .withConfigScope("sonarlint-intellij")
      .assistingConnectingAndBindingToSonarQube("intellij", CONNECTION_ID, serverWithIssues.baseUrl(),
        "projectKey")
      .assistingConnectingAndBindingToSonarQube("sonarlint-intellij", CONNECTION_ID, serverWithIssues.baseUrl(),
        "projectKey").build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithIssues)
      .withUnboundConfigScope("intellij")
      .withUnboundConfigScope("sonarlint-intellij")
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = executeOpenIssueRequest(ISSUE_KEY, PROJECT_KEY, BRANCH_NAME);

    assertThat(statusCode).isEqualTo(200);
    assertThat(fakeClient.getMessagesToShow()).isEmpty();
    await().during(1, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getIssueParamsToShowByIssueKey()).isEmpty());
  }

  @Test
  void it_should_assist_creating_the_connection_when_server_url_unknown() throws Exception {
    var fakeClient = newFakeClient()
      .withConfigScope("sonarlint-intellij")
      .assistingConnectingAndBindingToSonarQube("sonarlint-intellij", CONNECTION_ID, serverWithIssues.baseUrl(),
      "projectKey").build();
    backend = newBackend()
      .withUnboundConfigScope("sonarlint-intellij")
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = executeOpenIssueRequest(ISSUE_KEY, PROJECT_KEY, BRANCH_NAME);

    assertThat(statusCode).isEqualTo(200);
    assertThat(fakeClient.getMessagesToShow()).isEmpty();
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getIssueParamsToShowByIssueKey()).containsOnlyKeys(ISSUE_KEY));
    assertThat(fakeClient.getIssueParamsToShowByIssueKey().get(ISSUE_KEY).getRuleKey()).isEqualTo(RULE_KEY);
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
    HttpRequest request = openIssueWithProjectAndKeyRequest("&issue=" + issueKey, "&project=" + projectKey,  "&branch=" + branch);
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
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/issues/show?server=" + serverWithIssues.baseUrl() + projectParam + issueParam + branchParam))
      .header("Origin", "https://sonar.my")
      .GET().build();
  }

  private HttpRequest openIssueWithBranchAndPRRequest(String issueParam, String projectParam, String branchParam, String pullRequestParam) {
    return HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/issues/show?server=" + serverWithIssues.baseUrl() + projectParam + issueParam + branchParam + pullRequestParam))
      .header("Origin", "https://sonar.my")
      .GET().build();
  }
}
