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

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Paths;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonarsource.sonarlint.core.nodejs.OsSearchPath;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.SonarQubeCliState;

/**
 * Shared CLI discovery and integration state for IDE-hosted AI agents.
 * Clients remain responsible for detecting agents in their own IDE.
 * This service never transports credentials to the CLI.
 */
public class AiIntegrationService {

  private final SonarQubeCliLocator locator;

  public AiIntegrationService() {
    this(new SonarQubeCliLocator(System2.INSTANCE, CommandExecutor.create(),
      Paths.get(System.getProperty("user.home")), System.getenv(), OsSearchPath.MAC_OS_PATH_HELPER));
  }

  @VisibleForTesting
  AiIntegrationService(SonarQubeCliLocator locator) {
    this.locator = locator;
  }

  public GetAiIntegrationStateResponse getIntegrationState(GetAiIntegrationStateParams params) {
    var cliState = toCliState(locator.find());
    var agentCapabilities = params.getDetectedAgents().stream()
      .distinct()
      .map(AiAgentCapabilities::of)
      .toList();
    return new GetAiIntegrationStateResponse(cliState, agentCapabilities);
  }

  private SonarQubeCliState toCliState(SonarQubeCliLocator.CliLookup cli) {
    if (cli.installationStatus() != CliInstallationStatus.INSTALLED || cli.path() == null) {
      return new SonarQubeCliState(cli.installationStatus(), CliAuthenticationStatus.UNKNOWN,
        cli.path() == null ? null : cli.path().toString(), cli.version(), null, null);
    }

    var status = locator.readStatus(cli.path());
    return new SonarQubeCliState(CliInstallationStatus.INSTALLED, status.authenticationStatus(),
      cli.path().toString(), status.version().orElse(cli.version()), status.serverUrl(), status.organization());
  }

}
