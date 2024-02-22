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

import java.nio.file.Path;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class GetGlobalConfigurationResponse {

  private final List<Path> pluginPaths;
  private final List<Language> enabledLanguages;
  private final NodeJsDetailsDto nodeJsDetails;
  private final boolean isDataflowBugDetectionEnabled;

  public GetGlobalConfigurationResponse(List<Path> pluginPaths, List<Language> enabledLanguages, @Nullable NodeJsDetailsDto nodeJsDetails,
    boolean isDataflowBugDetectionEnabled) {
    this.pluginPaths = pluginPaths;
    this.enabledLanguages = enabledLanguages;
    this.nodeJsDetails = nodeJsDetails;
    this.isDataflowBugDetectionEnabled = isDataflowBugDetectionEnabled;
  }

  public List<Path> getPluginPaths() {
    return pluginPaths;
  }

  public List<Language> getEnabledLanguages() {
    return enabledLanguages;
  }

  @CheckForNull
  public NodeJsDetailsDto getNodeJsDetails() {
    return nodeJsDetails;
  }

  public boolean isDataflowBugDetectionEnabled() {
    return isDataflowBugDetectionEnabled;
  }
}
