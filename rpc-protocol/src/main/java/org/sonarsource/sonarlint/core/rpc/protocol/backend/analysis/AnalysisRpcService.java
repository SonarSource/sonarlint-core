/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2023 SonarSource SA
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
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

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

  @JsonRequest
  CompletableFuture<GetAnalysisConfigResponse> getAnalysisConfig(GetAnalysisConfigParams params);

  /**
   * @return Extra attributes that used to be returned on the Issue from Standalone/Connected engines.
   */
  @JsonRequest
  CompletableFuture<GetRuleDetailsResponse> getRuleDetails(GetRuleDetailsParams params);
}
