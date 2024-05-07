/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;

public class RaiseHotspotsParams {
  private final String configurationScopeId;
  private final Map<URI, List<RaisedIssueDto>> issuesByFileUri;
  // true if the publication is made for streaming purposes, false if it's the final publication for a given analysis
  private final boolean isIntermediatePublication;
  @Nullable
  // the ID that was provided when the analysis was triggered, or null if this publication is not a consequence of an analysis
  private final UUID analysisId;

  public RaiseHotspotsParams(String configurationScopeId, Map<URI, List<RaisedIssueDto>> issuesByFileUri, boolean isIntermediatePublication, @Nullable UUID analysisId) {
    this.configurationScopeId = configurationScopeId;
    this.issuesByFileUri = issuesByFileUri;
    this.isIntermediatePublication = isIntermediatePublication;
    this.analysisId = analysisId;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public Map<URI, List<RaisedIssueDto>> getIssuesByFileUri() {
    return issuesByFileUri;
  }

  public boolean isIntermediatePublication() {
    return isIntermediatePublication;
  }

  @CheckForNull
  public UUID getAnalysisId() {
    return analysisId;
  }
}
