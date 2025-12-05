/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.ai.ide;

import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetHookScriptContentResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetHookScriptContentResponse.HookScript;

public class AiHookService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final String WINDSURF_HOOK_CONFIG = """
      {
        "hooks": {
          "post_write_code": [
            {
              "command": "{{SCRIPT_PATH}}",
              "show_output": true
            }
          ]
        }
      }
      """;

  private static final String CURSOR_HOOK_CONFIG = """
      {
        "version": 1,
        "hooks": {
          "afterFileEdit": [
            {
              "command": "{{TRACK_SCRIPT_PATH}}"
            }
          ],
          "stop": [
            {
              "command": "{{ANALYZE_SCRIPT_PATH}}"
            }
          ]
        }
      }
      """;

  private final EmbeddedServer embeddedServer;
  private final ExecutableLocator executableLocator;

  @Inject
  public AiHookService(EmbeddedServer embeddedServer) {
    this(embeddedServer, new ExecutableLocator());
  }

  // For testing
  AiHookService(EmbeddedServer embeddedServer, ExecutableLocator executableLocator) {
    this.embeddedServer = embeddedServer;
    this.executableLocator = executableLocator;
  }

  public GetHookScriptContentResponse getHookScriptContent(AiAgent agent) {
    var port = embeddedServer.getPort();
    if (port <= 0) {
      throw new IllegalStateException("Embedded server is not started. Cannot generate hook script.");
    }

    var hookScriptType = executableLocator.detectBestExecutable()
      .orElseThrow(() -> new IllegalStateException("No suitable executable found for hook script generation. " +
        "Please ensure Node.js, Python, or Bash is available on your system."));

    var scripts = new ArrayList<HookScript>();
    String configContent;
    
    if (agent == AiAgent.CURSOR) {
      // Cursor needs two scripts: one for tracking, one for analyzing
      var trackScriptName = getTrackScriptName(hookScriptType);
      var analyzeScriptName = getAnalyzeScriptName(hookScriptType);
      
      var trackScriptContent = loadTemplateAndReplacePlaceholders(trackScriptName, port, agent);
      var analyzeScriptContent = loadTemplateAndReplacePlaceholders(analyzeScriptName, port, agent);
      
      scripts.add(new HookScript(trackScriptContent, trackScriptName));
      scripts.add(new HookScript(analyzeScriptContent, analyzeScriptName));
      
      configContent = generateHookConfiguration(agent);
    } else {
      // Windsurf needs single script
      var scriptContent = loadTemplateAndReplacePlaceholders(hookScriptType.getFileName(), port, agent);
      scripts.add(new HookScript(scriptContent, hookScriptType.getFileName()));
      configContent = generateHookConfiguration(agent);
    }
    
    var configFileName = getConfigFileName(agent);

    return new GetHookScriptContentResponse(scripts, configContent, configFileName);
  }

  private static String generateHookConfiguration(AiAgent agent) {
    return switch (agent) {
      case WINDSURF -> WINDSURF_HOOK_CONFIG;
      case CURSOR -> CURSOR_HOOK_CONFIG;
      case GITHUB_COPILOT -> throw new UnsupportedOperationException("GitHub Copilot does not support hooks");
    };
  }

  private static String getTrackScriptName(HookScriptType type) {
    return switch (type) {
      case NODEJS -> "track_file_edit.js";
      case PYTHON -> "track_file_edit.py";
      case BASH -> "track_file_edit.sh";
    };
  }

  private static String getAnalyzeScriptName(HookScriptType type) {
    return switch (type) {
      case NODEJS -> "analyze_and_report.js";
      case PYTHON -> "analyze_and_report.py";
      case BASH -> "analyze_and_report.sh";
    };
  }

  private static String getConfigFileName(AiAgent agent) {
    return switch (agent) {
      case WINDSURF, CURSOR -> "hooks.json";
      case GITHUB_COPILOT -> throw new UnsupportedOperationException("GitHub Copilot does not support hooks");
    };
  }

  private static String loadTemplateAndReplacePlaceholders(String templateFileName, int port, AiAgent agent) {
    var resourcePath = "/ai/hooks/" + templateFileName;
    try (var inputStream = AiHookService.class.getResourceAsStream(resourcePath)) {
      if (inputStream == null) {
        throw new IllegalStateException("Hook script template not found: " + resourcePath);
      }
      var template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      return template
        .replace("{{PORT}}", String.valueOf(port))
        .replace("{{AGENT}}", getIdeName(agent));
    } catch (IOException e) {
      LOG.error("Failed to load hook script template: {}", templateFileName, e);
      throw new IllegalStateException("Failed to load hook script template: " + templateFileName, e);
    }
  }

  private static String getIdeName(AiAgent agent) {
    return switch (agent) {
      case WINDSURF -> "Windsurf";
      case CURSOR -> "Cursor";
      case GITHUB_COPILOT -> throw new UnsupportedOperationException("GitHub Copilot does not support hooks");
    };
  }

}

