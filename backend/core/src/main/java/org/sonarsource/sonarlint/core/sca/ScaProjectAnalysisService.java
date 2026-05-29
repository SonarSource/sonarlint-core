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
import com.sonar.sca.scanner.ScaScannerOptionsBuilder;
import com.sonar.sca.scanner.analyzeproject.AnalyzeProjectOptions;
import com.sonar.sca.scanner.analyzeproject.AnalyzeProjectOptionsBuilder;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectIssue;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectRelease;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;
import org.sonarsource.sonarlint.core.sca.ScaAnalysisContextResolver.AnalysisContext;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;

/**
 * Orchestrates a manual SCA analysis for a configuration scope and returns a
 * {@link AnalyzeDependencyRiskProjectResponse} whose {@code dependencyRisks} field is the merge of server-tracked
 * dependency risks (from {@link DependencyRiskService#listAll}) and locally-detected risks.
 * <p>
 * The merge key is {@code localScannerIssue.key == serverDependencyRisk.id.toString()} (confirmed during PR review).
 * Local-only entries without a server UUID are kept with a {@code null} id (see {@link DependencyRiskDtoMapper}).
 * </p>
 */
public class ScaProjectAnalysisService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String SCA_SCANNER_CACHE_SUBPATH = "sca-scanner/cache";
  private static final String SCA_SCANNER_WORK_SUBPATH = "sca-scanner/work";

  private final ScaAnalysisContextResolver contextResolver;
  private final UserPaths userPaths;
  private final ScaScannerFactory scaScannerFactory;
  private final DependencyRiskService dependencyRiskService;
  private final DependencyRiskDtoMapper dependencyRiskDtoMapper;

  public ScaProjectAnalysisService(ScaAnalysisContextResolver contextResolver, UserPaths userPaths, ScaScannerFactory scaScannerFactory,
    DependencyRiskService dependencyRiskService, DependencyRiskDtoMapper dependencyRiskDtoMapper) {
    this.contextResolver = contextResolver;
    this.userPaths = userPaths;
    this.scaScannerFactory = scaScannerFactory;
    this.dependencyRiskService = dependencyRiskService;
    this.dependencyRiskDtoMapper = dependencyRiskDtoMapper;
  }

  public AnalyzeDependencyRiskProjectResponse analyzeProject(AnalyzeDependencyRiskProjectParams params, SonarLintCancelMonitor cancelMonitor) {
    var configurationScopeId = params.getConfigurationScopeId();
    var context = contextResolver.resolve(configurationScopeId);
    var localResponse = runLocalAnalysis(configurationScopeId, context);
    var serverRisks = loadServerRisks(configurationScopeId, cancelMonitor);
    var mergedRisks = mergeRisks(serverRisks, localResponse.releases());
    var errors = localResponse.errors().stream().map(dependencyRiskDtoMapper::toErrorDto).toList();
    return new AnalyzeDependencyRiskProjectResponse(mergedRisks, localResponse.parsedFiles(), errors);
  }

  private AnalyzeProjectResponse runLocalAnalysis(String configurationScopeId, AnalysisContext context) {
    Path workDir = null;
    try {
      workDir = createWorkDir();
      LOG.info("Running manual dependency risk analysis for configuration scope '{}'", configurationScopeId);
      var scanner = scaScannerFactory.create(buildScannerOptions(context));
      return scanner.analyzeProject(buildAnalyzeProjectOptions(context, workDir));
    } catch (IOException e) {
      throw requestFailed("Dependency risk analysis failed: " + e.getMessage());
    } catch (IllegalArgumentException e) {
      throw invalidArgument(e.getMessage());
    } catch (IllegalStateException e) {
      throw requestFailed(e.getMessage());
    } finally {
      deleteWorkDir(workDir);
    }
  }

  private ScaScannerOptions buildScannerOptions(AnalysisContext context) {
    return ScaScannerOptionsBuilder.builder()
      .setApiBaseUrl(context.endpoint().apiBaseUrl())
      .setDownloadBaseUrl(context.endpoint().downloadBaseUrl())
      .setSonarToken(context.sonarToken())
      .setCacheDir(userPaths.getStorageRoot().resolve(SCA_SCANNER_CACHE_SUBPATH))
      .build();
  }

  private static AnalyzeProjectOptions buildAnalyzeProjectOptions(AnalysisContext context, Path workDir) {
    return AnalyzeProjectOptionsBuilder.builder()
      .setProjectKey(context.binding().sonarProjectKey())
      .setBaseDir(context.baseDir())
      .setWorkDir(workDir)
      .setScannerProperties(Map.copyOf(context.scannerProperties()))
      .setInsideSqc(false)
      .setSqServerVersion(context.sqServerVersion())
      .build();
  }

  private List<DependencyRiskDto> loadServerRisks(String configurationScopeId, SonarLintCancelMonitor cancelMonitor) {
    return dependencyRiskService.listAll(configurationScopeId, false, cancelMonitor);
  }

  private List<DependencyRiskDto> mergeRisks(List<DependencyRiskDto> serverRisks, List<AnalyzeProjectRelease> localReleases) {
    var serverRiskById = new HashMap<UUID, DependencyRiskDto>();
    serverRisks.forEach(risk -> serverRiskById.put(risk.getId(), risk));

    var merged = new ArrayList<DependencyRiskDto>();
    var matchedServerIds = new HashSet<UUID>();

    for (var release : localReleases) {
      for (var issue : release.issues()) {
        var serverRisk = lookupServerRisk(serverRiskById, issue);
        if (serverRisk != null) {
          merged.add(dependencyRiskDtoMapper.enrichServerDto(serverRisk, release, issue));
          matchedServerIds.add(serverRisk.getId());
        } else {
          dependencyRiskDtoMapper.toLocalOnlyDto(release, issue).ifPresent(merged::add);
        }
      }
    }

    // Append server-only risks
    for (var serverRisk : serverRisks) {
      if (!matchedServerIds.contains(serverRisk.getId())) {
        merged.add(serverRisk);
      }
    }
    return merged;
  }

  @Nullable
  private static DependencyRiskDto lookupServerRisk(Map<UUID, DependencyRiskDto> byId, AnalyzeProjectIssue issue) {
    var key = issue.key();
    if (key == null || key.isBlank()) {
      return null;
    }
    try {
      return byId.get(UUID.fromString(key));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private Path createWorkDir() throws IOException {
    var scaWorkDir = userPaths.getWorkDir().resolve(SCA_SCANNER_WORK_SUBPATH);
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

  private static ResponseErrorException invalidArgument(String message) {
    return new ResponseErrorException(new ResponseError(SonarLintRpcErrorCode.INVALID_ARGUMENT, message, null));
  }

  private static ResponseErrorException requestFailed(String message) {
    return new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, message, null));
  }
}





