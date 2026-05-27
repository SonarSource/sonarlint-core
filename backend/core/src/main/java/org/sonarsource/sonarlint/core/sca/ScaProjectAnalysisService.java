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

import com.sonar.sca.scanner.ScaScannerOptionsBuilder;
import com.sonar.sca.scanner.analyzeproject.AnalyzeProjectOptionsBuilder;
import com.sonar.sca.scanner.analyzeproject.response.AnalysisErrorResource;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectIssue;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectRelease;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectResponse;
import com.sonar.sca.scanner.analyzeproject.response.VersionOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.Strings;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse.AnalyzeDependencyRiskProjectErrorDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse.AnalyzeDependencyRiskProjectIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse.AnalyzeDependencyRiskProjectReleaseDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse.AnalyzeDependencyRiskProjectVersionOptionDto;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.storage.StorageService;

public class ScaProjectAnalysisService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String SCA_DOWNLOAD_BASE_URL_PROPERTY = "sonarlint.sca.downloadBaseUrl";
  private static final String SONARCLOUD_SCA_SCANNER_CDN_URL = "https://scanner.sonarcloud.io/tidelift-cli";

  private final ConfigurationRepository configurationRepository;
  private final ConnectionConfigurationRepository connectionRepository;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final ClientFileSystemService clientFileSystemService;
  private final StorageService storageService;
  private final UserPaths userPaths;
  private final ScaScannerFactory scaScannerFactory;

  public ScaProjectAnalysisService(ConfigurationRepository configurationRepository, ConnectionConfigurationRepository connectionRepository,
    SonarQubeClientManager sonarQubeClientManager, ClientFileSystemService clientFileSystemService, StorageService storageService, UserPaths userPaths,
    ScaScannerFactory scaScannerFactory) {
    this.configurationRepository = configurationRepository;
    this.connectionRepository = connectionRepository;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.clientFileSystemService = clientFileSystemService;
    this.storageService = storageService;
    this.userPaths = userPaths;
    this.scaScannerFactory = scaScannerFactory;
  }

  public AnalyzeDependencyRiskProjectResponse analyzeProject(AnalyzeDependencyRiskProjectParams params) {
    return analyzeProject(params, new SonarLintCancelMonitor());
  }

  public AnalyzeDependencyRiskProjectResponse analyzeProject(AnalyzeDependencyRiskProjectParams params, SonarLintCancelMonitor cancelMonitor) {
    var configurationScopeId = params.getConfigurationScopeId();
    var binding = configurationRepository.getEffectiveBindingOrThrow(configurationScopeId);
    var connection = connectionRepository.getConnectionById(binding.connectionId());
    if (connection == null) {
      throw new ResponseErrorException(new ResponseError(SonarLintRpcErrorCode.CONNECTION_NOT_FOUND,
        "The provided configuration scope is bound to an unknown connection: " + binding.connectionId(), binding.connectionId()));
    }
    var baseDir = clientFileSystemService.getBaseDir(configurationScopeId);
    if (baseDir == null) {
      throw invalidArgument("No base directory is available for configuration scope '" + configurationScopeId + "'");
    }
    var endpointParams = connection.getEndpointParams();
    var apiBaseUrl = toScaApiBaseUrl(endpointParams);
    if (apiBaseUrl == null) {
      throw invalidArgument("Missing SCA API base URL");
    }
    var downloadBaseUrl = firstNonBlank(System.getProperty(SCA_DOWNLOAD_BASE_URL_PROPERTY), toScaDownloadBaseUrl(endpointParams));
    if (downloadBaseUrl == null) {
      throw invalidArgument("Missing SCA scanner download base URL. Provide it in the request or with system property '" + SCA_DOWNLOAD_BASE_URL_PROPERTY + "'");
    }
    var sonarQubeClient = sonarQubeClientManager.getValidClientOrThrow(binding.connectionId());
    var credentials = sonarQubeClient.getCredentials();
    if (!credentials.isLeft()) {
      throw invalidArgument("SCA project analysis requires token credentials");
    }
    var sonarToken = credentials.getLeft().getToken();
    var sqServerVersion = endpointParams.isSonarCloud() ? null : storageService.connection(binding.connectionId()).serverInfo().read()
      .map(info -> info.version().getName())
      .orElse(null);
    Path workDir = null;
    var active = new AtomicBoolean(true);
    try {
      workDir = createWorkDir();
      LOG.info("Running manual SCA project analysis for configuration scope '{}'", configurationScopeId);
      cancelMonitor.checkCanceled();
      var analysisThread = Thread.currentThread();
      cancelMonitor.onCancel(() -> {
        if (active.get()) {
          analysisThread.interrupt();
        }
      });
      var scanner = scaScannerFactory.create(ScaScannerOptionsBuilder.builder()
        .setApiBaseUrl(apiBaseUrl)
        .setDownloadBaseUrl(downloadBaseUrl)
        .setSonarToken(sonarToken)
        .setCacheDir(userPaths.getWorkDir().resolve("sca-scanner/cache"))
        .build());
      var response = scanner.analyzeProject(AnalyzeProjectOptionsBuilder.builder()
        .setProjectKey(binding.sonarProjectKey())
        .setBaseDir(baseDir)
        .setWorkDir(workDir)
        .setExcludedPaths(List.copyOf(params.getExcludedPaths()))
        .setScmExclusionEnabled(params.getScmExclusionEnabled() == null || params.getScmExclusionEnabled())
        .setScannerProperties(Map.copyOf(params.getScannerProperties()))
        .setInsideSqc(false)
        .setSqServerVersion(sqServerVersion)
        .setDebug(Boolean.TRUE.equals(params.getDebug()))
        .build());
      if (cancelMonitor.isCanceled()) {
        Thread.interrupted();
        throw new CancellationException();
      }
      return toDto(response);
    } catch (IOException e) {
      if (cancelMonitor.isCanceled()) {
        Thread.interrupted();
        throw new CancellationException();
      }
      throw invalidArgument("SCA project analysis failed: " + e.getMessage());
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw invalidArgument(e.getMessage());
    } finally {
      active.set(false);
      if (cancelMonitor.isCanceled()) {
        Thread.interrupted();
      }
      deleteWorkDir(workDir);
    }
  }

  private static AnalyzeDependencyRiskProjectResponse toDto(AnalyzeProjectResponse response) {
    return new AnalyzeDependencyRiskProjectResponse(
      response.releases().stream().map(ScaProjectAnalysisService::toDto).toList(),
      response.parsedFiles(),
      response.errors().stream().map(ScaProjectAnalysisService::toDto).toList());
  }

  private static AnalyzeDependencyRiskProjectReleaseDto toDto(AnalyzeProjectRelease release) {
    return new AnalyzeDependencyRiskProjectReleaseDto(
      new AnalyzeDependencyRiskProjectReleaseDto.PackageDto(release.packageUrl(), release.packageManager(), release.packageName(), release.version(), release.licenseExpression()),
      new AnalyzeDependencyRiskProjectReleaseDto.StatusDto(release.known(), release.knownPackage(), release.newlyIntroduced()),
      new AnalyzeDependencyRiskProjectReleaseDto.DependencyDto(release.dependencyFilePaths(), release.dependencyChains()),
      release.key(),
      release.issues().stream().map(ScaProjectAnalysisService::toDto).toList());
  }

  private static AnalyzeDependencyRiskProjectIssueDto toDto(AnalyzeProjectIssue issue) {
    return new AnalyzeDependencyRiskProjectIssueDto(
      new AnalyzeDependencyRiskProjectIssueDto.ClassificationDto(
        issue.severity(), issue.showIncreasedSeverityWarning(), issue.type().name(), issue.quality().name(), issue.status()),
      new AnalyzeDependencyRiskProjectIssueDto.VulnerabilityDto(issue.vulnerabilityId(), issue.cweIds(), issue.cvssScore(), issue.spdxLicenseId(),
        issue.versionOptions() == null ? null : issue.versionOptions().stream().map(ScaProjectAnalysisService::toDto).toList()),
      issue.key());
  }

  private static AnalyzeDependencyRiskProjectVersionOptionDto toDto(VersionOption versionOption) {
    return new AnalyzeDependencyRiskProjectVersionOptionDto(
      versionOption.version(),
      versionOption.vulnerabilityIds(),
      versionOption.prerelease(),
      versionOption.fixLevel(),
      versionOption.descriptionCode());
  }

  private static AnalyzeDependencyRiskProjectErrorDto toDto(AnalysisErrorResource error) {
    return new AnalyzeDependencyRiskProjectErrorDto(error.id(), error.code().name(), error.path(), error.message());
  }

  private Path createWorkDir() throws IOException {
    var scaWorkDir = userPaths.getWorkDir().resolve("sca-scanner/work");
    Files.createDirectories(scaWorkDir);
    LOG.debug("Using SCA scanner work directory: {}", scaWorkDir);
    return Files.createTempDirectory(scaWorkDir, "analyze-project-");
  }

  private static void deleteWorkDir(@Nullable Path workDir) {
    if (workDir != null) {
      try {
        FileUtils.deleteRecursively(workDir);
      } catch (RuntimeException e) {
        LOG.warn("Unable to delete SCA scanner work directory: {}", workDir, e);
      }
    }
  }

  @CheckForNull
  private static String toScaApiBaseUrl(EndpointParams endpointParams) {
    if (endpointParams.isSonarCloud()) {
      var apiBaseUrl = endpointParams.getApiBaseUrl() == null ? endpointParams.getBaseUrl() : endpointParams.getApiBaseUrl();
      if (apiBaseUrl == null) {
        return null;
      }
      return ServerApiHelper.concat(apiBaseUrl, "/sca");
    }
    return ServerApiHelper.concat(endpointParams.getBaseUrl(), "/api/v2/sca");
  }

  private static String toScaDownloadBaseUrl(EndpointParams endpointParams) {
    if (endpointParams.isSonarCloud()) {
      return SONARCLOUD_SCA_SCANNER_CDN_URL;
    }
    return ServerApiHelper.concat(endpointParams.getBaseUrl(), "/api/v2/sca/clis");
  }

  @CheckForNull
  private static String firstNonBlank(@Nullable String first, @Nullable String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    return second != null && !second.isBlank() ? second : null;
  }

  private static ResponseErrorException invalidArgument(String message) {
    return new ResponseErrorException(new ResponseError(SonarLintRpcErrorCode.INVALID_ARGUMENT, Strings.CS.removeEnd(message, "."), null));
  }
}
