/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.analysis.AnalysisResult;
import org.sonarsource.sonarlint.core.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.analysis.RawIssue;
import org.sonarsource.sonarlint.core.analysis.api.TriggerType;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFileListParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFullProjectParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeOpenFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeVCSChangedFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangeAnalysisPropertiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangeAutomaticAnalysisSettingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangeClientNodeJsPathParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangePathToCompileCommandsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.ForceAnalyzeResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetAnalysisConfigParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetAnalysisConfigResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetAutoDetectedNodeJsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetForcedNodeJsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetGlobalConfigurationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetGlobalConnectedConfigurationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetRuleDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetSupportedFilePatternsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetSupportedFilePatternsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.NodeJsDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.ShouldUseEnterpriseCSharpAnalyzerParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.ShouldUseEnterpriseCSharpAnalyzerResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.FileEditDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.QuickFixDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueFlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueLocationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.TextEditDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.core.rules.RuleDetailsAdapter;
import org.sonarsource.sonarlint.core.rules.RuleNotFoundException;
import org.sonarsource.sonarlint.core.rules.RulesService;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
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
      cancelChecker -> getBean(AnalysisService.class).getAnalysisConfig(params.getConfigScopeId(), false), params.getConfigScopeId());
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
  public CompletableFuture<GetForcedNodeJsResponse> didChangeClientNodeJsPath(DidChangeClientNodeJsPathParams params) {
    return requestAsync(cancelChecker -> {
      var forcedNodeJs = getBean(NodeJsService.class).didChangeClientNodeJsPath(params.getClientNodeJsPath());
      var dto = forcedNodeJs == null ? null : new NodeJsDetailsDto(forcedNodeJs.getPath(), forcedNodeJs.getVersion().toString());
      return new GetForcedNodeJsResponse(dto);
    });
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
  public CompletableFuture<AnalyzeFilesResponse> analyzeFilesAndTrack(AnalyzeFilesAndTrackParams params) {
    var configurationScopeId = params.getConfigurationScopeId();
    return requestAsync(cancelChecker -> {
      var analysisResults = getBean(AnalysisService.class)
        .scheduleAnalysis(params.getConfigurationScopeId(), params.getAnalysisId(), params.getFilesToAnalyze(), params.getExtraProperties(), params.getStartTime(),
          // consider this method as an automatic analysis. This will take exclusions into account
          params.isShouldFetchServerIssues(), TriggerType.AUTO, cancelChecker)
        .join();
      return generateAnalyzeFilesResponse(analysisResults);
    }, configurationScopeId);
  }

  @Override
  public void didSetUserAnalysisProperties(DidChangeAnalysisPropertiesParams params) {
    notify(() -> getBean(AnalysisService.class).setUserAnalysisProperties(params.getConfigurationScopeId(), params.getProperties()));
  }

  @Override
  public void didChangePathToCompileCommands(DidChangePathToCompileCommandsParams params) {
    notify(() -> getBean(AnalysisService.class).didChangePathToCompileCommands(params.getConfigurationScopeId(), params.getPathToCompileCommands()));
  }

  @Override
  public void didChangeAutomaticAnalysisSetting(DidChangeAutomaticAnalysisSettingParams params) {
    notify(() -> getBean(AnalysisService.class).didChangeAutomaticAnalysisSetting(params.isEnabled()));
  }

  @Override
  public CompletableFuture<ForceAnalyzeResponse> analyzeFullProject(AnalyzeFullProjectParams params) {
    return requestAsync(
      cancelChecker -> new ForceAnalyzeResponse(getBean(AnalysisService.class)
        .analyzeFullProject(params.getConfigScopeId(), params.isHotspotsOnly())));
  }

  @Override
  public CompletableFuture<ForceAnalyzeResponse> analyzeFileList(AnalyzeFileListParams params) {
    return requestAsync(
      cancelChecker -> new ForceAnalyzeResponse(getBean(AnalysisService.class)
        .analyzeFileList(params.getConfigScopeId(), params.getFilesToAnalyze())));
  }

  @Override
  public CompletableFuture<ForceAnalyzeResponse> analyzeOpenFiles(AnalyzeOpenFilesParams params) {
    return requestAsync(
      cancelChecker -> new ForceAnalyzeResponse(getBean(AnalysisService.class).forceAnalyzeOpenFiles(params.getConfigScopeId())));
  }

  @Override
  public CompletableFuture<ForceAnalyzeResponse> analyzeVCSChangedFiles(AnalyzeVCSChangedFilesParams params) {
    return requestAsync(
      cancelChecker -> new ForceAnalyzeResponse(getBean(AnalysisService.class).analyzeVCSChangedFiles(params.getConfigScopeId())));
  }

  @Override
  public CompletableFuture<ShouldUseEnterpriseCSharpAnalyzerResponse> shouldUseEnterpriseCSharpAnalyzer(ShouldUseEnterpriseCSharpAnalyzerParams params) {
    return requestAsync(
      cancelChecker -> new ShouldUseEnterpriseCSharpAnalyzerResponse(getBean(AnalysisService.class)
        .shouldUseEnterpriseCSharpAnalyzer(params.getConfigurationScopeId())));
  }

  private static AnalyzeFilesResponse generateAnalyzeFilesResponse(AnalysisResult analysisResults) {
    return new AnalyzeFilesResponse(analysisResults.failedAnalysisFiles(), analysisResults.rawIssues().stream().map(AnalysisRpcServiceDelegate::toDto).toList());
  }

  static RawIssueDto toDto(RawIssue issue) {
    var range = issue.getTextRange();
    var textRange = range != null ? adapt(range) : null;
    var fileUri = issue.getFileUri();
    var flows = issue.getFlows().stream().map(flow -> {
      var locations = flow.locations().stream().map(location -> {
        var locationTextRange = location.getTextRange();
        var locationTextRangeDto = locationTextRange == null ? null : adapt(locationTextRange);
        var locationInputFile = location.getInputFile();
        var locationFileUri = locationInputFile == null ? null : locationInputFile.uri();
        return new RawIssueLocationDto(locationTextRangeDto, location.getMessage(), locationFileUri);
      }).toList();
      return new RawIssueFlowDto(locations);
    }).toList();
    return new RawIssueDto(
      RuleDetailsAdapter.adapt(issue.getSeverity()),
      RuleDetailsAdapter.adapt(issue.getRuleType()),
      RuleDetailsAdapter.adapt(issue.getCleanCodeAttribute()),
      issue.getImpacts().entrySet().stream().map(entry -> Map.entry(SoftwareQuality.valueOf(entry.getKey().name()), ImpactSeverity.valueOf(entry.getValue().name())))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)),
      issue.getRuleKey(),
      requireNonNull(issue.getMessage()),
      fileUri,
      flows,
      issue.getQuickFixes().stream()
        .map(quickFix -> new QuickFixDto(
          quickFix.inputFileEdits().stream()
            .map(fileEdit -> new FileEditDto(fileEdit.target().uri(),
              fileEdit.textEdits().stream().map(textEdit -> new TextEditDto(adapt(textEdit.range()), textEdit.newText())).toList()))
            .toList(),
          quickFix.message()))
        .toList(),
      textRange,
      issue.getRuleDescriptionContextKey(),
      RuleDetailsAdapter.adapt(issue.getVulnerabilityProbability()));
  }

  private static TextRangeDto adapt(TextRange textRange) {
    return new TextRangeDto(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset());
  }
}
