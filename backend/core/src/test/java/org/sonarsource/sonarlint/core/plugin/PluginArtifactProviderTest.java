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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.event.PluginStatusChangedEvent;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.ondemand.OnDemandArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.ConnectedModeArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.EmbeddedArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.EmbeddedExtraArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.PremiumArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.UnsupportedArtifactResolver;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginArtifactProviderTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final Version PLUGIN_JAR_VERSION = Version.create("3.1.1");

  @TempDir
  Path tempDir;

  private LanguageSupportRepository languageSupportRepository;
  private StorageService storageService;
  private ConnectionConfigurationRepository connectionRepo;
  private EmbeddedExtraArtifactResolver embeddedExtraResolver;
  private SonarQubeClientManager sonarQubeClientManager;
  private ServerPluginsCache serverPluginsCache;
  private ApplicationEventPublisher eventPublisher;
  private ExecutorService downloadExecutor;

  @BeforeEach
  void setUp() {
    languageSupportRepository = mock(LanguageSupportRepository.class);
    storageService = mock(StorageService.class);
    connectionRepo = mock(ConnectionConfigurationRepository.class);
    embeddedExtraResolver = mock(EmbeddedExtraArtifactResolver.class);
    sonarQubeClientManager = mock(SonarQubeClientManager.class);
    serverPluginsCache = mock(ServerPluginsCache.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    // No-op executor: submitted tasks never run, so async downloads don't interfere with unit tests
    downloadExecutor = mock(ExecutorService.class);
    when(languageSupportRepository.getEnabledLanguagesInConnectedMode()).thenReturn(Set.of());
  }

  @Test
  void should_return_unsupported_when_language_is_not_supported_in_any_mode() {
    var provider = buildProvider();

    var result = provider.resolve(null);

    assertThat(result.get(SonarLanguage.JAVA).status().state()).isEqualTo(ArtifactState.UNSUPPORTED);
  }

  @Test
  void should_return_failed_in_standalone_when_no_embedded_path_is_available() {
    when(languageSupportRepository.isEnabledInStandaloneMode(SonarLanguage.JAVA)).thenReturn(true);
    var provider = buildProvider();

    var result = provider.resolve(null);

    assertThat(result.get(SonarLanguage.JAVA).status().state()).isEqualTo(ArtifactState.FAILED);
  }

  @Test
  void should_return_active_embedded_in_standalone_when_embedded_path_matches_language() throws IOException {
    when(languageSupportRepository.isEnabledInStandaloneMode(SonarLanguage.JAVA)).thenReturn(true);
    var javaJar = createJar("sonar-java-plugin.jar");
    var provider = buildProvider(Set.of(javaJar));

    var result = provider.resolve(null);

    assertThat(result.get(SonarLanguage.JAVA))
      .extracting(a -> a.status().state(), a -> a.status().source(), AnalyzerArtifacts::pluginJar)
      .containsExactly(ArtifactState.ACTIVE, ArtifactSource.EMBEDDED, javaJar);
  }

  @Test
  void should_return_unsupported_in_connected_mode_when_language_is_not_enabled_for_connected_mode() {
    // language supported only in standalone, not connected
    when(languageSupportRepository.isEnabledInStandaloneMode(SonarLanguage.JAVA)).thenReturn(true);
    var provider = buildProvider();

    var result = provider.resolve("conn");

    assertThat(result.get(SonarLanguage.JAVA).status().state()).isEqualTo(ArtifactState.UNSUPPORTED);
  }

  @Test
  void should_return_synced_in_connected_mode_when_plugin_is_in_storage() throws IOException {
    when(languageSupportRepository.isEnabledInConnectedMode(SonarLanguage.JAVA)).thenReturn(true);
    var javaJar = tempDir.resolve("sonar-java-plugin.jar");
    Files.createFile(javaJar);
    mockConnectionStorage("conn", ConnectionKind.SONARQUBE, Map.of(SonarLanguage.JAVA.getPluginKey(), javaJar));
    var provider = buildProvider();

    var result = provider.resolve("conn");

    assertThat(result.get(SonarLanguage.JAVA))
      .extracting(a -> a.status().state(), a -> a.status().source())
      .containsExactly(ArtifactState.SYNCED, ArtifactSource.SONARQUBE_SERVER);
  }

  @Test
  void should_return_premium_when_language_is_connected_mode_only_and_not_on_server() {
    when(languageSupportRepository.isEnabledInConnectedMode(SonarLanguage.COBOL)).thenReturn(true);
    var connectionStorage = mock(ConnectionStorage.class);
    var pluginsStorage = mock(PluginsStorage.class);
    var serverInfoStorage = mock(ServerInfoStorage.class);
    when(connectionStorage.plugins()).thenReturn(pluginsStorage);
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(serverInfoStorage.read()).thenReturn(Optional.empty());
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(Map.of());
    when(storageService.connection("conn")).thenReturn(connectionStorage);
    var connection = mock(AbstractConnectionConfiguration.class);
    when(connection.getKind()).thenReturn(ConnectionKind.SONARQUBE);
    when(connectionRepo.getConnectionById("conn")).thenReturn(connection);
    var provider = buildProvider();

    var result = provider.resolve("conn");

    assertThat(result.get(SonarLanguage.COBOL).status().state()).isEqualTo(ArtifactState.PREMIUM);
  }

  @Test
  void should_return_same_instance_on_second_call_for_same_connection() {
    var provider = buildProvider();

    var result1 = provider.resolve(null);
    var result2 = provider.resolve(null);

    assertThat(result1).isSameAs(result2);
  }

  @Test
  void should_invalidate_cache_for_given_connection_when_evicted() {
    var provider = buildProvider();

    var result1 = provider.resolve("conn");
    provider.evict("conn");
    var result2 = provider.resolve("conn");

    assertThat(result1).isNotSameAs(result2);
  }

  @Test
  void should_not_invalidate_other_connections_when_evicting_one() {
    var provider = buildProvider();

    var resultOther = provider.resolve("other");
    provider.evict("conn");
    var resultOtherAfter = provider.resolve("other");

    assertThat(resultOther).isSameAs(resultOtherAfter);
  }

  @Test
  void should_read_version_from_embedded_jar_for_active_plugin() throws IOException {
    when(languageSupportRepository.isEnabledInStandaloneMode(SonarLanguage.JAVA)).thenReturn(true);
    var javaJar = createJar("sonar-java-plugin.jar", "java", PLUGIN_JAR_VERSION.toString());
    var provider = buildProvider(Set.of(javaJar));

    var result = provider.resolve(null);

    assertThat(result.get(SonarLanguage.JAVA).status())
      .extracting(PluginStatus::state, PluginStatus::actualVersion)
      .containsExactly(ArtifactState.ACTIVE, PLUGIN_JAR_VERSION);
  }

  @Test
  void should_read_version_from_synced_jar_for_connected_plugin() throws URISyntaxException {
    when(languageSupportRepository.isEnabledInConnectedMode(SonarLanguage.JAVA)).thenReturn(true);
    mockConnectionStorage("conn", ConnectionKind.SONARQUBE, Map.of(SonarLanguage.JAVA.getPluginKey(), testJarPath()));
    var provider = buildProvider();

    var result = provider.resolve("conn");

    assertThat(result.get(SonarLanguage.JAVA).status())
      .extracting(PluginStatus::state, PluginStatus::actualVersion)
      .containsExactly(ArtifactState.SYNCED, PLUGIN_JAR_VERSION);
  }

  @Test
  void should_report_plugins_ready_when_all_are_active_or_unsupported() {
    when(languageSupportRepository.isEnabledInStandaloneMode(SonarLanguage.JAVA)).thenReturn(true);
    var provider = buildProvider();

    // Populate the cache — all languages will be UNSUPPORTED or FAILED (no embedded jars)
    provider.resolve(null);

    assertThat(provider.arePluginsReady(null)).isTrue();
  }

  @Test
  void should_report_plugins_not_ready_when_any_is_downloading() {
    var provider = buildProvider();

    // Populate cache first so the entry exists
    provider.resolve(null);
    // Simulate a DOWNLOADING status update via event
    var downloadingStatus = new PluginStatus(SonarLanguage.CS, ArtifactState.DOWNLOADING, null, null, null, null);
    provider.onPluginStatusChanged(new PluginStatusChangedEvent(null, List.of(downloadingStatus)));

    assertThat(provider.arePluginsReady(null)).isFalse();
  }

  @Test
  void should_report_plugins_ready_after_downloading_completes() {
    var provider = buildProvider();

    // Populate cache
    provider.resolve(null);
    // Simulate DOWNLOADING then ACTIVE
    var downloadingStatus = new PluginStatus(SonarLanguage.CS, ArtifactState.DOWNLOADING, null, null, null, null);
    provider.onPluginStatusChanged(new PluginStatusChangedEvent(null, List.of(downloadingStatus)));
    var activeStatus = new PluginStatus(SonarLanguage.CS, ArtifactState.ACTIVE, ArtifactSource.ON_DEMAND, null, null, null);
    provider.onPluginStatusChanged(new PluginStatusChangedEvent(null, List.of(activeStatus)));

    assertThat(provider.arePluginsReady(null)).isTrue();
  }

  private PluginArtifactProvider buildProvider() {
    return buildProvider(Set.of());
  }

  private PluginArtifactProvider buildProvider(Set<Path> embeddedPaths) {
    return buildProvider(embeddedPaths, Set.of());
  }

  private PluginArtifactProvider buildProvider(Set<Path> embeddedPaths, Set<String> disabledKeys) {
    var userPaths = mock(UserPaths.class);
    when(userPaths.getStorageRoot()).thenReturn(tempDir);
    var params = mockParams(embeddedPaths, disabledKeys);
    var connectedMode = new ConnectedModeArtifactResolver(storageService, connectionRepo, sonarQubeClientManager, serverPluginsCache, eventPublisher, downloadExecutor,
      params.getConnectedModeEmbeddedPluginPathsByKey().keySet());
    return new PluginArtifactProvider(
      storageService,
      languageSupportRepository,
      new UnsupportedArtifactResolver(languageSupportRepository),
      new EmbeddedArtifactResolver(params),
      connectedMode,
      new OnDemandArtifactResolver(userPaths, mock(HttpClientProvider.class), Map.of(), eventPublisher, downloadExecutor),
      new PremiumArtifactResolver(languageSupportRepository),
      embeddedExtraResolver,
      serverPluginsCache,
      eventPublisher);
  }

  private static InitializeParams mockParams(Set<Path> embeddedPaths, Set<String> disabledKeys) {
    var params = mock(InitializeParams.class);
    when(params.getEmbeddedPluginPaths()).thenReturn(embeddedPaths);
    when(params.getConnectedModeEmbeddedPluginPathsByKey()).thenReturn(Map.of());
    when(params.getDisabledPluginKeysForAnalysis()).thenReturn(disabledKeys);
    when(params.getLanguageSpecificRequirements()).thenReturn(null);
    return params;
  }

  private static Path testJarPath() throws URISyntaxException {
    var resource = PluginArtifactProviderTest.class.getClassLoader().getResource("sonar-cpp-plugin-test.jar");
    return Path.of(resource.toURI());
  }

  private Path createJar(String name) throws IOException {
    var pluginKey = name.replace("sonar-", "").replaceAll("-plugin\\.jar", "");
    return createJar(name, pluginKey, null);
  }

  private Path createJar(String name, String pluginKey, @Nullable String version) throws IOException {
    var path = tempDir.resolve(name);
    var manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Plugin-Key", pluginKey);
    if (version != null) {
      manifest.getMainAttributes().putValue("Plugin-Version", version);
    }
    try (var jos = new JarOutputStream(Files.newOutputStream(path), manifest)) {
      // empty JAR body with manifest
    }
    return path;
  }

  private void mockConnectionStorage(String connectionId, ConnectionKind kind, Map<String, Path> pluginPathsByKey) {
    var connectionStorage = mock(ConnectionStorage.class);
    var pluginsStorage = mock(PluginsStorage.class);
    var serverInfoStorage = mock(ServerInfoStorage.class);
    when(connectionStorage.plugins()).thenReturn(pluginsStorage);
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(serverInfoStorage.read()).thenReturn(Optional.empty());
    when(pluginsStorage.getStoredPluginPathsByKey()).thenReturn(pluginPathsByKey);
    when(storageService.connection(connectionId)).thenReturn(connectionStorage);
    var connection = mock(AbstractConnectionConfiguration.class);
    when(connection.getKind()).thenReturn(kind);
    when(connectionRepo.getConnectionById(connectionId)).thenReturn(connection);
    mockServerPlugins(connectionId, pluginPathsByKey.keySet().stream()
      .map(PluginArtifactProviderTest::mockServerPlugin)
      .toList());
  }

  private void mockServerPlugins(String connectionId, List<ServerPlugin> plugins) {
    when(serverPluginsCache.getPlugins(connectionId)).thenReturn(Optional.of(plugins));
  }

  private static ServerPlugin mockServerPlugin(String pluginKey) {
    var plugin = mock(ServerPlugin.class);
    when(plugin.getKey()).thenReturn(pluginKey);
    return plugin;
  }
}
