/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.issue;

import java.nio.file.Path;

public class ReopenAllIssuesForFileParams {

  private final String configurationScopeId;
  private final Path ideRelativePath;

  public ReopenAllIssuesForFileParams(String configurationScopeId, Path ideRelativePath) {
    this.configurationScopeId = configurationScopeId;
    this.ideRelativePath = ideRelativePath;
  }

  public Path getIdeRelativePath() {
    return ideRelativePath;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }
}
