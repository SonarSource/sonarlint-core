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

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifact;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.GetPluginStatusesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture.FakeSonarLintRpcClient;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.test.utils.server.BinariesServerFixture.BinariesServer;
import utils.TestPlugin;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OnDemandAnalyzersMediumTests {

  @SonarLintTest
  void should_download_and_cache_cfamily_in_standalone_mode(SonarLintTestHarness harness) {
    harness.newFakeBinariesServer()
      .withArtifactFromResource(BinariesArtifact.CFAMILY_PLUGIN, "fake-cpp-plugin.jar")
      .start();

    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.CPP)
      .start(client);

    await().atMost(15, SECONDS).untilAsserted(() -> {
      var statuses = backend.getPluginService().getPluginStatuses(new GetPluginStatusesParams(null)).get().getPluginStatuses();
      var cfamilyStatusOpt = statuses.stream().filter(s -> "C/C++/Objective-C".equals(s.getPluginName()) || s.getPluginName().contains("C++")).findFirst();
      assertThat(cfamilyStatusOpt).isPresent();
      assertThat(cfamilyStatusOpt.get().getState()).isEqualTo(PluginStateDto.ACTIVE);
    });
  }

  @ParameterizedTest(name = "failed cfamily download with HTTP {0} should not trigger infinite reload loop")
  @ValueSource(ints = {500, 503, 404})
  @ExtendWith(SonarLintTestHarness.class)
  void failed_cfamily_download_should_not_trigger_infinite_reload_loop(int status, SonarLintTestHarness harness) {
    var binariesServer = harness.newFakeBinariesServer()
      .withArtifact(BinariesArtifact.CFAMILY_PLUGIN, status)
      .start();
    var client = harness.newFakeClient().build();

    harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.CPP)
      .withUnboundConfigScope("scopeId")
      .start(client);

    assertDownloadFailedAndLoggedError(client, binariesServer, 1, "Failed to download plugin: HTTP " + status);
  }

  @SonarLintTest
  void should_log_error_after_http_retries_when_connection_is_reset(SonarLintTestHarness harness) {
    var binariesServer = harness.newFakeBinariesServer()
      .withConnectionReset(BinariesArtifact.CFAMILY_PLUGIN)
      .start();
    var client = harness.newFakeClient().build();

    harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.CPP)
      .withUnboundConfigScope("scopeId")
      .start(client);

    assertDownloadFailedAndLoggedError(client, binariesServer, 3, "Connection reset");
  }

  @SonarLintTest
  void should_log_error_and_not_retry_download_when_artifact_fails_signature_check(SonarLintTestHarness harness) {
    var binariesServer = harness.newFakeBinariesServer()
      .withArtifact(BinariesArtifact.CFAMILY_PLUGIN, new byte[0])
      .start();
    var client = harness.newFakeClient().build();

    harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.CPP)
      .withUnboundConfigScope("scopeId")
      .start(client);

    assertDownloadFailedAndLoggedError(client, binariesServer, 1, "Signature verification failed for cpp");
  }

  @SonarLintTest
  void should_download_server_plugins_in_connected_mode(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().withToken("connId", "token").build();
    var server = harness.newFakeSonarQubeServer()
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java"))
      .withProject("projectKey", project -> project.withQualityProfile("qpKey").withBranch("main"))
      .withPlugin(TestPlugin.JAVA)
      .start();

    // Start backend WITH JAVA enabled in standalone mode (to avoid PREMIUM state), without embedded artifact to force download.
    var backend = harness.newBackend()
      .withSonarQubeConnection("connId", server)
      .withBoundConfigScope("scopeId", "connId", "projectKey")
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withExtraEnabledLanguagesInConnectedMode(Language.JAVA)
      .withBackendCapability(BackendCapability.FULL_SYNCHRONIZATION)
      .start(client);

    client.waitForSynchronization();
    
    await().atMost(15, SECONDS).untilAsserted(() -> {
      var statuses = backend.getPluginService().getPluginStatuses(new GetPluginStatusesParams("scopeId")).get().getPluginStatuses();
      var javaStatusOpt = statuses.stream().filter(s -> "Java".equals(s.getPluginName())).findFirst();
      assertThat(javaStatusOpt).isPresent();
      assertThat(javaStatusOpt.get().getState()).isEqualTo(PluginStateDto.SYNCED);
    });
  }

  private static void assertDownloadFailedAndLoggedError(FakeSonarLintRpcClient client, BinariesServer binariesServer, int expectedRequestCount, String expectedCause) {
    await().atMost(10, SECONDS).until(() -> binariesServer.requestCount(BinariesArtifact.CFAMILY_PLUGIN) >= expectedRequestCount);
    await().during(2, SECONDS).atMost(5, SECONDS)
      .until(() -> binariesServer.requestCount(BinariesArtifact.CFAMILY_PLUGIN) == expectedRequestCount);

    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(client.getLogs()).anySatisfy(log -> assertDownloadFailureLog(log, expectedCause)));
  }

  private static void assertDownloadFailureLog(LogParams log, String expectedCause) {
    assertThat(log)
      .extracting(LogParams::getLevel, LogParams::getMessage)
      .containsExactly(LogLevel.ERROR, "Failed to download artifact with key cpp");
    assertThat(log.getStackTrace()).contains(expectedCause);
  }

}
