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

/**
 * Parameters for getting files matching a glob pattern.
 * Used to get *.json files from .sonarlint directory for binding clue detection.
 */
public class GetFilesByPatternParams {
  
  private final String configScopeId;
  private final String globPattern;

  public GetFilesByPatternParams(String configScopeId, String globPattern) {
    this.configScopeId = configScopeId;
    this.globPattern = globPattern;
  }

  public String getConfigScopeId() {
    return configScopeId;
  }

  public String getGlobPattern() {
    return globPattern;
  }
}


