/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.standalone;

import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;

/**
 * Entry point for SonarLint in standalone mode.
 */
public interface StandaloneSonarLintEngine extends SonarLintEngine {

  void stop();

  /**
   * Return rule details.
   * @param ruleKey See {@link Issue#getRuleKey()}
   * @return Rule details
   * @throws IllegalArgumentException if ruleKey is unknown
   * @since 1.2
   */
  Optional<StandaloneRuleDetails> getRuleDetails(String ruleKey);

  /**
   * Return rule details of all available rules for SonarLint standalone mode. For now, excluding hotspots and rule templates.
   */
  Collection<StandaloneRuleDetails> getAllRuleDetails();

  /**
   * Trigger an analysis
   */
  AnalysisResults analyze(StandaloneAnalysisConfiguration configuration, IssueListener issueListener, @Nullable LogOutput logOutput, @Nullable ProgressMonitor monitor);

}
