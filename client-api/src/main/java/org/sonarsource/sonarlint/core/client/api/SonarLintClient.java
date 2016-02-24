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

import java.util.Collection;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.SyncStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;

/**
 * Entry point for SonarLint.
 */
public interface SonarLintClient {

  void start();

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
   * Return list of all active rule keys.
   */
  Collection<String> getActiveRuleKeys();

  AnalysisResults analyze(AnalysisConfiguration configuration, IssueListener issueListener);

  /**
   * Check it is possible to reach server with provided configuration
   * @since 2.0
   */
  ValidationResult validateCredentials(ServerConfiguration serverConfig);

  /**
   * Sync current server
   * @since 2.0
   * @throws UnsupportedOperationException for unconnected mode
   */
  void sync(ServerConfiguration serverConfig);

  /**
   * Get information about current sync state
   * @return null if server was never synced
   * @since 2.0
   * @throws UnsupportedOperationException for unconnected mode and if this server was never synced (see {@link #alreadySynced()})
   */
  @CheckForNull
  SyncStatus getSyncStatus();

}
