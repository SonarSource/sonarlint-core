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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;

/**
 * Entry point for SonarLint.
 */
public interface ConnectedSonarLintEngine {

  enum State {
    UNKNOW,
    UPDATING,
    NEVER_UPDATED,
    UPDATED
  }

  State getState();

  void stop();

  void addStateListener(StateListener listener);

  void removeStateListener(StateListener listener);

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
  AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener);

  /**
   * Get information about current update state
   * @return null if server was never updated
   * @since 2.0
   * @throws UnsupportedOperationException for standalone mode
   */
  @CheckForNull
  GlobalUpdateStatus getUpdateStatus();

  /**
   * Get information about module update state
   * @return null if module was never updated
   * @since 2.0
   * @throws UnsupportedOperationException for standalone mode
   */
  @CheckForNull
  ModuleUpdateStatus getModuleUpdateStatus(String moduleKey);

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

  /**
   * Update current server.
   * @since 2.0
   * @throws UnsupportedOperationException for standalone mode
   * @throws UnsupportedServerException if server version is too low
   */
  GlobalUpdateStatus update(ServerConfiguration serverConfig);

  /**
   * Update given module.
   * @since 2.0
   * @throws UnsupportedOperationException for standalone mode
   */
  void updateModule(ServerConfiguration serverConfig, String moduleKey);

}
