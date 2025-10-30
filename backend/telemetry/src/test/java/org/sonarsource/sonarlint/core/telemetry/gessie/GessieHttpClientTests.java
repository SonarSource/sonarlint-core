package org.sonarsource.sonarlint.core.telemetry.gessie;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
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
import static org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieMetadata.GessieSource;
import static org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieMetadata.SonarLintDomain;

class GessieHttpClientTests {

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
  void upload() throws URISyntaxException, IOException {
    mockGessie.stubFor(post("/")
      .willReturn(aResponse().withStatus(202)));

    tested.postEvent(getPayload());

    var fileContent = getTestResource("GessieRequest");
    mockGessie.verify(postRequestedFor(urlEqualTo("/"))
      .withRequestBody(equalToJson(fileContent)));
  }

  // todo other responses

  private String getTestResource(String fileName) throws URISyntaxException, IOException {
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
