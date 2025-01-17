/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.analysis;

import java.util.UUID;


@Deprecated(since = "10.2")
public class DidRaiseIssueParams {
  private final String configurationScopeId;
  // the ID that was provided when the analysis was triggered
  private final UUID analysisId;
  private final RawIssueDto rawIssue;

  public DidRaiseIssueParams(String configurationScopeId, UUID analysisId, RawIssueDto rawIssue) {
    this.configurationScopeId = configurationScopeId;
    this.analysisId = analysisId;
    this.rawIssue = rawIssue;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public UUID getAnalysisId() {
    return analysisId;
  }

  public RawIssueDto getRawIssue() {
    return rawIssue;
  }
}
