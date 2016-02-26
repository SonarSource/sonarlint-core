/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.client.api;

import java.util.List;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalSyncStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ModuleSyncStatus;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;

/**
 * Entry point for SonarLint.
 */
public interface SonarLintClient {

  // COMMON TO UNCONNECTED AND CONNECTED MODES

  /**
   * Load analyzers and be ready to start analysis using {@link #analyze(AnalysisConfiguration, IssueListener)}
   */
  void start();

  /**
   * Unload everything.
   */
  void stop();

  /**
   * Change verbosity at runtime
   */
  void setVerbose(boolean verbose);

  /**
   * Return rule details.
   * @param ruleKey See {@link Issue#getRuleKey()}
   * @return Rule details
   * @throws IllegalArgumentException if ruleKey is unknown
   * @since 1.2
   */
  RuleDetails getRuleDetails(String ruleKey);

  /**
   * Trigger an analysis
   */
  AnalysisResults analyze(AnalysisConfiguration configuration, IssueListener issueListener);

  // ONLY FOR CONNECTED MODE

  /**
   * Get information about current sync state
   * @return null if server was never synced
   * @since 2.0
   * @throws UnsupportedOperationException for unconnected mode
   */
  @CheckForNull
  GlobalSyncStatus getSyncStatus();

  /**
   * Get information about module sync state
   * @return null if module was never synced
   * @since 2.0
   * @throws UnsupportedOperationException for unconnected mode
   */
  @CheckForNull
  ModuleSyncStatus getModuleSyncStatus(String moduleKey);

  // REQUIRES SERVER TO BE REACHABLE

  /**
   * Check it is possible to reach server with provided configuration
   * @since 2.0
   */
  ValidationResult validateCredentials(ServerConfiguration serverConfig);

  /**
   * Find module by exact key of by partial name. This is not using storage, so it will fail is server is not reachable.
   * @since 2.0
   * @throws UnsupportedOperationException for unconnected mode
   */
  List<RemoteModule> searchModule(ServerConfiguration serverConfig, String exactKeyOrPartialName);

  // REQUIRES SERVER TO BE REACHABLE AND SONARLINT CLIENT SHOULD BE STOPPED

  /**
   * Sync current server. Client should be stopped ({@link #stop()} during the sync process.
   * @since 2.0
   * @throws UnsupportedOperationException for unconnected mode
   */
  void sync(ServerConfiguration serverConfig);

  /**
   * Sync given module. Client should be stopped ({@link #stop()} during the sync process.
   * @since 2.0
   * @throws UnsupportedOperationException for unconnected mode
   */
  void syncModule(ServerConfiguration serverConfig, String moduleKey);

}
