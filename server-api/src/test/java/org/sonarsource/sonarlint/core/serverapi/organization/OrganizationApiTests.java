/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.organization;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.commons.testutils.MockWebServerExtension;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarcloud.ws.Organizations;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarcloud.ws.Organizations.Organization;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarcloud.ws.Organizations.SearchWsResponse;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.Paging;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationApiTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private final ProgressMonitor progressMonitor = new ProgressMonitor(null);

  @Test
  void testListUserOrganizationWithMoreThan20Pages() {
    var underTest = new OrganizationApi(new ServerApiHelper(mockServer.endpointParams("myOrg"), MockWebServerExtension.httpClient()));

    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"7.9\",\"status\": \"UP\"}");

    for (var i = 0; i < 21; i++) {
      mockOrganizationsPage(i + 1, 10500);
    }

    var orgs = underTest.listUserOrganizations(progressMonitor);

    assertThat(orgs).hasSize(10500);
  }

  @Test
  void should_get_organization_details() {
    mockServer.addStringResponse("/api/system/status", "{" +
      "\"status\": \"UP\"," +
      "\"version\": \"20.0.0\"" +
      "}");
    mockServer.addProtobufResponse("/api/organizations/search.protobuf?organizations=org%3Akey&ps=500&p=1", SearchWsResponse.newBuilder()
      .addOrganizations(Organization.newBuilder()
        .setKey("orgKey")
        .setName("orgName")
        .setDescription("orgDesc")
        .build())
      .build());
    mockServer.addProtobufResponse("/api/organizations/search.protobuf?organizations=org%3Akey&ps=500&p=2", SearchWsResponse.newBuilder().build());
    var underTest = new OrganizationApi(new ServerApiHelper(mockServer.endpointParams(), MockWebServerExtension.httpClient()));

    var organization = underTest.getOrganization("org:key", progressMonitor);

    assertThat(organization).hasValueSatisfying(org -> {
      assertThat(org.getKey()).isEqualTo("orgKey");
      assertThat(org.getName()).isEqualTo("orgName");
      assertThat(org.getDescription()).isEqualTo("orgDesc");
    });
  }

  private void mockOrganizationsPage(int page, int total) {
    List<Organization> orgs = IntStream.rangeClosed(1, 500)
      .mapToObj(i -> Organization.newBuilder().setKey("org_page" + page + "number" + i).build())
      .collect(Collectors.toList());

    var paging = Paging.newBuilder()
      .setPageSize(500)
      .setTotal(total)
      .setPageIndex(page)
      .build();
    var response = Organizations.SearchWsResponse.newBuilder()
      .setPaging(paging)
      .addAllOrganizations(orgs)
      .build();
    mockServer.addProtobufResponse("/api/organizations/search.protobuf?member=true&ps=500&p=" + page, response);
  }

}
