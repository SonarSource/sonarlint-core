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
package org.sonarsource.sonarlint.core.client.api.standalone;

import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;

import javax.annotation.Nullable;

/**
 * Entry point for SonarLint in standalone mode.
 */
public interface StandaloneSonarLintEngine {

  void stop();

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
  AnalysisResults analyze(StandaloneAnalysisConfiguration configuration, IssueListener issueListener);

  AnalysisResults analyze(StandaloneAnalysisConfiguration configuration, IssueListener issueListener, @Nullable LogOutput logOutput);

}
