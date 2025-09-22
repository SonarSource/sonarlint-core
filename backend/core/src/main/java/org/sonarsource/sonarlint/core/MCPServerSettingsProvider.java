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
package org.sonarsource.sonarlint.core;

import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.SonarLintException;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;

import static java.lang.String.format;

public class MCPServerSettingsProvider {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String SONARCLOUD_MCP_CONFIG = """
    {
      "command": "docker",
      "args": [
        "run",
        "-i",
        "--rm",
        "-e",
        "SONARQUBE_TOKEN",
        "-e",
        "SONARQUBE_ORG",
        "mcp/sonarqube",
        "stdio"
      ],
      "env": {
        "SONARQUBE_ORG": "%s",
        "SONARQUBE_TOKEN": "%s"
      }
    }
    """;
  private static final String SONARQUBE_MCP_CONFIG = """
    {
      "command": "docker",
      "args": [
        "run",
        "-i",
        "--rm",
        "-e",
        "SONARQUBE_TOKEN",
        "-e",
        "SONARQUBE_URL",
        "mcp/sonarqube",
        "stdio"
      ],
      "env": {
        "SONARQUBE_URL": "%s",
        "SONARQUBE_TOKEN": "%s"
      }
    }
    """;

  private final ConnectionConfigurationRepository connectionRepository;
  private final TelemetryService telemetryService;

  public MCPServerSettingsProvider(ConnectionConfigurationRepository connectionRepository, TelemetryService telemetryService) {
    this.connectionRepository = connectionRepository;
    this.telemetryService = telemetryService;
  }

  public String getMCPServerSettingsJSON(String connectionId, String token) {
    var connection = connectionRepository.getConnectionById(connectionId);
    if (connection != null) {
      telemetryService.mcpServerSettingsRequested();
      if (connection.getKind() == ConnectionKind.SONARCLOUD) {
        var sonarCloudConnection = (SonarCloudConnectionConfiguration) connection;
        var organization = sonarCloudConnection.getOrganization();

        return format(SONARCLOUD_MCP_CONFIG, organization, token);
      } else {
        var url = connection.getUrl();

        return format(SONARQUBE_MCP_CONFIG, url, token);
      }
    } else {
      LOG.warn("Request for generating MCP server settings JSON failed; Connection not found for '{}'", connectionId);
      throw new SonarLintException(format("Connection not found for '%s'; Cannot generate MCP server settings JSON", connectionId));
    }
  }
}
