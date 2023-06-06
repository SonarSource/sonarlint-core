/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.clientapi.backend.authentication.AuthenticationHelperService;
import org.sonarsource.sonarlint.core.clientapi.backend.branch.SonarProjectBranchService;
import org.sonarsource.sonarlint.core.clientapi.backend.config.ConfigurationService;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotService;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueService;
import org.sonarsource.sonarlint.core.rules.RulesServiceImpl;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;

public class SonarLintBackendImpl implements SonarLintBackend {
  private final SonarLintClient client;
  private InitializedSonarLintBackend sonarLintBackend;

  public SonarLintBackendImpl(SonarLintClient client) {
    this.client = client;
  }

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    if (sonarLintBackend != null) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException("Already initialized"));
    }
    sonarLintBackend = new InitializedSonarLintBackend(client, params);

    return CompletableFuture.completedFuture(null);
  }

  private InitializedSonarLintBackend getInitializedBackend() {
    if (sonarLintBackend == null) {
      throw new IllegalStateException("Backend is not initialized");
    }
    return sonarLintBackend;
  }

  @Override
  public ConnectionServiceImpl getConnectionService() {
    return getInitializedBackend().getConnectionService();
  }

  @Override
  public AuthenticationHelperService getAuthenticationHelperService() {
    return getInitializedBackend().getAuthenticationHelperService();
  }

  @Override
  public ConfigurationService getConfigurationService() {
    return getInitializedBackend().getConfigurationService();
  }

  @Override
  public HotspotService getHotspotService() {
    return getInitializedBackend().getHotspotService();
  }

  @Override
  public TelemetryServiceImpl getTelemetryService() {
    return getInitializedBackend().getTelemetryService();
  }

  @Override
  public AnalysisService getAnalysisService() {
    return getInitializedBackend().getAnalysisService();
  }

  @Override
  public RulesServiceImpl getRulesService() {
    return getInitializedBackend().getRulesService();
  }

  @Override
  public BindingSuggestionProviderImpl getBindingService() {
    return getInitializedBackend().getBindingService();
  }

  public SonarProjectBranchService getSonarProjectBranchService() {
    return getInitializedBackend().getSonarProjectBranchService();
  }

  @Override
  public IssueService getIssueService() {
    return getInitializedBackend().getIssueService();
  }

  @Override
  public CompletableFuture<Void> shutdown() {
    return getInitializedBackend().shutdown();
  }

  public int getEmbeddedServerPort() {
    return getInitializedBackend().getEmbeddedServerPort();
  }
}
