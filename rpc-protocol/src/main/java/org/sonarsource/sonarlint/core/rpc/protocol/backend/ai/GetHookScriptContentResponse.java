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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.ai;

import java.util.List;

public class GetHookScriptContentResponse {
  private final List<HookScript> scripts;
  private final String configContent;
  private final String configFileName;

  public GetHookScriptContentResponse(List<HookScript> scripts, String configContent, String configFileName) {
    this.scripts = scripts;
    this.configContent = configContent;
    this.configFileName = configFileName;
  }

  public List<HookScript> getScripts() {
    return scripts;
  }

  public String getConfigContent() {
    return configContent;
  }

  public String getConfigFileName() {
    return configFileName;
  }

  public static class HookScript {
    private final String content;
    private final String fileName;

    public HookScript(String content, String fileName) {
      this.content = content;
      this.fileName = fileName;
    }

    public String getContent() {
      return content;
    }

    public String getFileName() {
      return fileName;
    }
  }
}

