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
package mediumtest.connection;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.FuzzySearchUserOrganizationsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.GetOrganizationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.OrganizationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarcloud.ws.Organizations;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static testutils.TestUtils.protobufBody;

class OrganizationMediumTests {

  private SonarLintTestRpcServer backend;

  @RegisterExtension
  static WireMockExtension sonarcloudMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @Test
  void it_should_list_empty_user_organizations() throws ExecutionException, InterruptedException {
    var fakeClient = newFakeClient()
      .build();
    backend = newBackend()
      .withSonarCloudUrl(sonarcloudMock.baseUrl())
      .build(fakeClient);
    sonarcloudMock.stubFor(get("/api/organizations/search.protobuf?member=true&ps=500&p=1")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder()
        .build()))));

    var details = backend.getConnectionService().listUserOrganizations(new ListUserOrganizationsParams(Either.forLeft(new TokenDto("token"))));

    assertThat(details.get().getUserOrganizations()).isEmpty();
  }

  @Test
  void it_should_list_user_organizations() throws ExecutionException, InterruptedException {
    backend = newBackend()
      .withSonarCloudUrl(sonarcloudMock.baseUrl())
      .build();
    sonarcloudMock.stubFor(get("/api/organizations/search.protobuf?member=true&ps=500&p=1")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder()
        .addOrganizations(Organizations.Organization.newBuilder()
          .setKey("orgKey1")
          .setName("orgName1")
          .setDescription("orgDesc1")
          .build())
        .addOrganizations(Organizations.Organization.newBuilder()
          .setKey("orgKey2")
          .setName("orgName2")
          .setDescription("orgDesc2")
          .build())
        .build()))));
    sonarcloudMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withStatus(200).withBody("{\"id\": \"20160308094653\",\"version\": \"8.0\",\"status\": " +
        "\"UP\"}")));
    sonarcloudMock.stubFor(get("/api/organizations/search.protobuf?member=true&ps=500&p=2")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder().build()))));

    var details = backend.getConnectionService().listUserOrganizations(new ListUserOrganizationsParams(Either.forLeft(new TokenDto("token"))));

    assertThat(details.get().getUserOrganizations()).extracting(OrganizationDto::getKey, OrganizationDto::getName, OrganizationDto::getDescription)
      .containsExactlyInAnyOrder(
        tuple("orgKey1", "orgName1", "orgDesc1"),
        tuple("orgKey2", "orgName2", "orgDesc2"));

    sonarcloudMock.verify(getRequestedFor(urlEqualTo("/api/organizations/search.protobuf?member=true&ps=500&p=1"))
      .withHeader("Authorization", equalTo("Bearer token")));
  }

  @Test
  void it_should_get_organizations_by_key() throws ExecutionException, InterruptedException {
    backend = newBackend()
      .withSonarCloudUrl(sonarcloudMock.baseUrl())
      .build();
    sonarcloudMock.stubFor(get("/api/organizations/search.protobuf?organizations=myCustomOrg&ps=500&p=1")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder()
        .addOrganizations(Organizations.Organization.newBuilder()
          .setKey("myCustom")
          .setName("orgName")
          .setDescription("orgDesc")
          .build())
        .build()))));
    sonarcloudMock.stubFor(get("/api/organizations/search.protobuf?organizations=myCustomOrg&ps=500&p=2")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder().build()))));

    var details = backend.getConnectionService().getOrganization(new GetOrganizationParams(Either.forRight(new UsernamePasswordDto("user", "pwd")), "myCustomOrg"));

    var organization = details.get().getOrganization();
    assertThat(organization.getKey()).isEqualTo("myCustom");
    assertThat(organization.getName()).isEqualTo("orgName");
    assertThat(organization.getDescription()).isEqualTo("orgDesc");

    sonarcloudMock.verify(getRequestedFor(urlEqualTo("/api/organizations/search.protobuf?organizations=myCustomOrg&ps=500&p=1"))
      .withHeader("Authorization", equalTo("Basic " + Base64.getEncoder().encodeToString("user:pwd".getBytes(StandardCharsets.UTF_8)))));
  }

  @Test
  void it_should_fuzzy_search_and_cache_organizations_on_sonarcloud() {
    backend = newBackend()
      .withSonarCloudUrl(sonarcloudMock.baseUrl())
      .build();
    sonarcloudMock.stubFor(get("/api/organizations/search.protobuf?member=true&ps=500&p=1")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder()
        .addOrganizations(Organizations.Organization.newBuilder()
          .setKey("org-foo1")
          .setName("My Company Org Foo 1")
          .setDescription("orgDesc 1")
          .build())
        .addOrganizations(Organizations.Organization.newBuilder()
          .setKey("org-foo2")
          .setName("My Company Org Foo 2")
          .setDescription("orgDesc 2")
          .build())
        .addOrganizations(Organizations.Organization.newBuilder()
          .setKey("org-bar")
          .setName("My Company Org Bar")
          .setDescription("orgDesc 3")
          .build())
        .build()))));
    sonarcloudMock.stubFor(get("/api/organizations/search.protobuf?member=true&ps=500&p=2")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder().build()))));

    var credentials = Either.<TokenDto, UsernamePasswordDto>forRight(new UsernamePasswordDto("user", "pwd"));
    var emptySearch = backend.getConnectionService().fuzzySearchUserOrganizations(new FuzzySearchUserOrganizationsParams(credentials, "")).join();
    assertThat(emptySearch.getTopResults())
      .isEmpty();

    var searchMy = backend.getConnectionService().fuzzySearchUserOrganizations(new FuzzySearchUserOrganizationsParams(credentials, "My")).join();
    assertThat(searchMy.getTopResults())
      .extracting(OrganizationDto::getKey, OrganizationDto::getName)
      .containsExactly(
        Assertions.tuple("org-bar", "My Company Org Bar"),
        Assertions.tuple("org-foo1", "My Company Org Foo 1"),
        Assertions.tuple("org-foo2", "My Company Org Foo 2"));

    var searchFooByName = backend.getConnectionService().fuzzySearchUserOrganizations(new FuzzySearchUserOrganizationsParams(credentials, "Foo")).join();
    assertThat(searchFooByName.getTopResults())
      .extracting(OrganizationDto::getKey, OrganizationDto::getName)
      .containsExactly(
        Assertions.tuple("org-foo1", "My Company Org Foo 1"),
        Assertions.tuple("org-foo2", "My Company Org Foo 2"));

    var searchBarByKey = backend.getConnectionService().fuzzySearchUserOrganizations(new FuzzySearchUserOrganizationsParams(credentials, "org-bar")).join();
    assertThat(searchBarByKey.getTopResults())
      .extracting(OrganizationDto::getKey, OrganizationDto::getName)
      .containsExactly(
        Assertions.tuple("org-bar", "My Company Org Bar"));

    // Verify that the cache is used
    sonarcloudMock.verify(exactly(1), getRequestedFor(urlEqualTo("/api/organizations/search.protobuf?member=true&ps=500&p=1")));
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

}
