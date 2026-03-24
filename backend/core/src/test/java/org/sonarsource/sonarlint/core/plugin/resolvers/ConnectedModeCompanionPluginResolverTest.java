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
package org.sonarsource.sonarlint.core.plugin.resolvers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.ArtifactSource;
import org.sonarsource.sonarlint.core.plugin.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.ServerPluginsCache;
import org.sonarsource.sonarlint.core.serverapi.exception.ServerRequestException;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.storage.StorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConnectedModeCompanionPluginResolverTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private StorageService storageService;
  private ServerPluginsCache serverPluginsCache;
  private ServerPluginDownloader downloader;
  private LanguageSupportRepository languageSupportRepository;
  private PluginsStorage pluginsStorage;

  private ConnectedModeCompanionPluginResolver resolver;

  @BeforeEach
  void setUp() {
    storageService = mock(StorageService.class);
    serverPluginsCache = mock(ServerPluginsCache.class);
    downloader = mock(ServerPluginDownloader.class);
    languageSupportRepository = mock(LanguageSupportRepository.class);
    var connectionStorage = mock(ConnectionStorage.class);
    pluginsStorage = mock(PluginsStorage.class);

    when(storageService.connection("conn1")).thenReturn(connectionStorage);
    when(connectionStorage.plugins()).thenReturn(pluginsStorage);
    when(downloader.sourceFor("conn1")).thenReturn(ArtifactSource.SONARQUBE_SERVER);

    resolver = new ConnectedModeCompanionPluginResolver(storageService, serverPluginsCache, downloader, languageSupportRepository);
  }

  @Test
  void should_return_empty_when_connection_id_is_null() {
    var result = resolver.resolveCompanionPlugins(null);
    assertThat(result).isEmpty();
    verifyNoInteractions(storageService);
    verifyNoInteractions(serverPluginsCache);
  }

  @Test
  void should_ignore_language_plugins_from_server() throws ServerRequestException {
    when(pluginsStorage.getStoredPluginsByKey()).thenReturn(Map.of());
    when(serverPluginsCache.getPlugins("conn1")).thenReturn(Optional.of(List.of(
      new ServerPlugin("java", "hash", "sonar-java-plugin", true)
    )));

    var result = resolver.resolveCompanionPlugins("conn1");
    assertThat(result).isEmpty();
    verify(downloader, never()).scheduleCompanionPluginDownload(anyString(), any());
  }

  @Test
  void should_return_synced_for_stored_companion_plugin_with_same_hash(@TempDir Path tempDir) throws Exception {
    var jarFile = Files.createFile(tempDir.resolve("plugin.jar"));
    var storedPlugin = new StoredPlugin("companion1", "hash1", jarFile);
    when(pluginsStorage.getStoredPluginsByKey()).thenReturn(Map.of("companion1", storedPlugin));
    
    when(serverPluginsCache.getPlugins("conn1")).thenReturn(Optional.of(List.of(
      new ServerPlugin("companion1", "hash1", "plugin-1.0.jar", true)
    )));

    var result = resolver.resolveCompanionPlugins("conn1");
    
    assertThat(result).hasSize(1);
    assertThat(result.get("companion1").state()).isEqualTo(ArtifactState.SYNCED);
    assertThat(result.get("companion1").path()).isEqualTo(jarFile);
    verify(downloader, never()).scheduleCompanionPluginDownload(anyString(), any());
  }

  @Test
  void should_schedule_download_if_not_stored() {
    when(pluginsStorage.getStoredPluginsByKey()).thenReturn(Map.of());
    
    var serverPlugin = new ServerPlugin("companion1", "hash1", "plugin-1.0.jar", true);
    when(serverPluginsCache.getPlugins("conn1")).thenReturn(Optional.of(List.of(serverPlugin)));

    var result = resolver.resolveCompanionPlugins("conn1");
    
    assertThat(result).hasSize(1);
    assertThat(result.get("companion1").state()).isEqualTo(ArtifactState.DOWNLOADING);
    verify(downloader).scheduleCompanionPluginDownload("conn1", serverPlugin);
  }

  @Test
  void should_schedule_download_if_hash_differs(@TempDir Path tempDir) throws Exception {
    var jarFile = Files.createFile(tempDir.resolve("plugin.jar"));
    var storedPlugin = new StoredPlugin("companion1", "old_hash", jarFile);
    when(pluginsStorage.getStoredPluginsByKey()).thenReturn(Map.of("companion1", storedPlugin));
    
    var serverPlugin = new ServerPlugin("companion1", "new_hash", "plugin-1.0.jar", true);
    when(serverPluginsCache.getPlugins("conn1")).thenReturn(Optional.of(List.of(serverPlugin)));

    var result = resolver.resolveCompanionPlugins("conn1");
    
    assertThat(result).hasSize(1);
    assertThat(result.get("companion1").state()).isEqualTo(ArtifactState.DOWNLOADING);
    verify(downloader).scheduleCompanionPluginDownload("conn1", serverPlugin);
  }

  static Stream<Arguments> languageSpecificPluginsProvider() {
    return Stream.of(
      arguments("typescript", true, Set.of(), false),
      arguments("typescript", true, Set.of(SonarLanguage.TS), true),
      arguments("csharpenterprise", false, Set.of(SonarLanguage.CS), true),
      arguments("goenterprise", false, Set.of(SonarLanguage.GO), true)
    );
  }

  @ParameterizedTest
  @MethodSource("languageSpecificPluginsProvider")
  void should_handle_language_specific_plugins(String pluginKey, boolean isSupported, Set<SonarLanguage> enabledLanguages, boolean shouldDownload) {
    when(pluginsStorage.getStoredPluginsByKey()).thenReturn(Map.of());
    when(languageSupportRepository.getEnabledLanguagesInConnectedMode()).thenReturn(enabledLanguages);
    
    var serverPlugin = new ServerPlugin(pluginKey, "hash1", "plugin-1.0.jar", isSupported);
    when(serverPluginsCache.getPlugins("conn1")).thenReturn(Optional.of(List.of(serverPlugin)));

    var result = resolver.resolveCompanionPlugins("conn1");
    
    if (shouldDownload) {
      assertThat(result).hasSize(1);
      assertThat(result.get(pluginKey).state()).isEqualTo(ArtifactState.DOWNLOADING);
      verify(downloader).scheduleCompanionPluginDownload("conn1", serverPlugin);
    } else {
      assertThat(result).isEmpty();
      verify(downloader, never()).scheduleCompanionPluginDownload(anyString(), any());
    }
  }

  @Test
  void should_fallback_to_stored_companion_when_server_plugins_fetch_fails(@TempDir Path tempDir) throws Exception {
    var jarFile = Files.createFile(tempDir.resolve("plugin.jar"));
    var storedPlugin = new StoredPlugin("companion1", "hash1", jarFile);
    when(pluginsStorage.getStoredPluginsByKey()).thenReturn(Map.of("companion1", storedPlugin));
    
    when(serverPluginsCache.getPlugins("conn1")).thenThrow(new ServerRequestException("timeout"));

    var result = resolver.resolveCompanionPlugins("conn1");
    
    assertThat(result).hasSize(1);
    assertThat(result.get("companion1").state()).isEqualTo(ArtifactState.SYNCED);
    assertThat(result.get("companion1").path()).isEqualTo(jarFile);
    verify(downloader, never()).scheduleCompanionPluginDownload(anyString(), any());
  }

  @Test
  void should_include_stored_companions_not_on_server(@TempDir Path tempDir) throws Exception {
    var jarFile = Files.createFile(tempDir.resolve("plugin.jar"));
    var storedPlugin = new StoredPlugin("companion2", "hash2", jarFile);
    when(pluginsStorage.getStoredPluginsByKey()).thenReturn(Map.of("companion2", storedPlugin));
    
    when(serverPluginsCache.getPlugins("conn1")).thenReturn(Optional.of(List.of(
      new ServerPlugin("companion1", "hash1", "plugin-1.0.jar", true)
    )));

    var result = resolver.resolveCompanionPlugins("conn1");
    
    assertThat(result).hasSize(2);
    assertThat(result.get("companion2").state()).isEqualTo(ArtifactState.SYNCED);
    assertThat(result.get("companion1").state()).isEqualTo(ArtifactState.DOWNLOADING);
  }

}
