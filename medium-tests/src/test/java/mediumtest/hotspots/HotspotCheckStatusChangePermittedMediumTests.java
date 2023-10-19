/*
 * SonarLint Core - Medium Tests
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
import mediumtest.fixtures.ServerFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;

class HotspotCheckStatusChangePermittedMediumTests {

  private SonarLintBackendImpl backend;
  private ServerFixture.Server server;
  private String oldSonarCloudUrl;

  @BeforeEach
  void prepare() {
    oldSonarCloudUrl = System.getProperty("sonarlint.internal.sonarcloud.url");
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (server != null) {
      server.shutdown();
      server = null;
    }

    if (oldSonarCloudUrl == null) {
      System.clearProperty("sonarlint.internal.sonarcloud.url");
    } else {
      System.setProperty("sonarlint.internal.sonarcloud.url", oldSonarCloudUrl);
    }
  }

  @Test
  void it_should_fail_when_the_connection_is_unknown() {
    backend = newBackend().build();

    var response = checkStatusChangePermitted("connectionId");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(IllegalArgumentException.class)
      .withMessage("Connection with ID 'connectionId' does not exist");
  }

  @Test
  void it_should_return_3_statuses_for_sonarcloud() {
    server = newSonarQubeServer().withProject("projectKey", project -> project.withDefaultBranch(branch -> branch.withHotspot("hotspotKey"))).start();
    System.setProperty("sonarlint.internal.sonarcloud.url", server.baseUrl());
    backend = newBackend()
      .withSonarCloudConnection("connectionId", "orgKey")
      .build();

    var response = checkStatusChangePermitted("connectionId");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(true, null, List.of(HotspotStatus.TO_REVIEW, HotspotStatus.FIXED, HotspotStatus.SAFE));
  }

  @Test
  void it_should_return_4_statuses_for_sonarqube() {
    server = newSonarQubeServer().withProject("projectKey", project -> project.withDefaultBranch(branch -> branch.withHotspot("hotspotKey"))).start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .build();

    var response = checkStatusChangePermitted("connectionId");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(true, null, List.of(HotspotStatus.TO_REVIEW, HotspotStatus.ACKNOWLEDGED, HotspotStatus.FIXED, HotspotStatus.SAFE));
  }

  @Test
  void it_should_not_be_changeable_when_permission_missing() {
    server = newSonarQubeServer()
      .withProject("projectKey",
        project -> project.withDefaultBranch(branch -> branch.withHotspot("hotspotKey",
          hotspot -> hotspot.withoutStatusChangePermission())))
      .start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .build();

    var response = checkStatusChangePermitted("connectionId");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(false, "Changing a hotspot's status requires the 'Administer Security Hotspot' permission.",
        List.of(HotspotStatus.TO_REVIEW, HotspotStatus.ACKNOWLEDGED, HotspotStatus.FIXED, HotspotStatus.SAFE));
  }

  private CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(String connectionId) {
    return backend.getHotspotService().checkStatusChangePermitted(new CheckStatusChangePermittedParams(connectionId, "hotspotKey"));
  }
}
