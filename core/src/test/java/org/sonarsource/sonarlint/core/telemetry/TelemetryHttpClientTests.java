/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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

import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SystemStubsExtension.class)
class TelemetryHttpClientTests {

  private TelemetryHttpClient underTest;

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private final TelemetryClientAttributesProvider attributes = mock(TelemetryClientAttributesProvider.class);

  @BeforeEach
  public void setUp() {
    when(attributes.nodeVersion()).thenReturn(Optional.empty());
    underTest = new TelemetryHttpClient("product", "version", "ideversion", MockWebServerExtension.httpClient(), mockServer.url("/"));
  }

  @AfterAll
  public static void after() {
    // to avoid conflicts with SonarLintLogging
    new LogTester().setLevel(LoggerLevel.TRACE);
  }

  @Test
  void opt_out() throws Exception {
    mockServer.addResponse("/", new MockResponse());
    underTest.optOut(new TelemetryLocalStorage(), attributes);
    RecordedRequest takeRequest = mockServer.takeRequest();
    assertThat(takeRequest.getMethod()).isEqualTo("DELETE");
    assertThat(takeRequest.getBody().readUtf8())
      .matches("\\{\"days_since_installation\":0,\"days_of_use\":0,\"sonarlint_version\":\"version\",\"sonarlint_product\":\"product\",\"ide_version\":\"ideversion\",.*\\}");
  }

  @Test
  void upload() throws Exception {
    mockServer.addResponse("/", new MockResponse());
    underTest.upload(new TelemetryLocalStorage(), attributes);
    RecordedRequest takeRequest = mockServer.takeRequest();
    assertThat(takeRequest.getMethod()).isEqualTo("POST");
    assertThat(takeRequest.getBody().readUtf8())
      .matches("\\{\"days_since_installation\":0,\"days_of_use\":0,\"sonarlint_version\":\"version\",\"sonarlint_product\":\"product\",\"ide_version\":\"ideversion\",.*\\}");
  }

  @Test
  void should_not_crash_when_cannot_upload() throws Exception {
    underTest.upload(new TelemetryLocalStorage(), attributes);
    RecordedRequest takeRequest = mockServer.takeRequest();
    assertThat(takeRequest.getMethod()).isEqualTo("POST");
  }

  @Test
  void should_not_crash_when_cannot_opt_out() throws Exception {
    underTest.optOut(new TelemetryLocalStorage(), attributes);
    RecordedRequest takeRequest = mockServer.takeRequest();
    assertThat(takeRequest.getMethod()).isEqualTo("DELETE");
  }

  @Test
  void should_not_crash_when_cannot_build_payload_upload() throws Exception {
    when(attributes.nodeVersion()).thenThrow(new IllegalStateException("Unexpected error"));

    underTest.upload(new TelemetryLocalStorage(), attributes);

    assertThat(mockServer.getRequestCount()).isZero();
  }

  @Test
  void should_not_crash_when_cannot_build_payload_optout() throws Exception {
    when(attributes.nodeVersion()).thenThrow(new IllegalStateException("Unexpected error"));

    underTest.optOut(new TelemetryLocalStorage(), attributes);

    assertThat(mockServer.getRequestCount()).isZero();
  }

  @Test
  void failed_upload_should_log_if_debug(EnvironmentVariables env) {
    env.set("SONARLINT_INTERNAL_DEBUG", "true");
    underTest.upload(new TelemetryLocalStorage(), attributes);
    assertThat(logTester.logs(LoggerLevel.ERROR)).anyMatch(l -> l.matches("Failed to upload telemetry data: .*code=404.*"));
  }

  @Test
  void failed_optout_should_log_if_debug(EnvironmentVariables env) {
    env.set("SONARLINT_INTERNAL_DEBUG", "true");
    underTest.optOut(new TelemetryLocalStorage(), attributes);
    assertThat(logTester.logs(LoggerLevel.ERROR)).anyMatch(l -> l.matches("Failed to upload telemetry opt-out: .*code=404.*"));
  }

  @Test
  void failed_upload_payload_should_log_if_debug(EnvironmentVariables env) {
    env.set("SONARLINT_INTERNAL_DEBUG", "true");
    when(attributes.nodeVersion()).thenThrow(new IllegalStateException("Unexpected error"));

    underTest.upload(new TelemetryLocalStorage(), attributes);

    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("Failed to upload telemetry data");
  }

  @Test
  void failed_optout_payload_should_log_if_debug(EnvironmentVariables env) {
    env.set("SONARLINT_INTERNAL_DEBUG", "true");
    when(attributes.nodeVersion()).thenThrow(new IllegalStateException("Unexpected error"));

    underTest.optOut(new TelemetryLocalStorage(), attributes);

    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("Failed to upload telemetry opt-out");
  }
}
