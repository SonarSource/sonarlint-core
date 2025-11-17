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
package mediumtest;

import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingMode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AcceptedBindingSuggestionParams;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class BindingTelemetryMediumTests {

  private static final String CONNECTION_ID = "connectionId";
  private static final String CONFIG_SCOPE_ID = "scopeId";
  private static final String PROJECT_KEY = "projectKey";

  @SonarLintTest
  void should_count_new_binding_from_suggestion_remote_url(SonarLintTestHarness harness) {
    var backend = setupBackendUnboundWithTelemetry(harness);

    backend.getTelemetryService().acceptedBindingSuggestion(new AcceptedBindingSuggestionParams(
      BindingSuggestionOrigin.REMOTE_URL));

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getNewBindingsRemoteUrlCount()).isEqualTo(1));
  }

  @SonarLintTest
  void should_count_new_binding_from_suggestion_project_name(SonarLintTestHarness harness) {
    var backend = setupBackendUnboundWithTelemetry(harness);

    backend.getTelemetryService().acceptedBindingSuggestion(new AcceptedBindingSuggestionParams(
      BindingSuggestionOrigin.PROJECT_NAME));

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getNewBindingsProjectNameCount()).isEqualTo(1));
  }

  @SonarLintTest
  void should_count_new_binding_from_suggestion_shared_configuration(SonarLintTestHarness harness) {
    var backend = setupBackendUnboundWithTelemetry(harness);

    backend.getTelemetryService().acceptedBindingSuggestion(new AcceptedBindingSuggestionParams(
      BindingSuggestionOrigin.SHARED_CONFIGURATION));

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getNewBindingsSharedConfigurationCount()).isEqualTo(1));
  }

  @SonarLintTest
  void should_count_new_binding_from_suggestion_properties_file(SonarLintTestHarness harness) {
    var backend = setupBackendUnboundWithTelemetry(harness);

    backend.getTelemetryService().acceptedBindingSuggestion(new AcceptedBindingSuggestionParams(
      BindingSuggestionOrigin.PROPERTIES_FILE));

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getNewBindingsPropertiesFileCount()).isEqualTo(1));
  }

  @SonarLintTest
  void should_not_count_when_suggestion_origin_is_missing(SonarLintTestHarness harness) {
    var backend = setupBackendUnboundWithTelemetry(harness);

    backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(
      CONFIG_SCOPE_ID,
      new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY, true),
      BindingMode.FROM_SUGGESTION,
      null
    ));

    await().untilAsserted(() -> {
      assertThat(backend.telemetryFileContent().getNewBindingsRemoteUrlCount()).isZero();
      assertThat(backend.telemetryFileContent().getNewBindingsProjectNameCount()).isZero();
      assertThat(backend.telemetryFileContent().getNewBindingsSharedConfigurationCount()).isZero();
      assertThat(backend.telemetryFileContent().getNewBindingsPropertiesFileCount()).isZero();
    });
  }

  private SonarLintTestRpcServer setupBackendUnboundWithTelemetry(SonarLintTestHarness harness) {
    return harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withTelemetryEnabled()
      .start();
  }
}
