/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareCliCommandParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareCliCommandResponse;

final class CliCommandFactory {

  private static final String UNIX_INSTALL_SCRIPT_URL =
    "https://raw.githubusercontent.com/SonarSource/sonarqube-cli/refs/heads/master/user-scripts/install.sh";
  private static final String WINDOWS_INSTALL_SCRIPT_URL =
    "https://raw.githubusercontent.com/SonarSource/sonarqube-cli/refs/heads/master/user-scripts/install.ps1";

  private CliCommandFactory() {
  }

  static PrepareCliCommandResponse prepareInstallCommand(boolean windows) {
    if (windows) {
      return new PrepareCliCommandResponse("powershell.exe",
        List.of("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", "irm " + WINDOWS_INSTALL_SCRIPT_URL + " | iex"), true);
    }
    return new PrepareCliCommandResponse("/bin/bash", List.of("-o", "pipefail", "-c",
      "curl --fail --silent --show-error --location " + UNIX_INSTALL_SCRIPT_URL + " | bash"), true);
  }

  static PrepareCliCommandResponse prepareAuthenticationCommand(Path executable, PrepareCliCommandParams params) {
    var arguments = new ArrayList<>(List.of("auth", "login"));
    addOption(arguments, "--server", params.getServerUrl());
    addOption(arguments, "--org", params.getOrganization());
    return new PrepareCliCommandResponse(executable.toString(), arguments, true);
  }

  static PrepareCliCommandResponse prepareIntegrationCommand(Path executable, @Nullable AiAgent agent) {
    if (agent == null) {
      throw new IllegalArgumentException("An AI agent is required");
    }
    var cliTarget = cliTarget(agent)
      .orElseThrow(() -> new IllegalArgumentException(agent + " is not supported by the SonarQube CLI"));
    return new PrepareCliCommandResponse(executable.toString(), List.of("integrate", cliTarget, "--global"), true);
  }

  static Optional<String> cliTarget(AiAgent agent) {
    return switch (agent) {
      case CURSOR -> Optional.of("cursor");
      case CLAUDE_CODE -> Optional.of("claude");
      case CODEX -> Optional.of("codex");
      case WINDSURF, KIRO, GITHUB_COPILOT -> Optional.empty();
    };
  }

  private static void addOption(List<String> arguments, String option, @Nullable String value) {
    if (value != null && !value.isBlank()) {
      arguments.add(option);
      arguments.add(value.trim());
    }
  }
}
