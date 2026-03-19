/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.SupportedLanguageViewedParams;
import org.sonarsource.sonarlint.core.telemetry.gessie.GessieSpringConfig;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import java.time.Duration;

import static org.awaitility.Awaitility.await;

class GessieSupportedLanguageViewedMediumTests {

  private static final String IDE_ENDPOINT = "/ide";

  @RegisterExtension
  static WireMockExtension gessieEndpointMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @BeforeEach
  void setUp() {
    System.setProperty("sonarlint.http.retry.interval.seconds", "0");
    System.setProperty(GessieSpringConfig.PROPERTY_GESSIE_API_KEY, "value");
    gessieEndpointMock.stubFor(post(IDE_ENDPOINT).willReturn(aResponse().withStatus(202)));
  }

  @AfterEach
  void tearDown() {
    System.clearProperty("sonarlint.http.retry.interval.seconds");
    System.clearProperty(GessieSpringConfig.PROPERTY_GESSIE_API_KEY);
  }

  @SonarLintTest
  void it_should_send_supported_language_viewed_event_for_unbound_scope(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withGessieTelemetryEnabled(gessieEndpointMock.baseUrl())
      .withUnboundConfigScope("scopeId")
      .start();

    backend.getTelemetryService().supportedLanguageViewed(new SupportedLanguageViewedParams("scopeId"));

    await().untilAsserted(() -> gessieEndpointMock.verify(2, postRequestedFor(urlEqualTo(IDE_ENDPOINT))
      .withHeader("x-api-key", new EqualToPattern("value"))));

    gessieEndpointMock.verify(postRequestedFor(urlEqualTo(IDE_ENDPOINT))
      .withHeader("x-api-key", new EqualToPattern("value"))
      .withRequestBody(equalToJson("""
        {
          "metadata": {
            "event_id": "${json-unit.any-string}",
            "source": { "domain": "SLCore" },
            "event_type": "Analytics.IDE.IDESupportedLanguageViewed",
            "event_timestamp": "${json-unit.any-string}",
            "event_version": "1"
          },
          "event_payload": {
            "local_user_id": "${json-unit.any-string}",
            "sq_ide_version": "1.2.3",
            "os": "${json-unit.any-string}",
            "connection_type": null,
            "user_uuid": null,
            "organization_uuid_v4": null,
            "sqs_installation_id": null
          }
        }
        """, true, true)));
  }

  @SonarLintTest
  void it_should_not_send_supported_language_viewed_event_when_gessie_disabled(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withUnboundConfigScope("scopeId")
      .start();

    backend.getTelemetryService().supportedLanguageViewed(new SupportedLanguageViewedParams("scopeId"));

    await().during(Duration.ofSeconds(1)).untilAsserted(() -> gessieEndpointMock.verify(0, anyRequestedFor(urlEqualTo(IDE_ENDPOINT))));
  }

  @SonarLintTest
  void it_should_send_supported_language_viewed_event_for_sqs_bound_scope(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withGessieTelemetryEnabled(gessieEndpointMock.baseUrl())
      .withSonarQubeConnection("connectionId", storage -> storage.withServerVersion("9.9"))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .start();

    backend.getTelemetryService().supportedLanguageViewed(new SupportedLanguageViewedParams("scopeId"));

    await().untilAsserted(() -> gessieEndpointMock.verify(2, postRequestedFor(urlEqualTo(IDE_ENDPOINT))
      .withHeader("x-api-key", new EqualToPattern("value"))));

    gessieEndpointMock.verify(postRequestedFor(urlEqualTo(IDE_ENDPOINT))
      .withHeader("x-api-key", new EqualToPattern("value"))
      .withRequestBody(equalToJson("""
        {
          "metadata": {
            "event_id": "${json-unit.any-string}",
            "source": { "domain": "SLCore" },
            "event_type": "Analytics.IDE.IDESupportedLanguageViewed",
            "event_timestamp": "${json-unit.any-string}",
            "event_version": "1"
          },
          "event_payload": {
            "local_user_id": "${json-unit.any-string}",
            "sq_ide_version": "1.2.3",
            "os": "${json-unit.any-string}",
            "connection_type": "SQS",
            "user_uuid": null,
            "organization_uuid_v4": null,
            "sqs_installation_id": null
          }
        }
        """, true, true)));
  }

}
