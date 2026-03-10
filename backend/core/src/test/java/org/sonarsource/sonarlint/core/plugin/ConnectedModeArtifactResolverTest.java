/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.plugin.resolvers.ConnectedModeArtifactResolver;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectedModeArtifactResolverTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final Path JAVA_JAR = Paths.get("java", "sonar-java-plugin.jar");
  private static final Path SECRETS_JAR = Paths.get("secrets", "sonar-text-plugin.jar");
  private static final Path IAC_JAR = Paths.get("iac", "sonar-iac-plugin.jar");

  private StorageService storageService;
  private ConnectionConfigurationRepository connectionRepo;
  private ConnectionStorage connectionStorage;
  private ServerInfoStorage serverInfoStorage;
  private PluginsStorage pluginsStorage;
  private SonarQubeClientManager sonarQubeClientManager;
  private ServerPluginsCache serverPluginsCache;
  private ApplicationEventPublisher eventPublisher;
  private ExecutorService downloadExecutor;

  @BeforeEach
  void setUp() {
    storageService = mock(StorageService.class);
    connectionRepo = mock(ConnectionConfigurationRepository.class);
    connectionStorage = mock(ConnectionStorage.class);
    serverInfoStorage = mock(ServerInfoStorage.class);
    pluginsStorage = mock(PluginsStorage.class);
    sonarQubeClientManager = mock(SonarQubeClientManager.class);
    serverPluginsCache = mock(ServerPluginsCache.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    // Use a no-op executor so submitted tasks never run during tests
    downloadExecutor = mock(ExecutorService.class);
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(connectionStorage.plugins()).thenReturn(pluginsStorage);
  }

  @Test
  void should_return_empty_when_connection_id_is_null() {
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.JAVA, null);

    assertThat(result).isEmpty();
  }

  @Test
  void should_return_empty_when_plugin_is_in_storage_but_not_on_server() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarLanguage.JAVA.getPluginKey(), JAVA_JAR));
    mockServerPlugins("conn", List.of());
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).isEmpty();
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
  void should_return_failed_when_plugin_is_not_in_storage_but_on_server_and_download_fails() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of());
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarLanguage.JAVA.getPluginKey())));
    doThrow(new RuntimeException("Connection refused")).when(sonarQubeClientManager).withActiveClient(any(), any());
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).contains(new ResolvedArtifact(ArtifactState.FAILED, null, null, null));
  }

  // --- resolveAsync ---

  @Test
  void should_return_downloading_when_plugin_is_not_in_storage_but_on_server() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of());
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarLanguage.JAVA.getPluginKey())));
    var resolver = createResolver(Set.of());

    var result = resolver.resolveAsync(SonarLanguage.JAVA, "conn");

    assertThat(result).contains(new ResolvedArtifact(ArtifactState.DOWNLOADING, null, null, null));
  }

  @Test
  void should_start_separate_downloads_for_same_plugin_on_different_connections() {
    mockConnection("conn1", ConnectionKind.SONARQUBE);
    mockConnection("conn2", ConnectionKind.SONARQUBE);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of());
    var javaPlugin = mockServerPlugin(SonarLanguage.JAVA.getPluginKey());
    mockServerPlugins("conn1", List.of(javaPlugin));
    mockServerPlugins("conn2", List.of(javaPlugin));
    var resolver = createResolver(Set.of());

    resolver.resolveAsync(SonarLanguage.JAVA, "conn1");
    resolver.resolveAsync(SonarLanguage.JAVA, "conn2");

    verify(downloadExecutor, times(2)).submit(any(Runnable.class));
  }

  @Test
  void should_return_synced_with_sonarqube_server_source() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarLanguage.JAVA.getPluginKey(), JAVA_JAR));
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarLanguage.JAVA.getPluginKey())));
    var resolver = createResolver(Set.of());
    var expected = resolved(ArtifactState.SYNCED, JAVA_JAR, ArtifactSource.SONARQUBE_SERVER);

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).contains(expected);
  }

  @Test
  void should_return_synced_with_sonarcloud_source() {
    mockConnection("cloud", ConnectionKind.SONARCLOUD);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarLanguage.JAVA.getPluginKey(), JAVA_JAR));
    mockServerPlugins("cloud", List.of(mockServerPlugin(SonarLanguage.JAVA.getPluginKey())));
    var resolver = createResolver(Set.of());
    var expected = resolved(ArtifactState.SYNCED, JAVA_JAR, ArtifactSource.SONARQUBE_CLOUD);

    var result = resolver.resolve(SonarLanguage.JAVA, "cloud");

    assertThat(result).contains(expected);
  }

  // --- skipSyncPluginKeys (IDE-embedded override) ---

  @Test
  void should_return_empty_when_language_plugin_key_is_in_skip_set() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarLanguage.JAVA.getPluginKey(), JAVA_JAR));
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarLanguage.JAVA.getPluginKey())));
    var resolver = createResolver(Set.of(SonarLanguage.JAVA.getPluginKey()));

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).isEmpty();
  }

  // --- enterprise languages ---

  @Test
  void should_return_empty_for_enterprise_language_when_sq_version_is_below_minimum() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    // SECRETS minimum is 10.4, use 10.3
    mockServerVersion("conn", Version.create("10.3"));
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarLanguage.SECRETS.getPluginKey(), SECRETS_JAR));
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.SECRETS, "conn");

    assertThat(result).isEmpty();
  }

  @Test
  void should_return_synced_for_enterprise_language_when_sq_version_meets_minimum() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockServerVersion("conn", Version.create("10.4"));
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarLanguage.SECRETS.getPluginKey(), SECRETS_JAR));
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarLanguage.SECRETS.getPluginKey())));
    var resolver = createResolver(Set.of());
    var expected = resolved(ArtifactState.SYNCED, SECRETS_JAR, ArtifactSource.SONARQUBE_SERVER);

    var result = resolver.resolve(SonarLanguage.SECRETS, "conn");

    assertThat(result).contains(expected);
  }

  @Test
  void should_return_synced_for_enterprise_language_on_sonarcloud() {
    mockConnection("cloud", ConnectionKind.SONARCLOUD);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarLanguage.SECRETS.getPluginKey(), SECRETS_JAR));
    mockServerPlugins("cloud", List.of(mockServerPlugin(SonarLanguage.SECRETS.getPluginKey())));
    var resolver = createResolver(Set.of());
    var expected = resolved(ArtifactState.SYNCED, SECRETS_JAR, ArtifactSource.SONARQUBE_CLOUD);

    var result = resolver.resolve(SonarLanguage.SECRETS, "cloud");

    assertThat(result).contains(expected);
  }

  @Test
  void should_resolve_enterprise_language_even_when_its_key_is_in_skip_set() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    mockServerVersion("conn", Version.create("10.4"));
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarLanguage.SECRETS.getPluginKey(), SECRETS_JAR));
    mockServerPlugins("conn", List.of(mockServerPlugin(SonarLanguage.SECRETS.getPluginKey())));
    // Even though the plugin key is in the skip set, enterprise check takes priority
    var resolver = createResolver(Set.of(SonarLanguage.SECRETS.getPluginKey()));
    var expected = resolved(ArtifactState.SYNCED, SECRETS_JAR, ArtifactSource.SONARQUBE_SERVER);

    var result = resolver.resolve(SonarLanguage.SECRETS, "conn");

    assertThat(result).contains(expected);
  }

  @Test
  void should_return_empty_for_enterprise_language_when_connection_is_not_found() {
    when(connectionRepo.getConnectionById("unknown")).thenReturn(null);
    when(storageService.connection("unknown")).thenReturn(connectionStorage);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarLanguage.SECRETS.getPluginKey(), SECRETS_JAR));
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.SECRETS, "unknown");

    assertThat(result).isEmpty();
  }

  @Test
  void should_return_empty_for_enterprise_language_when_server_info_is_missing() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    when(storageService.connection("conn")).thenReturn(connectionStorage);
    when(serverInfoStorage.read()).thenReturn(Optional.empty());
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of(SonarLanguage.SECRETS.getPluginKey(), SECRETS_JAR));
    var resolver = createResolver(Set.of());

    var result = resolver.resolve(SonarLanguage.SECRETS, "conn");

    assertThat(result).isEmpty();
  }

  // --- ANSIBLE/GITHUBACTIONS use "iac" plugin key ---

  @Test
  void should_resolve_ansible_via_iac_plugin_on_old_server() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of("iac", IAC_JAR));
    mockServerPlugins("conn", List.of(mockServerPlugin("iac")));
    var resolver = createResolver(Set.of());
    var expected = resolved(ArtifactState.SYNCED, IAC_JAR, ArtifactSource.SONARQUBE_SERVER);

    var result = resolver.resolve(SonarLanguage.ANSIBLE, "conn");

    assertThat(result).contains(expected);
  }

  @Test
  void should_resolve_githubactions_via_iac_plugin_on_new_server() {
    mockConnection("conn", ConnectionKind.SONARQUBE);
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of("iac", IAC_JAR));
    mockServerPlugins("conn", List.of(mockServerPlugin("iac")));
    var resolver = createResolver(Set.of());
    var expected = resolved(ArtifactState.SYNCED, IAC_JAR, ArtifactSource.SONARQUBE_SERVER);

    var result = resolver.resolve(SonarLanguage.GITHUBACTIONS, "conn");

    assertThat(result).contains(expected);
  }

  private ConnectedModeArtifactResolver createResolver(Set<String> skipSyncPluginKeys) {
    return new ConnectedModeArtifactResolver(storageService, connectionRepo, sonarQubeClientManager, serverPluginsCache, eventPublisher, downloadExecutor, skipSyncPluginKeys);
  }

  private void mockConnection(String connectionId, ConnectionKind kind) {
    var connection = mock(AbstractConnectionConfiguration.class);
    when(connection.getConnectionId()).thenReturn(connectionId);
    when(connection.getKind()).thenReturn(kind);
    when(connectionRepo.getConnectionById(connectionId)).thenReturn(connection);
    when(storageService.connection(connectionId)).thenReturn(connectionStorage);
  }

  private void mockServerVersion(String connectionId, Version version) {
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

  private static ResolvedArtifact resolved(ArtifactState state, Path path, ArtifactSource source) {
    return new ResolvedArtifact(state, path, source, null);
  }
}
