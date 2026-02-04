/*
 * SonarLint Core - Medium Tests
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
package mediumtest.hotspots;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;

class HotspotCheckStatusChangePermittedMediumTests {

  @SonarLintTest
  void it_should_fail_when_the_connection_is_unknown(SonarLintTestHarness harness) {
    var backend = harness.newBackend().start();

    var response = checkStatusChangePermitted(backend, "connectionId");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .withMessage("Connection 'connectionId' is not valid");
  }

  @SonarLintTest
  void it_should_return_3_statuses_for_sonarcloud(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("orgKey", organization -> organization
        .withProject("projectKey", project -> project
          .withDefaultBranch(branch -> branch.withHotspot("hotspotKey"))))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "orgKey")
      .start();

    var response = checkStatusChangePermitted(backend, "connectionId");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(true, null, List.of(HotspotStatus.TO_REVIEW, HotspotStatus.FIXED, HotspotStatus.SAFE));
  }

  @SonarLintTest
  void it_should_return_4_statuses_for_sonarqube(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().withProject("projectKey", project -> project.withDefaultBranch(branch -> branch.withHotspot("hotspotKey"))).start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .start();

    var response = checkStatusChangePermitted(backend, "connectionId");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(true, null, List.of(HotspotStatus.TO_REVIEW, HotspotStatus.ACKNOWLEDGED, HotspotStatus.FIXED, HotspotStatus.SAFE));
  }

  @SonarLintTest
  void it_should_not_be_changeable_when_permission_missing(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey",
        project -> project.withDefaultBranch(branch -> branch.withHotspot("hotspotKey",
          HotspotBuilder::withoutStatusChangePermission)))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .start();

    var response = checkStatusChangePermitted(backend, "connectionId");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(false, "Changing a hotspot's status requires the 'Administer Security Hotspot' permission.",
        List.of(HotspotStatus.TO_REVIEW, HotspotStatus.ACKNOWLEDGED, HotspotStatus.FIXED, HotspotStatus.SAFE));
  }

  private CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(SonarLintTestRpcServer backend, String connectionId) {
    return backend.getHotspotService().checkStatusChangePermitted(new CheckStatusChangePermittedParams(connectionId, "hotspotKey"));
  }
}
