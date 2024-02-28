/*
 * SonarLint Core - Medium Tests
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
package mediumtest.http;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.GetOrganizationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarcloud.ws.Organizations;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static testutils.TestUtils.protobufBody;

class TimeoutMediumTests {

  @RegisterExtension
  static WireMockExtension sonarcloudMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private SonarLintRpcServer backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    System.clearProperty("sonarlint.http.responseTimeout");
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void it_should_timeout_on_long_response() {
    var fakeClient = newFakeClient()
      .build();
    backend = newBackend()
      .withHttpResponseTimeout(Duration.ofSeconds(1))
      .withSonarCloudUrl(sonarcloudMock.baseUrl())
      .build(fakeClient);
    sonarcloudMock.stubFor(get("/api/organizations/search.protobuf?organizations=myOrg&ps=500&p=1")
      .willReturn(aResponse().withStatus(200)
        .withFixedDelay(2000)
        .withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder()
          .addOrganizations(Organizations.Organization.newBuilder()
            .setKey("myCustom")
            .setName("orgName")
            .setDescription("orgDesc")
            .build())
          .build()))));

    var future = this.backend.getConnectionService().getOrganization(new GetOrganizationParams(Either.forLeft(new TokenDto("token")), "myOrg"));

    assertThat(future)
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class);
  }

}
