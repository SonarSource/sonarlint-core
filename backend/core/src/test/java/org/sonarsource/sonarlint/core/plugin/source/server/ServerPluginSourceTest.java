/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin.source.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactLocation;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerPluginSourceTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  private Path javaJar;
  private StorageService storageService;
  private ConnectionStorage connectionStorage;
  private PluginsStorage pluginsStorage;
  private ServerPluginsCache serverPluginsCache;
  private ServerPluginDownloader downloader;

  @BeforeEach
  void setUp() throws IOException {
    javaJar = Files.createFile(tempDir.resolve("sonar-java-plugin.jar"));
    storageService = mock(StorageService.class);
    connectionStorage = mock(ConnectionStorage.class);
    pluginsStorage = mock(PluginsStorage.class);
    serverPluginsCache = mock(ServerPluginsCache.class);
    downloader = mock(ServerPluginDownloader.class);
    when(connectionStorage.plugins()).thenReturn(pluginsStorage);
    when(pluginsStorage.getStoredPluginsByKey()).thenReturn(Map.of());
  }

  @Test
  void should_describe_matching_stored_plugin_as_local() {
    mockStorage("conn");
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), javaJar, "hash");
    mockServerPlugins("conn", List.of(serverPlugin(SonarPlugin.JAVA.getKey(), "hash")));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);

    var artifact = createSource("conn").listAvailableArtifacts(Set.of(SonarLanguage.JAVA)).get(0);

    assertThat(artifact.location()).isEqualTo(new ArtifactLocation.Local(javaJar, ArtifactOrigin.SONARQUBE_SERVER, null));
    assertThat(logTester.logs()).contains("[SYNC] Code analyzer 'java' is up-to-date. Skip downloading it.");
  }

  @Test
  void should_describe_missing_stored_plugin_as_remote() {
    mockStorage("conn");
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), tempDir.resolve("missing.jar"), "hash");
    mockServerPlugins("conn", List.of(serverPlugin(SonarPlugin.JAVA.getKey(), "hash")));
    when(downloader.deduplicationKeyFor("conn", serverPlugin(SonarPlugin.JAVA.getKey(), "hash"))).thenReturn("ignored");

    var artifact = createSource("conn").listAvailableArtifacts(Set.of(SonarLanguage.JAVA)).get(0);

    assertThat(artifact.location()).isInstanceOf(ArtifactLocation.Remote.class);
  }

  @Test
  void should_describe_plugin_with_a_different_stored_hash_as_remote() {
    mockStorage("conn");
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), javaJar, "old-hash");
    mockServerPlugins("conn", List.of(serverPlugin(SonarPlugin.JAVA.getKey(), "new-hash")));

    var artifact = createSource("conn").listAvailableArtifacts(Set.of(SonarLanguage.JAVA)).get(0);

    assertThat(artifact.location()).isInstanceOf(ArtifactLocation.Remote.class);
  }

  @Test
  void remote_download_should_use_source_deduplication_key_and_materialize_local_artifact() throws Exception {
    mockStorage("conn");
    var serverPlugin = serverPlugin(SonarPlugin.JAVA.getKey(), "hash");
    mockServerPlugins("conn", List.of(serverPlugin));
    when(downloader.deduplicationKeyFor("conn", serverPlugin)).thenReturn("https://server/api/plugins/download?plugin=java#hash");
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarPlugin.JAVA.getKey(), javaJar));

    var artifact = createSource("conn").listAvailableArtifacts(Set.of(SonarLanguage.JAVA)).get(0);
    var download = ((ArtifactLocation.Remote) artifact.location()).download();

    assertThat(download.deduplicationKey()).isEqualTo("https://server/api/plugins/download?plugin=java#hash");
    assertThat(download.download()).isEqualTo(new ArtifactLocation.Local(javaJar, ArtifactOrigin.SONARQUBE_SERVER, null));
    verify(downloader).downloadPluginSyncOrThrow("conn", serverPlugin);
  }

  @Test
  void should_return_empty_when_plugin_is_not_available_locally_or_remotely() {
    mockStorage("conn");
    mockServerPlugins("conn", List.of());

    assertThat(createSource("conn").listAvailableArtifacts(Set.of(SonarLanguage.JAVA))).isEmpty();
  }

  @Test
  void should_fall_back_to_stored_plugins_when_server_request_fails() {
    mockStorage("conn");
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), javaJar, "hash");
    when(serverPluginsCache.getPlugins("conn")).thenThrow(new RuntimeException("Connection refused"));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);

    var result = createSource("conn").listAvailableArtifacts(Set.of(SonarLanguage.JAVA));

    assertThat(result).singleElement().satisfies(artifact -> {
      assertThat(artifact.key()).isEqualTo(SonarPlugin.JAVA.getKey());
      assertThat(artifact.location()).isInstanceOf(ArtifactLocation.Local.class);
    });
  }

  @Test
  void should_include_language_plugin_only_when_its_language_is_enabled() {
    mockServerPlugins("conn", List.of(serverPlugin(SonarPlugin.JAVA.getKey(), "hash")));
    var source = createSource("conn");

    assertThat(source.listAvailableArtifacts(Set.of(SonarLanguage.JAVA))).hasSize(1);
    assertThat(source.listAvailableArtifacts(Set.of())).isEmpty();
  }

  @Test
  void should_include_enterprise_csharp_only_when_csharp_is_enabled() {
    mockServerPlugins("conn", List.of(serverPlugin(SonarPlugin.CSHARP_ENTERPRISE.getKey(), "hash")));
    var source = createSource("conn");

    assertThat(source.listAvailableArtifacts(Set.of(SonarLanguage.CS))).singleElement().satisfies(artifact -> {
      assertThat(artifact.key()).isEqualTo(SonarPlugin.CSHARP_ENTERPRISE.getKey());
      assertThat(artifact.isEnterprise()).isTrue();
    });
    assertThat(source.listAvailableArtifacts(Set.of())).isEmpty();
  }

  @Test
  void should_include_unknown_companion_plugin_only_when_sonarlint_supported() {
    var supported = new ServerPlugin("supported", "hash", "supported.jar", true);
    var unsupported = new ServerPlugin("unsupported", "hash", "unsupported.jar", false);
    mockServerPlugins("conn", List.of(supported, unsupported));

    assertThat(createSource("conn").listAvailableArtifacts(Set.of()))
      .extracting(artifact -> artifact.key())
      .containsExactly("supported");
  }

  @Test
  void should_mark_go_as_enterprise_when_server_version_qualifies() {
    mockStorage("conn");
    mockServerVersion("2025.2");
    mockServerPlugins("conn", List.of(serverPlugin(SonarPlugin.GO.getKey(), "hash")));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);

    assertThat(createSource("conn").listAvailableArtifacts(Set.of(SonarLanguage.GO)))
      .singleElement().extracting(artifact -> artifact.isEnterprise()).isEqualTo(true);
  }

  @Test
  void should_not_mark_go_as_enterprise_when_server_version_is_too_old() {
    mockStorage("conn");
    mockServerVersion("2025.1");
    mockServerPlugins("conn", List.of(serverPlugin(SonarPlugin.GO.getKey(), "hash")));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);

    assertThat(createSource("conn").listAvailableArtifacts(Set.of(SonarLanguage.GO)))
      .singleElement().extracting(artifact -> artifact.isEnterprise()).isEqualTo(false);
  }

  @Test
  void should_mark_go_as_enterprise_on_sonarqube_cloud() {
    mockServerPlugins("conn", List.of(serverPlugin(SonarPlugin.GO.getKey(), "hash")));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_CLOUD);

    assertThat(createSource("conn").listAvailableArtifacts(Set.of(SonarLanguage.GO)))
      .singleElement().extracting(artifact -> artifact.isEnterprise()).isEqualTo(true);
  }

  @Test
  void should_not_mark_java_as_enterprise() {
    mockServerPlugins("conn", List.of(serverPlugin(SonarPlugin.JAVA.getKey(), "hash")));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_CLOUD);

    assertThat(createSource("conn").listAvailableArtifacts(Set.of(SonarLanguage.JAVA)))
      .singleElement().extracting(artifact -> artifact.isEnterprise()).isEqualTo(false);
  }

  private ServerPluginSource createSource(String connectionId) {
    return new ServerPluginSource(connectionId, storageService, serverPluginsCache, downloader);
  }

  private void mockStorage(String connectionId) {
    when(storageService.connection(connectionId)).thenReturn(connectionStorage);
  }

  private void mockServerVersion(String version) {
    var serverInfoStorage = mock(ServerInfoStorage.class);
    var storedServerInfo = mock(StoredServerInfo.class);
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(serverInfoStorage.read()).thenReturn(Optional.of(storedServerInfo));
    when(storedServerInfo.version()).thenReturn(Version.create(version));
  }

  private void mockServerPlugins(String connectionId, List<ServerPlugin> plugins) {
    when(serverPluginsCache.getPlugins(connectionId)).thenReturn(Optional.of(plugins));
  }

  private void mockStoredPlugin(String pluginKey, Path jarPath, String hash) {
    when(pluginsStorage.getStoredPluginsByKey()).thenReturn(Map.of(pluginKey, new StoredPlugin(pluginKey, hash, jarPath)));
  }

  private static ServerPlugin serverPlugin(String pluginKey, String hash) {
    return new ServerPlugin(pluginKey, hash, pluginKey + ".jar", true);
  }
}
