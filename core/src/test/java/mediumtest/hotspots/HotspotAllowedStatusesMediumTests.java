/*
 * SonarLint Core - Implementation
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
package mediumtest.hotspots;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.ListAllowedStatusesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.ListAllowedStatusesResponse;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;

class HotspotAllowedStatusesMediumTests {

  private SonarLintBackendImpl backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void it_should_fail_when_the_connection_is_unknown() {
    backend = newBackend().build();

    var response = listAllowedStatuses("connectionId");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(IllegalArgumentException.class)
      .withMessage("Connection with ID 'connectionId' does not exist");
  }

  @Test
  void it_should_return_3_statuses_for_sonarcloud() {
    backend = newBackend()
      .withSonarCloudConnection("connectionId", "orgKey")
      .build();

    var response = listAllowedStatuses("connectionId");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(ListAllowedStatusesResponse::getAllowedStatuses)
      .isEqualTo(List.of(HotspotStatus.TO_REVIEW, HotspotStatus.FIXED, HotspotStatus.SAFE));
  }

  @Test
  void it_should_return_4_statuses_for_sonarqube() {
    backend = newBackend()
      .withSonarQubeConnection("connectionId", "url")
      .build();

    var response = listAllowedStatuses("connectionId");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(ListAllowedStatusesResponse::getAllowedStatuses)
      .isEqualTo(List.of(HotspotStatus.TO_REVIEW, HotspotStatus.ACKNOWLEDGED, HotspotStatus.FIXED, HotspotStatus.SAFE));
  }

  private CompletableFuture<ListAllowedStatusesResponse> listAllowedStatuses(String connectionId) {
    return backend.getHotspotService().listAllowedStatuses(new ListAllowedStatusesParams(connectionId));
  }
}
