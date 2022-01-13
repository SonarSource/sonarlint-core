/*
 * SonarLint Server API
 * Copyright (C) 2016-2022 SonarSource SA
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

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarqube.ws.Common.Paging;
import org.sonarqube.ws.Organizations;
import org.sonarqube.ws.Organizations.Organization;
import org.sonarqube.ws.Organizations.SearchWsResponse;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtension;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.util.Progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationApiTests {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  @Test
  void testListUserOrganizationWithMoreThan20Pages() throws IOException {
    OrganizationApi underTest = new OrganizationApi(new ServerApiHelper(mockServer.endpointParams("myOrg"), MockWebServerExtension.httpClient()));

    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"7.9\",\"status\": \"UP\"}");

    for (int i = 0; i < 21; i++) {
      mockOrganizationsPage(i + 1, 10500);
    }

    Progress progress = mock(Progress.class);
    when(progress.subProgress(anyFloat(), anyFloat(), anyString())).thenReturn(progress);
    List<ServerOrganization> orgs = underTest.listUserOrganizations(progress);

    assertThat(orgs).hasSize(10500);
  }

  private void mockOrganizationsPage(int page, int total) throws IOException {
    List<Organization> orgs = IntStream.rangeClosed(1, 500)
      .mapToObj(i -> Organization.newBuilder().setKey("org_page" + page + "number" + i).build())
      .collect(Collectors.toList());

    Paging paging = Paging.newBuilder()
      .setPageSize(500)
      .setTotal(total)
      .setPageIndex(page)
      .build();
    SearchWsResponse response = Organizations.SearchWsResponse.newBuilder()
      .setPaging(paging)
      .addAllOrganizations(orgs)
      .build();
    mockServer.addProtobufResponse("/api/organizations/search.protobuf?member=true&ps=500&p=" + page, response);
  }

}
