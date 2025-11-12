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
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetHookScriptContentResponse;

public class AiHookService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

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

    var executableType = executableLocator.detectBestExecutable()
      .orElseThrow(() -> new IllegalStateException("No suitable executable found for hook script generation. " +
        "Please ensure Node.js, Python, or Bash is available on your system."));

    var scriptContent = switch (executableType) {
      case NODEJS -> generateNodeJsScript(port, agent);
      case PYTHON -> generatePythonScript(port, agent);
      case BASH -> generateBashScript(port, agent);
    };

    var configContent = generateHookConfiguration(agent);
    var configFileName = getConfigFileName(agent);

    return new GetHookScriptContentResponse(scriptContent, executableType.getFileName(), configContent, configFileName);
  }

  private static String generateHookConfiguration(AiAgent agent) {
    return switch (agent) {
      case WINDSURF -> generateWindsurfHooksConfig();
      case CURSOR, GITHUB_COPILOT -> throw new UnsupportedOperationException(agent + " hook configuration not yet implemented");
    };
  }

  private static String generateWindsurfHooksConfig() {
    var scriptPath = "{{SCRIPT_PATH}}";
    return """
      {
        "hooks": {
          "post_write_code": [
            {
              "command": "%s",
              "show_output": true
            }
          ]
        }
      }
      """.formatted(scriptPath);
  }

  private static String getConfigFileName(AiAgent agent) {
    return switch (agent) {
      case WINDSURF -> "hooks.json";
      case CURSOR, GITHUB_COPILOT -> throw new UnsupportedOperationException(agent + " hook configuration not yet implemented");
    };
  }

  private static String generateBashScript(int port, AiAgent agent) {
    return loadTemplateAndReplacePlaceholders("post_write_code.sh", port, agent);
  }

  private static String generatePythonScript(int port, AiAgent agent) {
    return loadTemplateAndReplacePlaceholders("post_write_code.py", port, agent);
  }

  private static String generateNodeJsScript(int port, AiAgent agent) {
    return loadTemplateAndReplacePlaceholders("post_write_code.js", port, agent);
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
        .replace("{{AGENT}}", agent.toString());
    } catch (IOException e) {
      LOG.error("Failed to load hook script template: {}", templateFileName, e);
      throw new IllegalStateException("Failed to load hook script template: " + templateFileName, e);
    }
  }
}

