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
package mediumtest.plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.SocketPolicy;
import okio.Buffer;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.plugin.ondemand.DownloadableArtifact;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static utils.AnalysisUtils.createFile;

@ExtendWith(SystemStubsExtension.class)
class OnDemandMediumTests {

  private static final String CONFIG_SCOPE_ID = "configScopeId";

  // Bytes of sonar-cpp-plugin-test.jar, signed by sonar-cpp-plugin.jar.asc / sonarsource-public.key
  static final byte[] TEST_PLUGIN_JAR = loadTestJar();

  @SystemStub
  SystemProperties systemProperties;

  // ─── Critical Path ────────────────────────────────────────────────────────

  @SonarLintTest
  void successful_download_and_verification(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var mockServer = startMockServer(pluginJarResponse());
    setOnDemandProperties(mockServer);

    var backend = startBackendWithCLanguage(harness, tempDir);
    triggerCAnalysis(backend, tempDir);

    assertThat(mockServer.getRequestCount()).isEqualTo(1);
    assertThat(expectedCachedJar(tempDir)).exists();
    mockServer.shutdown();
  }

  @SonarLintTest
  void failed_verification_when_file_is_tampered(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var mockServer = startMockServer(new MockResponse.Builder().code(200).body("TAMPERED CONTENT").build());
    setOnDemandProperties(mockServer);

    var backend = startBackendWithCLanguage(harness, tempDir);
    triggerCAnalysis(backend, tempDir);

    assertThat(mockServer.getRequestCount()).isEqualTo(1);
    assertThat(expectedCachedJar(tempDir)).doesNotExist();
    mockServer.shutdown();
  }

  @SonarLintTest
  void download_network_failure_is_handled_gracefully(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var mockServer = startMockServer(new MockResponse.Builder().code(503).build());
    setOnDemandProperties(mockServer);

    var backend = startBackendWithCLanguage(harness, tempDir);
    triggerCAnalysis(backend, tempDir);

    assertThat(mockServer.getRequestCount()).isEqualTo(1);
    assertThat(expectedCachedJar(tempDir)).doesNotExist();
    mockServer.shutdown();
  }

  // ─── Backward Compatibility ───────────────────────────────────────────────

  @SonarLintTest
  void no_download_when_plugin_path_provided_in_initialize_params(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var mockServer = startMockServer(pluginJarResponse());
    setOnDemandProperties(mockServer);

    // Plugin path provided by the IDE — Plugin-Key manifest entry must be "cpp" to be recognized by EmbeddedArtifactResolver
    var fakePluginPath = tempDir.resolve("sonar-cpp-plugin-9.9.9.jar");
    createMinimalPluginJar(fakePluginPath, "cpp", "9.9.9");

    var cFile = createFile(tempDir, "test.c", "int main() { return 0; }");
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, tempDir, List.of(
        new ClientFileDto(cFile.toUri(), tempDir.relativize(cFile), CONFIG_SCOPE_ID, false, null, cFile, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withStorageRoot(tempDir.resolve("storage"))
      .withStandaloneEmbeddedPlugin(fakePluginPath)
      .withEnabledLanguageInStandaloneMode(Language.C)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start(client);

    triggerCAnalysis(backend, tempDir);

    assertThat(mockServer.getRequestCount()).isZero();
    assertThat(expectedCachedJar(tempDir)).doesNotExist();
    mockServer.shutdown();
  }

  // ─── Cache Scenarios ──────────────────────────────────────────────────────

  @SonarLintTest
  void cache_hit_skips_download(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var mockServer = startMockServer(pluginJarResponse());
    setOnDemandProperties(mockServer);

    // Pre-populate the cache with the valid signed test JAR
    prepopulateCache(tempDir, TEST_PLUGIN_JAR);

    var backend = startBackendWithCLanguage(harness, tempDir);
    triggerCAnalysis(backend, tempDir);

    assertThat(mockServer.getRequestCount()).isZero();
    mockServer.shutdown();
  }

  @SonarLintTest
  void corrupted_cached_file_triggers_redownload(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var mockServer = startMockServer(pluginJarResponse());
    setOnDemandProperties(mockServer);

    // Pre-populate the cache with content that won't match the signature
    prepopulateCache(tempDir, "CORRUPTED CONTENT".getBytes());

    var backend = startBackendWithCLanguage(harness, tempDir);
    triggerCAnalysis(backend, tempDir);

    assertThat(mockServer.getRequestCount()).isEqualTo(1);
    mockServer.shutdown();
  }

  // ─── Concurrency ──────────────────────────────────────────────────────────

  @SonarLintTest
  void concurrent_analyses_trigger_single_download(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var mockServer = startMockServer(pluginJarResponse());
    setOnDemandProperties(mockServer);

    var backend = startBackendWithCLanguage(harness, tempDir);

    var file1 = createFile(tempDir, "test1.c", "int main() {}");
    var file2 = createFile(tempDir, "test2.c", "int foo() { return 0; }");

    var future1 = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(),
        List.of(file1.toUri()), Map.of(), true));
    var future2 = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(),
        List.of(file2.toUri()), Map.of(), true));

    future1.get(10, TimeUnit.SECONDS);
    future2.get(10, TimeUnit.SECONDS);

    assertThat(mockServer.getRequestCount()).isEqualTo(1);
    mockServer.shutdown();
  }

  // ─── Cache Management ─────────────────────────────────────────────────────

  @SonarLintTest
  void old_cached_versions_are_cleaned_up_after_download(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var mockServer = startMockServer(pluginJarResponse());
    setOnDemandProperties(mockServer);

    // Create an old version directory (last-modified > 60 days ago)
    var oldVersionDir = tempDir.resolve("storage/ondemand-plugins/cpp/0.0.1-old");
    Files.createDirectories(oldVersionDir);
    oldVersionDir.toFile().setLastModified(System.currentTimeMillis() - 61L * 24 * 60 * 60 * 1000);

    var backend = startBackendWithCLanguage(harness, tempDir);
    triggerCAnalysis(backend, tempDir);

    assertThat(mockServer.getRequestCount()).isEqualTo(1);
    assertThat(oldVersionDir).doesNotExist();
    mockServer.shutdown();
  }

  // ─── HTTP Error Handling ──────────────────────────────────────────────────

  @SonarLintTest
  void http_404_does_not_cache_plugin(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var mockServer = startMockServer(new MockResponse.Builder().code(404).build());
    setOnDemandProperties(mockServer);

    var backend = startBackendWithCLanguage(harness, tempDir);
    triggerCAnalysis(backend, tempDir);

    assertThat(mockServer.getRequestCount()).isEqualTo(1);
    assertThat(expectedCachedJar(tempDir)).doesNotExist();
    mockServer.shutdown();
  }

  @SonarLintTest
  void http_500_does_not_cache_plugin(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var mockServer = startMockServer(new MockResponse.Builder().code(500).build());
    setOnDemandProperties(mockServer);

    var backend = startBackendWithCLanguage(harness, tempDir);
    triggerCAnalysis(backend, tempDir);

    assertThat(mockServer.getRequestCount()).isEqualTo(1);
    assertThat(expectedCachedJar(tempDir)).doesNotExist();
    mockServer.shutdown();
  }

  @SonarLintTest
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void connection_timeout_is_handled_gracefully(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var mockServer = startMockServer(new MockResponse.Builder().socketPolicy(SocketPolicy.NoResponse.INSTANCE).build());
    setOnDemandProperties(mockServer);

    var backend = startBackendWithCLanguage(harness, tempDir);
    triggerCAnalysis(backend, tempDir);

    assertThat(mockServer.getRequestCount()).isEqualTo(1);
    assertThat(expectedCachedJar(tempDir)).doesNotExist();
    mockServer.shutdown();
  }

  @SonarLintTest
  void incomplete_download_is_not_cached(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var mockServer = startMockServer(new MockResponse.Builder().socketPolicy(SocketPolicy.DisconnectDuringResponseBody.INSTANCE).build());
    setOnDemandProperties(mockServer);

    var backend = startBackendWithCLanguage(harness, tempDir);
    triggerCAnalysis(backend, tempDir);

    assertThat(mockServer.getRequestCount()).isEqualTo(1);
    assertThat(expectedCachedJar(tempDir)).doesNotExist();
    mockServer.shutdown();
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private static MockResponse pluginJarResponse() throws IOException {
    try (var buffer = new Buffer()) {
      return new MockResponse.Builder().code(200).body(buffer.write(TEST_PLUGIN_JAR)).build();
    }
  }

  private MockWebServer startMockServer(MockResponse response) throws IOException {
    var server = new MockWebServer();
    server.enqueue(response);
    server.start();
    return server;
  }

  private void setOnDemandProperties(MockWebServer server) {
    systemProperties.set(DownloadableArtifact.PROPERTY_URL_PATTERN, "http://" + server.getHostName() + ":" + server.getPort());
  }

  private SonarLintTestRpcServer startBackendWithCLanguage(SonarLintTestHarness harness, Path tempDir) {
    var cFile = createFile(tempDir, "test.c", "int main() { return 0; }");
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, tempDir, List.of(
        new ClientFileDto(cFile.toUri(), tempDir.relativize(cFile), CONFIG_SCOPE_ID, false, null, cFile, null, null, true)))
      .build();
    return harness.newBackend()
      .withStorageRoot(tempDir.resolve("storage"))
      .withEnabledLanguageInStandaloneMode(Language.C)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withHttpResponseTimeout(Duration.ofMillis(500))
      .start(client);
  }

  private void triggerCAnalysis(SonarLintTestRpcServer backend, Path tempDir) throws Exception {
    var cFile = tempDir.resolve("test.c");
    backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(),
        List.of(cFile.toUri()), Map.of(), true))
      .get(10, TimeUnit.SECONDS);
  }

  private Path onDemandCacheDir(Path tempDir) {
    return tempDir.resolve("storage/ondemand-plugins");
  }

  private Path expectedCachedJar(Path tempDir) {
    var version = DownloadableArtifact.CFAMILY_PLUGIN.version();
    return onDemandCacheDir(tempDir).resolve("cpp/" + version + "/sonar-cpp-plugin-" + version + ".jar");
  }

  private void prepopulateCache(Path tempDir, byte[] content) throws IOException {
    var version = DownloadableArtifact.CFAMILY_PLUGIN.version();
    var cacheDir = onDemandCacheDir(tempDir).resolve("cpp/" + version);
    Files.createDirectories(cacheDir);
    Files.write(cacheDir.resolve("sonar-cpp-plugin-" + version + ".jar"), content);
  }

  private static byte[] loadTestJar() {
    try (var in = OnDemandMediumTests.class.getClassLoader().getResourceAsStream("sonar-cpp-plugin-test.jar")) {
      if (in == null) {
        throw new IllegalStateException("sonar-cpp-plugin-test.jar not found on classpath");
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void createMinimalPluginJar(Path target, String pluginKey, String pluginVersion) throws IOException {
    var manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Plugin-Key", pluginKey);
    manifest.getMainAttributes().putValue("Plugin-Version", pluginVersion);
    try (var jos = new JarOutputStream(Files.newOutputStream(target), manifest)) {
      // minimal JAR with only the manifest
    }
  }
}
