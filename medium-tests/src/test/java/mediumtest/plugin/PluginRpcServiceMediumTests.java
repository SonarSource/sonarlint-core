/*
 * SonarLint Core - Medium Tests
 * Copyright (C) SonarSource Sàrl
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
package mediumtest.plugin;

import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.ArtifactSourceDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.GetPluginStatusesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStatusDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;

class PluginRpcServiceMediumTests {

  @SonarLintTest
  void should_return_active_status_for_embedded_plugin(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start();

    var response = backend.getPluginService()
      .getPluginStatuses(new GetPluginStatusesParams(null))
      .join();

    assertThat(response.getPluginStatuses())
      .filteredOn(s -> "Python".equals(s.getPluginName()))
      .singleElement()
      .satisfies(status -> {
        assertThat(status.getState()).isEqualTo(PluginStateDto.ACTIVE);
        assertThat(status.getSource()).isEqualTo(ArtifactSourceDto.EMBEDDED);
        assertThat(status.getActualVersion()).isNull();
        assertThat(status.getOverriddenVersion()).isNull();
      });
  }

  @SonarLintTest
  void should_return_unsupported_status_for_languages_with_no_plugin(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.PYTHON)
      .start();

    var response = backend.getPluginService()
      .getPluginStatuses(new GetPluginStatusesParams(null))
      .join();

    assertThat(response.getPluginStatuses())
      .filteredOn(s -> "Python".equals(s.getPluginName()))
      .singleElement()
      .satisfies(status -> {
        assertThat(status.getState()).isEqualTo(PluginStateDto.UNSUPPORTED);
        assertThat(status.getSource()).isNull();
        assertThat(status.getActualVersion()).isNull();
        assertThat(status.getOverriddenVersion()).isNull();
      });
  }

  @SonarLintTest
  void should_return_one_entry_per_known_language(SonarLintTestHarness harness) {
    var backend = harness.newBackend().start();

    var response = backend.getPluginService()
      .getPluginStatuses(new GetPluginStatusesParams(null))
      .join();

    assertThat(response.getPluginStatuses()).hasSize(SonarLanguage.values().length);
  }

  @SonarLintTest
  void should_return_premium_for_languages_only_available_in_connected_mode(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withExtraEnabledLanguagesInConnectedMode(Language.ABAP)
      .start();

    var response = backend.getPluginService()
      .getPluginStatuses(new GetPluginStatusesParams(null))
      .join();

    assertThat(response.getPluginStatuses())
      .filteredOn(s -> "Abap".equals(s.getPluginName()))
      .singleElement()
      .extracting(PluginStatusDto::getState)
      .isEqualTo(PluginStateDto.PREMIUM);
  }

  @SonarLintTest
  void should_return_synced_status_for_plugin_from_sonarqube_server_connection(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.PYTHON)
      .withSonarQubeConnection("connectionId", storage -> storage
        .withPlugin(TestPlugin.PYTHON))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start();

    var response = backend.getPluginService()
      .getPluginStatuses(new GetPluginStatusesParams("configScopeId"))
      .join();

    assertThat(response.getPluginStatuses())
      .filteredOn(s -> "Python".equals(s.getPluginName()))
      .singleElement()
      .satisfies(status -> {
        assertThat(status.getState()).isEqualTo(PluginStateDto.SYNCED);
        assertThat(status.getSource()).isEqualTo(ArtifactSourceDto.SONARQUBE_SERVER);
      });
  }

  @SonarLintTest
  void should_return_standalone_statuses_when_config_scope_has_no_binding(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .withUnboundConfigScope("configScopeId")
      .start();

    var response = backend.getPluginService()
      .getPluginStatuses(new GetPluginStatusesParams("configScopeId"))
      .join();

    assertThat(response.getPluginStatuses())
      .filteredOn(s -> "Python".equals(s.getPluginName()))
      .singleElement()
      .extracting(PluginStatusDto::getState)
      .isEqualTo(PluginStateDto.ACTIVE);
  }

}
