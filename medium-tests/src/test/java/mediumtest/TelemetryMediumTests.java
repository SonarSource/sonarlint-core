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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryPayloadResponse;
import org.sonarsource.sonarlint.core.telemetry.InternalDebug;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorageManager;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class TelemetryMediumTests {

  @RegisterExtension
  static WireMockExtension telemetryEndpointMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private SonarLintTestRpcServer backend;
  private String oldValue;
  private boolean oldDebugValue;

  @BeforeAll
  static void mockTelemetryEndpoint() {
    System.setProperty("sonarlint.internal.telemetry.endpoint", telemetryEndpointMock.baseUrl() + "/sonarlint-telemetry");
    System.setProperty("sonarlint.internal.telemetry.initialDelay", "0");
    telemetryEndpointMock.stubFor(post("/sonarlint-telemetry").willReturn(aResponse().withStatus(200)));
  }

  @AfterAll
  static void clearTelemetryEndpoint() {
    System.clearProperty("sonarlint.internal.telemetry.endpoint");
    System.clearProperty("sonarlint.internal.telemetry.initialDelay");
  }

  @BeforeEach
  void saveTelemetryFlag() {
    this.oldDebugValue = InternalDebug.isEnabled();
    InternalDebug.setEnabled(true);
    oldValue = System.getProperty("sonarlint.telemetry.disabled");
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();

    if (oldValue == null) {
      System.clearProperty("sonarlint.telemetry.disabled");
    } else {
      System.setProperty("sonarlint.telemetry.disabled", oldValue);
    }
    InternalDebug.setEnabled(oldDebugValue);
  }

  @Test
  void it_should_not_create_telemetry_file_if_telemetry_disabled_by_system_property() throws ExecutionException, InterruptedException {
    System.setProperty("sonarlint.telemetry.disabled", "true");
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build();

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();

    this.backend.getHotspotService().openHotspotInBrowser(new OpenHotspotInBrowserParams("scopeId", "master", "ab12ef45"));
    assertThat(backend.telemetryFilePath()).doesNotExist();
  }

  @Test
  void it_should_create_telemetry_file_if_telemetry_enabled() throws ExecutionException, InterruptedException {
    System.clearProperty("sonarlint.telemetry.disabled");

    var fakeClient = newFakeClient()
      .build();

    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build(fakeClient);

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isTrue();

    this.backend.getHotspotService().openHotspotInBrowser(new OpenHotspotInBrowserParams("scopeId", "master", "ab12ef45"));
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).isNotEmptyFile());


    await().untilAsserted(() -> telemetryEndpointMock.verify(postRequestedFor(urlEqualTo("/sonarlint-telemetry"))
      .withRequestBody(equalToJson("{\n" +
        "  \"sonarlint_version\" : \"1.2.3\",\n" +
        "  \"sonarlint_product\" : \"mediumTests\",\n" +
        "  \"ide_version\" : \"4.5.6\",\n" +
        "  \"platform\" : \"linux\",\n" +
        "  \"architecture\" : \"x64\",\n" +
        "  \"connected_mode_used\" : false,\n" +
        "  \"connected_mode_sonarcloud\" : false\n" +
        "}", true, true))));
  }

  @Test
  void it_should_consider_telemetry_status_in_file() throws ExecutionException, InterruptedException {
    System.clearProperty("sonarlint.telemetry.disabled");
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build();

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isTrue();
    assertThat(backend.telemetryFilePath()).isNotEmptyFile();

    // Emulate another process has disabled telemetry
    var telemetryLocalStorageManager = new TelemetryLocalStorageManager(backend.telemetryFilePath());
    telemetryLocalStorageManager.tryUpdateAtomically(data -> {
      data.setEnabled(false);
    });

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();
  }

  @Test
  void it_should_ping_telemetry_endpoint() throws ExecutionException, InterruptedException {
    System.clearProperty("sonarlint.telemetry.disabled");
    var fakeClient = spy(newFakeClient().build());
    when(fakeClient.getTelemetryPayload()).thenReturn(new TelemetryPayloadResponse(true, false, null, false, emptyList(), emptyList(), emptyMap()));

    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build(fakeClient);

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isTrue();

    telemetryEndpointMock.verify(postRequestedFor(urlEqualTo("/sonarlint-telemetry"))
      .withRequestBody(equalToJson("{\n" +
        "  \"sonarlint_version\" : \"1.2.3\",\n" +
        "  \"sonarlint_product\" : \"mediumTests\",\n" +
        "  \"ide_version\" : \"4.5.6\",\n" +
        "  \"platform\" : \"linux\",\n" +
        "  \"architecture\" : \"x64\",\n" +
        "  \"connected_mode_used\" : true,\n" +
        "  \"connected_mode_sonarcloud\" : false\n" +
        "}", true, true)));
  }

  @Test
  void it_should_disable_telemetry() throws ExecutionException, InterruptedException {
    var fakeClient = spy(newFakeClient().build());
    when(fakeClient.getTelemetryPayload()).thenReturn(new TelemetryPayloadResponse(true, false, null, false, emptyList(), emptyList(), emptyMap()));

    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build(fakeClient);

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isTrue();

    telemetryEndpointMock.verify(postRequestedFor(urlEqualTo("/sonarlint-telemetry"))
      .withRequestBody(equalToJson("{\n" +
        "  \"sonarlint_version\" : \"1.2.3\",\n" +
        "  \"sonarlint_product\" : \"mediumTests\",\n" +
        "  \"ide_version\" : \"4.5.6\",\n" +
        "  \"platform\" : \"linux\",\n" +
        "  \"architecture\" : \"x64\",\n" +
        "  \"connected_mode_used\" : true,\n" +
        "  \"connected_mode_sonarcloud\" : false\n" +
        "}", true, true)));

    backend.getTelemetryService().disableTelemetry();

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();
  }

  @Test
  void it_should_enable_disabled_telemetry() throws ExecutionException, InterruptedException {
    it_should_disable_telemetry();
    backend.getTelemetryService().enableTelemetry();
    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isTrue();
  }

  @Test
  void it_should_not_crash_when_cannot_build_payload() {
    var fakeClient = spy(newFakeClient().build());
    when(fakeClient.getTelemetryPayload()).thenThrow(new IllegalStateException("Unexpected error"));
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build(fakeClient);
    assertThat(telemetryEndpointMock.getAllServeEvents()).isEmpty();
  }

  @Test
  void failed_upload_payload_should_log_if_debug() {
    InternalDebug.setEnabled(true);

    var fakeClient = spy(newFakeClient().build());
    when(fakeClient.getTelemetryPayload()).thenThrow(new IllegalStateException("Unexpected error"));
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build(fakeClient);

    assertThat(telemetryEndpointMock.getAllServeEvents()).isEmpty();
    assertThat(fakeClient.getLogs()).anyMatch(logParams -> logParams.getMessage().equals("Failed to fetch telemetry payload"));
  }

}
