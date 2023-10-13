/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonDelegate;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.BindingService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.SonarProjectBranchService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.ConfigurationService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.IssueService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.NewCodeService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RulesService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.IssueTrackingService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.SecurityHotspotMatchingService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.usertoken.UserTokenService;

public interface SonarLintBackend {

  /**
   * Called by client once at startup, in order to initialize the backend
   */
  @JsonRequest
  CompletableFuture<Void> initialize(InitializeParams params);

  @JsonDelegate
  ConnectionService getConnectionService();

  @JsonDelegate
  ConfigurationService getConfigurationService();

  @JsonDelegate
  RulesService getRulesService();

  @JsonDelegate
  BindingService getBindingService();

  @JsonDelegate
  HotspotService getHotspotService();

  @JsonDelegate
  TelemetryService getTelemetryService();

  @JsonDelegate
  AnalysisService getAnalysisService();

  @JsonDelegate
  SonarProjectBranchService getSonarProjectBranchService();

  @JsonDelegate
  IssueService getIssueService();

  @JsonDelegate
  IssueTrackingService getIssueTrackingService();

  @JsonDelegate
  SecurityHotspotMatchingService getSecurityHotspotMatchingService();

  @JsonDelegate
  NewCodeService getNewCodeService();

  @JsonDelegate
  UserTokenService getUserTokenService();

  @JsonRequest
  CompletableFuture<Void> shutdown();

}
