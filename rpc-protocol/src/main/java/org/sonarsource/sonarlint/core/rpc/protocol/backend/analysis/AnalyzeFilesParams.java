/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class AnalyzeFilesParams {
  private final String configurationScopeId;
  private final List<URI> filesToAnalyze;
  private final Map<String, String> extraProperties;
  // this is determined by the client as other operations could occur before reaching the backend
  private final long startTime;

  public AnalyzeFilesParams(String configurationScopeId, List<URI> filesToAnalyze, Map<String, String> extraProperties, long startTime) {
    this.configurationScopeId = configurationScopeId;
    this.filesToAnalyze = filesToAnalyze;
    this.extraProperties = extraProperties;
    this.startTime = startTime;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public List<URI> getFilesToAnalyze() {
    return filesToAnalyze;
  }

  public Map<String, String> getExtraProperties() {
    return extraProperties;
  }

  public long getStartTime() {
    return startTime;
  }
}
