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
package mediumtest.labs;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.labs.JoinIdeLabsProgramParams;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.sonarsource.sonarlint.core.labs.IdeLabsSpringConfig.PROPERTY_IDE_LABS_SUBSCRIPTION_URL;

public class IdeLabsMediumTests {
  @RegisterExtension
  static WireMockExtension marketingCloudMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @AfterAll
  static void tearDown() {
    System.clearProperty(PROPERTY_IDE_LABS_SUBSCRIPTION_URL);
  }

  @SonarLintTest
  void it_should_join_labs_successfully(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withIdeLabsSubscriptionUrl(marketingCloudMock.baseUrl())
      .start();
    marketingCloudMock.stubFor(post("/")
      .willReturn(okJson("{ \"valid_email\": true }")));
    var sampleEmail = "example@example.com";
    var ideName = "VSCode";


    var response = backend.getIdeLabsService().joinIdeLabsProgram(new JoinIdeLabsProgramParams(sampleEmail, ideName));

    assertThat(response).succeedsWithin(2, TimeUnit.SECONDS);
    assertMarketingCloudEndpointCalled(sampleEmail, ideName);
    assertThat(response.join().isSuccess()).isTrue();
  }

  @SonarLintTest
  void it_should_fail_to_join_labs_with_invalid_email(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withIdeLabsSubscriptionUrl(marketingCloudMock.baseUrl())
      .start();
    marketingCloudMock.stubFor(post("/")
      .willReturn(okJson("{ \"valid_email\": false }")));
    var sampleEmail = "invalid-email";
    var ideName = "VSCode";

    var response = backend.getIdeLabsService().joinIdeLabsProgram(new JoinIdeLabsProgramParams(sampleEmail, ideName));

    assertThat(response).succeedsWithin(2, TimeUnit.SECONDS);
    assertMarketingCloudEndpointCalled(sampleEmail, ideName);
    assertThat(response.join().isSuccess()).isFalse();
    assertThat(response.join().getMessage()).contains("The provided email address is not valid. Please enter a valid email address.");
  }

  @SonarLintTest
  void it_should_handle_server_error_when_joining_labs(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withIdeLabsSubscriptionUrl(marketingCloudMock.baseUrl())
      .start();
    marketingCloudMock.stubFor(post("/")
      .willReturn(aResponse().withStatus(500)));
    var sampleEmail = "example@example.com";
    var ideName = "VSCode";

    var response = backend.getIdeLabsService().joinIdeLabsProgram(new JoinIdeLabsProgramParams(sampleEmail, ideName));

    assertThat(response).succeedsWithin(2, TimeUnit.SECONDS);
    assertMarketingCloudEndpointCalled(sampleEmail, ideName);
    assertThat(response.join().isSuccess()).isFalse();
    assertThat(response.join().getMessage()).contains("An unexpected error occurred. Server responded with status code: 500");
  }

  void assertMarketingCloudEndpointCalled(String email, String source) {
    var expectedRequestBody = String.format("""
      {
        "email": "%s",
        "source": "%s"
      }
      """, email, source);

    marketingCloudMock.verify(postRequestedFor(urlEqualTo("/"))
      .withHeader("Content-Type", equalTo("application/json"))
      .withRequestBody(equalToJson(expectedRequestBody)));
  }
}
