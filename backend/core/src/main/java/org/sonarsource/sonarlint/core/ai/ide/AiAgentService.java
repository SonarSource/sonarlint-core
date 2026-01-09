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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetRuleFileContentResponse;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;

public class AiAgentService {

  private final TelemetryService telemetryService;

  @Inject
  public AiAgentService(TelemetryService telemetryService) {
    this.telemetryService = telemetryService;
  }

  public GetRuleFileContentResponse getRuleFileContent(AiAgent agent) {
    var header = switch (agent) {
      case CURSOR, WINDSURF -> """
        ---
        description: SonarQube MCP Server usage guidelines
        globs:
        alwaysApply: true
        ---

        """;
      case KIRO -> """
        ---
        inclusion: always
        ---

        """;
      case GITHUB_COPILOT -> """
        ---
        applyTo: "**/*"
        ---

        """;
    };
    var response = new GetRuleFileContentResponse(header
      + """
        These are some guidelines when using the SonarQube MCP server.

        # Important Tool Guidelines

        ## Basic usage
        - **IMPORTANT**: After you finish generating or modifying any code files at the very end of the task, you MUST call the `analyze_file_list` \
        tool (if it exists) to analyze the files you created or modified.
        - **IMPORTANT**: When starting a new task, you MUST disable automatic analysis with the `toggle_automatic_analysis` tool if it exists.
        - **IMPORTANT**: When you are done generating code at the very end of the task, \
        you MUST re-enable automatic analysis with the `toggle_automatic_analysis` tool if it exists.

        ## Project Keys
        - When a user mentions a project key, use `search_my_sonarqube_projects` first to find the exact project key
        - Don't guess project keys - always look them up

        ## Code Language Detection
        - When analyzing code snippets, try to detect the programming language from the code syntax
        - If unclear, ask the user or make an educated guess based on syntax

        ## Branch and Pull Request Context
        - Many operations support branch-specific analysis
        - If user mentions working on a feature branch, include the branch parameter

        ## Code Issues and Violations
        - After fixing issues, do not attempt to verify them using `search_sonar_issues_in_projects`, as the server will not yet reflect the updates

        # Common Troubleshooting

        ## Authentication Issues
        - SonarQube requires USER tokens (not project tokens)
        - When the error `SonarQube answered with Not authorized` occurs, verify the token type

        ## Project Not Found
        - Use `search_my_sonarqube_projects` to find available projects
        - Verify project key spelling and format

        ## Code Analysis Issues
        - Ensure programming language is correctly specified
        - Remind users that snippet analysis doesn't replace full project scans
        - Provide full file content for better analysis results
        """);

    telemetryService.mcpRuleFileRequested();

    return response;
  }
}
