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
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.file.FilePathTranslation;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.FixSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

import static mediumtest.fixtures.ServerFixture.newSonarCloudServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class OpenFixSuggestionInIdeMediumTests {

  public static final String PROJECT_KEY = "projectKey";
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester(true);

  private static final String ISSUE_KEY = "myIssueKey";
  private static final String CONNECTION_ID = "connectionId";
  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String BRANCH_NAME = "branchName";
  private static final String ORG_KEY = "orgKey";
  private static final String FIX_PAYLOAD = "{\n" +
    "\"fileEdit\": {\n" +
    "\"path\": \"src/main/java/Main.java\",\n" +
    "\"changes\": [{\n" +
    "\"beforeLineRange\": {\n" +
    "\"startLine\": 0,\n" +
    "\"endLine\": 1\n" +
    "},\n" +
    "\"before\": \"\",\n" +
    "\"after\": \"var fix = 1;\"\n" +
    "}]\n" +
    "},\n" +
    "\"suggestionId\": \"eb93b2b4-f7b0-4b5c-9460-50893968c264\",\n" +
    "\"explanation\": \"Modifying the variable name is good\"\n" +
    "}\n";

  private SonarLintTestRpcServer backend;
  private ServerFixture.Server scServer = newSonarCloudServer(ORG_KEY)
    .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
    .start();

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (scServer != null) {
      scServer.shutdown();
      scServer = null;
    }
  }

  @Test
  void it_should_update_the_telemetry_on_show_issue() throws Exception {
    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarCloudUrl(scServer.baseUrl())
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .withTelemetryEnabled()
      .withOpenFixSuggestion()
      .build(fakeClient);

    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"fixSuggestionReceivedCounter\":{}");

    var statusCode = executeOpenFixSuggestionRequestWithoutToken(FIX_PAYLOAD, ISSUE_KEY, PROJECT_KEY, BRANCH_NAME, ORG_KEY);

    assertThat(statusCode).isEqualTo(200);
    await().atMost(2, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(backend.telemetryFilePath())
        .content().asBase64Decoded().asString()
        .contains("\"fixSuggestionReceivedCounter\":{\"eb93b2b4-f7b0-4b5c-9460-50893968c264\":{\"fixSuggestionReceivedCount\":1}}"));
  }

  @Test
  void it_should_open_a_fix_suggestion_in_ide() throws Exception {
    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarCloudUrl(scServer.baseUrl())
      .withSonarCloudConnection(CONNECTION_ID, ORG_KEY)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .withOpenFixSuggestion()
      .build(fakeClient);

    var statusCode = executeOpenFixSuggestionRequestWithoutToken(FIX_PAYLOAD, ISSUE_KEY, PROJECT_KEY, BRANCH_NAME, ORG_KEY);
    assertThat(statusCode).isEqualTo(200);

    ArgumentCaptor<FixSuggestionDto> captor = ArgumentCaptor.captor();
    verify(fakeClient, timeout(2000)).showFixSuggestion(eq(CONFIG_SCOPE_ID), eq(ISSUE_KEY), captor.capture());

    var pathTranslation = new FilePathTranslation(Path.of("ide"), Path.of("home"));

    var fixSuggestion = captor.getValue();
    assertThat(fixSuggestion).isNotNull();
    assertThat(fixSuggestion.suggestionId()).isEqualTo("eb93b2b4-f7b0-4b5c-9460-50893968c264");
    assertThat(fixSuggestion.explanation()).isEqualTo("Modifying the variable name is good");
    assertThat(fixSuggestion.fileEdit().idePath().toString()).contains(pathTranslation.serverToIdePath(Paths.get("src/main/java/Main.java")).toString());
    assertThat(fixSuggestion.fileEdit().changes()).hasSize(1);
    var change = fixSuggestion.fileEdit().changes().get(0);
    assertThat(change.before()).isEmpty();
    assertThat(change.after()).isEqualTo("var fix = 1;");
    assertThat(change.beforeLineRange().getStartLine()).isZero();
    assertThat(change.beforeLineRange().getEndLine()).isEqualTo(1);
  }

  @Test
  void it_should_assist_creating_the_binding_if_scope_not_bound() throws Exception {
    var fakeClient = newFakeClient().build();
    mockAssistCreatingConnection(fakeClient, CONNECTION_ID);
    mockAssistBinding(fakeClient, CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY);

    backend = newBackend()
      .withSonarCloudUrl(scServer.baseUrl())
      .withUnboundConfigScope(CONFIG_SCOPE_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .withOpenFixSuggestion()
      .build(fakeClient);

    var statusCode = executeOpenFixSuggestionRequestWithoutToken(FIX_PAYLOAD, ISSUE_KEY, PROJECT_KEY, BRANCH_NAME, ORG_KEY);
    assertThat(statusCode).isEqualTo(200);

    verify(fakeClient, timeout(2000)).showFixSuggestion(eq(CONFIG_SCOPE_ID), eq(ISSUE_KEY), any());
    verify(fakeClient, never()).showMessage(any(), any());
  }

  @Test
  void it_should_not_assist_binding_if_multiple_suggestions() throws Exception {
    var fakeClient = newFakeClient().build();
    mockAssistCreatingConnection(fakeClient, CONNECTION_ID);
    mockAssistBinding(fakeClient, CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY);
    backend = newBackend()
      .withSonarCloudUrl(scServer.baseUrl())
      .withUnboundConfigScope("configScopeA", PROJECT_KEY + " 1")
      .withUnboundConfigScope("configScopeB", PROJECT_KEY + " 2")
      .withEmbeddedServer()
      .withOpenFixSuggestion()
      .build(fakeClient);

    var statusCode = executeOpenFixSuggestionRequestWithoutToken(FIX_PAYLOAD, ISSUE_KEY, PROJECT_KEY, BRANCH_NAME, ORG_KEY);

    assertThat(statusCode).isEqualTo(200);
    verify(fakeClient, timeout(1000)).noBindingSuggestionFound(PROJECT_KEY);
    verify(fakeClient, never()).showIssue(any(), any());
  }

  @Test
  void it_should_assist_binding_if_multiple_suggestions_but_scopes_are_parent_and_child() throws Exception {
    var fakeClient = newFakeClient().build();
    mockAssistCreatingConnection(fakeClient, CONNECTION_ID);
    mockAssistBinding(fakeClient, "configScopeParent", CONNECTION_ID, PROJECT_KEY);
    backend = newBackend()
      .withSonarCloudUrl(scServer.baseUrl())
      .withUnboundConfigScope("configScopeParent", PROJECT_KEY)
      .withUnboundConfigScope("configScopeChild", PROJECT_KEY, "configScopeParent")
      .withEmbeddedServer()
      .withOpenFixSuggestion()
      .build(fakeClient);

    var statusCode = executeOpenFixSuggestionRequestWithoutToken(FIX_PAYLOAD, ISSUE_KEY, PROJECT_KEY, BRANCH_NAME, ORG_KEY);

    assertThat(statusCode).isEqualTo(200);
    verify(fakeClient, timeout(2000)).showFixSuggestion(any(), any(), any());
    verify(fakeClient, never()).showMessage(any(), any());
  }

  @Test
  void it_should_assist_creating_the_connection_when_no_sc_connection() throws Exception {
    var fakeClient = newFakeClient().build();
    mockAssistCreatingConnection(fakeClient, CONNECTION_ID);
    mockAssistBinding(fakeClient, CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY);

    backend = newBackend()
      .withSonarCloudUrl(scServer.baseUrl())
      .withUnboundConfigScope(CONFIG_SCOPE_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .withOpenFixSuggestion()
      .build(fakeClient);

    var statusCode = executeOpenFixSuggestionRequestWithToken(FIX_PAYLOAD, ISSUE_KEY, PROJECT_KEY, BRANCH_NAME, ORG_KEY, "token-name", "token-value");
    assertThat(statusCode).isEqualTo(200);

    verify(fakeClient, timeout(2000)).showFixSuggestion(eq(CONFIG_SCOPE_ID), eq(ISSUE_KEY), any());
    verify(fakeClient, never()).showMessage(any(), any());

    ArgumentCaptor<AssistCreatingConnectionParams> captor = ArgumentCaptor.captor();
    verify(fakeClient, timeout(1000)).assistCreatingConnection(captor.capture(), any());
    assertThat(captor.getAllValues())
      .extracting(connectionParams -> connectionParams.getConnectionParams().getRight().getOrganizationKey(),
        AssistCreatingConnectionParams::getTokenName,
        AssistCreatingConnectionParams::getTokenValue)
      .containsExactly(tuple(ORG_KEY, "token-name", "token-value"));
  }

  @Test
  void it_should_revoke_token_when_exception_thrown_while_assist_creating_the_connection() throws Exception {
    var fakeClient = newFakeClient().build();
    doThrow(RuntimeException.class).when(fakeClient).assistCreatingConnection(any(), any());

    backend = newBackend()
      .withSonarCloudUrl(scServer.baseUrl())
      .withUnboundConfigScope(CONFIG_SCOPE_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .withOpenFixSuggestion()
      .build(fakeClient);

    var statusCode = executeOpenFixSuggestionRequestWithToken(FIX_PAYLOAD, ISSUE_KEY, PROJECT_KEY, BRANCH_NAME, "orgKey", "token-name", "token-value");
    assertThat(statusCode).isEqualTo(200);

    ArgumentCaptor<LogParams> captor = ArgumentCaptor.captor();
    verify(fakeClient, after(500).atLeastOnce()).log(captor.capture());
    assertThat(captor.getAllValues())
      .extracting(LogParams::getMessage)
      .containsAnyOf("Revoking token 'token-name'");
  }

  @Test
  void it_should_fail_request_when_issue_parameter_missing() throws Exception {
    backend = newBackend()
      .withEmbeddedServer()
      .withOpenFixSuggestion()
      .build();

    var statusCode = executeOpenFixSuggestionRequestWithoutToken(FIX_PAYLOAD, "", PROJECT_KEY, BRANCH_NAME, ORG_KEY);

    assertThat(statusCode).isEqualTo(400);
  }

  @Test
  void it_should_fail_request_when_feature_not_enabled() throws Exception {
    backend = newBackend()
      .withEmbeddedServer()
      .build();

    var statusCode = executeOpenFixSuggestionRequestWithoutToken(FIX_PAYLOAD, ISSUE_KEY, PROJECT_KEY, BRANCH_NAME, ORG_KEY);

    assertThat(statusCode).isEqualTo(400);
  }

  @Test
  void it_should_fail_request_when_project_parameter_missing() throws IOException, InterruptedException {
    backend = newBackend()
      .withEmbeddedServer()
      .build();

    var statusCode = executeOpenFixSuggestionRequestWithoutToken(ISSUE_KEY, "", "", "", "");

    assertThat(statusCode).isEqualTo(400);
  }

  private Object executeOpenFixSuggestionRequestWithToken(String payload, String issueKey, String projectKey, String branchName, String orgKey,
    String tokenName, String tokenValue) throws IOException, InterruptedException {
    HttpRequest request = openFixSuggestionRequest(payload, "&issue=" + issueKey, "&project=" + projectKey, "&branch=" + branchName,
      "&organizationKey=" + orgKey, "&tokenName=" + tokenName, "&tokenValue=" + tokenValue);
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    return response.statusCode();
  }

  private Object executeOpenFixSuggestionRequestWithoutToken(String payload, String issueKey, String projectKey, String branchName, String orgKey) throws IOException, InterruptedException {
    HttpRequest request = openFixSuggestionRequest(payload, "&issue=" + issueKey, "&project=" + projectKey, "&branch=" + branchName,
      "&organizationKey=" + orgKey);
    var response = java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    return response.statusCode();
  }

  private HttpRequest openFixSuggestionRequest(String payload, String... params) {
    return HttpRequest.newBuilder()
      .uri(URI.create(
        "http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/fix/show?server=" + scServer.baseUrl() + String.join("", params)))
      .header("Origin", scServer.baseUrl())
      .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
  }

  private void mockAssistBinding(SonarLintBackendFixture.FakeSonarLintRpcClient fakeClient, String configScopeId, String connectionId,
    String sonarProjectKey) {
    doAnswer((Answer<AssistBindingResponse>) invocation -> {
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId,
        new BindingConfigurationDto(connectionId, sonarProjectKey, false)));
      return new AssistBindingResponse(configScopeId);
    }).when(fakeClient).assistBinding(any(), any());
  }

  private void mockAssistCreatingConnection(SonarLintBackendFixture.FakeSonarLintRpcClient fakeClient, String connectionId) {
    doAnswer((Answer<AssistCreatingConnectionResponse>) invocation -> {
      backend.getConnectionService().didUpdateConnections(
        new DidUpdateConnectionsParams(Collections.emptyList(),
          List.of(new SonarCloudConnectionConfigurationDto(connectionId, ORG_KEY, true))));
      return new AssistCreatingConnectionResponse(connectionId);
    }).when(fakeClient).assistCreatingConnection(any(), any());
  }
}
