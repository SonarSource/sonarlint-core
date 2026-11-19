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

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifact;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.GetPluginStatusesParams;
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
  private static final String FAKE_CPP_PLUGIN_RESOURCE = "/wiremock/__files/fake-cpp-plugin.jar";

  @SonarLintTest
  void should_notify_downloading_then_active_for_a_plugin_download(SonarLintTestHarness harness) throws IOException {
    // The delay keeps the download running while the test checks the DOWNLOADING state
    harness.fakeBinariesServerBuilder()
      .withDelayedArtifact(BinariesArtifact.CFAMILY_PLUGIN, Duration.ofSeconds(5), fakeCppPlugin())
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.CPP)
      .withUnboundConfigScope(SCOPE_ID)
      .start(client);

    await().atMost(20, SECONDS).untilAsserted(() -> assertThat(notifiedStates(client)).contains(PluginStateDto.DOWNLOADING));

    assertThat(notifiedStates(client)).doesNotContain(PluginStateDto.ACTIVE);
    var statuses = backend.getPluginService().getPluginStatuses(new GetPluginStatusesParams(SCOPE_ID)).join().getPluginStatuses();
    assertThat(cppStateOf(statuses)).isEqualTo(PluginStateDto.DOWNLOADING);

    await().atMost(20, SECONDS).untilAsserted(() -> assertThat(notifiedStates(client)).contains(PluginStateDto.ACTIVE));

    assertThat(client.getPluginStatusChanges())
      .extracting(DidChangePluginStatusesParams::getConfigScopeId)
      .containsOnly(SCOPE_ID);
    assertThat(notifiedStates(client)).containsSubsequence(PluginStateDto.DOWNLOADING, PluginStateDto.ACTIVE);
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

  private static byte[] fakeCppPlugin() throws IOException {
    try (var input = PluginDownloadStatusNotificationMediumTests.class.getResourceAsStream(FAKE_CPP_PLUGIN_RESOURCE)) {
      return Objects.requireNonNull(input, FAKE_CPP_PLUGIN_RESOURCE).readAllBytes();
    }
  }

}
