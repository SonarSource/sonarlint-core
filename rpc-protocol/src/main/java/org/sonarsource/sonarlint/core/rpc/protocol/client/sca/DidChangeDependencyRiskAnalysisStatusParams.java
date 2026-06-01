/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.sca;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectTrigger;

public class DidChangeDependencyRiskAnalysisStatusParams {
  private final String configurationScopeId;
  private final DependencyRiskAnalysisStatus status;
  private final AnalyzeDependencyRiskProjectTrigger trigger;
  private final boolean rerunRequested;
  @Nullable
  private final String message;

  public DidChangeDependencyRiskAnalysisStatusParams(String configurationScopeId, DependencyRiskAnalysisStatus status, AnalyzeDependencyRiskProjectTrigger trigger,
    boolean rerunRequested, @Nullable String message) {
    this.configurationScopeId = configurationScopeId;
    this.status = status;
    this.trigger = trigger;
    this.rerunRequested = rerunRequested;
    this.message = message;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public DependencyRiskAnalysisStatus getStatus() {
    return status;
  }

  public AnalyzeDependencyRiskProjectTrigger getTrigger() {
    return trigger;
  }

  public boolean isRerunRequested() {
    return rerunRequested;
  }

  @Nullable
  public String getMessage() {
    return message;
  }

  public enum DependencyRiskAnalysisStatus {
    STARTED,
    COMPLETED,
    FAILED,
    CANCELLED
  }
}
