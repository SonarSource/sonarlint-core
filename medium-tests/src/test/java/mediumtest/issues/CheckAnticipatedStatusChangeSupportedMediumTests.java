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
package mediumtest.issues;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckAnticipatedStatusChangeSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckAnticipatedStatusChangeSupportedResponse;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;

class CheckAnticipatedStatusChangeSupportedMediumTests {

  @RegisterExtension
  public final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();

  @AfterEach
  void tearDown() {
    mockWebServerExtension.shutdown();
  }

  @SonarLintTest
  void it_should_fail_when_the_connection_is_unknown(SonarLintTestHarness harness) {
    var backend = harness.newBackend().build();

    assertThat(checkAnticipatedStatusChangeSupported(backend, "configScopeId"))
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .withMessage("No binding for config scope 'configScopeId'");
  }

  @SonarLintTest
  void it_should_not_be_available_for_sonarcloud(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarCloudUrl(mockWebServerExtension.endpointParams().getBaseUrl())
      .withSonarCloudConnection("connectionId", "orgKey")
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    assertThat(checkAnticipatedStatusChangeSupported(backend, "configScopeId"))
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckAnticipatedStatusChangeSupportedResponse::isSupported)
      .isEqualTo(false);
  }

  @SonarLintTest
  void it_should_not_be_available_for_sonarqube_prior_to_10_2(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.1"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    assertThat(checkAnticipatedStatusChangeSupported(backend, "configScopeId"))
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckAnticipatedStatusChangeSupportedResponse::isSupported)
      .isEqualTo(false);
  }

  @SonarLintTest
  void it_should_be_available_for_sonarqube_10_2_plus(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(),  storage -> storage.withServerVersion("10.2"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    assertThat(checkAnticipatedStatusChangeSupported(backend, "configScopeId"))
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckAnticipatedStatusChangeSupportedResponse::isSupported)
      .isEqualTo(true);
  }

  private CompletableFuture<CheckAnticipatedStatusChangeSupportedResponse> checkAnticipatedStatusChangeSupported(SonarLintTestRpcServer backend, String configScopeId) {
    return backend.getIssueService().checkAnticipatedStatusChangeSupported(new CheckAnticipatedStatusChangeSupportedParams(configScopeId));
  }
}
