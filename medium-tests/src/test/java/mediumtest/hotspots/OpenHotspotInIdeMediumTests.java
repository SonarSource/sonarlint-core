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
package mediumtest.hotspots;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintBackendFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.stubbing.Answer;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

class OpenHotspotInIdeMediumTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  public static final String CONNECTION_ID = "connectionId";
  public static final String SCOPE_ID = "scopeId";

  private SonarLintTestRpcServer backend;
  public static final String PROJECT_KEY = "projectKey";
  static ServerFixture.Server serverWithHotspot = newSonarQubeServer("1.2.3")
    .withProject(PROJECT_KEY,
      project -> project.withDefaultBranch(branch -> branch.withHotspot("key",
        hotspot -> hotspot.withRuleKey("ruleKey")
          .withMessage("msg")
          .withAuthor("author")
          .withFilePath("file/path")
          .withStatus(HotspotReviewStatus.SAFE)
          .withTextRange(new TextRange(1, 0, 3, 4)))
        .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile"))))
    .start();
  static ServerFixture.Server serverWithoutHotspot = newSonarQubeServer("1.2.3")
    .start();

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @AfterAll
  static void stopServer() {
    serverWithHotspot.shutdown();
    serverWithoutHotspot.shutdown();
  }

  @Test
  void it_should_fail_request_when_server_parameter_missing() {
    backend = newBackend()
      .withEmbeddedServer()
      .build();

    var statusCode = requestGetOpenHotspotWithParams("project=projectKey&hotspot=key");

    assertThat(statusCode).isEqualTo(400);
  }

  @Test
  void it_should_fail_request_when_project_parameter_missing() {
    backend = newBackend()
      .withEmbeddedServer()
      .build();

    var statusCode = requestGetOpenHotspotWithParams("server=" + urlEncode(serverWithHotspot.baseUrl()) + "&hotspot=key");

    assertThat(statusCode).isEqualTo(400);
  }

  @Test
  void it_should_fail_request_when_hotspot_parameter_missing() {
    backend = newBackend()
      .withEmbeddedServer()
      .build();

    var statusCode = requestGetOpenHotspotWithParams("server=" + urlEncode(serverWithHotspot.baseUrl()) + "&project=projectKey");

    assertThat(statusCode).isEqualTo(400);
  }

  @Test
  void it_should_open_hotspot_in_ide_when_project_bound() throws InterruptedException {
    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithHotspot)
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = requestGetOpenHotspotWithParams("server=" + urlEncode(serverWithHotspot.baseUrl()) + "&project=projectKey&hotspot=key");
    assertThat(statusCode).isEqualTo(200);

    Thread.sleep(100);
    verify(fakeClient, never()).showMessage(any(), any());

    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getHotspotToShowByConfigScopeId()).containsOnlyKeys(SCOPE_ID));
    assertThat(fakeClient.getHotspotToShowByConfigScopeId().get(SCOPE_ID))
      .extracting(HotspotDetailsDto::getKey, HotspotDetailsDto::getMessage, HotspotDetailsDto::getAuthor, HotspotDetailsDto::getFilePath,
        HotspotDetailsDto::getStatus, HotspotDetailsDto::getResolution, HotspotDetailsDto::getCodeSnippet)
      .containsExactly(tuple("key", "msg", "author", "file/path", "REVIEWED", "SAFE", "source\ncode\nfile"));
  }

  @Test
  void it_should_update_telemetry_data_when_opening_hotspot_in_ide() {
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithHotspot)
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .build();

    requestGetOpenHotspotWithParams("server=" + urlEncode(serverWithHotspot.baseUrl()) + "&project=projectKey&hotspot=key");

    await().atMost(2, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(backend.telemetryFilePath())
        .content().asBase64Decoded().asString()
        .contains("\"showHotspotRequestsCount\":1"));
  }

  @Test
  void it_should_assist_creating_the_connection_when_server_url_unknown() throws InterruptedException {
    var fakeClient = newFakeClient().build();
    mockAssistCreatingConnection(fakeClient, CONNECTION_ID);
    mockAssistBinding(fakeClient, SCOPE_ID, CONNECTION_ID, PROJECT_KEY);

    backend = newBackend()
      .withUnboundConfigScope(SCOPE_ID)
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = requestGetOpenHotspotWithParams("server=" + urlEncode(serverWithHotspot.baseUrl()) + "&project=projectKey&hotspot=key");
    assertThat(statusCode).isEqualTo(200);

    Thread.sleep(100);
    verify(fakeClient, never()).showMessage(any(), any());

    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getHotspotToShowByConfigScopeId()).containsOnlyKeys(SCOPE_ID));
    assertThat(fakeClient.getHotspotToShowByConfigScopeId().get(SCOPE_ID))
      .extracting(HotspotDetailsDto::getMessage)
      .containsExactly("msg");
  }

  @Test
  void it_should_assist_creating_the_binding_if_scope_not_bound() throws InterruptedException {
    var fakeClient = newFakeClient().build();
    mockAssistCreatingConnection(fakeClient, CONNECTION_ID);
    mockAssistBinding(fakeClient, SCOPE_ID, CONNECTION_ID, PROJECT_KEY);

    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithHotspot)
      .withUnboundConfigScope(SCOPE_ID)
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = requestGetOpenHotspotWithParams("server=" + urlEncode(serverWithHotspot.baseUrl()) + "&project=projectKey&hotspot=key");
    assertThat(statusCode).isEqualTo(200);

    Thread.sleep(100);
    verify(fakeClient, never()).showMessage(any(), any());

    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getHotspotToShowByConfigScopeId()).containsOnlyKeys(SCOPE_ID));
    assertThat(fakeClient.getHotspotToShowByConfigScopeId().get(SCOPE_ID))
      .extracting(HotspotDetailsDto::getMessage)
      .containsExactly("msg");
  }

  @Test
  void it_should_display_a_message_when_failing_to_fetch_the_hotspot() throws InterruptedException {
    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithoutHotspot)
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = requestGetOpenHotspotWithParams("server=" + urlEncode(serverWithoutHotspot.baseUrl()) + "&project=projectKey&hotspot=key");
    assertThat(statusCode).isEqualTo(200);

    verify(fakeClient, timeout(2000)).showMessage(MessageType.ERROR, "Could not show the hotspot. See logs for more details");

    assertThat(fakeClient.getHotspotToShowByConfigScopeId()).isEmpty();
  }

  @Test
  void it_should_not_accept_post_method() {
    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithoutHotspot)
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = requestPostOpenHotspotWithParams("server=" + urlEncode(serverWithoutHotspot.baseUrl()) + "&project=projectKey&hotspot=key");

    assertThat(statusCode).isEqualTo(400);
  }

  private int requestGetOpenHotspotWithParams(String query) {
    return requestOpenHotspotWithParams(query, "GET", HttpRequest.BodyPublishers.noBody());
  }

  private int requestPostOpenHotspotWithParams(String query) {
    return requestOpenHotspotWithParams(query, "POST", HttpRequest.BodyPublishers.ofString(""));
  }

  private int requestOpenHotspotWithParams(String query, String method, HttpRequest.BodyPublisher bodyPublisher) {
    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/hotspots/show?" + query))
      .method(method, bodyPublisher)
      .build();
    HttpResponse<String> response = null;
    try {
      response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return response.statusCode();
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
        new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto(connectionId, serverWithHotspot.baseUrl(), true)), Collections.emptyList()));
      return new AssistCreatingConnectionResponse(connectionId);
    }).when(fakeClient).assistCreatingConnection(any(), any());
  }

}
