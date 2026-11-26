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
package mediumtest.plugin.resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import org.sonarsource.sonarlint.core.serverconnection.storage.StaticResourceStorage;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.AbstractServerBuilder.ServerPluginBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.PROJECT_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;
import static org.sonarsource.sonarlint.core.test.utils.plugins.SonarPluginBuilder.newSonarPlugin;
import static utils.AnalysisUtils.createFile;

class ConnectedStaticResourceMediumTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String CONNECTION_ID = "connectionId";
  private static final String SCOPE_ID = "scopeId";
  private static final String PROJECT_KEY = "projectKey";
  private static final String PLUGIN_KEY = "javascript";
  private static final String PLUGIN_HASH = "javascript-hash";
  private static final String RESOURCE_PATH = "linux-x64/native-tool";
  private static final String FOLDER = "folder";

  @SonarLintTest
  void sync_stores_the_static_resource_inventory_and_a_later_sync_keeps_it(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var jar = pluginJar(baseDir, false);
    var server = sonarQube(harness, jar, plugin -> plugin.withStaticResourceFolder(FOLDER).withStaticResource(RESOURCE_PATH, bytes));
    var backend = harness.newBackend()
      .withExtraEnabledLanguagesInConnectedMode(Language.JS)
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(harness.newFakeClient().build());

    awaitInventory(backend, bytes);

    var plugins = backend.getApplicationContext().getBean(PluginsService.class);
    plugins.unloadPlugins(CONNECTION_ID);
    plugins.getPlugins(CONNECTION_ID);

    awaitInventory(backend, bytes);
    assertThat(server.getMockServer().findAll(getRequestedFor(urlPathEqualTo("/api/plugins/download")))).hasSize(1);
  }

  @SonarLintTest
  void sonarQube_analysis_downloads_the_resource_and_stores_it(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var analysis = analyze(harness, baseDir, "fetch " + PLUGIN_KEY + " " + RESOURCE_PATH, false,
      plugin -> plugin.withStaticResource(RESOURCE_PATH, bytes));

    assertThat(analysis.result()).isEqualTo("native");
    assertThat(analysis.cached(RESOURCE_PATH, bytes)).hasContent("native");
    var requests = analysis.server().getMockServer().findAll(getRequestedFor(urlPathEqualTo("/static/" + PLUGIN_KEY + "/" + RESOURCE_PATH)));
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).getHeader("Authorization")).isEqualTo("Bearer token");
  }

  @SonarLintTest
  void sonarCloud_analysis_downloads_the_resource_from_the_cdn(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var analysis = analyze(harness, baseDir, "fetch " + PLUGIN_KEY + " " + RESOURCE_PATH, true,
      plugin -> plugin.withStaticResourceFolder(FOLDER).withStaticResource(RESOURCE_PATH, bytes));

    assertThat(analysis.result()).isEqualTo("native");
    assertThat(analysis.cached(RESOURCE_PATH, bytes)).hasContent("native");
    var cdnPath = "/plugins/" + PLUGIN_KEY + "/versions/" + FOLDER + "/" + RESOURCE_PATH;
    assertThat(analysis.server().getMockServer().findAll(getRequestedFor(urlPathEqualTo(cdnPath)))).hasSize(1);
  }

  @SonarLintTest
  void sonarCloud_plugin_without_a_resource_folder_fails_the_fetch(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var analysis = analyze(harness, baseDir, "fetch " + PLUGIN_KEY + " " + RESOURCE_PATH, true,
      plugin -> plugin.withStaticResource(RESOURCE_PATH, bytes));

    assertThat(analysis.result()).isEqualTo("IllegalStateException: Plugin '" + PLUGIN_KEY + "' has no static resource folder");
    assertThat(analysis.cached(RESOURCE_PATH, bytes)).doesNotExist();
  }

  @SonarLintTest
  void unknown_plugin_fails_the_fetch(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var analysis = analyze(harness, baseDir, "fetch python " + RESOURCE_PATH, false,
      plugin -> plugin.withStaticResource(RESOURCE_PATH, bytes));

    assertThat(analysis.result()).isEqualTo("IllegalStateException: Plugin 'python' is not stored for connection '" + CONNECTION_ID + "'");
  }

  @SonarLintTest
  void undeclared_path_fails_the_fetch(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var analysis = analyze(harness, baseDir, "fetch " + PLUGIN_KEY + " " + RESOURCE_PATH, false,
      plugin -> plugin.withStaticResource("other/native-tool", bytes));

    assertThat(analysis.result()).isEqualTo("IllegalStateException: Plugin '" + PLUGIN_KEY + "' has no static resource '" + RESOURCE_PATH + "'");
  }

  @SonarLintTest
  void hash_mismatch_fails_the_fetch(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var analysis = analyze(harness, baseDir, "fetch " + PLUGIN_KEY + " " + RESOURCE_PATH, false,
      plugin -> plugin.withStaticResource(RESOURCE_PATH, "deadbeef", bytes));

    assertThat(analysis.result()).startsWith("IllegalStateException: ").contains("expected deadbeef");
    assertThat(analysis.resources().resolve("deadbeef")).doesNotExist();
  }

  @SonarLintTest
  void connection_that_cannot_download_fails_the_fetch(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var analysis = analyze(harness, baseDir, "fetch " + PLUGIN_KEY + " " + RESOURCE_PATH, false,
      plugin -> plugin.withStaticResource(RESOURCE_PATH, resource -> resource.withContent(bytes).withStatus(401)));

    assertThat(analysis.result()).isEqualTo("IllegalStateException: Connection '" + CONNECTION_ID + "' cannot download static resources");
    assertThat(analysis.cached(RESOURCE_PATH, bytes)).doesNotExist();
  }

  @SonarLintTest
  void unreadable_download_fails_the_fetch(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var analysis = analyze(harness, baseDir, "fetch " + PLUGIN_KEY + " " + RESOURCE_PATH, false,
      plugin -> plugin.withStaticResource(RESOURCE_PATH, resource -> resource.withContent(bytes).withEmptyResponse()));

    assertThat(analysis.result()).startsWith("NetworkException: ");
    assertThat(analysis.cached(RESOURCE_PATH, bytes)).doesNotExist();
  }

  @SonarLintTest
  void fetchAndExtract_unpacks_a_zip(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = zip("hello.txt", "hello");
    var path = "tools/native.zip";
    var analysis = analyze(harness, baseDir, "extract " + PLUGIN_KEY + " " + path, false, plugin -> plugin.withStaticResource(path, bytes));

    assertThat(analysis.result()).isEqualTo("hello");
    assertThat(analysis.extracted(bytes).resolve("hello.txt")).hasContent("hello");
    assertThat(analysis.extracted(bytes).resolve(StaticResourceStorage.EXTRACTION_COMPLETE)).isEmptyFile();
  }

  @SonarLintTest
  void fetchAndExtract_unpacks_a_tar_gz(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = tarGz("hello.txt", "hello");
    var path = "tools/native.tar.gz";
    var analysis = analyze(harness, baseDir, "extract " + PLUGIN_KEY + " " + path, false, plugin -> plugin.withStaticResource(path, bytes));

    assertThat(analysis.result()).isEqualTo("hello");
    assertThat(analysis.extracted(bytes).resolve("hello.txt")).hasContent("hello");
  }

  @SonarLintTest
  void fetchAndExtract_rejects_a_file_that_is_not_an_archive(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var analysis = analyze(harness, baseDir, "extract " + PLUGIN_KEY + " " + RESOURCE_PATH, false, plugin -> plugin.withStaticResource(RESOURCE_PATH, bytes));

    assertThat(analysis.result()).startsWith("IllegalArgumentException: ").contains("only zip and tar.gz");
  }

  @SonarLintTest
  void zip_that_escapes_the_destination_fails_the_extraction(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = zip("../outside.txt", "escaped");
    var path = "tools/native.zip";
    var analysis = analyze(harness, baseDir, "extract " + PLUGIN_KEY + " " + path, false, plugin -> plugin.withStaticResource(path, bytes));

    assertThat(analysis.result()).startsWith("IllegalStateException: ").contains("outside the target directory");
    assertThat(analysis.resources().resolve(DigestUtils.sha256Hex(bytes)).resolve("outside.txt")).doesNotExist();
  }

  @SonarLintTest
  void purge_drops_hashes_missing_from_the_inventory(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = "native".getBytes(StandardCharsets.UTF_8);
    var hash = DigestUtils.sha256Hex(bytes);
    var jar = pluginJar(baseDir, false);
    var server = sonarQube(harness, jar, plugin -> plugin.withStaticResource(RESOURCE_PATH, bytes));
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withExtraEnabledLanguagesInConnectedMode(Language.JS)
      .withSonarQubeConnection("kept", server, storage -> storage.withPlugin(PLUGIN_KEY, jar, PLUGIN_HASH)
        .withCachedStaticResource(hash + "/native-tool", "keep".getBytes(StandardCharsets.UTF_8))
        .withCachedStaticResource("orphan/native-tool", "remove".getBytes(StandardCharsets.UTF_8))
        .withCachedStaticResource("notes.txt", "stray".getBytes(StandardCharsets.UTF_8)))
      .withSonarQubeConnection("empty", server, storage -> storage.withPlugin(PLUGIN_KEY, jar, PLUGIN_HASH))
      .withBoundConfigScope("keptScope", "kept", PROJECT_KEY)
      .withBoundConfigScope("emptyScope", "empty", PROJECT_KEY)
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);

    client.waitForSynchronization();

    var keptResources = resources(backend, "kept");
    await().atMost(20, SECONDS).untilAsserted(() -> {
      assertThat(keptResources.resolve("orphan")).doesNotExist();
      assertThat(keptResources.resolve(hash).resolve("native-tool")).hasContent("keep");
      assertThat(keptResources.resolve("notes.txt")).hasContent("stray");
      assertThat(resources(backend, "empty")).doesNotExist();
      assertThat(references(backend, "empty")).containsKey(PLUGIN_KEY);
    });
  }

  @SonarLintTest
  void stale_extracting_directory_is_removed_and_a_recent_one_is_left(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var bytes = zip("hello.txt", "hello");
    var path = "tools/native.zip";
    var analysis = analyze(harness, baseDir, "extract " + PLUGIN_KEY + " " + path, false, plugin -> plugin.withStaticResource(path, bytes));
    var hashDirectory = analysis.resources().resolve(DigestUtils.sha256Hex(bytes));
    var stale = hashDirectory.resolve(".extracting-stale");
    var current = hashDirectory.resolve(".extracting-current");
    Files.createDirectories(stale);
    Files.writeString(stale.resolve("partial"), "partial");
    Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minus(Duration.ofHours(2))));
    Files.createDirectories(current);
    Files.writeString(current.resolve("partial"), "partial");

    analyzeAgain(analysis);

    assertThat(analysis.result()).isEqualTo("hello");
    assertThat(stale).doesNotExist();
    assertThat(current.resolve("partial")).hasContent("partial");
  }

  private static void awaitInventory(SonarLintTestRpcServer backend, byte[] bytes) {
    await().atMost(20, SECONDS).untilAsserted(() -> {
      var reference = references(backend, CONNECTION_ID).get(PLUGIN_KEY);
      assertThat(reference).isNotNull();
      assertThat(reference.getStaticResourcesCount()).isEqualTo(1);
      var resource = reference.getStaticResources(0);
      assertThat(resource.getPath()).isEqualTo(RESOURCE_PATH);
      assertThat(resource.getHash()).isEqualTo(DigestUtils.sha256Hex(bytes));
      assertThat(resource.getSize()).isEqualTo(bytes.length);
      assertThat(reference.getStaticResourceFolder()).isEqualTo(FOLDER);
    });
  }

  private static Map<String, Sonarlint.PluginReferences.PluginReference> references(SonarLintTestRpcServer backend, String connectionId) {
    var file = backend.getStorageRoot().resolve(encodeForFs(connectionId)).resolve("plugins").resolve("plugin_references.pb");
    if (!Files.exists(file)) {
      return Map.of();
    }
    return ProtobufFileUtil.readFile(file, Sonarlint.PluginReferences.parser()).getPluginsByKeyMap();
  }

  private static Analysis analyze(SonarLintTestHarness harness, Path baseDir, String instruction, boolean cloud, Consumer<ServerPluginBuilder> customize) throws IOException {
    var jar = pluginJar(baseDir, true);
    var server = cloud ? sonarCloud(harness, jar, customize) : sonarQube(harness, jar, customize);
    var file = createFile(baseDir, "Foo.js", instruction);
    var client = harness.newFakeClient()
      .withInitialFs(SCOPE_ID, baseDir, List.of(new ClientFileDto(file.toUri(), baseDir.relativize(file), SCOPE_ID, false, null, file, null, Language.JS, true)))
      .build();
    var backendBuilder = harness.newBackend()
      .withExtraEnabledLanguagesInConnectedMode(Language.JS)
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withBackendCapability(FULL_SYNCHRONIZATION, PROJECT_SYNCHRONIZATION);
    if (cloud) {
      backendBuilder
        .withSonarQubeCloudEuRegionUri(server.baseUrl())
        .withSonarQubeCloudEuRegionApiUri(server.baseUrl())
        .withSonarQubeCloudEuRegionCdnUri(server.baseUrl())
        .withSonarCloudConnection(CONNECTION_ID, "orgKey", true, storage -> storage.withPlugin(PLUGIN_KEY, jar, PLUGIN_HASH));
    } else {
      backendBuilder.withSonarQubeConnection(CONNECTION_ID, server, storage -> storage.withPlugin(PLUGIN_KEY, jar, PLUGIN_HASH));
    }
    var backend = backendBuilder.start(client);
    await().atMost(20, SECONDS).untilAsserted(() -> {
      var reference = references(backend, CONNECTION_ID).get(PLUGIN_KEY);
      assertThat(reference).isNotNull();
      assertThat(reference.getStaticResourcesCount()).isPositive();
    });
    var analysis = new Analysis(server, backend, file);
    analyzeAgain(analysis);
    return analysis;
  }

  private static void analyzeAgain(Analysis analysis) {
    var result = analysis.backend().getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(SCOPE_ID, UUID.randomUUID(), List.of(analysis.file().toUri()), Map.of(), false, System.currentTimeMillis()))
      .orTimeout(30, TimeUnit.SECONDS)
      .join();
    assertThat(result.getFailedAnalysisFiles()).isEmpty();
  }

  private static ServerFixture.Server sonarQube(SonarLintTestHarness harness, Path jar, Consumer<ServerPluginBuilder> customize) {
    return harness.newFakeSonarQubeServer("2025.1")
      .withProject(PROJECT_KEY, project -> project.withBranch("main"))
      .withPlugin(PLUGIN_KEY, plugin -> customizePlugin(plugin, jar, customize))
      .start();
  }

  private static ServerFixture.Server sonarCloud(SonarLintTestHarness harness, Path jar, Consumer<ServerPluginBuilder> customize) {
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("orgKey", org -> org
        .withProject(PROJECT_KEY, project -> project.withBranch("main").withQualityProfile("qp"))
        .withQualityProfile("qp", profile -> profile.withLanguage("js")))
      .withPlugin(PLUGIN_KEY, plugin -> customizePlugin(plugin, jar, customize))
      .start();
    return server;
  }

  private static ServerPluginBuilder customizePlugin(ServerPluginBuilder plugin, Path jar, Consumer<ServerPluginBuilder> customize) {
    plugin.withJarPath(jar).withHash(PLUGIN_HASH);
    customize.accept(plugin);
    return plugin;
  }

  private static Path pluginJar(Path baseDir, boolean withSensor) throws IOException {
    var folder = Files.createDirectories(baseDir.resolve("plugin"));
    var builder = newSonarPlugin(PLUGIN_KEY);
    if (withSensor) {
      builder.withSensor(StaticResourceSensor.class);
    }
    return builder.generate(folder);
  }

  private static Path resources(SonarLintTestRpcServer backend, String connectionId) {
    return backend.getStorageRoot().resolve(encodeForFs(connectionId)).resolve("resources");
  }

  private static byte[] zip(String name, String content) throws IOException {
    var file = Files.createTempFile("resource", ".zip");
    try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
      zip.putNextEntry(new ZipEntry(name));
      zip.write(content.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return Files.readAllBytes(file);
  }

  private static byte[] tarGz(String name, String content) throws IOException {
    var file = Files.createTempFile("resource", ".tar.gz");
    var data = content.getBytes(StandardCharsets.UTF_8);
    try (var tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(Files.newOutputStream(file)))) {
      var entry = new TarArchiveEntry(name);
      entry.setSize(data.length);
      tar.putArchiveEntry(entry);
      tar.write(data);
      tar.closeArchiveEntry();
    }
    return Files.readAllBytes(file);
  }

  private record Analysis(ServerFixture.Server server, SonarLintTestRpcServer backend, Path file) {
    private String result() throws IOException {
      return Files.readString(file.resolveSibling(file.getFileName() + ".result"));
    }

    private Path resources() {
      return ConnectedStaticResourceMediumTests.resources(backend, CONNECTION_ID);
    }

    private Path cached(String resourcePath, byte[] content) {
      return resources().resolve(DigestUtils.sha256Hex(content)).resolve(Path.of(resourcePath).getFileName());
    }

    private Path extracted(byte[] content) {
      return resources().resolve(DigestUtils.sha256Hex(content)).resolve("extracted");
    }
  }
}
