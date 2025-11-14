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
package mediumtest.gessie;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.telemetry.InternalDebug;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.awaitility.Awaitility.await;

class GessieIntegrationMediumTests {

  private static final String IDE_ENDPOINT = "/ide";
  public static final String FAILED_ONCE = "Failed once";

  @RegisterExtension
  static WireMockExtension gessieEndpointMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private boolean oldDebugValue;

  @BeforeAll
  static void mockGessieEndpoint() {
    gessieEndpointMock.stubFor(post("/ide").willReturn(aResponse().withStatus(202)));
  }

  @BeforeEach
  void saveInternalDebugFlag() {
    this.oldDebugValue = InternalDebug.isEnabled();
    InternalDebug.setEnabled(true);
  }

  @AfterEach
  void tearDown() {
    InternalDebug.setEnabled(oldDebugValue);
  }

  @SonarLintTest
  void it_should_send_startup_event(SonarLintTestHarness harness) throws URISyntaxException, IOException {
    harness.newBackend()
      .withGessieTelemetryEnabled(gessieEndpointMock.baseUrl())
      .start();

    var fileContent = getTestJson("GessieRequest");
    await().untilAsserted(() -> gessieEndpointMock.verify(postRequestedFor(urlEqualTo(IDE_ENDPOINT))
      .withHeader("x-api-key", new EqualToPattern("value"))
      .withRequestBody(equalToJson(fileContent))));
  }

  @SonarLintTest
  void it_should_not_send_anything_if_gessie_telemetry_is_disabled(SonarLintTestHarness harness) {
    harness.newBackend()
      .start();

    await().untilAsserted(() -> gessieEndpointMock.verify(0, anyRequestedFor(urlEqualTo(IDE_ENDPOINT))));
  }

  @SonarLintTest
  void it_should_retry_503_error(SonarLintTestHarness harness) throws URISyntaxException, IOException {
    gessieEndpointMock.stubFor(post("/ide")
      .inScenario("Retry")
      .whenScenarioStateIs(Scenario.STARTED)
      .willSetStateTo(FAILED_ONCE)
      .willReturn(aResponse().withStatus(503)));
    gessieEndpointMock.stubFor(post("/ide")
      .inScenario("Retry")
      .whenScenarioStateIs(FAILED_ONCE)
      .willReturn(aResponse().withStatus(202)));

    harness.newBackend()
      .withGessieTelemetryEnabled(gessieEndpointMock.baseUrl())
      .start();

    var fileContent = getTestJson("GessieRequest");
    await().atMost(15, TimeUnit.SECONDS)
      .untilAsserted(() -> gessieEndpointMock.verify(2, postRequestedFor(urlEqualTo(IDE_ENDPOINT))
      .withHeader("x-api-key", new EqualToPattern("value"))
      .withRequestBody(equalToJson(fileContent))));
  }

  @SonarLintTest
  void it_should_retry_503_error_only_twice(SonarLintTestHarness harness) throws URISyntaxException, IOException {
    gessieEndpointMock.stubFor(post("/ide")
      .willReturn(aResponse().withStatus(503)));

    harness.newBackend()
      .withGessieTelemetryEnabled(gessieEndpointMock.baseUrl())
      .start();

    var fileContent = getTestJson("GessieRequest");
    // Wait for timeframe enough for more than 2 retries.
    await().timeout(15, TimeUnit.SECONDS)
      .pollDelay(12, TimeUnit.SECONDS)
      .untilAsserted(() -> gessieEndpointMock.verify(3, postRequestedFor(urlEqualTo(IDE_ENDPOINT))
      .withHeader("x-api-key", new EqualToPattern("value"))
      .withRequestBody(equalToJson(fileContent))));
  }

  private String getTestJson(String fileName) throws URISyntaxException, IOException {
    var resource = Objects.requireNonNull(getClass().getResource("/response/gessie/GessieIntegrationMediumTests/" + fileName + ".json"))
      .toURI();
    return Files.readString(Path.of(resource));
  }
}
