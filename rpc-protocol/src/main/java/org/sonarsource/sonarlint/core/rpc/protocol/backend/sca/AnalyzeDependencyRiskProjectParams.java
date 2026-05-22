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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.sca;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public class AnalyzeDependencyRiskProjectParams {
  private final String configurationScopeId;
  private final List<String> excludedPaths;
  private final Map<String, String> scannerProperties;
  private final Boolean scmExclusionEnabled;
  private final Boolean debug;

  public AnalyzeDependencyRiskProjectParams(String configurationScopeId) {
    this(configurationScopeId, List.of(), Map.of(), null, null);
  }

  public AnalyzeDependencyRiskProjectParams(String configurationScopeId, List<String> excludedPaths, Map<String, String> scannerProperties,
    @Nullable Boolean scmExclusionEnabled, @Nullable Boolean debug) {
    this.configurationScopeId = configurationScopeId;
    this.excludedPaths = excludedPaths;
    this.scannerProperties = scannerProperties;
    this.scmExclusionEnabled = scmExclusionEnabled;
    this.debug = debug;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public List<String> getExcludedPaths() {
    return excludedPaths;
  }

  public Map<String, String> getScannerProperties() {
    return scannerProperties;
  }

  @Nullable
  public Boolean getScmExclusionEnabled() {
    return scmExclusionEnabled;
  }

  @Nullable
  public Boolean getDebug() {
    return debug;
  }
}
