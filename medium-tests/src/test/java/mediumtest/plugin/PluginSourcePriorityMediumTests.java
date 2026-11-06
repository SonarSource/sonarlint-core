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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifact;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.ArtifactSourceDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.GetPluginStatusesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStatusDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.test.utils.plugins.Plugin;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PluginSourcePriorityMediumTests {

  private static final String FAKE_CPP_PLUGIN_RESOURCE = "/wiremock/__files/fake-cpp-plugin.jar";
  private static final String FAKE_CPP_PLUGIN_FILE_NAME = "fake-cpp-plugin.jar";
  private static final String FAKE_CPP_PLUGIN_VERSION = "10.0.0";
  private static final String FAKE_CPP_PLUGIN_HASH = "fake-cpp-plugin-hash";

  @SonarLintTest
  void should_prefer_embedded_over_binaries_in_standalone_mode(SonarLintTestHarness harness) throws URISyntaxException, ExecutionException, InterruptedException {
    var binariesServer = harness.fakeBinariesServerBuilder()
      .withArtifactFromResource(BinariesArtifact.CFAMILY_PLUGIN, FAKE_CPP_PLUGIN_FILE_NAME)
      .start();

    var backend = harness.newBackend()
      .withStandaloneEmbeddedPlugin(fakeCppPluginPath())
      .withEnabledLanguageInStandaloneMode(Language.CPP)
      .start();

    assertThat(statusOf(backend.getPluginService(), Language.CPP, null))
      .extracting(PluginStatusDto::getState, PluginStatusDto::getSource)
      .containsExactly(PluginStateDto.ACTIVE, ArtifactSourceDto.EMBEDDED);
    await().during(2, SECONDS).atMost(5, SECONDS)
      .until(() -> binariesServer.requestCount(BinariesArtifact.CFAMILY_PLUGIN) == 0);
  }

  @SonarLintTest
  void should_prefer_embedded_over_server_and_binaries_in_connected_mode(SonarLintTestHarness harness)
    throws URISyntaxException, ExecutionException, InterruptedException {
    var cppPlugin = fakeCppPlugin();
    var binariesServer = harness.fakeBinariesServerBuilder()
      .withArtifactFromResource(BinariesArtifact.CFAMILY_PLUGIN, FAKE_CPP_PLUGIN_FILE_NAME)
      .start();
    var server = harness.newFakeSonarQubeServer()
      .withPlugin(cppPlugin)
      .start();

    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(cppPlugin)
      .withSonarQubeConnection("connectionId", server, storage -> storage.withPlugin(cppPlugin))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start();

    assertThat(statusOf(backend.getPluginService(), Language.CPP, "configScopeId"))
      .extracting(PluginStatusDto::getState, PluginStatusDto::getSource)
      .containsExactly(PluginStateDto.ACTIVE, ArtifactSourceDto.EMBEDDED);
    await().during(2, SECONDS).atMost(5, SECONDS)
      .until(() -> binariesServer.requestCount(BinariesArtifact.CFAMILY_PLUGIN) == 0);
  }

  @ParameterizedTest(name = "Go plugin on SonarQube Server {0} should be {1} from {2}")
  @CsvSource({
    "2025.2, SYNCED, SONARQUBE_SERVER",
    "2025.1, ACTIVE, EMBEDDED"
  })
  @ExtendWith(SonarLintTestHarness.class)
  void should_pick_go_source_according_to_server_version(String serverVersion, PluginStateDto expectedState, ArtifactSourceDto expectedSource,
    SonarLintTestHarness harness) throws URISyntaxException, ExecutionException, InterruptedException {
    var goPlugin = fakeGoPlugin();
    var server = harness.newFakeSonarQubeServer(serverVersion)
      .withPlugin(goPlugin)
      .start();

    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(goPlugin)
      .withSonarQubeConnection("connectionId", server, storage -> storage.withServerVersion(serverVersion).withPlugin(goPlugin))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start();

    assertThat(statusOf(backend.getPluginService(), Language.GO, "configScopeId"))
      .extracting(PluginStatusDto::getState, PluginStatusDto::getSource)
      .containsExactly(expectedState, expectedSource);
  }

  @SonarLintTest
  void should_use_server_plugin_in_bound_scope_when_plugin_is_embedded_only_for_standalone_mode(SonarLintTestHarness harness)
    throws URISyntaxException, ExecutionException, InterruptedException {
    var cppPlugin = fakeCppPlugin();
    var binariesServer = harness.fakeBinariesServerBuilder()
      .withArtifactFromResource(BinariesArtifact.CFAMILY_PLUGIN, FAKE_CPP_PLUGIN_FILE_NAME)
      .start();
    var server = harness.newFakeSonarQubeServer()
      .withPlugin(cppPlugin)
      .start();

    var backend = harness.newBackend()
      .withStandaloneEmbeddedPlugin(cppPlugin)
      .withEnabledLanguageInStandaloneMode(Language.CPP)
      .withSonarQubeConnection("connectionId", server, storage -> storage.withPlugin(cppPlugin))
      .withBoundConfigScope("boundScopeId", "connectionId", "projectKey")
      .withUnboundConfigScope("unboundScopeId")
      .start();

    assertThat(statusOf(backend.getPluginService(), Language.CPP, "boundScopeId"))
      .extracting(PluginStatusDto::getState, PluginStatusDto::getSource)
      .containsExactly(PluginStateDto.SYNCED, ArtifactSourceDto.SONARQUBE_SERVER);
    assertThat(statusOf(backend.getPluginService(), Language.CPP, "unboundScopeId"))
      .extracting(PluginStatusDto::getState, PluginStatusDto::getSource)
      .containsExactly(PluginStateDto.ACTIVE, ArtifactSourceDto.EMBEDDED);
    await().during(2, SECONDS).atMost(5, SECONDS)
      .until(() -> binariesServer.requestCount(BinariesArtifact.CFAMILY_PLUGIN) == 0);
  }

  @SonarLintTest
  void should_prefer_server_over_binaries_in_connected_mode(SonarLintTestHarness harness)
    throws URISyntaxException, ExecutionException, InterruptedException {
    var cppPlugin = fakeCppPlugin();
    var binariesServer = harness.fakeBinariesServerBuilder()
      .withArtifactFromResource(BinariesArtifact.CFAMILY_PLUGIN, FAKE_CPP_PLUGIN_FILE_NAME)
      .start();
    var server = harness.newFakeSonarQubeServer()
      .withPlugin(cppPlugin)
      .start();

    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.CPP)
      .withSonarQubeConnection("connectionId", server, storage -> storage.withPlugin(cppPlugin))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start();

    assertThat(statusOf(backend.getPluginService(), Language.CPP, "configScopeId"))
      .extracting(PluginStatusDto::getState, PluginStatusDto::getSource)
      .containsExactly(PluginStateDto.SYNCED, ArtifactSourceDto.SONARQUBE_SERVER);
    await().during(2, SECONDS).atMost(5, SECONDS)
      .until(() -> binariesServer.requestCount(BinariesArtifact.CFAMILY_PLUGIN) == 0);
  }

  private static Plugin fakeGoPlugin() throws URISyntaxException {
    return new Plugin("go", Set.of(Language.GO), fakeCppPluginPath(), FAKE_CPP_PLUGIN_VERSION, FAKE_CPP_PLUGIN_HASH);
  }

  private static Plugin fakeCppPlugin() throws URISyntaxException {
    return new Plugin(Set.of(Language.CPP), fakeCppPluginPath(), FAKE_CPP_PLUGIN_VERSION, FAKE_CPP_PLUGIN_HASH);
  }

  private static Path fakeCppPluginPath() throws URISyntaxException {
    return Path.of(PluginSourcePriorityMediumTests.class.getResource(FAKE_CPP_PLUGIN_RESOURCE).toURI());
  }

  private static PluginStatusDto statusOf(PluginRpcService pluginService, Language language, @Nullable String scopeId)
    throws ExecutionException, InterruptedException {
    return pluginService.getPluginStatuses(new GetPluginStatusesParams(scopeId)).get().getPluginStatuses().stream()
      .filter(status -> status.getLanguage() == language)
      .findFirst()
      .orElseThrow();
  }
}
