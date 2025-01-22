/*
 * SonarLint Core - Medium Tests
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
package mediumtest.hotspots;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

class OpenHotspotInIdeMediumTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  public static final String CONNECTION_ID = "connectionId";
  public static final String SCOPE_ID = "scopeId";
  public static final String PROJECT_KEY = "projectKey";
  public static final String SONAR_PROJECT_NAME = "Project Name";

  @SonarLintTest
  void it_should_fail_request_when_server_parameter_missing(SonarLintTestHarness harness) {
    var serverWithoutHotspot = harness.newFakeSonarQubeServer("1.2.3").start();
    var backend = harness.newBackend()
      .withEmbeddedServer()
      .start();

    var statusCode = requestGetOpenHotspotWithParams(backend, serverWithoutHotspot, "project=projectKey&hotspot=key");

    assertThat(statusCode).isEqualTo(400);
  }

  @SonarLintTest
  void it_should_fail_request_when_project_parameter_missing(SonarLintTestHarness harness) {
    var serverWithoutHotspot = harness.newFakeSonarQubeServer("1.2.3").start();
    var backend = harness.newBackend()
      .withEmbeddedServer()
      .start();

    var statusCode = requestGetOpenHotspotWithParams(backend, serverWithoutHotspot, "server=URL&hotspot=key");

    assertThat(statusCode).isEqualTo(400);
  }

  @SonarLintTest
  void it_should_fail_request_when_hotspot_parameter_missing(SonarLintTestHarness harness) {
    var serverWithoutHotspot = harness.newFakeSonarQubeServer("1.2.3").start();
    var backend = harness.newBackend()
      .withEmbeddedServer()
      .start();

    var statusCode = requestGetOpenHotspotWithParams(backend, serverWithoutHotspot, "server=URL&project=projectKey");

    assertThat(statusCode).isEqualTo(400);
  }

  @SonarLintTest
  void it_should_open_hotspot_in_ide_when_project_bound(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    var serverWithHotspot = buildServerWithHotspot(harness).start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithHotspot)
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .start(fakeClient);

    var statusCode = requestGetOpenHotspotWithParams(backend, serverWithHotspot, "server=" + urlEncode(serverWithHotspot.baseUrl()) + "&project=projectKey&hotspot=key");
    assertThat(statusCode).isEqualTo(200);

    ArgumentCaptor<HotspotDetailsDto> captor = ArgumentCaptor.captor();
    verify(fakeClient, timeout(1000)).showHotspot(eq(SCOPE_ID), captor.capture());
    verify(fakeClient, never()).showMessage(any(), any());

    assertThat(captor.getAllValues())
      .extracting(HotspotDetailsDto::getKey, HotspotDetailsDto::getMessage, HotspotDetailsDto::getAuthor, HotspotDetailsDto::getIdeFilePath,
        HotspotDetailsDto::getStatus, HotspotDetailsDto::getResolution, HotspotDetailsDto::getCodeSnippet)
      .containsExactly(tuple("key", "msg", "author", Path.of("file/path"), "REVIEWED", "SAFE", "source\ncode\nfile"));
  }

  @SonarLintTest
  void it_should_update_telemetry_data_when_opening_hotspot_in_ide(SonarLintTestHarness harness) {
    var serverWithHotspot = buildServerWithHotspot(harness).start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithHotspot)
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .withTelemetryEnabled()
      .start();

    requestGetOpenHotspotWithParams(backend, serverWithHotspot, "server=" + urlEncode(serverWithHotspot.baseUrl()) + "&project=projectKey&hotspot=key");

    await().atMost(2, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(backend.telemetryFilePath())
        .content().asBase64Decoded().asString()
        .contains("\"showHotspotRequestsCount\":1"));
  }

  @SonarLintTest
  void it_should_assist_creating_the_connection_when_server_url_unknown(SonarLintTestHarness harness) {
    var serverWithHotspot = buildServerWithHotspot(harness).start();
    var fakeClient = harness.newFakeClient().build();

    var backend = harness.newBackend()
      .withUnboundConfigScope(SCOPE_ID, SONAR_PROJECT_NAME)
      .withEmbeddedServer()
      .beforeInitialize(createdBackend -> {
        mockAssistCreatingConnection(createdBackend, fakeClient, serverWithHotspot, CONNECTION_ID);
        mockAssistBinding(createdBackend, fakeClient, SCOPE_ID, CONNECTION_ID, PROJECT_KEY);
      })
      .start(fakeClient);

    var statusCode = requestGetOpenHotspotWithParams(backend, serverWithHotspot, "server=" + urlEncode(serverWithHotspot.baseUrl()) + "&project=projectKey&hotspot=key");
    assertThat(statusCode).isEqualTo(200);

    ArgumentCaptor<HotspotDetailsDto> captor = ArgumentCaptor.captor();
    verify(fakeClient, timeout(1000)).showHotspot(eq(SCOPE_ID), captor.capture());
    verify(fakeClient, never()).showMessage(any(), any());

    assertThat(captor.getAllValues())
      .extracting(HotspotDetailsDto::getMessage)
      .containsExactly("msg");
  }

  @SonarLintTest
  void it_should_assist_creating_the_binding_if_scope_not_bound(SonarLintTestHarness harness) {
    var serverWithHotspot = buildServerWithHotspot(harness).start();
    var fakeClient = harness.newFakeClient().build();

    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithHotspot)
      .withUnboundConfigScope(SCOPE_ID, SONAR_PROJECT_NAME)
      .withEmbeddedServer()
      .beforeInitialize(createdBackend -> {
        mockAssistCreatingConnection(createdBackend, fakeClient, serverWithHotspot, CONNECTION_ID);
        mockAssistBinding(createdBackend, fakeClient, SCOPE_ID, CONNECTION_ID, PROJECT_KEY);
      })
      .start(fakeClient);

    var statusCode = requestGetOpenHotspotWithParams(backend, serverWithHotspot, "server=" + urlEncode(serverWithHotspot.baseUrl()) + "&project=projectKey&hotspot=key");
    assertThat(statusCode).isEqualTo(200);

    ArgumentCaptor<HotspotDetailsDto> captor = ArgumentCaptor.captor();
    verify(fakeClient, timeout(1000)).showHotspot(eq(SCOPE_ID), captor.capture());
    verify(fakeClient, never()).showMessage(any(), any());

    assertThat(captor.getAllValues())
      .extracting(HotspotDetailsDto::getMessage)
      .containsExactly("msg");
  }

  @SonarLintTest
  void it_should_display_a_message_when_failing_to_fetch_the_hotspot(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    var serverWithoutHotspot = harness.newFakeSonarQubeServer("1.2.3").start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithoutHotspot)
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .start(fakeClient);

    var statusCode = requestGetOpenHotspotWithParams(backend, "server=" + urlEncode(serverWithoutHotspot.baseUrl()) + "&project=projectKey&hotspot=key",
      serverWithoutHotspot.baseUrl());
    assertThat(statusCode).isEqualTo(200);

    verify(fakeClient, timeout(2000)).showMessage(MessageType.ERROR, "Could not show the hotspot. See logs for more details");
    verify(fakeClient, never()).showHotspot(anyString(), any());
  }

  @SonarLintTest
  void it_should_not_accept_post_method(SonarLintTestHarness harness) {
    var serverWithoutHotspot = harness.newFakeSonarQubeServer("1.2.3").start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithoutHotspot)
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withEmbeddedServer()
      .start();

    var statusCode = requestPostOpenHotspotWithParams(backend, serverWithoutHotspot, "server=" + urlEncode(serverWithoutHotspot.baseUrl()) + "&project=projectKey&hotspot=key");

    assertThat(statusCode).isEqualTo(400);
  }

  private int requestGetOpenHotspotWithParams(SonarLintTestRpcServer backend, String query, String baseUrl) {
    return requestOpenHotspotWithParams(backend, query, "GET", baseUrl, HttpRequest.BodyPublishers.noBody());
  }

  private int requestGetOpenHotspotWithParams(SonarLintTestRpcServer backend, ServerFixture.Server server, String query) {
    return requestOpenHotspotWithParams(backend, query, "GET", server.baseUrl(), HttpRequest.BodyPublishers.noBody());
  }

  private int requestPostOpenHotspotWithParams(SonarLintTestRpcServer backend, ServerFixture.Server server, String query) {
    return requestOpenHotspotWithParams(backend, query, "POST", server.baseUrl(), HttpRequest.BodyPublishers.ofString(""));
  }

  private int requestOpenHotspotWithParams(SonarLintTestRpcServer backend, String query, String method, String baseUrl, HttpRequest.BodyPublisher bodyPublisher) {
    var request = HttpRequest.newBuilder()
      .uri(URI.create("http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/hotspots/show?" + query))
      .header("Origin", baseUrl)
      .method(method, bodyPublisher)
      .build();
    HttpResponse<String> response;
    try {
      response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
    return response.statusCode();
  }

  private void mockAssistBinding(SonarLintTestRpcServer backend, SonarLintBackendFixture.FakeSonarLintRpcClient fakeClient, String configScopeId, String connectionId,
    String sonarProjectKey) {
    doAnswer((Answer<AssistBindingResponse>) invocation -> {
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId, new BindingConfigurationDto(connectionId, sonarProjectKey, false)));
      return new AssistBindingResponse(configScopeId);
    }).when(fakeClient).assistBinding(any(), any());
  }

  private void mockAssistCreatingConnection(SonarLintTestRpcServer backend, SonarLintBackendFixture.FakeSonarLintRpcClient fakeClient, ServerFixture.Server serverWithHotspot,
    String connectionId) {
    doAnswer((Answer<AssistCreatingConnectionResponse>) invocation -> {
      backend.getConnectionService().didUpdateConnections(
        new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto(connectionId, serverWithHotspot.baseUrl(), true)), Collections.emptyList()));
      return new AssistCreatingConnectionResponse(connectionId);
    }).when(fakeClient).assistCreatingConnection(any(), any());
  }

  private static ServerFixture.ServerBuilder buildServerWithHotspot(SonarLintTestHarness harness) {
    return harness.newFakeSonarQubeServer("1.2.3")
      .withProject(PROJECT_KEY,
        project -> project.withProjectName(SONAR_PROJECT_NAME).withDefaultBranch(branch -> branch.withHotspot("key",
          hotspot -> hotspot.withRuleKey("ruleKey")
            .withMessage("msg")
            .withAuthor("author")
            .withFilePath("file/path")
            .withStatus(HotspotReviewStatus.SAFE)
            .withTextRange(new TextRange(1, 0, 3, 4)))
          .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile"))));
  }

}
