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
package org.sonarsource.sonarlint.core.sca;

import com.sonar.sca.scanner.ScaScannerOptions;
import com.sonar.sca.scanner.analyzeproject.AnalyzeProjectOptions;
import com.sonar.sca.scanner.analyzeproject.response.AnalysisErrorCode;
import com.sonar.sca.scanner.analyzeproject.response.AnalysisErrorResource;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectIssue;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectRelease;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectResponse;
import com.sonar.sca.scanner.analyzeproject.response.Cwe;
import com.sonar.sca.scanner.analyzeproject.response.ScaIssueType;
import com.sonar.sca.scanner.analyzeproject.response.SoftwareQuality;
import com.sonar.sca.scanner.analyzeproject.response.VersionOption;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.SonarCloudRegion;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.analysis.UserAnalysisPropertiesRepository;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.connection.SonarQubeClient;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.CheckDependencyRiskSupportedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.features.Feature;
import org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfiguration;
import org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfigurationStorage;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.ServerSettings;
import org.sonarsource.sonarlint.core.serverconnection.SonarProjectStorage;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScaProjectAnalysisServiceTests {

  @RegisterExtension
  static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";
  private static final String PROJECT_KEY = "projectKey";

  @TempDir
  Path tempDir;

  private final ConfigurationRepository configurationRepository = new ConfigurationRepository();
  private final ConnectionConfigurationRepository connectionRepository = new ConnectionConfigurationRepository();
  private final SonarQubeClientManager sonarQubeClientManager = mock(SonarQubeClientManager.class);
  private final ClientFileSystemService clientFileSystemService = mock(ClientFileSystemService.class);
  private final StorageService storageService = mock(StorageService.class);
  private final UserPaths userPaths = mock(UserPaths.class);
  private final CapturingScaScannerFactory scaScannerFactory = new CapturingScaScannerFactory();
  private final DependencyRiskService dependencyRiskService = mock(DependencyRiskService.class);
  private final DependencyRiskDtoMapper dependencyRiskDtoMapper = new DependencyRiskDtoMapper();
  private final UserAnalysisPropertiesRepository userAnalysisPropertiesRepository = new UserAnalysisPropertiesRepository();

  @Test
  void it_should_forward_sonarqube_analysis_options_and_map_response() throws IOException {
    var baseDir = Files.createDirectories(tempDir.resolve("project"));
    var workDir = Files.createDirectories(tempDir.resolve("work"));
    var storageRoot = Files.createDirectories(tempDir.resolve("storage"));
    setupBoundScope();
    connectionRepository.addOrReplace(new SonarQubeConnectionConfiguration(CONNECTION_ID, "https://sonarqube.example", false));
    setupBaseDirAndPaths(baseDir, workDir, storageRoot);
    setupTokenCredentials("token-value");
    setupStoredServerVersion("2025.4");
    setupStoredAnalyzerConfiguration(Map.of("sonar.verbose", "true"));
    setupServerRisks(List.of());
    scaScannerFactory.response = sampleLocalOnlyResponse();

    var response = underTest().analyzeProject(new AnalyzeDependencyRiskProjectParams(CONFIG_SCOPE_ID), new SonarLintCancelMonitor());

    assertSonarQubeScannerOptions(storageRoot);
    assertAnalysisOptions(baseDir, workDir);
    assertLocalAnalysisDiagnostics(response);
    assertThat(Files.exists(scaScannerFactory.analyzeProjectOptions.workDir())).isFalse();
  }

  @Test
  void it_should_forward_sonarcloud_urls_with_token_credentials() throws IOException {
    var baseDir = Files.createDirectories(tempDir.resolve("project"));
    var workDir = Files.createDirectories(tempDir.resolve("work"));
    var storageRoot = Files.createDirectories(tempDir.resolve("storage"));
    setupBoundScope();
    connectionRepository.addOrReplace(new SonarCloudConnectionConfiguration(URI.create("https://sonarcloud.io"), URI.create("https://api.sonarcloud.io"), CONNECTION_ID,
      "organization", SonarCloudRegion.EU, false));
    setupBaseDirAndPaths(baseDir, workDir, storageRoot);
    setupTokenCredentials("token-value");
    setupStoredAnalyzerConfiguration(Map.of());
    setupServerRisks(List.of());
    scaScannerFactory.response = new AnalyzeProjectResponse(List.of(), List.of(), List.of());

    underTest().analyzeProject(new AnalyzeDependencyRiskProjectParams(CONFIG_SCOPE_ID), new SonarLintCancelMonitor());

    assertThat(scaScannerFactory.scannerOptions.apiBaseUrl()).isEqualTo("https://api.sonarcloud.io/sca/");
    assertThat(scaScannerFactory.scannerOptions.downloadBaseUrl()).isEqualTo("https://scanner.sonarcloud.io/tidelift-cli/");
    assertThat(scaScannerFactory.scannerOptions.sonarToken()).isEqualTo("token-value");
    assertThat(scaScannerFactory.scannerOptions.headers()).isEmpty();
    assertThat(scaScannerFactory.analyzeProjectOptions.sqServerVersion()).isNull();
    assertThat(scaScannerFactory.analyzeProjectOptions.scmExclusionEnabled()).isTrue();
    assertThat(scaScannerFactory.analyzeProjectOptions.debug()).isFalse();
  }

  @Test
  void it_should_reject_username_password_credentials() throws IOException {
    var baseDir = Files.createDirectories(tempDir.resolve("project"));
    var workDir = Files.createDirectories(tempDir.resolve("work"));
    var storageRoot = Files.createDirectories(tempDir.resolve("storage"));
    setupBoundScope();
    connectionRepository.addOrReplace(new SonarQubeConnectionConfiguration(CONNECTION_ID, "https://sonarqube.example", false));
    setupBaseDirAndPaths(baseDir, workDir, storageRoot);
    setupUsernamePasswordCredentials("john", "secret");

    assertThatThrownBy(() -> underTest().analyzeProject(new AnalyzeDependencyRiskProjectParams(CONFIG_SCOPE_ID), new SonarLintCancelMonitor()))
      .hasMessageContaining("SCA project analysis requires token credentials");
    assertThat(scaScannerFactory.scannerOptions).isNull();
  }

  @Test
  void it_should_reject_analysis_when_sca_is_not_enabled() {
    setupBoundScope();
    when(dependencyRiskService.checkSupported(CONFIG_SCOPE_ID))
      .thenReturn(new CheckDependencyRiskSupportedResponse(false, "Advanced Security is not enabled"));

    assertThatThrownBy(() -> underTest().analyzeProject(new AnalyzeDependencyRiskProjectParams(CONFIG_SCOPE_ID), new SonarLintCancelMonitor()))
      .hasMessageContaining("Advanced Security is not enabled");
    assertThat(scaScannerFactory.scannerOptions).isNull();
    assertThat(scaScannerFactory.analyzeProjectOptions).isNull();
  }

  @Test
  void it_should_update_dependency_risks_from_local_analysis() throws IOException {
    setupBoundSqWithDefaults();
    setupServerRisks(List.of());
    var localAnalysis = singleIssueResponse(null, "org.example:library", "1.2.3");
    scaScannerFactory.response = localAnalysis;

    underTest().analyzeProject(new AnalyzeDependencyRiskProjectParams(CONFIG_SCOPE_ID), new SonarLintCancelMonitor());

    verify(dependencyRiskService).updateLocalAnalysisAndNotify(eq(CONFIG_SCOPE_ID), eq(localAnalysis), any());
  }

  @Test
  void it_should_propagate_parsed_files_and_errors() throws IOException {
    setupBoundSqWithDefaults();
    setupServerRisks(List.of());
    var error = new AnalysisErrorResource("error-id", AnalysisErrorCode.MISSING_LOCKFILE, "pom.xml", "Missing lock file");
    scaScannerFactory.response = new AnalyzeProjectResponse(List.of(), List.of("pom.xml"), List.of(error));

    var response = underTest().analyzeProject(new AnalyzeDependencyRiskProjectParams(CONFIG_SCOPE_ID), new SonarLintCancelMonitor());

    assertThat(response.getParsedFiles()).containsExactly("pom.xml");
    assertThat(response.getErrors()).hasSize(1);
    assertThat(response.getErrors().get(0).getId()).isEqualTo("error-id");
    assertThat(response.getErrors().get(0).getCode()).isEqualTo("MISSING_LOCKFILE");
    assertThat(response.getErrors().get(0).getPath()).isEqualTo("pom.xml");
    assertThat(response.getErrors().get(0).getMessage()).isEqualTo("Missing lock file");
  }

  private void assertSonarQubeScannerOptions(Path storageRoot) {
    assertThat(scaScannerFactory.scannerOptions.apiBaseUrl()).isEqualTo("https://sonarqube.example/api/v2/sca/");
    assertThat(scaScannerFactory.scannerOptions.downloadBaseUrl()).isEqualTo("https://sonarqube.example/api/v2/sca/clis/");
    assertThat(scaScannerFactory.scannerOptions.sonarToken()).isEqualTo("token-value");
    assertThat(scaScannerFactory.scannerOptions.headers()).isEmpty();
    assertThat(scaScannerFactory.scannerOptions.cacheDir()).isEqualTo(storageRoot.resolve("sca-scanner/cache"));
  }

  private void assertAnalysisOptions(Path baseDir, Path workDir) {
    assertThat(scaScannerFactory.analyzeProjectOptions.projectKey()).isEqualTo(PROJECT_KEY);
    assertThat(scaScannerFactory.analyzeProjectOptions.baseDir()).isEqualTo(baseDir);
    assertThat(scaScannerFactory.analyzeProjectOptions.workDir()).hasParentRaw(workDir.resolve("sca-scanner/work"));
    assertThat(scaScannerFactory.analyzeProjectOptions.scmExclusionEnabled()).isTrue();
    assertThat(scaScannerFactory.analyzeProjectOptions.debug()).isFalse();
    assertThat(scaScannerFactory.analyzeProjectOptions.scannerProperties()).containsEntry("sonar.verbose", "true");
    assertThat(scaScannerFactory.analyzeProjectOptions.insideSqc()).isFalse();
    assertThat(scaScannerFactory.analyzeProjectOptions.sqServerVersion()).isEqualTo("2025.4");
  }

  private static void assertLocalAnalysisDiagnostics(AnalyzeDependencyRiskProjectResponse response) {
    assertThat(response.getParsedFiles()).containsExactly("pom.xml");
    assertThat(response.getErrors()).hasSize(1);
  }

  private ScaProjectAnalysisService underTest() {
    var contextResolver = new ScaAnalysisContextResolver(configurationRepository, connectionRepository, sonarQubeClientManager, clientFileSystemService,
      storageService, userAnalysisPropertiesRepository);
    return new ScaProjectAnalysisService(contextResolver, userPaths, scaScannerFactory, dependencyRiskService, dependencyRiskDtoMapper);
  }

  private void setupBoundScope() {
    configurationRepository.addOrReplace(new ConfigurationScope(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID), new BindingConfiguration(CONNECTION_ID, PROJECT_KEY, false));
    lenient().when(dependencyRiskService.checkSupported(CONFIG_SCOPE_ID)).thenReturn(new CheckDependencyRiskSupportedResponse(true, null));
  }

  private void setupBoundSqWithDefaults() throws IOException {
    var baseDir = Files.createDirectories(tempDir.resolve("project"));
    var workDir = Files.createDirectories(tempDir.resolve("work"));
    var storageRoot = Files.createDirectories(tempDir.resolve("storage"));
    setupBoundScope();
    connectionRepository.addOrReplace(new SonarQubeConnectionConfiguration(CONNECTION_ID, "https://sonarqube.example", false));
    setupBaseDirAndPaths(baseDir, workDir, storageRoot);
    setupTokenCredentials("token-value");
    setupStoredServerVersion("2025.4");
    setupStoredAnalyzerConfiguration(Map.of());
  }

  private void setupBaseDirAndPaths(Path baseDir, Path workDir, Path storageRoot) {
    when(clientFileSystemService.getBaseDir(CONFIG_SCOPE_ID)).thenReturn(baseDir);
    when(userPaths.getWorkDir()).thenReturn(workDir);
    when(userPaths.getStorageRoot()).thenReturn(storageRoot);
  }

  private void setupTokenCredentials(String token) {
    setupCredentials(Either.forLeft(new TokenDto(token)));
  }

  private void setupUsernamePasswordCredentials(String username, String password) {
    setupCredentials(Either.forRight(new UsernamePasswordDto(username, password)));
  }

  private void setupCredentials(Either<TokenDto, UsernamePasswordDto> credentials) {
    when(sonarQubeClientManager.getValidClientOrThrow(CONNECTION_ID))
      .thenReturn(new SonarQubeClient(CONNECTION_ID, mock(ServerApi.class), credentials, mock(SonarLintRpcClient.class)));
  }

  private void setupStoredServerVersion(String version) {
    var connectionStorage = mock(ConnectionStorage.class);
    var serverInfoStorage = mock(ServerInfoStorage.class);
    when(storageService.connection(CONNECTION_ID)).thenReturn(connectionStorage);
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(serverInfoStorage.read()).thenReturn(Optional.of(new StoredServerInfo(Version.create(version), Set.of(Feature.SCA), new ServerSettings(Map.of()), "serverId")));
  }

  private void setupStoredAnalyzerConfiguration(Map<String, String> settings) {
    var projectStorage = mock(SonarProjectStorage.class);
    var analyzerConfigStorage = mock(AnalyzerConfigurationStorage.class);
    lenient().when(storageService.binding(any())).thenReturn(projectStorage);
    lenient().when(projectStorage.analyzerConfiguration()).thenReturn(analyzerConfigStorage);
    lenient().when(analyzerConfigStorage.read()).thenReturn(new AnalyzerConfiguration(settings, Map.of(), 0));
  }

  private void setupServerRisks(List<DependencyRiskDto> risks) {
    lenient().when(dependencyRiskService.updateLocalAnalysisAndNotify(eq(CONFIG_SCOPE_ID), any(), any())).thenReturn(risks);
  }

  private static AnalyzeProjectResponse sampleLocalOnlyResponse() {
    return singleIssueResponse(null, "org.example:library", "1.2.3");
  }

  private static AnalyzeProjectResponse singleIssueResponse(String issueKey, String packageName, String version) {
    var issue = new AnalyzeProjectIssue(issueKey, "HIGH", true, ScaIssueType.VULNERABILITY, SoftwareQuality.SECURITY, "OPEN", "CVE-1234",
      List.of(new Cwe("CWE-79", "Improper Neutralization of Input During Web Page Generation", "Cross-site scripting")),
      "9.8", "MIT", List.of(new VersionOption("1.2.4", List.of("CVE-5678"), false, "FULL", "UPGRADE")));
    var release = new AnalyzeProjectRelease("release-key", "pkg:maven/org.example/library@" + version, "maven", packageName, version, "MIT", true, true,
      false, List.of(issue), List.of("pom.xml"), List.of(List.of("root", packageName)));
    var error = new AnalysisErrorResource("error-id", AnalysisErrorCode.MISSING_LOCKFILE, "pom.xml", "Missing lock file");
    return new AnalyzeProjectResponse(List.of(release), List.of("pom.xml"), List.of(error));
  }


  private static class CapturingScaScannerFactory extends ScaScannerFactory {
    private AnalyzeProjectResponse response;
    private ScaScannerOptions scannerOptions;
    private AnalyzeProjectOptions analyzeProjectOptions;

    @Override
    public ScaProjectScanner create(ScaScannerOptions options) {
      this.scannerOptions = options;
      return analyzeProjectOptions -> {
        this.analyzeProjectOptions = analyzeProjectOptions;
        return response;
      };
    }
  }
}
