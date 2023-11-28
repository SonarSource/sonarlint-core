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
package org.sonarsource.sonarlint.core.rpc.protocol;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonDelegate;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.BindingRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.SonarProjectBranchRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.ConfigurationRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.FileRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.fs.FileSystemRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.IssueRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.NewCodeRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RulesRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityTrackingRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.IssueTrackingRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.SecurityHotspotMatchingRpcService;

public interface SonarLintRpcServer {

  /**
   * Called by client once at startup, in order to initialize the backend
   */
  @JsonRequest
  CompletableFuture<Void> initialize(InitializeParams params);

  @JsonDelegate
  ConnectionRpcService getConnectionService();

  @JsonDelegate
  ConfigurationRpcService getConfigurationService();

  @JsonDelegate
  FileRpcService getFileService();

  @JsonDelegate
  RulesRpcService getRulesService();

  @JsonDelegate
  BindingRpcService getBindingService();

  @JsonDelegate
  HotspotRpcService getHotspotService();

  @JsonDelegate
  TelemetryRpcService getTelemetryService();

  @JsonDelegate
  AnalysisRpcService getAnalysisService();

  @JsonDelegate
  SonarProjectBranchRpcService getSonarProjectBranchService();

  @JsonDelegate
  IssueRpcService getIssueService();

  @JsonDelegate
  IssueTrackingRpcService getIssueTrackingService();

  @JsonDelegate
  SecurityHotspotMatchingRpcService getSecurityHotspotMatchingService();

  @JsonDelegate
  NewCodeRpcService getNewCodeService();

  @JsonDelegate
  TaintVulnerabilityTrackingRpcService getTaintVulnerabilityTrackingService();

  @JsonDelegate
  FileSystemRpcService getFileSystemService();

  @JsonRequest
  CompletableFuture<Void> shutdown();

}
