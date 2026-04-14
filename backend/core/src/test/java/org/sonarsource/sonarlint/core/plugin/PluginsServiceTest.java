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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ArtifactsLoadingResult;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ConnectedArtifactsLoadingStrategy;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ConnectedArtifactsLoadingStrategyFactory;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.StandaloneArtifactsLoadingStrategy;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPluginsRepository;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifactSource;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginsServiceTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final Path ossPath = Paths.get("folder", "oss");
  private static final Path enterprisePath = Paths.get("folder", "enterprise");
  private PluginsService underTest;
  private PluginsRepository pluginsRepository;
  private ConnectionConfigurationRepository connectionConfigurationStorage;
  private StorageService storageService;
  private ConnectionStorage connectionStorage;
  private ServerInfoStorage serverInfoStorage;
  private PluginsStorage pluginStorage;
  private InitializeParams initializeParams;
  private ApplicationEventPublisher eventPublisher;
  private ConnectedArtifactsLoadingStrategyFactory connectedArtifactsLoadingStrategyFactory;

  @BeforeEach
  void prepare() {
    pluginsRepository = mock(PluginsRepository.class);
    storageService = mock(StorageService.class);
    connectionConfigurationStorage = mock(ConnectionConfigurationRepository.class);
    connectionStorage = mock(ConnectionStorage.class);
    serverInfoStorage = mock(ServerInfoStorage.class);
    pluginStorage = mock(PluginsStorage.class);
    when(connectionStorage.plugins()).thenReturn(pluginStorage);
    initializeParams = mock(InitializeParams.class);
    when(initializeParams.getDisabledPluginKeysForAnalysis()).thenReturn(Set.of());
    eventPublisher = mock(ApplicationEventPublisher.class);
    when(pluginStorage.getStoredPluginsByKey()).thenReturn(Map.of());

    var standaloneArtifactsLoadingStrategy = mock(StandaloneArtifactsLoadingStrategy.class);
    connectedArtifactsLoadingStrategyFactory = mock(ConnectedArtifactsLoadingStrategyFactory.class);
    var connectedArtifactsLoadingStrategy = mock(ConnectedArtifactsLoadingStrategy.class);

    var csharpArtifact = new ResolvedArtifact(ArtifactState.ACTIVE, ossPath, ArtifactOrigin.EMBEDDED, null, null);
    when(standaloneArtifactsLoadingStrategy.resolveArtifacts()).thenReturn(new ArtifactsLoadingResult(Set.of(), Map.of("csharp", csharpArtifact)));
    when(connectedArtifactsLoadingStrategy.resolveArtifacts()).thenReturn(new ArtifactsLoadingResult(Set.of(), Map.of("csharp", csharpArtifact)));
    when(connectedArtifactsLoadingStrategyFactory.getOrCreate(any())).thenReturn(connectedArtifactsLoadingStrategy);

    var binariesArtifactSource = mock(BinariesArtifactSource.class);
    when(binariesArtifactSource.getOmnisharpExtraProperties()).thenReturn(Map.of());

    underTest = new PluginsService(pluginsRepository, mock(SkippedPluginsRepository.class), storageService,
      initializeParams, connectionConfigurationStorage, mock(NodeJsService.class), eventPublisher,
      standaloneArtifactsLoadingStrategy, connectedArtifactsLoadingStrategyFactory, binariesArtifactSource);
  }

  @Test
  void shouldUseEnterpriseCSharpAnalyzer_connectionDoesNotExist_returnsFalse() {
    var connectionId = "notExisting";
    mockNoConnection(connectionId);

    var result = underTest.shouldUseEnterpriseCSharpAnalyzer(connectionId);

    assertThat(result).isFalse();
  }

  @Test
  void shouldUseEnterpriseCSharpAnalyzer_connectionIsToCloud_returnsTrue() {
    var connectionId = "SQC";
    var connection = createConnection(connectionId, ConnectionKind.SONARCLOUD);
    mockConnection(connection);

    var result = underTest.shouldUseEnterpriseCSharpAnalyzer(connectionId);

    assertThat(result).isTrue();
  }

  @Test
  void shouldUseEnterpriseCSharpAnalyzer_connectionIsToServerThatDoesNotExistOnStorage_returnsFalse() {
    var connectionId = "SQS";
    var connection = createConnection(connectionId, ConnectionKind.SONARQUBE);
    mockConnection(connection);

    var result = underTest.shouldUseEnterpriseCSharpAnalyzer(connectionId);

    assertThat(result).isFalse();
  }

  @Test
  void shouldUseEnterpriseCSharpAnalyzer_connectionIsToServer_Older_Than_10_8_returnsTrue() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.7"));

    var result = underTest.shouldUseEnterpriseCSharpAnalyzer(connectionId);

    assertThat(result).isTrue();
  }

  @Test
  void shouldUseEnterpriseCSharpAnalyzer_connectionIsToServerWithRepackagedPluginAndPluginIsNotPresentOnTheServer_returnsFalse() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin("otherPlugin");

    var result = underTest.shouldUseEnterpriseCSharpAnalyzer(connectionId);

    assertThat(result).isFalse();
  }

  @Test
  void shouldUseEnterpriseCSharpAnalyzer_connectionIsToServerWithRepackagedPluginAndPluginIsPresentOnTheServer_returnsTrue() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin(PluginsService.CSHARP_ENTERPRISE_PLUGIN_ID);

    var result = underTest.shouldUseEnterpriseCSharpAnalyzer(connectionId);

    assertThat(result).isTrue();
  }

  @Test
  void shouldUseEnterpriseVbAnalyzer_connectionDoesNotExist_returnsFalse() {
    var connectionId = "notExisting";
    mockNoConnection(connectionId);

    var result = underTest.shouldUseEnterpriseVbAnalyzer(connectionId);

    assertThat(result).isFalse();
  }

  @Test
  void shouldUseEnterpriseVbAnalyzer_connectionIsToCloud_returnsTrue() {
    var connectionId = "SQC";
    var connection = createConnection(connectionId, ConnectionKind.SONARCLOUD);
    mockConnection(connection);

    var result = underTest.shouldUseEnterpriseVbAnalyzer(connectionId);

    assertThat(result).isTrue();
  }

  @Test
  void shouldUseEnterpriseVbAnalyzer_connectionIsToServerThatDoesNotExistOnStorage_returnsFalse() {
    var connectionId = "SQS";
    var connection = createConnection(connectionId, ConnectionKind.SONARQUBE);
    mockConnection(connection);

    var result = underTest.shouldUseEnterpriseVbAnalyzer(connectionId);

    assertThat(result).isFalse();
  }

  @Test
  void shouldUseEnterpriseVbAnalyzer_connectionIsToServer_Older_Than_10_8_returnsTrue() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.7"));

    var result = underTest.shouldUseEnterpriseVbAnalyzer(connectionId);

    assertThat(result).isTrue();
  }

  @Test
  void shouldUseEnterpriseVbAnalyzer_connectionIsToServerWithRepackagedPluginAndPluginIsNotPresentOnTheServer_returnsFalse() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin("otherPlugin");

    var result = underTest.shouldUseEnterpriseVbAnalyzer(connectionId);

    assertThat(result).isFalse();
  }

  @Test
  void shouldUseEnterpriseVbAnalyzer_connectionIsToServerWithRepackagedPluginAndPluginIsPresentOnTheServer_returnsTrue() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin(PluginsService.VBNET_ENTERPRISE_PLUGIN_ID);

    var result = underTest.shouldUseEnterpriseVbAnalyzer(connectionId);

    assertThat(result).isTrue();
  }

  @ParameterizedTest
  @EnumSource(value = Language.class, names = {"CS", "VBNET", "COBOL"})
  void getEmbeddedPlugins_extraProperties_ReturnsExpectedDotnetProperties(Language language) {
    mockEnabledLanguages(language);

    var props = underTest.getEmbeddedPlugins().extraProperties();

    assertThat(props)
      .containsEntry("sonar.cs.internal.analyzerPath", ossPath.toString());
    if (language == Language.CS) {
      assertThat(props).containsEntry("sonar.cs.internal.shouldUseCsharpEnterprise", "false");
    }
    if (language == Language.VBNET) {
      assertThat(props).containsEntry("sonar.cs.internal.shouldUseVbEnterprise", "false");
    }
  }

  @Test
  void getPlugins_extraProperties_forCloud_fallsBackToOss_whenEnterpriseNotInStorage() {
    var connectionId = "SQC";
    var connection = createConnection(connectionId, ConnectionKind.SONARCLOUD);
    mockConnection(connection);
    mockEnabledLanguages(Language.CS);

    var props = underTest.getPlugins(connectionId).extraProperties();

    assertThat(props)
      .containsEntry("sonar.cs.internal.analyzerPath", ossPath.toString())
      .containsEntry("sonar.cs.internal.shouldUseCsharpEnterprise", "true");
  }

  @Test
  void getPlugins_extraProperties_forCloud_ReturnsEnterpriseProperties() {
    var connectionId = "SQC";
    var connection = createConnection(connectionId, ConnectionKind.SONARCLOUD);
    mockConnection(connection);
    mockPlugin(PluginsService.CSHARP_ENTERPRISE_PLUGIN_ID, enterprisePath);
    mockEnabledLanguages(Language.CS, Language.VBNET);

    var props = underTest.getPlugins(connectionId).extraProperties();

    assertThat(props)
      .containsEntry("sonar.cs.internal.analyzerPath", enterprisePath.toString())
      .containsEntry("sonar.cs.internal.shouldUseCsharpEnterprise", "true")
      .containsEntry("sonar.cs.internal.shouldUseVbEnterprise", "true");
  }

  @Test
  void getPlugins_extraProperties_connectionIsToServer_Older_Than_10_8_ReturnsEnterpriseProperties() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.7"));
    mockPlugin(PluginsService.CSHARP_ENTERPRISE_PLUGIN_ID, enterprisePath);
    mockEnabledLanguages(Language.CS, Language.VBNET);

    var props = underTest.getPlugins(connectionId).extraProperties();

    assertThat(props)
      .containsEntry("sonar.cs.internal.analyzerPath", enterprisePath.toString())
      .containsEntry("sonar.cs.internal.shouldUseCsharpEnterprise", "true")
      .containsEntry("sonar.cs.internal.shouldUseVbEnterprise", "true");
  }

  @Test
  void getPlugins_extraProperties_connectionIsToServerWithRepackagedCsharpPlugin_ReturnsEnterprisePropertiesForCsharp() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin(PluginsService.CSHARP_ENTERPRISE_PLUGIN_ID, enterprisePath);
    mockEnabledLanguages(Language.CS, Language.VBNET);

    var props = underTest.getPlugins(connectionId).extraProperties();

    assertThat(props)
      .containsEntry("sonar.cs.internal.analyzerPath", enterprisePath.toString())
      .containsEntry("sonar.cs.internal.shouldUseCsharpEnterprise", "true")
      .containsEntry("sonar.cs.internal.shouldUseVbEnterprise", "false");
  }

  @Test
  void getPlugins_extraProperties_connectionIsToServerWithRepackagedVbPlugin_ReturnsEnterprisePropertiesForVb() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin(PluginsService.VBNET_ENTERPRISE_PLUGIN_ID);
    mockEnabledLanguages(Language.CS, Language.VBNET);

    var props = underTest.getPlugins(connectionId).extraProperties();

    assertThat(props)
      .containsEntry("sonar.cs.internal.analyzerPath", ossPath.toString())
      .containsEntry("sonar.cs.internal.shouldUseCsharpEnterprise", "false")
      .containsEntry("sonar.cs.internal.shouldUseVbEnterprise", "true");
  }

  @Test
  void should_return_list_size_equal_to_sonar_language_values() {
    var connectionId = "connection1";
    mockNoConnection(connectionId);

    var result = underTest.getPluginStatuses(connectionId);

    assertThat(result).hasSize(SonarLanguage.values().length);
  }

  @Test
  void unloadPlugins_should_not_publish_event_when_no_plugins_were_loaded() {
    var connectionId = "connection1";

    underTest.unloadPlugins(connectionId);

    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void unloadPlugins_should_evict_connected_strategy_from_cache() {
    var connectionId = "connection1";

    underTest.unloadPlugins(connectionId);

    verify(connectedArtifactsLoadingStrategyFactory).evict(connectionId);
  }

  @Test
  void unloadEmbeddedPlugins_should_not_publish_event_when_no_embedded_plugins_were_loaded() {
    underTest.unloadPlugins(null);

    verify(eventPublisher, never()).publishEvent(any());
  }

  private void mockNoConnection(String connectionId) {
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(storageService.connection(connectionId)).thenReturn(connectionStorage);
    when(connectionConfigurationStorage.getConnectionById(connectionId)).thenReturn(null);
  }

  private void mockConnection(String connectionId, ConnectionKind kind, Version version) {
    var connection = createConnection(connectionId, kind);
    mockConnection(connection);
    mockConnectionVersion(version);
  }

  private AbstractConnectionConfiguration createConnection(String connectionId, ConnectionKind kind) {
    var connection = mock(AbstractConnectionConfiguration.class);
    when(connection.getConnectionId()).thenReturn(connectionId);
    when(connection.getKind()).thenReturn(kind);
    return connection;
  }

  private void mockConnection(AbstractConnectionConfiguration connection) {
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(storageService.connection(connection.getConnectionId())).thenReturn(connectionStorage);
    when(connectionConfigurationStorage.getConnectionById(connection.getConnectionId())).thenReturn(connection);
  }

  private void mockPlugin(String pluginKey) {
    mockPlugin(pluginKey, null);
  }

  private void mockPlugin(String pluginKey, @Nullable Path jarPath) {
    var plugin = mock(StoredPlugin.class);
    when(plugin.getKey()).thenReturn(pluginKey);
    when(plugin.getJarPath()).thenReturn(jarPath);
    when(pluginStorage.getStoredPlugins()).thenReturn(List.of(plugin));
    when(pluginStorage.getStoredPluginsByKey()).thenReturn(Map.of(pluginKey, plugin));
  }

  private void mockConnectionVersion(Version version) {
    var serverInfo = mock(StoredServerInfo.class);
    when(serverInfo.version()).thenReturn(version);
    when(serverInfoStorage.read()).thenReturn(Optional.of(serverInfo));
  }

  private void mockEnabledLanguages(Language... languages) {
    when(initializeParams.getEnabledLanguagesInStandaloneMode()).thenReturn(Set.of(languages));
    when(initializeParams.getBackendCapabilities()).thenReturn(Set.of());
  }
}
