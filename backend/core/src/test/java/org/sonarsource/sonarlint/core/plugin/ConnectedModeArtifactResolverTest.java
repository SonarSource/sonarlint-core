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
package org.sonarsource.sonarlint.core.plugin;
import org.sonarsource.sonarlint.core.plugin.source.server.ServerPluginsCache;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.plugins.SonarPlugin;
import org.sonarsource.sonarlint.core.plugin.resolvers.ConnectedModeArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.PluginOverrideRegistry;
import org.sonarsource.sonarlint.core.plugin.source.server.ServerPluginDownloader;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverapi.exception.ServerRequestException;
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

class ConnectedModeArtifactResolverTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  private Path javaJar;
  private Path secretsJar;
  private Path iacJar;

  private StorageService storageService;
  private ConnectionConfigurationRepository connectionRepo;
  private ConnectionStorage connectionStorage;
  private ServerInfoStorage serverInfoStorage;
  private PluginsStorage pluginsStorage;
  private ServerPluginsCache serverPluginsCache;
  private ServerPluginDownloader downloader;
  private PluginOverrideRegistry overrideRegistry;

  @BeforeEach
  void setUp() throws IOException {
    javaJar = Files.createFile(tempDir.resolve("sonar-java-plugin.jar"));
    secretsJar = Files.createFile(tempDir.resolve("sonar-text-plugin.jar"));
    iacJar = Files.createFile(tempDir.resolve("sonar-iac-plugin.jar"));

    storageService = mock(StorageService.class);
    connectionRepo = mock(ConnectionConfigurationRepository.class);
    connectionStorage = mock(ConnectionStorage.class);
    serverInfoStorage = mock(ServerInfoStorage.class);
    pluginsStorage = mock(PluginsStorage.class);
    serverPluginsCache = mock(ServerPluginsCache.class);
    downloader = mock(ServerPluginDownloader.class);
    overrideRegistry = new PluginOverrideRegistry(connectionRepo, storageService);
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(connectionStorage.plugins()).thenReturn(pluginsStorage);
    when(pluginsStorage.getStoredPluginsByKey()).thenReturn(Map.of());
  }

  @Test
  void should_return_empty_when_connection_id_is_null() {
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.JAVA, null);

    assertThat(result).isEmpty();
  }

  @Test
  void should_return_synced_from_storage_when_plugin_is_in_storage_but_not_on_server() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), javaJar, "hash");
    mockServerPlugins("conn", List.of());
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    var resolver = createResolver(Set.of());
    var expected = resolved(ArtifactState.SYNCED, javaJar, ArtifactOrigin.SONARQUBE_SERVER);

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).contains(expected);
  }

  @Test
  void should_return_empty_when_plugin_is_not_in_storage_and_not_on_server() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of());
    mockServerPlugins("conn", List.of());
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).isEmpty();
  }

  @Test
  void should_trigger_download_when_stored_jar_does_not_exist_on_disk() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), tempDir.resolve("missing.jar"), "hash");
    var serverPlugin = mockServerPlugin(SonarPlugin.JAVA.getKey(), "hash");
    mockServerPlugins("conn", List.of(serverPlugin));
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).contains(new ResolvedArtifact(ArtifactState.DOWNLOADING, null, null, null));
    verify(downloader).schedulePluginDownload("conn", serverPlugin);
  }

  @Test
  void should_return_downloading_when_plugin_is_not_in_storage_but_on_server() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of());
    var serverPlugin = mockServerPlugin(SonarPlugin.JAVA.getKey());
    mockServerPlugins("conn", List.of(serverPlugin));
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).contains(new ResolvedArtifact(ArtifactState.DOWNLOADING, null, null, null));
    verify(downloader).schedulePluginDownload("conn", serverPlugin);
  }

  @Test
  void should_return_empty_when_server_plugin_list_request_fails_and_no_storage() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    when(serverPluginsCache.getPlugins("conn")).thenThrow(new ServerRequestException("Connection refused"));
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).isEmpty();
  }

  @Test
  void should_return_synced_from_storage_when_server_plugin_list_request_fails() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), javaJar, "hash");
    when(serverPluginsCache.getPlugins("conn")).thenThrow(new ServerRequestException("Connection refused"));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).contains(new ResolvedArtifact(ArtifactState.SYNCED, javaJar, ArtifactOrigin.SONARQUBE_SERVER, null));
  }

  @Test
  void should_return_synced_with_sonarqube_server_source() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), javaJar, "hash");
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarPlugin.JAVA.getKey(), "hash")));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    var resolver = createResolver(Set.of());
    var expected = resolved(ArtifactState.SYNCED, javaJar, ArtifactOrigin.SONARQUBE_SERVER);

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).contains(expected);
  }

  @Test
  void should_return_synced_with_sonarqube_cloud_source() {
    mockConnection("cloud", ConnectionKind.SONARCLOUD);
    mockStoredPlugin(SonarPlugin.JAVA.getKey(), javaJar, "hash");
    mockServerPlugins("cloud", List.of(mockServerPlugin(SonarPlugin.JAVA.getKey(), "hash")));
    when(downloader.sourceFor("cloud")).thenReturn(ArtifactOrigin.SONARQUBE_CLOUD);
    var resolver = createResolver(Set.of());
    var expected = resolved(ArtifactState.SYNCED, javaJar, ArtifactOrigin.SONARQUBE_CLOUD);

    var result = resolver.resolve(SonarLanguage.JAVA, "cloud");

    assertThat(result).contains(expected);
  }

  // --- skipSyncPluginKeys (IDE-embedded override) ---

  @Test
  void should_return_empty_when_language_plugin_key_is_in_skip_set() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarPlugin.JAVA.getKey(), javaJar));
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarPlugin.JAVA.getKey())));
    var resolver = createResolver(Set.of(SonarPlugin.JAVA.getKey()));

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).isEmpty();
  }

  // --- enterprise languages ---

  @Test
  void should_return_empty_for_enterprise_language_when_sq_version_is_below_minimum() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    // SECRETS minimum is 10.4, use 10.3
    mockServerVersion(Version.create("10.3"));
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarPlugin.TEXT.getKey(), secretsJar));
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.SECRETS, "conn");

    assertThat(result).isEmpty();
  }

  @Test
  void should_return_synced_for_enterprise_language_when_sq_version_meets_minimum() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockServerVersion(Version.create("10.4"));
    mockStoredPlugin(SonarPlugin.TEXT.getKey(), secretsJar, "hash");
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarPlugin.TEXT.getKey(), "hash")));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    var resolver = createResolver(Set.of());
    var expected = resolved(ArtifactState.SYNCED, secretsJar, ArtifactOrigin.SONARQUBE_SERVER);

    var result = resolver.resolve(SonarLanguage.SECRETS, "conn");

    assertThat(result).contains(expected);
  }

  @Test
  void should_return_synced_for_enterprise_language_on_sonarqube_cloud() {
    mockConnection("cloud", ConnectionKind.SONARCLOUD);
    mockStoredPlugin(SonarPlugin.TEXT.getKey(), secretsJar, "hash");
    mockServerPlugins("cloud", List.of(mockServerPlugin(SonarPlugin.TEXT.getKey(), "hash")));
    when(downloader.sourceFor("cloud")).thenReturn(ArtifactOrigin.SONARQUBE_CLOUD);
    var resolver = createResolver(Set.of());
    var expected = resolved(ArtifactState.SYNCED, secretsJar, ArtifactOrigin.SONARQUBE_CLOUD);

    var result = resolver.resolve(SonarLanguage.SECRETS, "cloud");

    assertThat(result).contains(expected);
  }

  @Test
  void should_resolve_enterprise_language_even_when_its_key_is_in_skip_set() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockServerVersion(Version.create("10.4"));
    mockStoredPlugin(SonarPlugin.TEXT.getKey(), secretsJar, "hash");
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarPlugin.TEXT.getKey(), "hash")));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    // Even though the plugin key is in the skip set, enterprise check takes priority
    var resolver = createResolver(Set.of(SonarPlugin.TEXT.getKey()));
    var expected = resolved(ArtifactState.SYNCED, secretsJar, ArtifactOrigin.SONARQUBE_SERVER);

    var result = resolver.resolve(SonarLanguage.SECRETS, "conn");

    assertThat(result).contains(expected);
  }

  @Test
  void should_return_empty_for_enterprise_language_when_connection_is_not_found() {
    when(connectionRepo.getConnectionById("unknown")).thenReturn(null);
    when(storageService.connection("unknown")).thenReturn(connectionStorage);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarPlugin.TEXT.getKey(), secretsJar));
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.SECRETS, "unknown");

    assertThat(result).isEmpty();
  }

  @Test
  void should_return_empty_for_enterprise_language_when_server_info_is_missing() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    when(storageService.connection("conn")).thenReturn(connectionStorage);
    when(serverInfoStorage.read()).thenReturn(Optional.empty());
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarPlugin.TEXT.getKey(), secretsJar));
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.SECRETS, "conn");

    assertThat(result).isEmpty();
  }

  @Test
  void should_resolve_textenterprise_when_connected_to_sonarcloud() throws IOException {
    var textEnterpriseJar = Files.createFile(tempDir.resolve("sonar-text-enterprise-plugin.jar"));
    mockConnection("cloud", ConnectionKind.SONARCLOUD);
    mockStoredPlugin("textenterprise", textEnterpriseJar, "hash");
    mockServerPlugins("cloud", List.of(mockServerPlugin("textenterprise", "hash")));
    when(downloader.sourceFor("cloud")).thenReturn(ArtifactOrigin.SONARQUBE_CLOUD);
    var resolver = createResolver(Set.of(SonarPlugin.TEXT.getKey()));
    var expected = resolved(ArtifactState.SYNCED, textEnterpriseJar, ArtifactOrigin.SONARQUBE_CLOUD);

    var result = resolver.resolve(SonarLanguage.SECRETS, "cloud");

    assertThat(result).contains(expected);
  }

  @Test
  void should_resolve_goenterprise_when_sq_version_meets_minimum() throws IOException {
    var goEnterpriseJar = Files.createFile(tempDir.resolve("sonar-go-enterprise-plugin.jar"));
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockServerVersion(Version.create("2025.2"));
    mockStoredPlugin("goenterprise", goEnterpriseJar, "hash");
    mockServerPlugins("conn", List.of(mockServerPlugin("goenterprise", "hash")));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    var resolver = createResolver(Set.of(SonarPlugin.GO.getKey()));
    var expected = resolved(ArtifactState.SYNCED, goEnterpriseJar, ArtifactOrigin.SONARQUBE_SERVER);

    var result = resolver.resolve(SonarLanguage.GO, "conn");

    assertThat(result).contains(expected);
  }

  // --- ANSIBLE/GITHUBACTIONS use "iac" plugin key ---

  @Test
  void should_resolve_ansible_via_iac_plugin_on_old_server() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockStoredPlugin("iac", iacJar, "hash");
    mockServerPlugins("conn", List.of(mockServerPlugin("iac", "hash")));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    var resolver = createResolver(Set.of());
    var expected = resolved(ArtifactState.SYNCED, iacJar, ArtifactOrigin.SONARQUBE_SERVER);

    var result = resolver.resolve(SonarLanguage.ANSIBLE, "conn");

    assertThat(result).contains(expected);
  }

  @Test
  void should_resolve_githubactions_via_iac_plugin_on_new_server() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockStoredPlugin("iac", iacJar, "hash");
    mockServerPlugins("conn", List.of(mockServerPlugin("iac", "hash")));
    when(downloader.sourceFor("conn")).thenReturn(ArtifactOrigin.SONARQUBE_SERVER);
    var resolver = createResolver(Set.of());
    var expected = resolved(ArtifactState.SYNCED, iacJar, ArtifactOrigin.SONARQUBE_SERVER);

    var result = resolver.resolve(SonarLanguage.GITHUBACTIONS, "conn");

    assertThat(result).contains(expected);
  }

  private ConnectedModeArtifactResolver createResolver(Set<String> skipSyncPluginKeys) {
    var initializeParams = mock(InitializeParams.class);
    when(initializeParams.getConnectedModeEmbeddedPluginPathsByKey()).thenReturn(skipSyncPluginKeys.stream().collect(Collectors.toMap(k -> k, k -> Path.of("dummy"))));
    return new ConnectedModeArtifactResolver(storageService, serverPluginsCache, downloader, overrideRegistry, initializeParams);
  }

  private void mockConnection(String connectionId, ConnectionKind kind) {
    var connection = mock(AbstractConnectionConfiguration.class);
    when(connection.getConnectionId()).thenReturn(connectionId);
    when(connection.getKind()).thenReturn(kind);
    when(connectionRepo.getConnectionById(connectionId)).thenReturn(connection);
    when(storageService.connection(connectionId)).thenReturn(connectionStorage);
  }

  private void mockServerVersion(Version version) {
    var serverInfo = mock(StoredServerInfo.class);
    when(serverInfo.version()).thenReturn(version);
    when(serverInfoStorage.read()).thenReturn(Optional.of(serverInfo));
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
    return new ResolvedArtifact(state, path, source, null);
  }

}
