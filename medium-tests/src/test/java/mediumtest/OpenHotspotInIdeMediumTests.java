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
package mediumtest;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestBackend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

class OpenHotspotInIdeMediumTests {
  public static final String CONNECTION_ID = "connectionId";
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private SonarLintTestBackend backend;
  static ServerFixture.Server serverWithHotspot = newSonarQubeServer("1.2.3")
    .withProject("projectKey",
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
  void it_should_open_hotspot_in_ide_when_project_bound() {
    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithHotspot)
      .withBoundConfigScope("scopeId", CONNECTION_ID, "projectKey")
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = requestGetOpenHotspotWithParams("server=" + urlEncode(serverWithHotspot.baseUrl()) + "&project=projectKey&hotspot=key");

    assertThat(statusCode).isEqualTo(200);
    assertThat(fakeClient.getMessagesToShow()).isEmpty();
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getHotspotToShowByConfigScopeId()).containsOnlyKeys("scopeId"));
    assertThat(fakeClient.getHotspotToShowByConfigScopeId().get("scopeId"))
      .extracting(HotspotDetailsDto::getKey, HotspotDetailsDto::getMessage, HotspotDetailsDto::getAuthor, HotspotDetailsDto::getFilePath,
        HotspotDetailsDto::getStatus, HotspotDetailsDto::getResolution, HotspotDetailsDto::getCodeSnippet)
      .containsExactly(tuple("key", "msg", "author", "file/path", "REVIEWED", "SAFE", "source\ncode\nfile"));
  }

  @Test
  void it_should_update_telemetry_data_when_opening_hotspot_in_ide() {
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithHotspot)
      .withBoundConfigScope("scopeId", CONNECTION_ID, "projectKey")
      .withEmbeddedServer()
      .build();

    requestGetOpenHotspotWithParams("server=" + urlEncode(serverWithHotspot.baseUrl()) + "&project=projectKey&hotspot=key");

    await().atMost(2, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(backend.telemetryFilePath())
          .content().asBase64Decoded().asString()
          .contains("\"showHotspotRequestsCount\":1"));
  }

  @Test
  void it_should_assist_creating_the_connection_when_server_url_unknown() {
    var fakeClient = newFakeClient().assistingConnectingAndBindingToSonarQube("scopeId", CONNECTION_ID, serverWithHotspot.baseUrl(), "projectKey").build();
    backend = newBackend()
      .withUnboundConfigScope("scopeId")
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = requestGetOpenHotspotWithParams("server=" + urlEncode(serverWithHotspot.baseUrl()) + "&project=projectKey&hotspot=key");

    assertThat(statusCode).isEqualTo(200);
    assertThat(fakeClient.getMessagesToShow()).isEmpty();
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getHotspotToShowByConfigScopeId()).containsOnlyKeys("scopeId"));
    assertThat(fakeClient.getHotspotToShowByConfigScopeId().get("scopeId"))
      .extracting(HotspotDetailsDto::getMessage)
      .containsExactly("msg");
  }

  @Test
  void it_should_assist_creating_the_binding_if_scope_not_bound() {
    var fakeClient = newFakeClient().assistingConnectingAndBindingToSonarQube("scopeId", CONNECTION_ID, serverWithHotspot.baseUrl(), "projectKey").build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithHotspot)
      .withUnboundConfigScope("scopeId")
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = requestGetOpenHotspotWithParams("server=" + urlEncode(serverWithHotspot.baseUrl()) + "&project=projectKey&hotspot=key");

    assertThat(statusCode).isEqualTo(200);
    assertThat(fakeClient.getMessagesToShow()).isEmpty();
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fakeClient.getHotspotToShowByConfigScopeId()).containsOnlyKeys("scopeId"));
    assertThat(fakeClient.getHotspotToShowByConfigScopeId().get("scopeId"))
      .extracting(HotspotDetailsDto::getMessage)
      .containsExactly("msg");
  }

  @Test
  void it_should_display_a_message_when_failing_to_fetch_the_hotspot() {
    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithoutHotspot)
      .withBoundConfigScope("scopeId", CONNECTION_ID, "projectKey")
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = requestGetOpenHotspotWithParams("server=" + urlEncode(serverWithoutHotspot.baseUrl()) + "&project=projectKey&hotspot=key");

    assertThat(statusCode).isEqualTo(200);
    await().atMost(2, TimeUnit.SECONDS).until(() -> !fakeClient.getMessagesToShow().isEmpty());
    assertThat(fakeClient.getMessagesToShow())
      .extracting(ShowMessageParams::getType, ShowMessageParams::getText)
      .containsExactly(tuple("ERROR", "Could not show the hotspot. See logs for more details"));
    assertThat(fakeClient.getHotspotToShowByConfigScopeId()).isEmpty();
  }

  @Test
  void it_should_not_accept_post_method() {
    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, serverWithoutHotspot)
      .withBoundConfigScope("scopeId", CONNECTION_ID, "projectKey")
      .withEmbeddedServer()
      .build(fakeClient);

    var statusCode = requestPostOpenHotspotWithParams("server=" + urlEncode(serverWithoutHotspot.baseUrl()) + "&project=projectKey&hotspot=key");

    assertThat(statusCode).isEqualTo(400);
  }

  private int requestGetOpenHotspotWithParams(String query) {
    var embeddedServerPort = backend.getEmbeddedServerPort();
    var response = backend.getHttpClient(CONNECTION_ID).get("http://localhost:" + embeddedServerPort + "/sonarlint/api/hotspots/show?" + query);
    var statusCode = response.code();
    response.close();
    return statusCode;
  }

  private int requestPostOpenHotspotWithParams(String query) {
    var embeddedServerPort = backend.getEmbeddedServerPort();
    var response = backend.getHttpClient(CONNECTION_ID).post("http://localhost:" + embeddedServerPort + "/sonarlint/api/hotspots/show?" + query, "application/json", "");
    var statusCode = response.code();
    response.close();
    return statusCode;
  }

}
