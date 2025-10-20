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
package mediumtest.log;

import java.time.Duration;
import java.util.List;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.log.SetLogLevelParams;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class LoggingMediumTests {

  @SonarLintTest
  void it_should_print_a_debug_log_when_level_allows(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withLogLevel(LogLevel.TRACE)
      .start(fakeClient);

    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto("id", null, true, "name", null))));

    await().untilAsserted(() -> assertThat(fakeClient.getLogMessages()).contains("Added configuration scope 'id'"));
  }

  @SonarLintTest
  void it_should_not_print_a_log_when_level_does_not_allow(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withLogLevel(LogLevel.OFF)
      .start(fakeClient);

    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto("id", null, true, "name", null))));

    await().during(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(fakeClient.getLogMessages()).isEmpty());
  }

  @SonarLintTest
  void it_should_adjust_the_logging_after_initialization(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withLogLevel(LogLevel.DEBUG)
      .start(fakeClient);
    backend.getLogService().setLogLevel(new SetLogLevelParams(LogLevel.OFF));
    fakeClient.clearLogs();

    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto("id", null, true, "name", null))));

    await().during(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(fakeClient.getLogMessages()).isEmpty());
  }

}
