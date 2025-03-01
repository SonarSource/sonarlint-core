/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaiseHotspotsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaiseIssuesParams;

@JsonSegment("analysis")
public interface AnalysisRpcService {
  /**
   * This is the list of file patterns declared as part of a language by one of the enabled analyzer.
   * Beware that some analyzers may analyze more files that the one matching one of those patterns.
   * @param params contains configuration scope id as string
   * @return list of the glob patterns
   */
  @JsonRequest
  CompletableFuture<GetSupportedFilePatternsResponse> getSupportedFilePatterns(GetSupportedFilePatternsParams params);

  @JsonRequest
  CompletableFuture<GetGlobalConfigurationResponse> getGlobalStandaloneConfiguration();

  @JsonRequest
  CompletableFuture<GetGlobalConfigurationResponse> getGlobalConnectedConfiguration(GetGlobalConnectedConfigurationParams params);

  /**
   * @since 10.3 this method returns not only server analyser properties, but also user properties provided by client
   */
  @JsonRequest
  CompletableFuture<GetAnalysisConfigResponse> getAnalysisConfig(GetAnalysisConfigParams params);

  /**
   * @return Extra attributes that used to be returned on the Issue from Standalone/Connected engines.
   */
  @JsonRequest
  CompletableFuture<GetRuleDetailsResponse> getRuleDetails(GetRuleDetailsParams params);

  /**
   * Inform the backend that the client has changed the location of the nodejs executable to be used by analyzer
   * @return The Node.js path and version that were forced on the user's machine.
   */
  @JsonRequest
  CompletableFuture<GetForcedNodeJsResponse> didChangeClientNodeJsPath(DidChangeClientNodeJsPathParams params);

  /**
   * @return The Node.js path and version that were automatically detected on the user's machine.
   */
  @JsonRequest
  CompletableFuture<GetAutoDetectedNodeJsResponse> getAutoDetectedNodeJs();

  /**
   * Analyze and track issues in the provided files.
   * Issues will be reported to the client via
   * {@link SonarLintRpcClient#raiseIssues(RaiseIssuesParams)} and {@link SonarLintRpcClient#raiseHotspots(RaiseHotspotsParams)}
   */
  @JsonRequest
  CompletableFuture<AnalyzeFilesResponse> analyzeFilesAndTrack(AnalyzeFilesAndTrackParams params);

  /**
   *  Inform the backend that user settings analysis properties has changed.
   *  The backend will take the provided set of properties as new user configuration, and previous user values will be cleared.
   * @param params configuration scope ID, new properties for this scope
   */
  @JsonNotification
  void didSetUserAnalysisProperties(DidChangeAnalysisPropertiesParams params);

  /**
   * Inform the backend that path to compile commands has changed.
   * The backend will trigger the analysis for all open files after updating the setting value.
   * @param params configuration scope ID, path to compile commands
   */
  @JsonNotification
  void didChangePathToCompileCommands(DidChangePathToCompileCommandsParams params);

  /**
   * Allows to enable or disable automatic analysis.
   * Automatic analysis happens on the following triggers:
   * <ul>
   *   <li>on file open</li>
   *   <li>on open file content change</li>
   *   <li>on some server events, e.g. when some rules were enabled</li>
   * </ul>
   * When this setting becomes enabled, an automatic analysis of open files will be triggered.
   */
  @JsonNotification
  void didChangeAutomaticAnalysisSetting(DidChangeAutomaticAnalysisSettingParams params);

  /**
   * Analyze all files in the project. User file exclusions and .gitignore will be respected.
   * @param params configuration scope ID, flag to report only hotspots
   * @return analysis ID or null if not ready for analysis
   * Issues will be reported to the client via
   * {@link SonarLintRpcClient#raiseIssues(RaiseIssuesParams)} and {@link SonarLintRpcClient#raiseHotspots(RaiseHotspotsParams)}
   */
  @JsonRequest
  CompletableFuture<ForceAnalyzeResponse> analyzeFullProject(AnalyzeFullProjectParams params);

  /**
   * Analyze all files in the provided list. User file exclusions and .gitignore will be respected.
   * @param params configuration scope ID, list of files to analyse
   * @return analysis ID or null if not ready for analysis
   * Issues will be reported to the client via
   * {@link SonarLintRpcClient#raiseIssues(RaiseIssuesParams)} and {@link SonarLintRpcClient#raiseHotspots(RaiseHotspotsParams)}
   */
  @JsonRequest
  CompletableFuture<ForceAnalyzeResponse> analyzeFileList(AnalyzeFileListParams params);

  /**
   * Analyze all files that were reported by the client as opened. User file exclusions and .gitignore will be respected.
   * @param params configuration scope ID
   * @return analysis ID or null if not ready for analysis
   * Issues will be reported to the client via
   * {@link SonarLintRpcClient#raiseIssues(RaiseIssuesParams)} and {@link SonarLintRpcClient#raiseHotspots(RaiseHotspotsParams)}
   */
  @JsonRequest
  CompletableFuture<ForceAnalyzeResponse> analyzeOpenFiles(AnalyzeOpenFilesParams params);

  /**
   * Analyze all files that were created/modified and tracked by git since the last commit. User file exclusions and .gitignore will be respected.
   * @param params configuration scope ID, list of files to analyse
   * @return analysis ID or null if not ready for analysis
   * Issues will be reported to the client via
   * {@link SonarLintRpcClient#raiseIssues(RaiseIssuesParams)} and {@link SonarLintRpcClient#raiseHotspots(RaiseHotspotsParams)}
   */
  @JsonRequest
  CompletableFuture<ForceAnalyzeResponse> analyzeVCSChangedFiles(AnalyzeVCSChangedFilesParams params);


  /**
   * For a given configuration scope, returns whether an enterprise csharp analyzer should be used.
   * 1. configScope is not bound to anything -> false
   * 2. configScope is bound to a SonarQube server that does not have enterprise CSharp analyzer -> false
   * 3. configScope is bound to a SonarQube server that has enterprise CSharp analyzer -> true
   * 4. configScope is bound to a SonarQube server that does not have repackaged analyzer (<10.8) -> true
   * 5. configScope is bound to SonarCloud -> true
   */
  @JsonRequest
  CompletableFuture<ShouldUseEnterpriseCSharpAnalyzerResponse> shouldUseEnterpriseCSharpAnalyzer(ShouldUseEnterpriseCSharpAnalyzerParams params);
}
