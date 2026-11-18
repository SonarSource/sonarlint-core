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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifact;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.GetPluginStatusesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStatusDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture.FakeSonarLintRpcClient;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PluginFailureStatusMediumTests {

  private static final String SCOPE_ID = "scopeId";
  private static final String CONNECTION_ID = "connId";

  @SonarLintTest
  void should_report_failed_and_notify_when_a_binaries_download_fails(SonarLintTestHarness harness) {
    harness.fakeBinariesServerBuilder()
      .withArtifact(BinariesArtifact.CFAMILY_PLUGIN, 500)
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.CPP)
      .withUnboundConfigScope(SCOPE_ID)
      .start(client);

    awaitPluginState(client, Language.CPP, PluginStateDto.FAILED);
    assertThat(stateOf(pluginStatuses(backend), Language.CPP)).isEqualTo(PluginStateDto.FAILED);
  }

  @SonarLintTest
  void should_report_failed_and_notify_when_a_server_plugin_download_fails(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().withPlugin(TestPlugin.JAVA).start();
    server.getMockServer().stubFor(get(urlEqualTo("/api/plugins/download?plugin=java"))
      .atPriority(1)
      .willReturn(aResponse().withStatus(500)));
    var client = harness.newFakeClient().withToken(CONNECTION_ID, "token").build();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(Language.JAVA)
      .start(client);

    backend.getPluginService().getPluginStatuses(new GetPluginStatusesParams(SCOPE_ID)).join();

    awaitPluginState(client, Language.JAVA, PluginStateDto.FAILED);
    assertThat(stateOf(pluginStatuses(backend), Language.JAVA)).isEqualTo(PluginStateDto.FAILED);
  }

  @SonarLintTest
  void should_notify_active_after_a_successful_download_when_an_analysis_scheduler_exists(SonarLintTestHarness harness) {
    harness.fakeBinariesServerBuilder()
      .withArtifactFromResource(BinariesArtifact.CFAMILY_PLUGIN, "fake-cpp-plugin.jar")
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.CPP)
      .withUnboundConfigScope(SCOPE_ID)
      .start(client);

    awaitPluginState(client, Language.CPP, PluginStateDto.ACTIVE);
    assertThat(stateOf(pluginStatuses(backend), Language.CPP)).isEqualTo(PluginStateDto.ACTIVE);
  }

  @SonarLintTest
  void should_notify_synced_after_a_successful_download_when_no_analysis_scheduler_exists(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().withPlugin(TestPlugin.JAVA).start();
    var client = harness.newFakeClient().withToken(CONNECTION_ID, "token").build();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(Language.JAVA)
      .start(client);

    // The binding is not ready for analysis, so no scheduler is created. The notification still has to arrive.
    backend.getPluginService().getPluginStatuses(new GetPluginStatusesParams(SCOPE_ID)).join();

    awaitPluginState(client, Language.JAVA, PluginStateDto.SYNCED);
    assertThat(stateOf(pluginStatuses(backend), Language.JAVA)).isEqualTo(PluginStateDto.SYNCED);
  }

  @SonarLintTest
  void should_report_failed_and_notify_when_a_plugin_is_skipped_for_incompatible_api(SonarLintTestHarness harness, @TempDir Path tempDir) throws IOException {
    var plugin = pluginJar(tempDir, "java.jar", "java", "Sonar-Version", "99.9");
    assertEmbeddedPluginFailed(harness, plugin, Language.JAVA);
  }

  @SonarLintTest
  void should_report_failed_and_notify_when_a_plugin_is_skipped_for_unsatisfied_runtime(SonarLintTestHarness harness, @TempDir Path tempDir) throws IOException {
    var plugin = pluginJar(tempDir, "java.jar", "java", "Jre-Min-Version", "99");
    assertEmbeddedPluginFailed(harness, plugin, Language.JAVA);
  }

  @SonarLintTest
  void should_report_failed_and_notify_when_a_plugin_is_skipped_for_unsatisfied_dependency(SonarLintTestHarness harness, @TempDir Path tempDir) throws IOException {
    var plugin = pluginJar(tempDir, "javascript.jar", "javascript", "Plugin-RequirePlugins", "notinstalled:1.0");
    assertEmbeddedPluginFailed(harness, plugin, Language.JS);
  }

  @SonarLintTest
  void should_keep_unsupported_when_a_plugin_is_skipped_because_its_languages_are_not_enabled(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPlugin(TestPlugin.PYTHON)
      .withUnboundConfigScope(SCOPE_ID)
      .start(client);

    awaitPluginState(client, Language.PYTHON, PluginStateDto.UNSUPPORTED);
    assertThat(notifiedStates(client, Language.PYTHON)).doesNotContain(PluginStateDto.FAILED);
    assertThat(stateOf(pluginStatuses(backend), Language.PYTHON)).isEqualTo(PluginStateDto.UNSUPPORTED);
  }

  @SonarLintTest
  void should_keep_active_when_a_plugin_is_skipped_for_an_unsupported_feature(SonarLintTestHarness harness, @TempDir Path tempDir) throws IOException {
    var dbdPlugin = pluginJar(tempDir, "dbd.jar", "dbdpythonfrontend");
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .withStandaloneEmbeddedPlugin(dbdPlugin)
      .withUnboundConfigScope(SCOPE_ID)
      .start(client);

    awaitPluginState(client, Language.PYTHON, PluginStateDto.ACTIVE);
    assertThat(notifiedStates(client, Language.PYTHON)).doesNotContain(PluginStateDto.FAILED);
    assertThat(stateOf(pluginStatuses(backend), Language.PYTHON)).isEqualTo(PluginStateDto.ACTIVE);
  }

  private static void assertEmbeddedPluginFailed(SonarLintTestHarness harness, Path plugin, Language language) {
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPlugin(plugin)
      .withEnabledLanguageInStandaloneMode(language)
      .withUnboundConfigScope(SCOPE_ID)
      .start(client);

    awaitPluginState(client, language, PluginStateDto.FAILED);
    assertThat(stateOf(pluginStatuses(backend), language)).isEqualTo(PluginStateDto.FAILED);
  }

  private static List<PluginStatusDto> pluginStatuses(SonarLintTestRpcServer backend) {
    return backend.getPluginService().getPluginStatuses(new GetPluginStatusesParams(SCOPE_ID)).join().getPluginStatuses();
  }

  private static void awaitPluginState(FakeSonarLintRpcClient client, Language language, PluginStateDto state) {
    await().atMost(20, SECONDS).untilAsserted(() -> assertThat(notifiedStates(client, language)).contains(state));
  }

  private static List<PluginStateDto> notifiedStates(FakeSonarLintRpcClient client, Language language) {
    return client.getPluginStatusChanges().stream()
      .filter(change -> SCOPE_ID.equals(change.getConfigScopeId()))
      .map(change -> stateOf(change.getPluginStatuses(), language))
      .toList();
  }

  private static PluginStateDto stateOf(List<PluginStatusDto> statuses, Language language) {
    return statuses.stream()
      .filter(status -> status.getLanguage() == language)
      .map(PluginStatusDto::getState)
      .findFirst()
      .orElseThrow();
  }

  private static Path pluginJar(Path directory, String fileName, String pluginKey, String... manifestEntries) throws IOException {
    var path = directory.resolve(fileName);
    var manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Plugin-Key", pluginKey);
    for (var i = 0; i < manifestEntries.length; i += 2) {
      manifest.getMainAttributes().putValue(manifestEntries[i], manifestEntries[i + 1]);
    }
    try (var output = new JarOutputStream(Files.newOutputStream(path), manifest)) {
      output.flush();
    }
    return path;
  }

}
