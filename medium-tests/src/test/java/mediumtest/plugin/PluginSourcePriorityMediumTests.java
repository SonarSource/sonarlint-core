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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import mediumtest.analysis.sensor.IssueResolutionRulesDefinition;
import mediumtest.analysis.sensor.IssueResolutionSensor;
import mediumtest.tracking.IssueStreamingRulesDefinition;
import mediumtest.tracking.IssueStreamingSensor;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifact;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.ArtifactSourceDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.GetPluginStatusesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStatusDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture.FakeSonarLintRpcClient;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.test.utils.plugins.Plugin;
import org.sonarsource.sonarlint.core.test.utils.plugins.SonarPluginBuilder;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.Server;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static utils.AnalysisUtils.createFile;

class PluginSourcePriorityMediumTests {

  private static final String FAKE_CPP_PLUGIN_RESOURCE = "/wiremock/__files/fake-cpp-plugin.jar";
  private static final String FAKE_CPP_PLUGIN_FILE_NAME = "fake-cpp-plugin.jar";
  private static final String FAKE_PLUGIN_VERSION = "10.0.0";
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

  @ParameterizedTest(name = "{0} plugin should be preferred from SonarQube Server 2026.6 over the embedded one")
  @CsvSource({
    "go, GO",
    "iac, DOCKER"
  })
  @ExtendWith(SonarLintTestHarness.class)
  void should_prefer_enterprise_server_plugin_over_embedded_on_sonarqube_server(String pluginKey, Language language, @TempDir Path tempDir,
    SonarLintTestHarness harness) throws IOException, ExecutionException, InterruptedException {
    var plugin = generatedPlugin(tempDir, pluginKey, language);
    var server = harness.newFakeSonarQubeServer("2026.6")
      .withPlugin(plugin)
      .start();

    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(plugin)
      .withSonarQubeConnection("connectionId", server, storage -> storage.withServerVersion("2026.6").withPlugin(plugin))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start();

    assertStatus(backend.getPluginService(), language, "configScopeId", PluginStateDto.SYNCED, ArtifactSourceDto.SONARQUBE_SERVER);
  }

  @ParameterizedTest(name = "{0} plugin should be preferred from SonarQube Cloud over the embedded one")
  @CsvSource({
    "go, GO",
    "iac, DOCKER"
  })
  @ExtendWith(SonarLintTestHarness.class)
  void should_prefer_enterprise_server_plugin_over_embedded_on_sonarqube_cloud(String pluginKey, Language language, @TempDir Path tempDir,
    SonarLintTestHarness harness) throws IOException, ExecutionException, InterruptedException {
    var plugin = generatedPlugin(tempDir, pluginKey, language);
    var server = harness.newFakeSonarCloudServer()
      .withPlugin(plugin)
      .start();

    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(plugin)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "orgKey", true, storage -> storage.withPlugin(plugin))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start();

    assertStatus(backend.getPluginService(), language, "configScopeId", PluginStateDto.SYNCED, ArtifactSourceDto.SONARQUBE_CLOUD);
  }

  @ParameterizedTest(name = "{0}: embedded {1} should give way to {2} from the server")
  @CsvSource({
    "CS, csharp, csharpenterprise",
    "VBNET, vbnet, vbnetenterprise",
    "TEXT, text, textenterprise",
    "TEXT, text, textdeveloper"
  })
  @ExtendWith(SonarLintTestHarness.class)
  void should_use_enterprise_variant_instead_of_embedded_base_plugin(Language language, String baseKey, String variantKey, @TempDir Path tempDir,
    SonarLintTestHarness harness) throws IOException {
    var basePlugin = generatedPlugin(tempDir, baseKey, language);
    var variantPlugin = generatedPlugin(tempDir, variantKey, language);
    var server = harness.newFakeSonarQubeServer("2026.6")
      .withPlugin(variantPlugin)
      .start();

    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(basePlugin)
      .withSonarQubeConnection("connectionId", server, storage -> storage.withServerVersion("2026.6"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start(client);

    assertStatusEventually(backend.getPluginService(), language, "configScopeId", PluginStateDto.SYNCED, ArtifactSourceDto.SONARQUBE_SERVER);
    assertDownloadCounts(server, variantKey, 1, baseKey, 0);
    assertLoadedPlugin(client, variantKey, baseKey);
  }

  @ParameterizedTest(name = "{0}: with {1} and {2} on the server, only {2} should be downloaded")
  @CsvSource({
    "CS, csharp, csharpenterprise",
    "VBNET, vbnet, vbnetenterprise",
    "TEXT, text, textenterprise",
    "TEXT, text, textdeveloper"
  })
  @ExtendWith(SonarLintTestHarness.class)
  void should_download_only_enterprise_variant_when_server_lists_base_and_variant(Language language, String baseKey, String variantKey,
    @TempDir Path tempDir, SonarLintTestHarness harness) throws IOException {
    var basePlugin = generatedPlugin(tempDir, baseKey, language);
    var variantPlugin = generatedPlugin(tempDir, variantKey, language);
    var server = harness.newFakeSonarQubeServer("2026.6")
      .withPlugin(basePlugin)
      .withPlugin(variantPlugin)
      .start();

    var backend = harness.newBackend()
      .withExtraEnabledLanguagesInConnectedMode(language)
      .withSonarQubeConnection("connectionId", server, storage -> storage.withServerVersion("2026.6"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start();

    assertStatusEventually(backend.getPluginService(), language, "configScopeId", PluginStateDto.SYNCED, ArtifactSourceDto.SONARQUBE_SERVER);
    assertDownloadCounts(server, variantKey, 1, baseKey, 0);
  }

  @SonarLintTest
  void should_use_enterprise_variant_instead_of_embedded_base_plugin_on_sonarqube_cloud(SonarLintTestHarness harness, @TempDir Path tempDir)
    throws IOException, ExecutionException, InterruptedException {
    var basePlugin = generatedPlugin(tempDir, "csharp", Language.CS);
    var variantPlugin = generatedPlugin(tempDir, "csharpenterprise", Language.CS);
    var server = harness.newFakeSonarCloudServer()
      .withPlugin(variantPlugin)
      .start();

    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(basePlugin)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "orgKey", true, storage -> storage.withPlugin(variantPlugin))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start(client);

    assertStatus(backend.getPluginService(), Language.CS, "configScopeId", PluginStateDto.SYNCED, ArtifactSourceDto.SONARQUBE_CLOUD);
    assertLoadedPlugin(client, "csharpenterprise", "csharp");
  }

  @SonarLintTest
  void should_raise_issues_only_from_enterprise_variant_when_embedded_base_plugin_exists(SonarLintTestHarness harness, @TempDir Path tempDir)
    throws IOException {
    var baseDir = Files.createDirectories(tempDir.resolve("project"));
    var filePath = createFile(baseDir, "Class.cs", "int unused;");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs("configScopeId", baseDir,
        List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScopeId", false, null, filePath, null, null, true)))
      .build();
    var basePlugin = generatedPlugin(tempDir, "csharp", Language.CS, IssueStreamingSensor.class, IssueStreamingRulesDefinition.class);
    var variantPlugin = generatedPlugin(tempDir, "csharpenterprise", Language.CS, IssueResolutionSensor.class, IssueResolutionRulesDefinition.class);
    var server = harness.newFakeSonarQubeServer("2026.6")
      .withPlugin(variantPlugin)
      .start();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(basePlugin)
      .withExtraEnabledLanguagesInConnectedMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server, storage -> storage
        .withServerVersion("2026.6")
        .withProject("projectKey", project -> project
          .withMainBranch("main")
          .withRuleSet("java", ruleSet -> ruleSet.withActiveRule("repo:rule", "MAJOR"))))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start(client);
    assertStatusEventually(backend.getPluginService(), Language.CS, "configScopeId", PluginStateDto.SYNCED, ArtifactSourceDto.SONARQUBE_SERVER);

    var result = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams("configScopeId", UUID.randomUUID(), List.of(fileUri), Map.of(), false)).join();

    assertThat(result.getRawIssues()).extracting(RawIssueDto::getPrimaryMessage).containsExactly("Remove this unused variable");
  }

  private static Plugin fakeGoPlugin() throws URISyntaxException {
    return new Plugin("go", Set.of(Language.GO), fakeCppPluginPath(), FAKE_PLUGIN_VERSION, FAKE_CPP_PLUGIN_HASH);
  }

  private static Plugin fakeCppPlugin() throws URISyntaxException {
    return new Plugin(Set.of(Language.CPP), fakeCppPluginPath(), FAKE_PLUGIN_VERSION, FAKE_CPP_PLUGIN_HASH);
  }

  private static Path fakeCppPluginPath() throws URISyntaxException {
    return Path.of(PluginSourcePriorityMediumTests.class.getResource(FAKE_CPP_PLUGIN_RESOURCE).toURI());
  }

  private static Plugin generatedPlugin(Path tempDir, String pluginKey, Language language) throws IOException {
    var folder = Files.createDirectories(tempDir.resolve(pluginKey));
    var jarPath = SonarPluginBuilder.newSonarPlugin(pluginKey).generate(folder);
    return new Plugin(pluginKey, Set.of(language), jarPath, FAKE_PLUGIN_VERSION, pluginKey + "-hash");
  }

  private static Plugin generatedPlugin(Path tempDir, String pluginKey, Language language, Class<? extends Sensor> sensorClass,
    Class<? extends RulesDefinition> rulesDefinitionClass) throws IOException {
    var folder = Files.createDirectories(tempDir.resolve(pluginKey));
    var jarPath = SonarPluginBuilder.newSonarPlugin(pluginKey).withSensor(sensorClass).withRulesDefinition(rulesDefinitionClass).generate(folder);
    return new Plugin(pluginKey, Set.of(language), jarPath, FAKE_PLUGIN_VERSION, pluginKey + "-hash");
  }

  private static void assertStatusEventually(PluginRpcService pluginService, Language language, String scopeId, PluginStateDto expectedState,
    ArtifactSourceDto expectedSource) {
    await().atMost(15, SECONDS).untilAsserted(() -> assertStatus(pluginService, language, scopeId, expectedState, expectedSource));
  }

  private static void assertStatus(PluginRpcService pluginService, Language language, String scopeId, PluginStateDto expectedState,
    ArtifactSourceDto expectedSource) throws ExecutionException, InterruptedException {
    assertThat(statusOf(pluginService, language, scopeId))
      .extracting(PluginStatusDto::getState, PluginStatusDto::getSource)
      .containsExactly(expectedState, expectedSource);
  }

  private static void assertDownloadCounts(Server server, String pluginKey, int expectedCount, String otherPluginKey, int otherExpectedCount) {
    await().during(2, SECONDS).atMost(5, SECONDS)
      .untilAsserted(() -> assertThat(List.of(downloadCount(server, pluginKey), downloadCount(server, otherPluginKey)))
        .containsExactly(expectedCount, otherExpectedCount));
  }

  private static void assertLoadedPlugin(FakeSonarLintRpcClient client, String loadedPluginKey, String notLoadedPluginKey) {
    await().during(2, SECONDS).atMost(15, SECONDS)
      .untilAsserted(() -> assertThat(client.getLogs()).extracting(LogParams::getMessage)
        .contains(loadedPluginLogLine(loadedPluginKey))
        .doesNotContain(loadedPluginLogLine(notLoadedPluginKey)));
  }

  private static String loadedPluginLogLine(String pluginKey) {
    return "  * " + pluginKey + " " + FAKE_PLUGIN_VERSION + " (" + pluginKey + ")";
  }

  private static int downloadCount(Server server, String pluginKey) {
    var downloadRequest = getRequestedFor(urlEqualTo("/api/plugins/download?plugin=" + pluginKey)).build();
    return server.getMockServer().countRequestsMatching(downloadRequest).getCount();
  }

  private static PluginStatusDto statusOf(PluginRpcService pluginService, Language language, @Nullable String scopeId)
    throws ExecutionException, InterruptedException {
    return pluginService.getPluginStatuses(new GetPluginStatusesParams(scopeId)).get().getPluginStatuses().stream()
      .filter(status -> status.getLanguage() == language)
      .findFirst()
      .orElseThrow();
  }
}
