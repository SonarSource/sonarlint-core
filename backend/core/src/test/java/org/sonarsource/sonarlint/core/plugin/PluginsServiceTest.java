/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPluginsRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginsServiceTest {

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
    mockOmnisharpLanguageRequirements();
    underTest = new PluginsService(pluginsRepository, mock(SkippedPluginsRepository.class), mock(LanguageSupportRepository.class), storageService,
      initializeParams, connectionConfigurationStorage, mock(NodeJsService.class));
  }

  @Test
  void should_initialize_csharp_analyzers_to_null_when_no_language_requirements_passed() {
    var underTest = new PluginsService.CSharpSupport(null);
    assertThat(underTest.csharpOssPluginPath).isNull();
    assertThat(underTest.csharpEnterprisePluginPath).isNull();
  }

  @Test
  void should_initialize_csharp_analyzers_to_null_when_no_omnisharp_requirements_passed() {
    var underTest = new PluginsService.CSharpSupport(new LanguageSpecificRequirements(null, null));
    assertThat(underTest.csharpOssPluginPath).isNull();
    assertThat(underTest.csharpEnterprisePluginPath).isNull();
  }

  @Test
  void should_initialize_csharp_analyzers_paths_when_omnisharp_requirements_passed(@TempDir Path tempDir) {
    var monoPath = tempDir.resolve("mono");
    var net6Path = tempDir.resolve("net6Path");
    var net472Path = tempDir.resolve("net472Path");
    var ossPath = tempDir.resolve("ossPath");
    var enterprisePath = tempDir.resolve("enterprisePath");
    var underTest = new PluginsService.CSharpSupport(new LanguageSpecificRequirements(null, new OmnisharpRequirementsDto(monoPath, net6Path, net472Path, ossPath, enterprisePath)));
    assertThat(underTest.csharpOssPluginPath).isEqualTo(ossPath);
    assertThat(underTest.csharpEnterprisePluginPath).isEqualTo(enterprisePath);
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

  private void mockOmnisharpLanguageRequirements() {
    var languageSpecificRequirements = mock(LanguageSpecificRequirements.class);
    var omnisharpRequirements = mock(OmnisharpRequirementsDto.class);
    when(omnisharpRequirements.getOssAnalyzerPath()).thenReturn(ossPath);
    when(omnisharpRequirements.getEnterpriseAnalyzerPath()).thenReturn(enterprisePath);
    when(languageSpecificRequirements.getOmnisharpRequirements()).thenReturn(omnisharpRequirements);
    when(initializeParams.getLanguageSpecificRequirements()).thenReturn(languageSpecificRequirements);
  }
}
