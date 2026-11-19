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

import java.util.List;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifact;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStatusDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidChangePluginStatusesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture.FakeSonarLintRpcClient;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Pins the {@code didChangePluginStatuses} notifications sent for one plugin download in standalone mode.
 * No test opens a file or starts an analysis: the notifications come from the download alone.
 */
class PluginDownloadStatusNotificationMediumTests {

  private static final String SCOPE_ID = "scopeId";

  @SonarLintTest
  void should_notify_downloading_then_active_for_a_plugin_download(SonarLintTestHarness harness) {
    harness.fakeBinariesServerBuilder()
      .withArtifactFromResource(BinariesArtifact.CFAMILY_PLUGIN, "fake-cpp-plugin.jar")
      .start();
    var client = harness.newFakeClient().build();
    harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.CPP)
      .withUnboundConfigScope(SCOPE_ID)
      .start(client);

    await().atMost(20, SECONDS).untilAsserted(() -> assertThat(notifiedStates(client))
      .containsSubsequence(PluginStateDto.DOWNLOADING, PluginStateDto.ACTIVE));

    assertThat(client.getPluginStatusChanges())
      .extracting(DidChangePluginStatusesParams::getConfigScopeId)
      .containsOnly(SCOPE_ID);
  }

  private static List<PluginStateDto> notifiedStates(FakeSonarLintRpcClient client) {
    return client.getPluginStatusChanges().stream()
      .filter(change -> SCOPE_ID.equals(change.getConfigScopeId()))
      .map(change -> cppStateOf(change.getPluginStatuses()))
      .toList();
  }

  private static PluginStateDto cppStateOf(List<PluginStatusDto> statuses) {
    return statuses.stream()
      .filter(status -> status.getLanguage() == Language.CPP)
      .map(PluginStatusDto::getState)
      .findFirst()
      .orElseThrow();
  }

}
