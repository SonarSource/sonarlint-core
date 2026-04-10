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
package org.sonarsource.sonarlint.core.plugin.source;

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
import org.sonarsource.sonarlint.core.plugin.source.server.ServerPluginsCache;
import org.sonarsource.sonarlint.core.plugin.source.server.ServerPluginDownloader;
import org.sonarsource.sonarlint.core.plugin.source.server.ServerPluginSource;
import org.sonarsource.sonarlint.core.serverapi.exception.ServerRequestException;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerPluginSourceTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  private Path javaJar;
  private Path iacJar;

  private StorageService storageService;
  private ConnectionStorage connectionStorage;
  private PluginsStorage pluginsStorage;
  private ServerPluginsCache serverPluginsCache;
  private ServerPluginDownloader downloader;

  @BeforeEach
  void setUp() throws IOException {
    javaJar = Files.createFile(tempDir.resolve("sonar-java-plugin.jar"));
    iacJar = Files.createFile(tempDir.resolve("sonar-iac-plugin.jar"));

    storageService = mock(StorageService.class);
    connectionStorage = mock(ConnectionStorage.class);
    pluginsStorage = mock(PluginsStorage.class);
    serverPluginsCache = mock(ServerPluginsCache.class);
    downloader = mock(ServerPluginDownloader.class);
    when(downloader.schedulePluginDownload(any(), any())).thenReturn(null);
    when(connectionStorage.plugins()).thenReturn(pluginsStorage);
    when(pluginsStorage.getStoredPluginsByKey()).thenReturn(Map.of());
  }

  // --- load() — connected mode ---

  @Test
  void load_should_return_synced_from_storage_when_plugin_is_in_storage_but_not_on_server() {
    mockStorage("conn");
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), javaJar, "hash");
    mockServerPlugins("conn", List.of());
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    var source = createSource("conn");
    var expected = resolved(ArtifactState.SYNCED, javaJar, ArtifactOrigin.SONARQUBE_SERVER);

    var result = source.load(SonarPlugin.JAVA.getKey());

    assertThat(result).contains(expected);
  }

  @Test
  void load_should_return_empty_when_plugin_is_not_in_storage_and_not_on_server() {
    mockStorage("conn");
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of());
    mockServerPlugins("conn", List.of());
    var source = createSource("conn");

    var result = source.load(SonarPlugin.JAVA.getKey());

    assertThat(result).isEmpty();
  }

  @Test
  void load_should_trigger_download_when_stored_jar_does_not_exist_on_disk() {
    mockStorage("conn");
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), tempDir.resolve("missing.jar"), "hash");
    var serverPlugin = mockServerPlugin(SonarPlugin.JAVA.getKey(), "hash");
    mockServerPlugins("conn", List.of(serverPlugin));
    var source = createSource("conn");

    var result = source.load(SonarPlugin.JAVA.getKey());

    assertThat(result).contains(new ResolvedArtifact(ArtifactState.DOWNLOADING, null, null, null, null));
    verify(downloader).schedulePluginDownload("conn", serverPlugin);
  }

  @Test
  void load_should_return_downloading_when_plugin_is_not_in_storage_but_on_server() {
    mockStorage("conn");
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of());
    var serverPlugin = mockServerPlugin(SonarPlugin.JAVA.getKey());
    mockServerPlugins("conn", List.of(serverPlugin));
    var source = createSource("conn");

    var result = source.load(SonarPlugin.JAVA.getKey());

    assertThat(result).contains(new ResolvedArtifact(ArtifactState.DOWNLOADING, null, null, null, null));
    verify(downloader).schedulePluginDownload("conn", serverPlugin);
  }

  @Test
  void load_should_return_empty_when_server_plugin_list_AvailablePlugins_request_fails_and_no_storage() {
    mockStorage("conn");
    when(serverPluginsCache.getPlugins("conn")).thenThrow(new ServerRequestException("Connection refused"));
    var source = createSource("conn");

    var result = source.load(SonarPlugin.JAVA.getKey());

    assertThat(result).isEmpty();
  }

  @Test
  void load_should_return_synced_from_storage_when_server_plugin_list_AvailablePlugins_request_fails() {
    mockStorage("conn");
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), javaJar, "hash");
    when(serverPluginsCache.getPlugins("conn")).thenThrow(new ServerRequestException("Connection refused"));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    var source = createSource("conn");

    var result = source.load(SonarPlugin.JAVA.getKey());

    assertThat(result).contains(new ResolvedArtifact(ArtifactState.SYNCED, javaJar, ArtifactOrigin.SONARQUBE_SERVER, null, null));
  }

  @Test
  void load_should_return_synced_with_sonarqube_server_source() {
    mockStorage("conn");
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), javaJar, "hash");
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarPlugin.JAVA.getKey(), "hash")));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    var source = createSource("conn");
    var expected = resolved(ArtifactState.SYNCED, javaJar, ArtifactOrigin.SONARQUBE_SERVER);

    var result = source.load(SonarPlugin.JAVA.getKey());

    assertThat(result).contains(expected);
  }

  @Test
  void load_should_return_synced_with_sonarqube_cloud_source() {
    mockStorage("cloud");
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), javaJar, "hash");
    mockServerPlugins("cloud", List.of(mockServerPlugin(SonarPlugin.JAVA.getKey(), "hash")));
    when(downloader.sourceFor("cloud")).thenReturn(ArtifactOrigin.SONARQUBE_CLOUD);
    var source = createSource("cloud");
    var expected = resolved(ArtifactState.SYNCED, javaJar, ArtifactOrigin.SONARQUBE_CLOUD);

    var result = source.load(SonarPlugin.JAVA.getKey());

    assertThat(result).contains(expected);
  }

  // --- ANSIBLE/GITHUBACTIONS use "iac" plugin key ---

  @Test
  void load_should_resolve_iac_plugin_for_iac_language() {
    mockStorage("conn");
    mockStoredPlugin("iac", iacJar, "hash");
    mockServerPlugins("conn", List.of(mockServerPlugin("iac", "hash")));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    var source = createSource("conn");
    var expected = resolved(ArtifactState.SYNCED, iacJar, ArtifactOrigin.SONARQUBE_SERVER);

    var result = source.load(SonarPlugin.IAC.getKey());

    assertThat(result).contains(expected);
  }

  // --- listAvailableArtifacts() ---

  @Test
  void listAvailableArtifacts_should_include_language_plugin_when_language_is_enabled() {
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarPlugin.JAVA.getKey())));
    var source = createSource("conn");

    var result = source.listAvailableArtifacts(Set.of(SonarLanguage.JAVA));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).key()).isEqualTo(SonarPlugin.JAVA.getKey());
  }

  @Test
  void listAvailableArtifacts_should_exclude_language_plugin_when_language_not_enabled() {
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarPlugin.JAVA.getKey())));
    var source = createSource("conn");

    var result = source.listAvailableArtifacts(Set.of());

    assertThat(result).isEmpty();
  }

  @Test
  void listAvailableArtifacts_should_include_csharpenterprise_when_csharp_is_enabled() {
    var csEnterprise = mockServerPlugin("csharpenterprise");
    when(csEnterprise.isSonarLintSupported()).thenReturn(false);
    mockServerPlugins("conn", List.of(csEnterprise));
    var source = createSource("conn");

    var result = source.listAvailableArtifacts(Set.of(SonarLanguage.CS));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).key()).isEqualTo("csharpenterprise");
  }

  @Test
  void listAvailableArtifacts_should_exclude_csharpenterprise_when_csharp_not_enabled() {
    var csEnterprise = mockServerPlugin("csharpenterprise");
    when(csEnterprise.isSonarLintSupported()).thenReturn(false);
    mockServerPlugins("conn", List.of(csEnterprise));
    var source = createSource("conn");

    var result = source.listAvailableArtifacts(Set.of());

    assertThat(result).isEmpty();
  }

  @Test
  void listAvailableArtifacts_should_mark_csharpenterprise_as_enterprise() {
    var csEnterprise = mockServerPlugin("csharpenterprise");
    mockServerPlugins("conn", List.of(csEnterprise));
    var source = createSource("conn");

    var result = source.listAvailableArtifacts(Set.of(SonarLanguage.CS));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).isEnterprise()).isTrue();
  }

  @Test
  void listAvailableArtifacts_should_include_companion_plugin_when_sonar_lint_supported() {
    var companion = mockServerPlugin("my-companion-plugin");
    when(companion.isSonarLintSupported()).thenReturn(true);
    mockServerPlugins("conn", List.of(companion));
    var source = createSource("conn");

    var result = source.listAvailableArtifacts(Set.of());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).key()).isEqualTo("my-companion-plugin");
  }

  @Test
  void listAvailableArtifacts_should_exclude_companion_plugin_when_not_sonar_lint_supported() {
    var companion = mockServerPlugin("some-unknown-plugin");
    when(companion.isSonarLintSupported()).thenReturn(false);
    mockServerPlugins("conn", List.of(companion));
    var source = createSource("conn");

    var result = source.listAvailableArtifacts(Set.of());

    assertThat(result).isEmpty();
  }

  @Test
  void listAvailableArtifacts_should_return_empty_when_server_request_fails_and_nothing_stored() {
    mockStorage("conn");
    when(serverPluginsCache.getPlugins("conn")).thenThrow(new RuntimeException("Connection refused"));
    var source = createSource("conn");

    assertThat(source.listAvailableArtifacts(Set.of(SonarLanguage.JAVA))).isEmpty();
  }

  @Test
  void listAvailableArtifacts_should_fall_back_to_stored_plugins_when_server_request_fails() {
    mockStorage("conn");
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), javaJar, "hash");
    when(serverPluginsCache.getPlugins("conn")).thenThrow(new RuntimeException("Connection refused"));
    var source = createSource("conn");

    var result = source.listAvailableArtifacts(Set.of(SonarLanguage.JAVA));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).key()).isEqualTo(SonarPlugin.JAVA.getKey());
  }

  // --- Enterprise detection for same-key plugins (GO, IAC, TEXT) ---

  @Test
  void listAvailableArtifacts_should_mark_go_as_enterprise_when_server_version_qualifies() {
    mockStorage("conn");
    mockServerVersion("2025.2");
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarPlugin.GO.getKey())));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    var source = createSource("conn");

    var result = source.listAvailableArtifacts(Set.of(SonarLanguage.GO));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).key()).isEqualTo(SonarPlugin.GO.getKey());
    assertThat(result.get(0).isEnterprise()).isTrue();
  }

  @Test
  void listAvailableArtifacts_should_not_mark_go_as_enterprise_when_server_version_too_old() {
    mockStorage("conn");
    mockServerVersion("2025.1");
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarPlugin.GO.getKey())));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    var source = createSource("conn");

    var result = source.listAvailableArtifacts(Set.of(SonarLanguage.GO));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).isEnterprise()).isFalse();
  }

  @Test
  void listAvailableArtifacts_should_mark_go_as_enterprise_on_sonarqube_cloud() {
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarPlugin.GO.getKey())));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_CLOUD);
    var source = createSource("conn");

    var result = source.listAvailableArtifacts(Set.of(SonarLanguage.GO));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).isEnterprise()).isTrue();
  }

  @Test
  void listAvailableArtifacts_should_not_mark_java_as_enterprise() {
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarPlugin.JAVA.getKey())));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_CLOUD);
    var source = createSource("conn");

    var result = source.listAvailableArtifacts(Set.of(SonarLanguage.JAVA));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).isEnterprise()).isFalse();
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

  private static ServerPlugin mockServerPlugin(String pluginKey) {
    var plugin = mock(ServerPlugin.class);
    when(plugin.getKey()).thenReturn(pluginKey);
    return plugin;
  }

  private static ServerPlugin mockServerPlugin(String pluginKey, String hash) {
    var plugin = mock(ServerPlugin.class);
    when(plugin.getKey()).thenReturn(pluginKey);
    when(plugin.getHash()).thenReturn(hash);
    return plugin;
  }

  private void mockStoredPlugin(String pluginKey, Path jarPath, String hash) {
    when(pluginsStorage.getStoredPluginsByKey()).thenReturn(Map.of(pluginKey, new StoredPlugin(pluginKey, hash, jarPath)));
  }

  private static ResolvedArtifact resolved(ArtifactState state, Path path, ArtifactOrigin source) {
    return new ResolvedArtifact(state, path, source, null, null);
  }
}
