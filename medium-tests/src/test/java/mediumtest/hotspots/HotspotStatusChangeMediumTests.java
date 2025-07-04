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
package mediumtest.hotspots;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.Disabled;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.ChangeHotspotStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;
import static org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.ServerStatus.DOWN;

class HotspotStatusChangeMediumTests {

  @SonarLintTest
  void it_should_fail_the_future_when_the_server_returns_an_error(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().withStatus(DOWN).start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start();

    var response = setStatusToSafe(backend, "configScopeId", "hotspotKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(ResponseErrorException.class);
  }

  @SonarLintTest
  void it_should_do_nothing_when_the_configuration_scope_is_unknown(SonarLintTestHarness harness) {
    var backend = harness.newBackend().start();

    var response = setStatusToSafe(backend, "configScopeId", "hotspotKey");

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
  }

  @SonarLintTest
  void it_should_do_nothing_when_the_configuration_scope_bound_connection_is_unknown(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start();

    var response = setStatusToSafe(backend, "configScopeId", "hotspotKey");

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
  }

  @SonarLintTest
  void it_should_update_the_status_on_sonarcloud_through_the_web_api(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarCloudServer().start();

    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "orgKey")
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start();

    var response = setStatusToSafe(backend, "configScopeId", "hotspotKey");

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    server.getMockServer()
      .verify(WireMock.postRequestedFor(urlEqualTo("/api/hotspots/change_status"))
        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
        .withRequestBody(equalTo("hotspot=hotspotKey&status=REVIEWED&resolution=SAFE")));
  }

  @SonarLintTest
  void it_should_update_the_status_on_sonarqube_through_the_web_api(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start();

    var response = setStatusToSafe(backend, "configScopeId", "hotspotKey");

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    waitAtMost(2, SECONDS).untilAsserted(() -> server.getMockServer()
      .verify(WireMock.postRequestedFor(urlEqualTo("/api/hotspots/change_status"))
        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
        .withRequestBody(equalTo("hotspot=hotspotKey&status=REVIEWED&resolution=SAFE"))));
  }

  @SonarLintTest
  @Disabled("TODO")
  void it_should_update_the_hotspot_status_in_the_storage(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start();

    var response = setStatusToSafe(backend, "configScopeId", "hotspotKey");

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    server.getMockServer()
      .verify(WireMock.postRequestedFor(urlEqualTo("/api/hotspots/change_status"))
        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
        .withRequestBody(equalTo("hotspot=hotspotKey&status=REVIEWED&resolution=SAFE")));
  }

  @SonarLintTest
  void it_should_count_status_change_in_telemetry(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .start();

    var response = setStatusToSafe(backend, "configScopeId", "hotspotKey");

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(backend.telemetryFileContent().hotspotStatusChangedCount()).isEqualTo(1);
  }

  private CompletableFuture<Void> setStatusToSafe(SonarLintTestRpcServer backend, String configScopeId, String hotspotKey) {
    return backend.getHotspotService().changeStatus(new ChangeHotspotStatusParams(configScopeId, hotspotKey, HotspotStatus.SAFE));
  }
}
