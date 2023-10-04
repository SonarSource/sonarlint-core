/*
 * SonarLint Core - Implementation
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

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.clientapi.common.TextRangeDto;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.embedded.server.ShowIssueRequestHandler;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static mediumtest.fixtures.storage.ServerIssueFixtures.aServerIssue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ShowIssueMediumTests {

  private SonarLintTestBackend backend;
  private ServerFixture.Server server;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (server != null) {
      server.shutdown();
      server = null;
    }
  }

  @Test
  void it_should_update_the_telemetry_on_show_issue() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(), storage -> storage
        .withProject("projectKey",
          project -> project.withBranch("main",
            branch -> branch.withIssue(
              aServerIssue("myIssueKey")
                .withRuleKey("rule:key")
                .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
                .withIntroductionDate(Instant.EPOCH.plusSeconds(1))
                .withType(RuleType.BUG))))
        .withServerVersion("9.8"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .build();

    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"showIssueRequestsCount\":0");

    var showIssueRequestHandler = new ShowIssueRequestHandler(newFakeClient().build(), backend.getConnectionConfigurationRepository(),
      backend.getConfigurationServiceImpl(), backend.getBindingSuggestionProviderImpl(), backend.getServerApiProvider(),
      backend.getTelemetryServiceImpl());

    showIssueRequestHandler.showIssue(new ShowIssueRequestHandler.ShowIssueQuery(server.baseUrl(), "projectKey", "issueKey"));

    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"showIssueRequestsCount\":1");
  }

  @Test
  void it_should_open_an_issue_in_ide() throws Exception{
    var issueKey = "myIssueKey";
    var projectKey = "projectKey";
    var connectionId = "connectionId";
    server = newSonarQubeServer("10.2")
      .withProject(projectKey,
        project -> project.withBranch("branchName",
          branch -> branch.withIssue(issueKey, "ruleKey", "msg", "author", "file/path", "OPEN", "", "2023-05-13T17:55:39+0202",
            new TextRange(1, 0, 3, 4))))
      .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile"))
      .start();

    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection(connectionId, server)
      .withBoundConfigScope("configScopeId", connectionId, projectKey)
      .withEmbeddedServer()
      .build(fakeClient);

    HttpRequest request = openIssueWithProjectAndKeyRequest("&issue=" + issueKey, "&project=" + projectKey);

    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);

    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getIssueParamsToShowByIssueKey()).containsOnlyKeys(issueKey));
    var showIssueParams = fakeClient.getIssueParamsToShowByIssueKey().get(issueKey);
    assertThat(showIssueParams.getIssueKey()).isEqualTo(issueKey);
    assertThat(showIssueParams.getMessage()).isEqualTo("msg");
    assertThat(showIssueParams.getRuleKey()).isEqualTo("ruleKey");
    assertThat(showIssueParams.getCreationDate()).isEqualTo("2023-05-13T17:55:39+0202");
    assertThat(showIssueParams.getTextRange()).extracting(TextRangeDto::getStartLine, TextRangeDto::getStartLineOffset, TextRangeDto::getEndLine, TextRangeDto::getEndLineOffset)
      .contains(1,0,3,4);
  }

  @Test
  void it_should_fail_request_when_issue_parameter_missing() throws Exception{
    server = newSonarQubeServer("10.2").start();

    backend = newBackend()
      .withEmbeddedServer()
      .build();

    HttpRequest request = openIssueWithProjectAndKeyRequest("&issue=issueKey", "");
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void it_should_fail_request_when_project_parameter_missing() throws Exception{
    server = newSonarQubeServer("10.2").start();

    backend = newBackend()
      .withEmbeddedServer()
      .build();

    HttpRequest request = openIssueWithProjectAndKeyRequest("", "&project=projectKey");
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(400);
  }

  private HttpRequest openIssueWithProjectAndKeyRequest(String issueParam, String projectParam) {
    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/issues/show?server=" + server.baseUrl() + projectParam + issueParam))
      .header("Origin", "https://sonar.my")
      .GET().build();
    return request;
  }
}
