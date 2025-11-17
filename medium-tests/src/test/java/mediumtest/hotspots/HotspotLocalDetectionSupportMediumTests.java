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
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedResponse;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;

class HotspotLocalDetectionSupportMediumTests {

  @SonarLintTest
  void it_should_fail_when_the_configuration_scope_id_is_unknown(SonarLintTestHarness harness) {
    var backend = harness.newBackend().start();

    var future = backend.getHotspotService().checkLocalDetectionSupported(new CheckLocalDetectionSupportedParams("configScopeId"));

    assertThat(future)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .withMessage("The provided configuration scope does not exist: configScopeId");
  }

  @SonarLintTest
  void it_should_fail_when_the_configuration_scope_is_bound_to_an_unknown_connection(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start();

    var future = backend.getHotspotService().checkLocalDetectionSupported(new CheckLocalDetectionSupportedParams("configScopeId"));

    assertThat(future)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .withMessage("The provided configuration scope is bound to an unknown connection: connectionId");
  }

  @SonarLintTest
  void it_should_not_support_local_detection_in_standalone_mode(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withUnboundConfigScope("configScopeId")
      .start();

    var checkResponse = checkLocalDetectionSupported(backend, "configScopeId");

    assertThat(checkResponse)
      .extracting(CheckLocalDetectionSupportedResponse::isSupported, CheckLocalDetectionSupportedResponse::getReason)
      .containsExactly(false, "The project is not bound, please bind it to SonarQube (Server, Cloud)");
  }

  @SonarLintTest
  void it_should_support_local_detection_when_connected_to_sonarcloud(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarCloudConnection("connectionId", "orgKey")
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start();

    var checkResponse = checkLocalDetectionSupported(backend, "configScopeId");

    assertThat(checkResponse)
      .extracting(CheckLocalDetectionSupportedResponse::isSupported, CheckLocalDetectionSupportedResponse::getReason)
      .containsExactly(true, null);
  }

  @SonarLintTest
  void it_should_support_local_detection_when_connected_to_sonarqube(SonarLintTestHarness harness) {
    var configScopeId = "configScopeId";
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", storage -> storage.withServerVersion("9.9")
        .withProject("projectKey"))
      .withBoundConfigScope(configScopeId, "connectionId", "projectKey")
      .start();

    var checkResponse = checkLocalDetectionSupported(backend, configScopeId);

    assertThat(checkResponse)
      .extracting(CheckLocalDetectionSupportedResponse::isSupported, CheckLocalDetectionSupportedResponse::getReason)
      .containsExactly(true, null);
  }

  private CheckLocalDetectionSupportedResponse checkLocalDetectionSupported(SonarLintTestRpcServer backend, String configScopeId) {
    try {
      return backend.getHotspotService().checkLocalDetectionSupported(new CheckLocalDetectionSupportedParams(configScopeId)).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

}
