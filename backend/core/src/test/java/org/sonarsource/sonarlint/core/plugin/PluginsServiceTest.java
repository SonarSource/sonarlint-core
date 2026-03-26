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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.resolvers.ConnectedModeArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.EmbeddedArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.OnDemandArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.PremiumArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.UnsupportedArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPluginsRepository;
import org.sonarsource.sonarlint.core.plugin.resolvers.ArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.resolvers.CompanionPluginResolver;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.OmnisharpRequirementsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.PluginsSynchronizer;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

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
    var companionPluginResolver = mock(CompanionPluginResolver.class);
    mockOmnisharpLanguageRequirements();

    underTest = new PluginsService(pluginsRepository, mock(SkippedPluginsRepository.class), storageService,
      initializeParams, connectionConfigurationStorage, mock(NodeJsService.class), eventPublisher,
      List.of(companionPluginResolver), mock(UnsupportedArtifactResolver.class), mock(ConnectedModeArtifactResolver.class),
      mock(EmbeddedArtifactResolver.class), mock(OnDemandArtifactResolver.class), mock(PremiumArtifactResolver.class));
  }

  @Test
  void should_initialize_csharp_analyzers_to_null_when_no_language_requirements_passed() {
    var csharpSupport = new PluginsService.CSharpSupport(null);
    assertThat(csharpSupport.csharpOssPluginPath).isNull();
    assertThat(csharpSupport.csharpEnterprisePluginPath).isNull();
  }

  @Test
  void should_initialize_csharp_analyzers_to_null_when_no_omnisharp_requirements_passed() {
    var csharpSupport = new PluginsService.CSharpSupport(new LanguageSpecificRequirements(null, null));
    assertThat(csharpSupport.csharpOssPluginPath).isNull();
    assertThat(csharpSupport.csharpEnterprisePluginPath).isNull();
  }

  @Test
  void should_initialize_csharp_analyzers_paths_when_omnisharp_requirements_passed(@TempDir Path tempDir) {
    var monoPath = tempDir.resolve("mono");
    var net6Path = tempDir.resolve("net6Path");
    var net472Path = tempDir.resolve("net472Path");
    var ossPath = tempDir.resolve("ossPath");
    var enterprisePath = tempDir.resolve("enterprisePath");
    var csharpSupport = new PluginsService.CSharpSupport(new LanguageSpecificRequirements(null, new OmnisharpRequirementsDto(monoPath, net6Path, net472Path, ossPath, enterprisePath)));
    assertThat(csharpSupport.csharpOssPluginPath).isEqualTo(ossPath);
    assertThat(csharpSupport.csharpEnterprisePluginPath).isEqualTo(enterprisePath);
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
    mockPlugin(PluginsSynchronizer.CSHARP_ENTERPRISE_PLUGIN_ID);

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
    mockPlugin(PluginsSynchronizer.VBNET_ENTERPRISE_PLUGIN_ID);

    var result = underTest.shouldUseEnterpriseVbAnalyzer(connectionId);

    assertThat(result).isTrue();
  }

  @ParameterizedTest
  @EnumSource(value = Language.class, names = {"CS", "VBNET", "COBOL"})
  void getDotnetSupport_nullConnection_ReturnsExpectedProperties(Language language) {
    mockEnabledLanguages(language);

    var result = underTest.getDotnetSupport(null);

    assertThat(result.getActualCsharpAnalyzerPath()).isEqualTo(ossPath);
    assertThat(result.isShouldUseCsharpEnterprise()).isFalse();
    assertThat(result.isShouldUseVbNetEnterprise()).isFalse();
    assertThat(result.isSupportsCsharp()).isEqualTo(language == Language.CS);
    assertThat(result.isSupportsVbNet()).isEqualTo(language == Language.VBNET);
  }

  @Test
  void getDotnetSupport_connectionForCloud_ReturnsEnterpriseProperties() {
    var connectionId = "SQC";
    var connection = createConnection(connectionId, ConnectionKind.SONARCLOUD);
    mockConnection(connection);

    var result = underTest.getDotnetSupport(connectionId);

    assertThat(result.getActualCsharpAnalyzerPath()).isEqualTo(enterprisePath);
    assertThat(result.isShouldUseCsharpEnterprise()).isTrue();
    assertThat(result.isShouldUseVbNetEnterprise()).isTrue();
  }

  @Test
  void getDotnetSupport_connectionIsToServer_Older_Than_10_8_ReturnsEnterpriseProperties() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.7"));

    var result = underTest.getDotnetSupport(connectionId);

    assertThat(result.getActualCsharpAnalyzerPath()).isEqualTo(enterprisePath);
    assertThat(result.isShouldUseCsharpEnterprise()).isTrue();
    assertThat(result.isShouldUseVbNetEnterprise()).isTrue();
  }

  @Test
  void getDotnetSupport_connectionIsToServerWithRepackagedCsharpPlugin_ReturnsEnterprisePropertiesForCsharp() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin(PluginsSynchronizer.CSHARP_ENTERPRISE_PLUGIN_ID);

    var result = underTest.getDotnetSupport(connectionId);

    assertThat(result.getActualCsharpAnalyzerPath()).isEqualTo(enterprisePath);
    assertThat(result.isShouldUseCsharpEnterprise()).isTrue();
    assertThat(result.isShouldUseVbNetEnterprise()).isFalse();
  }

  @Test
  void getDotnetSupport_connectionIsToServerWithRepackagedVbPlugin_ReturnsEnterprisePropertiesForVb() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin(PluginsSynchronizer.VBNET_ENTERPRISE_PLUGIN_ID);

    var result = underTest.getDotnetSupport(connectionId);

    assertThat(result.getActualCsharpAnalyzerPath()).isEqualTo(ossPath);
    assertThat(result.isShouldUseCsharpEnterprise()).isFalse();
    assertThat(result.isShouldUseVbNetEnterprise()).isTrue();
  }

  @Test
  void should_return_list_size_equal_to_sonar_language_values() {
    var connectionId = "connection1";

    var result = underTest.getPluginStatuses(connectionId);

    assertThat(result).hasSize(SonarLanguage.values().length);
  }

  @Test
  void unloadPlugins_should_publish_event_when_plugins_were_loaded() {
    var connectionId = "connection1";
    when(pluginsRepository.getLoadedPlugins(connectionId)).thenReturn(mock(LoadedPlugins.class));

    underTest.unloadPlugins(connectionId);

    var captor = ArgumentCaptor.forClass(PluginStatusesChangedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().connectionId()).isEqualTo(connectionId);
    assertThat(captor.getValue().pluginStatuses()).isNotNull();
  }

  @Test
  void unloadPlugins_should_not_publish_event_when_no_plugins_were_loaded() {
    var connectionId = "connection1";
    when(pluginsRepository.getLoadedPlugins(connectionId)).thenReturn(null);

    underTest.unloadPlugins(connectionId);

    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void unloadEmbeddedPlugins_should_publish_event_when_embedded_plugins_were_loaded() {
    when(pluginsRepository.getLoadedEmbeddedPlugins()).thenReturn(mock(LoadedPlugins.class));

    underTest.unloadPlugins(null);

    var captor = ArgumentCaptor.forClass(PluginStatusesChangedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().connectionId()).isNull();
    assertThat(captor.getValue().pluginStatuses()).isNotNull();
  }

  @Test
  void unloadEmbeddedPlugins_should_not_publish_event_when_no_embedded_plugins_were_loaded() {
    when(pluginsRepository.getLoadedEmbeddedPlugins()).thenReturn(null);

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
    var plugin = mock(StoredPlugin.class);
    when(plugin.getKey()).thenReturn(pluginKey);
    when(pluginStorage.getStoredPlugins()).thenReturn(List.of(plugin));
  }

  private void mockConnectionVersion(Version version) {
    var serverInfo = mock(StoredServerInfo.class);
    when(serverInfo.version()).thenReturn(version);
    when(serverInfoStorage.read()).thenReturn(Optional.of(serverInfo));
  }

  private void mockOmnisharpLanguageRequirements() {
    var languageSpecificRequirements = mock(LanguageSpecificRequirements.class);
    var omnisharpRequirements = mock(OmnisharpRequirementsDto.class);
    when(omnisharpRequirements.getOssAnalyzerPath()).thenReturn(ossPath);
    when(omnisharpRequirements.getEnterpriseAnalyzerPath()).thenReturn(enterprisePath);
    when(languageSpecificRequirements.getOmnisharpRequirements()).thenReturn(omnisharpRequirements);
    when(initializeParams.getLanguageSpecificRequirements()).thenReturn(languageSpecificRequirements);
  }

  private void mockEnabledLanguages(Language... languages) {
    when(initializeParams.getEnabledLanguagesInStandaloneMode()).thenReturn(Set.of(languages));
    when(initializeParams.getBackendCapabilities()).thenReturn(Set.of());
  }

}
