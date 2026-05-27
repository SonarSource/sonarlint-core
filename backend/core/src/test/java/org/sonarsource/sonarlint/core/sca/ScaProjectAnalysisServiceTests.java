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
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
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
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.features.Feature;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.ServerSettings;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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

  @Test
  void it_should_forward_sonarqube_analysis_options_and_map_response() throws IOException {
    var baseDir = Files.createDirectories(tempDir.resolve("project"));
    var workDir = Files.createDirectories(tempDir.resolve("work"));
    setupBoundScope();
    connectionRepository.addOrReplace(new SonarQubeConnectionConfiguration(CONNECTION_ID, "https://sonarqube.example", false));
    setupBaseDirAndWorkDir(baseDir, workDir);
    setupTokenCredentials("token-value");
    setupStoredServerVersion("2025.4");
    scaScannerFactory.response = sampleResponse();
    var response = underTest().analyzeProject(new AnalyzeDependencyRiskProjectParams(CONFIG_SCOPE_ID, List.of("excluded/path"), Map.of("sonar.verbose", "true"), false, true));

    assertSonarQubeScannerOptions(workDir);
    assertAnalysisOptions(baseDir, workDir);
    assertAnalysisResponse(response);
    assertThat(Files.exists(scaScannerFactory.analyzeProjectOptions.workDir())).isFalse();
  }

  @Test
  void it_should_forward_sonarcloud_urls_with_token_credentials() throws IOException {
    var baseDir = Files.createDirectories(tempDir.resolve("project"));
    var workDir = Files.createDirectories(tempDir.resolve("work"));
    setupBoundScope();
    connectionRepository.addOrReplace(new SonarCloudConnectionConfiguration(URI.create("https://sonarcloud.io"), URI.create("https://api.sonarcloud.io"), CONNECTION_ID,
      "organization", SonarCloudRegion.EU, false));
    setupBaseDirAndWorkDir(baseDir, workDir);
    setupTokenCredentials("token-value");
    scaScannerFactory.response = new AnalyzeProjectResponse(List.of(), List.of(), List.of());

    underTest().analyzeProject(new AnalyzeDependencyRiskProjectParams(CONFIG_SCOPE_ID));

    assertThat(scaScannerFactory.scannerOptions.apiBaseUrl()).isEqualTo("https://api.sonarcloud.io/sca/");
    assertThat(scaScannerFactory.scannerOptions.downloadBaseUrl()).isEqualTo("https://scanner.sonarcloud.io/tidelift-cli/");
    assertThat(scaScannerFactory.scannerOptions.sonarToken()).isEqualTo("token-value");
    assertThat(scaScannerFactory.scannerOptions.headers()).isEmpty();
    assertThat(scaScannerFactory.analyzeProjectOptions.sqServerVersion()).isNull();
    assertThat(scaScannerFactory.analyzeProjectOptions.scmExclusionEnabled()).isTrue();
    assertThat(scaScannerFactory.analyzeProjectOptions.debug()).isFalse();
    verifyNoInteractions(storageService);
  }

  @Test
  void it_should_reject_username_password_credentials() throws IOException {
    var baseDir = Files.createDirectories(tempDir.resolve("project"));
    var workDir = Files.createDirectories(tempDir.resolve("work"));
    setupBoundScope();
    connectionRepository.addOrReplace(new SonarQubeConnectionConfiguration(CONNECTION_ID, "https://sonarqube.example", false));
    setupBaseDirAndWorkDir(baseDir, workDir);
    setupUsernamePasswordCredentials("john", "secret");

    assertThatThrownBy(() -> underTest().analyzeProject(new AnalyzeDependencyRiskProjectParams(CONFIG_SCOPE_ID)))
      .hasMessageContaining("SCA project analysis requires token credentials");
    assertThat(scaScannerFactory.scannerOptions).isNull();
  }

  private void assertSonarQubeScannerOptions(Path workDir) {
    assertThat(scaScannerFactory.scannerOptions.apiBaseUrl()).isEqualTo("https://sonarqube.example/api/v2/sca/");
    assertThat(scaScannerFactory.scannerOptions.downloadBaseUrl()).isEqualTo("https://sonarqube.example/api/v2/sca/clis/");
    assertThat(scaScannerFactory.scannerOptions.sonarToken()).isEqualTo("token-value");
    assertThat(scaScannerFactory.scannerOptions.headers()).isEmpty();
    assertThat(scaScannerFactory.scannerOptions.cacheDir()).isEqualTo(workDir.resolve("sca-scanner/cache"));
  }

  private void assertAnalysisOptions(Path baseDir, Path workDir) {
    assertThat(scaScannerFactory.analyzeProjectOptions.projectKey()).isEqualTo(PROJECT_KEY);
    assertThat(scaScannerFactory.analyzeProjectOptions.baseDir()).isEqualTo(baseDir);
    assertThat(scaScannerFactory.analyzeProjectOptions.workDir()).hasParentRaw(workDir.resolve("sca-scanner/work"));
    assertThat(scaScannerFactory.analyzeProjectOptions.excludedPaths()).containsExactly("excluded/path");
    assertThat(scaScannerFactory.analyzeProjectOptions.scmExclusionEnabled()).isFalse();
    assertThat(scaScannerFactory.analyzeProjectOptions.scannerProperties()).containsEntry("sonar.verbose", "true");
    assertThat(scaScannerFactory.analyzeProjectOptions.insideSqc()).isFalse();
    assertThat(scaScannerFactory.analyzeProjectOptions.sqServerVersion()).isEqualTo("2025.4");
    assertThat(scaScannerFactory.analyzeProjectOptions.debug()).isTrue();
  }

  private static void assertAnalysisResponse(org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse response) {
    assertThat(response.getParsedFiles()).containsExactly("pom.xml");
    assertAnalysisError(response);
    assertAnalysisRelease(response);
  }

  private static void assertAnalysisError(org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse response) {
    assertThat(response.getErrors()).hasSize(1)
      .first()
      .satisfies(error -> {
        assertThat(error.getId()).isEqualTo("error-id");
        assertThat(error.getCode()).isEqualTo("MISSING_LOCKFILE");
        assertThat(error.getPath()).isEqualTo("pom.xml");
        assertThat(error.getMessage()).isEqualTo("Missing lock file");
      });
  }

  private static void assertAnalysisRelease(org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse response) {
    assertThat(response.getReleases()).hasSize(1)
      .first()
      .satisfies(release -> {
        assertThat(release.getKey()).isEqualTo("release-key");
        assertThat(release.getPackageUrl()).isEqualTo("pkg:maven/org.example/library@1.2.3");
        assertThat(release.getPackageManager()).isEqualTo("maven");
        assertThat(release.getPackageName()).isEqualTo("org.example:library");
        assertThat(release.getVersion()).isEqualTo("1.2.3");
        assertThat(release.getLicenseExpression()).isEqualTo("MIT");
        assertThat(release.isKnown()).isTrue();
        assertThat(release.isKnownPackage()).isTrue();
        assertThat(release.isNewlyIntroduced()).isFalse();
        assertThat(release.getDependencyFilePaths()).containsExactly("pom.xml");
        assertThat(release.getDependencyChains()).containsExactly(List.of("root", "org.example:library"));
        assertAnalysisIssue(release);
      });
  }

  private static void assertAnalysisIssue(org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse.AnalyzeDependencyRiskProjectReleaseDto release) {
    assertThat(release.getIssues()).hasSize(1)
      .first()
      .satisfies(issue -> {
        assertThat(issue.getKey()).isEqualTo("issue-key");
        assertThat(issue.getSeverity()).isEqualTo("HIGH");
        assertThat(issue.getShowIncreasedSeverityWarning()).isTrue();
        assertThat(issue.getType()).isEqualTo("VULNERABILITY");
        assertThat(issue.getQuality()).isEqualTo("SECURITY");
        assertThat(issue.getStatus()).isEqualTo("OPEN");
        assertThat(issue.getVulnerabilityId()).isEqualTo("CVE-1234");
        assertThat(issue.getCweIds()).containsExactly("CWE-79");
        assertThat(issue.getCvssScore()).isEqualTo("9.8");
        assertThat(issue.getSpdxLicenseId()).isEqualTo("MIT");
        assertVersionOption(issue);
      });
  }

  private static void assertVersionOption(org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse.AnalyzeDependencyRiskProjectIssueDto issue) {
    assertThat(issue.getVersionOptions()).hasSize(1)
      .first()
      .satisfies(versionOption -> {
        assertThat(versionOption.getVersion()).isEqualTo("1.2.4");
        assertThat(versionOption.getVulnerabilityIds()).containsExactly("CVE-5678");
        assertThat(versionOption.isPrerelease()).isFalse();
        assertThat(versionOption.getFixLevel()).isEqualTo("FULL");
        assertThat(versionOption.getDescriptionCode()).isEqualTo("UPGRADE");
      });
  }

  private ScaProjectAnalysisService underTest() {
    return new ScaProjectAnalysisService(configurationRepository, connectionRepository, sonarQubeClientManager, clientFileSystemService, storageService, userPaths,
      scaScannerFactory);
  }

  private void setupBoundScope() {
    configurationRepository.addOrReplace(new ConfigurationScope(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID), new BindingConfiguration(CONNECTION_ID, PROJECT_KEY, false));
  }

  private void setupBaseDirAndWorkDir(Path baseDir, Path workDir) {
    when(clientFileSystemService.getBaseDir(CONFIG_SCOPE_ID)).thenReturn(baseDir);
    when(userPaths.getWorkDir()).thenReturn(workDir);
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

  private static AnalyzeProjectResponse sampleResponse() {
    var issue = new AnalyzeProjectIssue("issue-key", "HIGH", true, ScaIssueType.VULNERABILITY, SoftwareQuality.SECURITY, "OPEN", "CVE-1234", List.of("CWE-79"),
      "9.8", "MIT", List.of(new VersionOption("1.2.4", List.of("CVE-5678"), false, "FULL", "UPGRADE")));
    var release = new AnalyzeProjectRelease("release-key", "pkg:maven/org.example/library@1.2.3", "maven", "org.example:library", "1.2.3", "MIT", true, true,
      false, List.of(issue), List.of("pom.xml"), List.of(List.of("root", "org.example:library")));
    var error = new AnalysisErrorResource("error-id", AnalysisErrorCode.MISSING_LOCKFILE, "pom.xml", "Missing lock file");
    return new AnalyzeProjectResponse(List.of(release), List.of("pom.xml"), List.of(error));
  }

  private static class CapturingScaScannerFactory extends ScaScannerFactory {
    private AnalyzeProjectResponse response;
    private ScaScannerOptions scannerOptions;
    private AnalyzeProjectOptions analyzeProjectOptions;

    @Override
    public Scanner create(ScaScannerOptions options) {
      this.scannerOptions = options;
      return analyzeProjectOptions -> {
        this.analyzeProjectOptions = analyzeProjectOptions;
        return response;
      };
    }
  }
}
