/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry.gessie;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieEvent;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieMetadata;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.payload.MessagePayload;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieMetadata.GessieSource;
import static org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieMetadata.SonarLintDomain;

class GessieHttpClientTests {

  private static final String IDE_ENDPOINT = "/ide";

  private GessieHttpClient tested;

  @RegisterExtension
  static WireMockExtension mockGessie = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @BeforeEach
  void setUp() {
    tested = new GessieHttpClient(HttpClientProvider.forTesting(), mockGessie.baseUrl());
  }

  @Test
  void should_upload_accepted_payload() throws URISyntaxException, IOException {
    mockGessie.stubFor(post(IDE_ENDPOINT)
      .willReturn(aResponse().withStatus(202)));

    tested.postEvent(getPayload());

    var fileContent = getTestJson("GessieRequest");
    await().untilAsserted(() -> mockGessie.verify(postRequestedFor(urlEqualTo(IDE_ENDPOINT))
        .withHeader("x-api-key", new EqualToPattern("value"))
      .withRequestBody(equalToJson(fileContent))));
  }

  @Test
  void should_handle_400_error_gracefully() throws URISyntaxException, IOException {
    mockGessie.stubFor(post(IDE_ENDPOINT)
      .willReturn(aResponse().withStatus(400)));

    tested.postEvent(new GessieEvent(null, null));

    var invalidRequest = getTestJson("InvalidRequest");
    await().untilAsserted(() -> mockGessie.verify(postRequestedFor(urlEqualTo(IDE_ENDPOINT))
      .withHeader("x-api-key", new EqualToPattern("value"))
      .withRequestBody(equalToJson(invalidRequest))));
  }

  @Test
  void should_handle_403_error_gracefully() throws URISyntaxException, IOException {
    mockGessie.stubFor(post(IDE_ENDPOINT)
      .willReturn(aResponse().withStatus(403)));

    tested.postEvent(getPayload());

    var fileContent = getTestJson("GessieRequest");
    await().untilAsserted(() -> mockGessie.verify(postRequestedFor(urlEqualTo(IDE_ENDPOINT))
      .withHeader("x-api-key", new EqualToPattern("value"))
      .withRequestBody(equalToJson(fileContent))));
  }

  private String getTestJson(String fileName) throws URISyntaxException, IOException {
    var resource = Objects.requireNonNull(getClass().getResource("/response/gessie/GessieHttpClientTest/" + fileName + ".json"))
      .toURI();
    return Files.readString(Path.of(resource));
  }

  private static GessieEvent getPayload() {
    return new GessieEvent(
      new GessieMetadata(UUID.fromString("a36e25e8-5a92-4b5d-93b4-ba0045947b4c"),
        new GessieSource(SonarLintDomain.INTELLIJ),
        "Analytics.Test.TestEvent",
        "1761821877867",
        "0"),
      new MessagePayload("Test event", "test")
    );
  }
}
