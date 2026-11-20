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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.ArtifactSourceDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.GetPluginStatusesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStatusDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidChangePluginStatusesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture.FakeSonarLintRpcClient;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Checks {@code didChangePluginStatuses} notifications and the statuses sent to each scope.
 * No test opens a file or starts an analysis: the notifications come from the download alone.
 */
class PluginDownloadStatusNotificationMediumTests {

  private static final String SCOPE_ID = "scopeId";
  private static final String CONNECTION_ID = "connectionId";

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

    await().atMost(20, SECONDS).untilAsserted(() -> assertThat(notifiedStates(client, SCOPE_ID, Language.CPP))
      .containsSubsequence(PluginStateDto.DOWNLOADING, PluginStateDto.ACTIVE));

    assertThat(client.getPluginStatusChanges())
      .extracting(DidChangePluginStatusesParams::getConfigScopeId)
      .containsOnly(SCOPE_ID);
  }

  @SonarLintTest
  void should_notify_only_scopes_bound_to_the_connection_when_a_server_plugin_downloads(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().withPlugin(TestPlugin.JAVA).start();
    var otherServer = harness.newFakeSonarQubeServer().start();
    var client = harness.newFakeClient().withToken(CONNECTION_ID, "token").withToken("otherConnection", "token").build();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withSonarQubeConnection("otherConnection", otherServer)
      .withExtraEnabledLanguagesInConnectedMode(Language.JAVA)
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, "project1")
      .withBoundConfigScope("secondBoundScope", CONNECTION_ID, "project2")
      .withChildConfigScope("inheritedScope", SCOPE_ID)
      .withBoundConfigScope("otherConnectionScope", "otherConnection", "otherProject")
      .withUnboundConfigScope("unboundScope")
      .start(client);

    backend.getPluginService().getPluginStatuses(new GetPluginStatusesParams(SCOPE_ID)).join();

    var boundScopeIds = List.of(SCOPE_ID, "secondBoundScope", "inheritedScope");
    await().atMost(20, SECONDS).untilAsserted(() -> {
      for (var scopeId : boundScopeIds) {
        assertThat(notifiedStates(client, scopeId, Language.JAVA))
          .containsSubsequence(PluginStateDto.DOWNLOADING, PluginStateDto.SYNCED);
      }
    });

    var serverDownloadNotifications = client.getPluginStatusChanges().stream()
      .filter(change -> {
        var state = statusOf(change.getPluginStatuses(), Language.JAVA).getState();
        return state == PluginStateDto.DOWNLOADING || state == PluginStateDto.SYNCED;
      })
      .toList();
    assertThat(serverDownloadNotifications)
      .extracting(DidChangePluginStatusesParams::getConfigScopeId)
      .containsOnlyElementsOf(boundScopeIds);
    assertThat(serverDownloadNotifications)
      .filteredOn(change -> statusOf(change.getPluginStatuses(), Language.JAVA).getState() == PluginStateDto.SYNCED)
      .allSatisfy(change -> assertThat(statusOf(change.getPluginStatuses(), Language.JAVA).getSource()).isEqualTo(ArtifactSourceDto.SONARQUBE_SERVER));
  }

  @SonarLintTest
  void should_notify_every_scope_with_its_effective_connection_statuses_when_a_binaries_download_finishes(SonarLintTestHarness harness) {
    harness.fakeBinariesServerBuilder()
      .withArtifactFromResource(BinariesArtifact.CFAMILY_PLUGIN, "fake-cpp-plugin.jar")
      .start();
    var server = harness.newFakeSonarQubeServer().withPlugin(TestPlugin.JAVA).start();
    var client = harness.newFakeClient().withToken(CONNECTION_ID, "token").build();
    harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withPlugin("java", TestPlugin.JAVA.getPath(), TestPlugin.JAVA.getHash()))
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .withEnabledLanguageInStandaloneMode(Language.CPP)
      .withExtraEnabledLanguagesInConnectedMode(Language.JAVA)
      .withUnboundConfigScope(SCOPE_ID)
      .withUnboundConfigScope("secondUnboundScope")
      .withBoundConfigScope("boundScope", CONNECTION_ID, "project")
      .withChildConfigScope("inheritedScope", "boundScope")
      .start(client);

    await().atMost(20, SECONDS).untilAsserted(() -> {
      var completedDownloads = client.getPluginStatusChanges().stream()
        .filter(change -> statusOf(change.getPluginStatuses(), Language.CPP).getState() == PluginStateDto.ACTIVE)
        .toList();
      assertThat(completedDownloads)
        .extracting(DidChangePluginStatusesParams::getConfigScopeId)
        .contains(SCOPE_ID, "secondUnboundScope", "boundScope", "inheritedScope");
      assertThat(completedDownloads).allSatisfy(change -> {
        if (List.of("boundScope", "inheritedScope").contains(change.getConfigScopeId())) {
          assertThat(statusOf(change.getPluginStatuses(), Language.JAVA).getState()).isEqualTo(PluginStateDto.SYNCED);
          assertThat(statusOf(change.getPluginStatuses(), Language.JAVA).getSource()).isEqualTo(ArtifactSourceDto.SONARQUBE_SERVER);
          assertThat(statusOf(change.getPluginStatuses(), Language.PYTHON).getState()).isEqualTo(PluginStateDto.UNSUPPORTED);
        } else {
          assertThat(statusOf(change.getPluginStatuses(), Language.PYTHON).getState()).isEqualTo(PluginStateDto.ACTIVE);
          assertThat(statusOf(change.getPluginStatuses(), Language.PYTHON).getSource()).isEqualTo(ArtifactSourceDto.EMBEDDED);
          assertThat(statusOf(change.getPluginStatuses(), Language.JAVA).getState()).isEqualTo(PluginStateDto.PREMIUM);
        }
      });
    });
  }

  private static List<PluginStateDto> notifiedStates(FakeSonarLintRpcClient client, String scopeId, Language language) {
    return client.getPluginStatusChanges().stream()
      .filter(change -> scopeId.equals(change.getConfigScopeId()))
      .map(change -> statusOf(change.getPluginStatuses(), language).getState())
      .toList();
  }

  private static PluginStatusDto statusOf(List<PluginStatusDto> statuses, Language language) {
    return statuses.stream()
      .filter(status -> status.getLanguage() == language)
      .findFirst()
      .orElseThrow();
  }

}
