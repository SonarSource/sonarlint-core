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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.resolvers.ConnectedModeArtifactResolver;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPluginsRepository;
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
  private static final Version A_VERSION = Version.create("9.8.7");

  private PluginsService underTest;
  private PluginsRepository pluginsRepository;

  private ConnectionConfigurationRepository connectionConfigurationStorage;
  private StorageService storageService;
  private ConnectionStorage connectionStorage;
  private ServerInfoStorage serverInfoStorage;
  private PluginsStorage pluginStorage;
  private InitializeParams initializeParams;
  private ApplicationEventPublisher eventPublisher;
  private PluginArtifactProvider pluginArtifactProvider;

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
    var languageSupportRepository = mock(LanguageSupportRepository.class);
    pluginArtifactProvider = mock(PluginArtifactProvider.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    underTest = new PluginsService(pluginsRepository, mock(SkippedPluginsRepository.class), languageSupportRepository, storageService,
      initializeParams, connectionConfigurationStorage, mock(NodeJsService.class), eventPublisher, pluginArtifactProvider);
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
    mockPlugin(ConnectedModeArtifactResolver.VBNET_ENTERPRISE_PLUGIN_KEY);

    var result = underTest.shouldUseEnterpriseVbAnalyzer(connectionId);

    assertThat(result).isTrue();
  }

  @ParameterizedTest
  @EnumSource(value = Language.class, names = {"CS", "VBNET", "COBOL"})
  void getDotnetSupport_nullConnection_ReturnsExpectedProperties(Language language) {
    mockEnabledLanguages(language);
    mockCsArtifact(null, ossPath);

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
    mockCsArtifact(connectionId, enterprisePath);

    var result = underTest.getDotnetSupport(connectionId);

    assertThat(result.getActualCsharpAnalyzerPath()).isEqualTo(enterprisePath);
    assertThat(result.isShouldUseCsharpEnterprise()).isTrue();
    assertThat(result.isShouldUseVbNetEnterprise()).isTrue();
  }

  @Test
  void getDotnetSupport_connectionIsToServer_Older_Than_10_8_ReturnsEnterpriseProperties() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.7"));
    mockCsArtifact(connectionId, enterprisePath);

    var result = underTest.getDotnetSupport(connectionId);

    assertThat(result.getActualCsharpAnalyzerPath()).isEqualTo(enterprisePath);
    assertThat(result.isShouldUseCsharpEnterprise()).isTrue();
    assertThat(result.isShouldUseVbNetEnterprise()).isTrue();
  }

  @Test
  void getDotnetSupport_connectionIsToServerWithRepackagedCsharpPlugin_ReturnsEnterprisePropertiesForCsharp() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin(ConnectedModeArtifactResolver.CSHARP_ENTERPRISE_PLUGIN_KEY);
    mockCsArtifact(connectionId, enterprisePath);

    var result = underTest.getDotnetSupport(connectionId);

    assertThat(result.getActualCsharpAnalyzerPath()).isEqualTo(enterprisePath);
    assertThat(result.isShouldUseCsharpEnterprise()).isTrue();
    assertThat(result.isShouldUseVbNetEnterprise()).isFalse();
  }

  @Test
  void getDotnetSupport_connectionIsToServerWithRepackagedVbPlugin_ReturnsEnterprisePropertiesForVb() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin(ConnectedModeArtifactResolver.VBNET_ENTERPRISE_PLUGIN_KEY);
    mockCsArtifact(connectionId, ossPath);

    var result = underTest.getDotnetSupport(connectionId);

    assertThat(result.getActualCsharpAnalyzerPath()).isEqualTo(ossPath);
    assertThat(result.isShouldUseCsharpEnterprise()).isFalse();
    assertThat(result.isShouldUseVbNetEnterprise()).isTrue();
  }

  @Test
  void should_return_list_size_equal_to_sonar_language_values() {
    var connectionId = "connection1";
    mockArtifacts(connectionId, Map.of());

    var result = underTest.getPluginStatuses(connectionId);

    assertThat(result).hasSize(SonarLanguage.values().length);
  }

  @Test
  void should_return_active_embedded_when_plugin_not_disabled() {
    var connectionId = "connection1";
    mockArtifacts(connectionId, Map.of(SonarLanguage.JAVA,
      new PluginStatus(SonarLanguage.JAVA, ArtifactState.ACTIVE, ArtifactSource.EMBEDDED, A_VERSION, null, null)));

    var result = underTest.getPluginStatuses(connectionId);

    assertThat(result).contains(new PluginStatus(SonarLanguage.JAVA, ArtifactState.ACTIVE, ArtifactSource.EMBEDDED, A_VERSION, null, null));
  }

  @Test
  void should_return_synced_sonar_cloud_when_plugin_not_disabled() {
    var connectionId = "SQC";
    mockArtifacts(connectionId, Map.of(SonarLanguage.PYTHON,
      new PluginStatus(SonarLanguage.PYTHON, ArtifactState.SYNCED, ArtifactSource.SONARQUBE_CLOUD, A_VERSION, null, null)));

    var result = underTest.getPluginStatuses(connectionId);

    assertThat(result).contains(new PluginStatus(SonarLanguage.PYTHON, ArtifactState.SYNCED, ArtifactSource.SONARQUBE_CLOUD, A_VERSION, null, null));
  }

  @Test
  void should_return_synced_sonar_qube_server_when_plugin_not_disabled() {
    var connectionId = "SQS";
    mockArtifacts(connectionId, Map.of(SonarLanguage.PYTHON,
      new PluginStatus(SonarLanguage.PYTHON, ArtifactState.SYNCED, ArtifactSource.SONARQUBE_SERVER, A_VERSION, null, null)));

    var result = underTest.getPluginStatuses(connectionId);

    assertThat(result).contains(new PluginStatus(SonarLanguage.PYTHON, ArtifactState.SYNCED, ArtifactSource.SONARQUBE_SERVER, A_VERSION, null, null));
  }

  @Test
  void should_return_unsupported_with_null_fields_when_plugin_in_sonar_language_but_not_in_any_collection() {
    var connectionId = "connection1";
    mockArtifacts(connectionId, Map.of());

    var result = underTest.getPluginStatuses(connectionId);

    assertThat(result)
      .isNotEmpty()
      .allMatch(status ->
        status.state() == ArtifactState.UNSUPPORTED &&
          status.source() == null &&
          status.actualVersion() == null &&
          status.overriddenVersion() == null
      );
  }

  @Test
  void should_return_premium_for_connected_mode_only_languages() {
    var connectionId = "SQS";
    mockArtifacts(connectionId, Map.of(
      SonarLanguage.PYTHON, new PluginStatus(SonarLanguage.PYTHON, ArtifactState.PREMIUM, null, null, null, null)));

    var result = underTest.getPluginStatuses(connectionId);

    assertThat(result).contains(new PluginStatus(SonarLanguage.PYTHON, ArtifactState.PREMIUM, null, null, null, null));
  }

  @Test
  void unloadPlugins_should_not_publish_event() {
    var connectionId = "connection1";
    mockConnectionPlugins(connectionId, Set.of("python"), Set.of("python"));

    underTest.unloadPlugins(connectionId);

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

  private void mockEnabledLanguages(Language... languages) {
    when(initializeParams.getEnabledLanguagesInStandaloneMode()).thenReturn(Set.of(languages));
  }

  private void mockCsArtifact(@Nullable String connectionId, Path csJarPath) {
    var csStatus = new PluginStatus(SonarLanguage.CS, ArtifactState.ACTIVE, ArtifactSource.EMBEDDED, null, null, null);
    when(pluginArtifactProvider.resolve(connectionId))
      .thenReturn(Map.of(SonarLanguage.CS, new AnalyzerArtifacts(csStatus, csJarPath, Map.of())));
  }

  // Configures artifact provider to return UNSUPPORTED for all languages by default, with specific overrides
  private void mockArtifacts(String connectionId, Map<SonarLanguage, PluginStatus> overrides) {
    var artifacts = Arrays.stream(SonarLanguage.values())
      .collect(Collectors.toMap(l -> l, l -> {
        var status = overrides.getOrDefault(l, PluginStatus.unsupported(l));
        return new AnalyzerArtifacts(status, null, Map.of());
      }));
    when(pluginArtifactProvider.resolve(connectionId)).thenReturn(artifacts);
  }

  private void mockConnectionPlugins(String connectionId, Set<String> pluginKeys, Set<String> extraArtifactKeys) {
    when(pluginsRepository.getLoadedPlugins(connectionId)).thenReturn(mock(LoadedPlugins.class));
  }
}
