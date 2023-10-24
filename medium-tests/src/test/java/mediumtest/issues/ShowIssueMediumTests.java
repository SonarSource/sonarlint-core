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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.core.commons.TextRange;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ShowIssueMediumTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester(true);

  private static final String ISSUE_KEY = "myIssueKey";
  private static final String PROJECT_KEY = "projectKey";
  private static final String CONNECTION_ID = "connectionId";
  private static final String CONFIG_SCOPE_ID = "configScopeId";
  public static final String RULE_KEY = "ruleKey";
  private ServerFixture.Server serverWithIssue = newSonarQubeServer("10.2")
    .withProject(PROJECT_KEY,
      project -> project.withBranch("branchName",
        branch -> branch.withIssue(ISSUE_KEY, RULE_KEY, "msg", "author", "file/path", "OPEN", "", "2023-05-13T17:55:39+0202",
          new TextRange(1, 0, 3, 4))))
    .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile"))
    .start();
  private SonarLintTestRpcServer backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (serverWithIssue != null) {
      serverWithIssue.shutdown();
      serverWithIssue = null;
    }
  }

  @Test
  void it_should_update_the_telemetry_on_show_issue() throws Exception {
    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithIssue)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .build(fakeClient);

    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"showIssueRequestsCount\":0");

    var statusCode = executeOpenIssueRequest(ISSUE_KEY, PROJECT_KEY);

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
      .withSonarQubeConnection(connectionId, serverWithIssue)
      .withBoundConfigScope(configScopeId, connectionId, projectKey)
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = executeOpenIssueRequest(ISSUE_KEY, PROJECT_KEY);

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
  }

  @Test
  void it_should_assist_creating_the_binding_if_scope_not_bound() throws Exception {
    var fakeClient = newFakeClient().assistingConnectingAndBindingToSonarQube("scopeId", CONNECTION_ID, serverWithIssue.baseUrl(),
      "projectKey").build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithIssue)
      .withUnboundConfigScope("scopeId")
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = executeOpenIssueRequest(ISSUE_KEY, PROJECT_KEY);

    assertThat(statusCode).isEqualTo(200);
    assertThat(fakeClient.getMessagesToShow()).isEmpty();
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getIssueParamsToShowByIssueKey()).containsOnlyKeys(ISSUE_KEY));
    assertThat(fakeClient.getIssueParamsToShowByIssueKey().get(ISSUE_KEY).getRuleKey()).isEqualTo(RULE_KEY);
  }

  @Test
  void it_should_assist_creating_the_connection_when_server_url_unknown() throws Exception {
    var fakeClient = newFakeClient().assistingConnectingAndBindingToSonarQube("scopeId", CONNECTION_ID, serverWithIssue.baseUrl(),
      "projectKey").build();
    backend = newBackend()
      .withUnboundConfigScope("scopeId")
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = executeOpenIssueRequest(ISSUE_KEY, PROJECT_KEY);

    assertThat(statusCode).isEqualTo(200);
    assertThat(fakeClient.getMessagesToShow()).isEmpty();
    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getIssueParamsToShowByIssueKey()).containsOnlyKeys(ISSUE_KEY));
    assertThat(fakeClient.getIssueParamsToShowByIssueKey().get(ISSUE_KEY).getRuleKey()).isEqualTo(RULE_KEY);
  }

  @Test
  void it_should_fail_request_when_issue_parameter_missing() throws Exception {
    backend = newBackend()
      .withEmbeddedServer()
      .build();

    var statusCode = executeOpenIssueRequest("", PROJECT_KEY);

    assertThat(statusCode).isEqualTo(400);
  }

  @Test
  void it_should_fail_request_when_project_parameter_missing() throws Exception {
    backend = newBackend()
      .withEmbeddedServer()
      .build();

    var statusCode = executeOpenIssueRequest(ISSUE_KEY, "");

    assertThat(statusCode).isEqualTo(400);
  }

  private int executeOpenIssueRequest(String issueKey, String projectKey) throws IOException, InterruptedException {
    HttpRequest request = openIssueWithProjectAndKeyRequest("&issue=" + issueKey, "&project=" + projectKey);
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    return response.statusCode();
  }

  private HttpRequest openIssueWithProjectAndKeyRequest(String issueParam, String projectParam) {
    return HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/issues/show?server=" + serverWithIssue.baseUrl() + projectParam + issueParam))
      .header("Origin", "https://sonar.my")
      .GET().build();
  }
}