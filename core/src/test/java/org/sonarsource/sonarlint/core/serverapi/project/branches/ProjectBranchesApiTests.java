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
package org.sonarsource.sonarlint.core.serverapi.project.branches;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarqube.ws.Hotspots;
import org.sonarqube.ws.ProjectBranches;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.container.connected.exceptions.NotFoundException;
import org.sonarsource.sonarlint.core.serverapi.ApiException;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectBranchesApiTests {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  private ProjectBranchesApi api;

  @BeforeEach
  public void setUp() {
    api = new ServerApi(new ServerApiHelper(mockServer.endpointParams(), MockWebServerExtension.httpClient())).projectBranches();
  }

  @Test
  void it_should_call_the_expected_api_endpoint_when_fetching_hotspot_details() {
    mockServer.addProtobufResponse("/api/project_branches/list.protobuf?project=key", ProjectBranches.ListWsResponse.newBuilder().build());

    api.list("key");

    RecordedRequest recordedRequest = mockServer.takeRequest();
    assertThat(recordedRequest.getPath()).isEqualTo("/api/project_branches/list.protobuf?project=key");
  }

  @Test
  void it_should_urlencode_the_project_key_when_listing_branches() {
    mockServer.addProtobufResponse("/api/project_branches/list.protobuf?project=pro%2Fject", ProjectBranches.ListWsResponse.newBuilder().build());

    api.list("pro/ject");

    RecordedRequest recordedRequest = mockServer.takeRequest();
    assertThat(recordedRequest.getPath()).isEqualTo("/api/project_branches/list.protobuf?project=pro%2Fject");
  }

  @Test
  void it_should_adapt_and_return_the_branches_list() {
    mockServer.addProtobufResponse("/api/project_branches/list.protobuf?project=key", ProjectBranches.ListWsResponse.newBuilder()
      .addBranches(ProjectBranches.Branch.newBuilder().setName("main").setAnalysisDate("2020-03-10T13:37:00+0100").setIsMain(true).build())
      .addBranches(ProjectBranches.Branch.newBuilder().setName("branch1").setAnalysisDate("2019-03-10T13:37:00+0100").setIsMain(false).build())
      .build());

    List<ServerProjectBranch> branches = api.list("key");

    assertThat(branches)
      .extracting(ServerProjectBranch::getName, ServerProjectBranch::isMain, ServerProjectBranch::getLastAnalysisDate)
      .containsExactly(
        tuple("main", true, OffsetDateTime.of(2020, 3, 10, 13, 37, 0, 0, ZoneOffset.ofHours(1))),
        tuple("branch1", false, OffsetDateTime.of(2019, 3, 10, 13, 37, 0, 0, ZoneOffset.ofHours(1)))
      );
  }

  @Test
  void it_should_throw_an_exception_when_the_service_is_not_found() {
    assertThrows(NotFoundException.class, () -> api.list("key"));
  }

  @Test
  void it_should_throw_when_parser_throws_an_exception() {
    mockServer.addProtobufResponse("/api/project_branches/list.protobuf?project=key", Hotspots.ShowWsResponse.newBuilder().setKey("key").build());

    assertThrows(ApiException.class, () -> api.list("key"));
  }
}
