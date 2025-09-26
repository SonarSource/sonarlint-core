/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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

import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAssistedIde;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetRuleFileContentResponse;

public class AiAssistedIdeService {
  public GetRuleFileContentResponse getRuleFileContent(AiAssistedIde ide) {
    var header = ide == AiAssistedIde.CURSOR ? """
      ---
      description: SonarQube MCP Server usage guidelines
      globs:
      alwaysApply: true
      ---

      """ : "";
    return new GetRuleFileContentResponse(header
      + """
        These are some guidelines when using the SonarQube MCP server.

        # Important Tool Guidelines

        ## Basic usage
        - When starting a new task, disable automatic analysis with the `toggle_automatic_analysis` tool if it exists.
        - When you are done generating code at the very end of the task, re-enable automatic analysis with the `toggle_automatic_analysis` tool if it exists.
        Then call the `analyze_file_list` tool if it exists.

        ## Project Keys
        - When a user mentions a project key, use `search_my_sonarqube_projects` first to find the exact project key
        - Don't guess project keys - always look them up

        ## Code Language Detection
        - When analyzing code snippets, try to detect the programming language from the code syntax
        - If unclear, ask the user or make an educated guess based on syntax

        ## Branch and Pull Request Context
        - Many operations support branch-specific analysis
        - If user mentions working on a feature branch, include the branch parameter
        - Pull request analysis is available for PR-specific insights

        ## Code Issues and Violations
        - After fixing issues, do not attempt to verify them using `search_sonar_issues_in_projects`, as the server will not yet reflect the updates

        # Common Troubleshooting

        ## Authentication Issues
        - SonarQube requires USER tokens (not project tokens)
        - When the error `SonarQube answered with Not authorized` occurs, verify the token type

        ## Project Not Found
        - Use `search_my_sonarqube_projects` to confirm available projects
        - Check if user has access to the specific project
        - Verify project key spelling and format

        ## Code Analysis Issues
        - Ensure programming language is correctly specified
        - Remind users that snippet analysis doesn't replace full project scans
        - Provide full file content for better analysis results
        - Mention that code snippet analysis tool has limited capabilities compared to full SonarQube scans
        """);
  }
}
