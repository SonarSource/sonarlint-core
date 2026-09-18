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
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.nodejs.OsSearchPath;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationConnection;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareAuthenticateCliCommandParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareCliCommandResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareIntegrateCliCommandParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.SonarQubeCliState;

/**
 * Shared CLI discovery, integration state, and command preparation for IDE-hosted AI agents.
 * Clients remain responsible for detecting agents in their own IDE and running commands in a
 * native terminal. This service never transports credentials to the CLI.
 */
public class AiIntegrationService {

  private final SonarQubeCliLocator locator;
  private final ConnectionConfigurationRepository connectionRepository;
  private final ConfigurationRepository configurationRepository;

  @Inject
  public AiIntegrationService(ConnectionConfigurationRepository connectionRepository, ConfigurationRepository configurationRepository) {
    this(new SonarQubeCliLocator(System2.INSTANCE, CommandExecutor.create(),
        Paths.get(System.getProperty("user.home")), System.getenv(), OsSearchPath.MAC_OS_PATH_HELPER),
      connectionRepository, configurationRepository);
  }

  @VisibleForTesting
  public AiIntegrationService(System2 system2, CommandExecutor commandExecutor, Path userHome, Map<String, String> environment,
    ConnectionConfigurationRepository connectionRepository, ConfigurationRepository configurationRepository) {
    this(new SonarQubeCliLocator(system2, commandExecutor, userHome, environment, OsSearchPath.MAC_OS_PATH_HELPER),
      connectionRepository, configurationRepository);
  }

  @VisibleForTesting
  AiIntegrationService(SonarQubeCliLocator locator, ConnectionConfigurationRepository connectionRepository,
    ConfigurationRepository configurationRepository) {
    this.locator = locator;
    this.connectionRepository = connectionRepository;
    this.configurationRepository = configurationRepository;
  }

  public GetAiIntegrationStateResponse getIntegrationState(GetAiIntegrationStateParams params) {
    var cliState = toCliState(locator.find());
    var agentCapabilities = params.getDetectedAgents().stream()
      .distinct()
      .map(agent -> AiAgentCapabilities.of(params.getIdeHost(), agent, params.getScope()))
      .toList();
    var connectionChoices = cliState.getAuthenticationStatus().offersConnectionPrefill()
      ? availableConnections()
      : List.<AiIntegrationConnection>of();
    return new GetAiIntegrationStateResponse(cliState, agentCapabilities, connectionChoices,
      recommendedConnectionId(params, connectionChoices));
  }

  public PrepareCliCommandResponse prepareInstallCommand() {
    return CliCommandFactory.prepareInstallCommand(locator.isWindows());
  }

  public PrepareCliCommandResponse prepareAuthenticateCommand(PrepareAuthenticateCliCommandParams params) {
    var connection = selectedConnection(params.getConnectionId());
    var serverUrl = connection == null ? params.getServerUrl() : connection.getServerUrl();
    var organization = connection == null ? params.getOrganization() : connection.getOrganization();
    return CliCommandFactory.prepareAuthenticationCommand(requireInstalledCli(), serverUrl, organization);
  }

  public PrepareCliCommandResponse prepareIntegrateCommand(PrepareIntegrateCliCommandParams params) {
    return CliCommandFactory.prepareIntegrationCommand(requireInstalledCli(), params.getAgent());
  }

  private Path requireInstalledCli() {
    var cli = locator.find();
    if (cli.installationStatus() != CliInstallationStatus.INSTALLED || cli.path() == null) {
      throw new IllegalStateException("A working SonarQube CLI installation is required");
    }
    return cli.path();
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

  @Nullable
  private AiIntegrationConnection selectedConnection(@Nullable String connectionId) {
    if (connectionId == null || connectionId.isBlank()) {
      return null;
    }
    var connection = connectionRepository.getConnectionById(connectionId);
    if (connection == null) {
      throw new IllegalArgumentException("Unknown SonarQube connection: " + connectionId);
    }
    return asConnection(connection);
  }

  private List<AiIntegrationConnection> availableConnections() {
    return connectionRepository.getConnectionsById().values().stream()
      .map(AiIntegrationService::asConnection)
      .sorted(Comparator.comparing(AiIntegrationConnection::getConnectionId))
      .toList();
  }

  @Nullable
  private String recommendedConnectionId(GetAiIntegrationStateParams params, List<AiIntegrationConnection> connectionChoices) {
    if (connectionChoices.isEmpty()) {
      return null;
    }
    var scopeId = params.getConfigurationScopeId();
    if (scopeId != null) {
      var connectionId = configurationRepository.getEffectiveBinding(scopeId).map(Binding::connectionId).orElse(null);
      if (connectionId != null && connectionChoices.stream().anyMatch(connection -> connection.getConnectionId().equals(connectionId))) {
        return connectionId;
      }
    }
    return connectionChoices.size() == 1 ? connectionChoices.get(0).getConnectionId() : null;
  }

  private static AiIntegrationConnection asConnection(AbstractConnectionConfiguration connection) {
    var organization = connection instanceof SonarCloudConnectionConfiguration sonarCloudConnection
      ? sonarCloudConnection.getOrganization()
      : null;
    return new AiIntegrationConnection(connection.getConnectionId(), connection.getUrl(), organization);
  }

}
