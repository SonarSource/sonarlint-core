/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.LogOutput.Level;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class TelemetryHttpClientTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private TelemetryHttpClient underTest;

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();


  @BeforeEach
  void setUp() {
    underTest = new TelemetryHttpClient("product", "version", "ideversion", "platform", "architecture",
      HttpClientProvider.forTesting().getHttpClient(), sonarqubeMock.baseUrl(), Map.of("additionalKey", "additionalValue"));
  }

  @Test
  void opt_out() {
    sonarqubeMock.stubFor(delete("/")
      .willReturn(aResponse()));

    underTest.optOut(new TelemetryLocalStorage(), getTelemetryLiveAttributesDto());

    sonarqubeMock.verify(deleteRequestedFor(urlEqualTo("/"))
      .withRequestBody(
        equalToJson(
          "{\"days_since_installation\":0,\"days_of_use\":0,\"sonarlint_version\":\"version\",\"sonarlint_product\":\"product\",\"ide_version\":\"ideversion\",\"platform\":\"platform\",\"architecture\":\"architecture\"}",
          true, true)));
  }

  @Test
  void upload() {
    assertTelemetryUploaded(false);
    assertThat(logTester.logs(Level.INFO)).noneMatch(l -> l.matches("Sending telemetry payload."));
  }

  @Test
  void upload_with_telemetry_debug_enabled() {
    assertTelemetryUploaded(true);
    assertThat(logTester.logs(Level.INFO)).anyMatch(l -> l.matches("Sending telemetry payload."));
    assertThat(logTester.logs(Level.INFO)).anyMatch(l -> l.contains("{\"days_since_installation\":0,\"days_of_use\":0,\"sonarlint_version\":\"version\",\"sonarlint_product\":\"product\",\"ide_version\":\"ideversion\",\"platform\":\"platform\",\"architecture\":\"architecture\""));
  }

  private void assertTelemetryUploaded(boolean isDebugEnabled) {
    var spy = spy(underTest);
    doReturn(isDebugEnabled).when(spy).isDebugTelemetryEnabled();
    sonarqubeMock.stubFor(post("/")
      .willReturn(aResponse()));
    spy.upload(new TelemetryLocalStorage(), getTelemetryLiveAttributesDto());

    sonarqubeMock.verify(postRequestedFor(urlEqualTo("/"))
      .withRequestBody(
        equalToJson(
          "{\"days_since_installation\":0,\"days_of_use\":0,\"sonarlint_version\":\"version\",\"sonarlint_product\":\"product\",\"ide_version\":\"ideversion\",\"platform\":\"platform\",\"architecture\":\"architecture\",\"additionalKey\" : \"additionalValue\"}",
          true, true)));
  }

  @Test
  void should_not_crash_when_cannot_upload() {
    sonarqubeMock.stubFor(post("/")
      .willReturn(aResponse().withStatus(500)));

    underTest.upload(new TelemetryLocalStorage(), getTelemetryLiveAttributesDto());

    sonarqubeMock.verify(postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void should_not_crash_when_cannot_opt_out() {
    sonarqubeMock.stubFor(delete("/")
      .willReturn(aResponse().withStatus(500)));

    underTest.optOut(new TelemetryLocalStorage(), getTelemetryLiveAttributesDto());

    sonarqubeMock.verify(deleteRequestedFor(urlEqualTo("/")));
  }

  @Test
  void failed_upload_should_log_if_debug() {
    InternalDebug.setEnabled(true);

    underTest.upload(new TelemetryLocalStorage(), getTelemetryLiveAttributesDto());

    assertThat(logTester.logs(Level.ERROR)).anyMatch(l -> l.matches("Failed to upload telemetry data: .*404.*"));
  }

  @Test
  void failed_optout_should_log_if_debug() {
    InternalDebug.setEnabled(true);

    underTest.optOut(new TelemetryLocalStorage(), getTelemetryLiveAttributesDto());

    assertThat(logTester.logs(Level.ERROR)).anyMatch(l -> l.matches("Failed to upload telemetry opt-out: .*404.*"));
  }

  private static TelemetryLiveAttributes getTelemetryLiveAttributesDto() {
    var serverAttributes = new TelemetryServerLiveAttributes(true, true, false, Collections.emptyList(), Collections.emptyList(), "3.1.7");
    var clientAttributes = new TelemetryClientLiveAttributesResponse(emptyMap());
    return new TelemetryLiveAttributes(serverAttributes, clientAttributes);
  }
}
