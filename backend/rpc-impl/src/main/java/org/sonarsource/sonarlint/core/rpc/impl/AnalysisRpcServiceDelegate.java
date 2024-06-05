/*
 * SonarLint Core - RPC Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.analysis.AnalysisExtraPropertiesService;
import org.sonarsource.sonarlint.core.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangeClientNodeJsPathParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangedAnalysisExtraPropertiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetAnalysisConfigParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetAnalysisConfigResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetAutoDetectedNodeJsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetGlobalConfigurationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetGlobalConnectedConfigurationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetRuleDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetSupportedFilePatternsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetSupportedFilePatternsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.NodeJsDetailsDto;
import org.sonarsource.sonarlint.core.rules.RuleNotFoundException;
import org.sonarsource.sonarlint.core.rules.RulesService;

import static org.sonarsource.sonarlint.core.DtoMapper.toRuleDetailsResponse;

class AnalysisRpcServiceDelegate extends AbstractRpcServiceDelegate implements AnalysisRpcService {

  public AnalysisRpcServiceDelegate(SonarLintRpcServerImpl server) {
    super(server);
  }

  @Override
  public CompletableFuture<GetSupportedFilePatternsResponse> getSupportedFilePatterns(GetSupportedFilePatternsParams params) {
    return requestAsync(
      cancelChecker -> new GetSupportedFilePatternsResponse(getBean(AnalysisService.class).getSupportedFilePatterns(params.getConfigScopeId())),
      params.getConfigScopeId());
  }

  @Override
  public CompletableFuture<GetGlobalConfigurationResponse> getGlobalStandaloneConfiguration() {
    return requestAsync(
      cancelChecker -> getBean(AnalysisService.class).getGlobalStandaloneConfiguration());
  }

  @Override
  public CompletableFuture<GetGlobalConfigurationResponse> getGlobalConnectedConfiguration(GetGlobalConnectedConfigurationParams params) {
    return requestAsync(
      cancelChecker -> getBean(AnalysisService.class).getGlobalConnectedConfiguration(params.getConnectionId()));
  }

  @Override
  public CompletableFuture<GetAnalysisConfigResponse> getAnalysisConfig(GetAnalysisConfigParams params) {
    return requestAsync(
      cancelChecker -> getBean(AnalysisService.class).getAnalysisConfig(params.getConfigScopeId()), params.getConfigScopeId());
  }

  @Override
  public CompletableFuture<GetRuleDetailsResponse> getRuleDetails(GetRuleDetailsParams params) {
    return requestAsync(
      cancelChecker -> {
        try {
          return toRuleDetailsResponse(getBean(RulesService.class).getRuleDetailsForAnalysis(params.getConfigScopeId(), params.getRuleKey()));
        } catch (RuleNotFoundException e) {
          var error = new ResponseError(SonarLintRpcErrorCode.RULE_NOT_FOUND, e.getMessage(), e.getRuleKey());
          throw new ResponseErrorException(error);
        }
      }, params.getConfigScopeId());
  }

  @Override
  public void didChangeClientNodeJsPath(DidChangeClientNodeJsPathParams params) {
    notify(() -> getBean(NodeJsService.class).didChangeClientNodeJsPath(params.getClientNodeJsPath()));
  }

  @Override
  public CompletableFuture<GetAutoDetectedNodeJsResponse> getAutoDetectedNodeJs() {
    return requestAsync(cancelChecker -> {
      var autoDetectedNodeJs = getBean(AnalysisService.class).getAutoDetectedNodeJs();
      var dto = autoDetectedNodeJs == null ? null : new NodeJsDetailsDto(autoDetectedNodeJs.getPath(), autoDetectedNodeJs.getVersion().toString());
      return new GetAutoDetectedNodeJsResponse(dto);
    });
  }

  @Override
  public CompletableFuture<AnalyzeFilesResponse> analyzeFiles(AnalyzeFilesParams params) {
    var configurationScopeId = params.getConfigurationScopeId();
    return requestAsync(cancelChecker -> {
      var analysisResults = getBean(AnalysisService.class)
        .analyze(cancelChecker, params.getConfigurationScopeId(), params.getAnalysisId(), params.getFilesToAnalyze(),
          params.getExtraProperties(), params.getStartTime(), false, false).join();
      return generateAnalyzeFilesResponse(analysisResults);
    }, configurationScopeId);
  }

  @Override
  public CompletableFuture<AnalyzeFilesResponse> analyzeFilesAndTrack(AnalyzeFilesAndTrackParams params) {
    var configurationScopeId = params.getConfigurationScopeId();
    return requestAsync(cancelChecker -> {
      var analysisResults = getBean(AnalysisService.class)
        .analyze(cancelChecker, params.getConfigurationScopeId(), params.getAnalysisId(), params.getFilesToAnalyze(), params.getExtraProperties(), params.getStartTime(),
          true, params.isShouldFetchServerIssues()).join();
      return generateAnalyzeFilesResponse(analysisResults);
    }, configurationScopeId);
  }

  @Override
  public void didSetAnalysisExtraProperties(DidChangedAnalysisExtraPropertiesParams params) {
    notify(() -> getBean(AnalysisExtraPropertiesService.class).setExtraProperties(params.getConfigurationScopeId(), params.getExtraProperties()));
  }

  @Override
  public void didUpdateAnalysisExtraProperties(DidChangedAnalysisExtraPropertiesParams params) {
    notify(() -> getBean(AnalysisExtraPropertiesService.class).setOrUpdateExtraProperties(params.getConfigurationScopeId(), params.getExtraProperties()));
  }

  private static AnalyzeFilesResponse generateAnalyzeFilesResponse(AnalysisResults analysisResults) {
    Set<URI> failedAnalysisFiles = analysisResults
      .failedAnalysisFiles().stream()
      .map(ClientInputFile::getClientObject)
      .map(clientObj -> ((ClientFile) clientObj).getUri())
      .collect(Collectors.toSet());
    return new AnalyzeFilesResponse(failedAnalysisFiles, analysisResults.getRawIssues());
  }
}
