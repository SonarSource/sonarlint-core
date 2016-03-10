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

import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalSyncStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ModuleSyncStatus;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.UnsupportedServerException;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;

/**
 * Entry point for SonarLint.
 */
public interface SonarLintEngine {

  enum State {
    UNKNOW,
    SYNCING,
    NOT_SYNCED,
    SYNCED
  }

  State getState();

  void stop();

  void addStateListener(StateListener listener);

  void removeStateListener(StateListener listener);

  // COMMON TO UNCONNECTED AND CONNECTED MODES

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
   * @throws UnsupportedOperationException for standalone mode
   */
  @CheckForNull
  GlobalSyncStatus getSyncStatus();

  /**
   * Get information about module sync state
   * @return null if module was never synced
   * @since 2.0
   * @throws UnsupportedOperationException for standalone mode
   */
  @CheckForNull
  ModuleSyncStatus getModuleSyncStatus(String moduleKey);

  /**
   * Return all modules by key
   * @since 2.0
   * @throws UnsupportedOperationException for standalone mode
   */
  Map<String, RemoteModule> allModulesByKey();

  // REQUIRES SERVER TO BE REACHABLE

  /**
   * Check if it is possible to reach server with provided configuration
   * @since 2.0
   */
  ValidationResult validateCredentials(ServerConfiguration serverConfig);

  // REQUIRES SERVER TO BE REACHABLE AND SONARLINT CLIENT SHOULD BE STOPPED

  /**
   * Sync current server.
   * @since 2.0
   * @throws UnsupportedOperationException for standalone mode
   * @throws UnsupportedServerException if server version is to low
   */
  GlobalSyncStatus sync(ServerConfiguration serverConfig);

  /**
   * Sync given module.
   * @since 2.0
   * @throws UnsupportedOperationException for standalone mode
   */
  void syncModule(ServerConfiguration serverConfig, String moduleKey);

}
