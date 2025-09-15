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
package mediumtest.sca;

import java.util.concurrent.CompletionException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.CheckDependencyRiskSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.CheckDependencyRiskSupportedResponse;
import org.sonarsource.sonarlint.core.serverapi.features.Feature;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class CheckDependencyRisksSupportedMediumTests {

  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";
  private static final String PROJECT_KEY = "projectKey";

  @SonarLintTest
  void it_should_fail_when_config_scope_not_found() {
    var harness = new SonarLintTestHarness();
    var backend = harness.newBackend().start();

    var throwable = catchThrowable(() -> checkSupported(backend, "unknown-scope"));

    assertThat(throwable)
      .isInstanceOf(CompletionException.class)
      .hasCauseInstanceOf(ResponseErrorException.class);
    var responseErrorException = (ResponseErrorException) throwable.getCause();
    assertThat(responseErrorException.getResponseError().getCode()).isEqualTo(SonarLintRpcErrorCode.CONFIG_SCOPE_NOT_FOUND);
    assertThat(responseErrorException.getResponseError().getMessage()).contains("does not exist: unknown-scope");
  }

  @SonarLintTest
  void it_should_fail_when_config_scope_not_bound() {
    var harness = new SonarLintTestHarness();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start();

    var response = checkSupported(backend, CONFIG_SCOPE_ID);

    assertThat(response.isSupported()).isFalse();
    assertThat(response.getReason()).contains("not bound").contains("2025.4");
  }

  @SonarLintTest
  void it_should_fail_when_connection_not_found() {
    var harness = new SonarLintTestHarness();
    var backend = harness.newBackend()
      .withBoundConfigScope(CONFIG_SCOPE_ID, "missing-connection", PROJECT_KEY)
      .start();

    var throwable = catchThrowable(() -> checkSupported(backend, CONFIG_SCOPE_ID));

    assertThat(throwable)
      .isInstanceOf(CompletionException.class)
      .hasCauseInstanceOf(ResponseErrorException.class);
    var responseErrorException = (ResponseErrorException) throwable.getCause();
    assertThat(responseErrorException.getResponseError().getCode()).isEqualTo(SonarLintRpcErrorCode.CONNECTION_NOT_FOUND);
    assertThat(responseErrorException.getResponseError().getMessage()).contains("unknown connection");
  }

  @SonarLintTest
  void it_should_fail_on_sonarcloud() {
    var harness = new SonarLintTestHarness();
    var backend = harness.newBackend()
      .withSonarCloudConnection(CONNECTION_ID)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = checkSupported(backend, CONFIG_SCOPE_ID);

    assertThat(response.isSupported()).isFalse();
    assertThat(response.getReason()).contains("SonarQube Cloud does not yet support dependency risks");
  }

  @SonarLintTest
  void it_should_fail_when_server_version_too_old() {
    var harness = new SonarLintTestHarness();
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage
          .withServerFeature(Feature.SCA)
          .withServerVersion("2025.3"))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = checkSupported(backend, CONFIG_SCOPE_ID);

    assertThat(response.isSupported()).isFalse();
    assertThat(response.getReason()).contains("lower than the minimum supported version 2025.4");
  }

  @SonarLintTest
  void it_should_fail_when_sca_disabled() {
    var harness = new SonarLintTestHarness();
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage
          .withServerVersion("2025.4"))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = checkSupported(backend, CONFIG_SCOPE_ID);

    assertThat(response.isSupported()).isFalse();
    assertThat(response.getReason()).contains("does not have Advanced Security enabled");
  }

  @SonarLintTest
  void it_should_fail_when_server_info_missing() {
    var harness = new SonarLintTestHarness();
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var throwable = catchThrowable(() -> checkSupported(backend, CONFIG_SCOPE_ID));

    assertThat(throwable)
      .isInstanceOf(CompletionException.class)
      .hasCauseInstanceOf(ResponseErrorException.class);
    var responseErrorException = (ResponseErrorException) throwable.getCause();
    assertThat(responseErrorException.getResponseError().getCode()).isEqualTo(SonarLintRpcErrorCode.CONNECTION_NOT_FOUND);
    assertThat(responseErrorException.getResponseError().getMessage()).contains("Could not retrieve server information");
  }

  @SonarLintTest
  void it_should_succeed_when_all_conditions_met() {
    var harness = new SonarLintTestHarness();
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage
          .withServerFeature(Feature.SCA)
          .withServerVersion("2025.4"))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = checkSupported(backend, CONFIG_SCOPE_ID);

    assertThat(response.isSupported()).isTrue();
    assertThat(response.getReason()).isNull();
  }

  @SonarLintTest
  void it_should_succeed_with_newer_server_version() {
    var harness = new SonarLintTestHarness();
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage
          .withServerFeature(Feature.SCA)
          .withServerVersion("2025.5"))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = checkSupported(backend, CONFIG_SCOPE_ID);

    assertThat(response.isSupported()).isTrue();
    assertThat(response.getReason()).isNull();
  }

  private CheckDependencyRiskSupportedResponse checkSupported(SonarLintTestRpcServer backend, String configScopeId) {
    return backend.getDependencyRiskService().checkSupported(new CheckDependencyRiskSupportedParams(configScopeId)).join();
  }
}
