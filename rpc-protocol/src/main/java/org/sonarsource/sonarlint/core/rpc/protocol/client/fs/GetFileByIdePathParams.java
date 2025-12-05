/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.fs;

import java.nio.file.Path;

/**
 * Parameters for getting a specific file by its IDE path.
 * Used to get sonar-project.properties and .sonarcloud.properties files.
 */
public class GetFileByIdePathParams {
  
  private final String configScopeId;
  private final Path filePath;

  public GetFileByIdePathParams(String configScopeId, Path filePath) {
    this.configScopeId = configScopeId;
    this.filePath = filePath;
  }

  public String getConfigScopeId() {
    return configScopeId;
  }

  public Path getFilePath() {
    return filePath;
  }
}


