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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonarsource.sonarlint.core.ai.ide.SonarQubeCliStatusDecoder.CliStatus;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.os.OsSearchPath;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationConnection;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionResponse.Status;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareAuthenticateCliCommandParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareCliCommandResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareIntegrateCliCommandParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.SonarQubeCliState;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.UninstallCliResponse;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsResponse;

/**
 * Shared CLI discovery, integration state, and command preparation for IDE-hosted AI agents.
 * Clients remain responsible for detecting agents in their own IDE and running commands in a
 * native terminal. Token-based authentication runs here so credentials never appear in a terminal command.
 */
public class AiIntegrationService {

  private static final String CREDENTIALS_ERROR = "Could not retrieve the selected connection's credentials.";
  private static final Version MIN_TOKEN_AUTHENTICATION_CLI_VERSION = Version.create("1.9.0");

  private final SonarQubeCliLocator locator;
  private final AgentCliLocator agentCliLocator;
  private final ConnectionConfigurationRepository connectionRepository;
  private final ConfigurationRepository configurationRepository;
  private final SonarLintRpcClient client;
  private final CliTokenAuthenticationRunner tokenAuthenticationRunner;

  @Inject
  public AiIntegrationService(ConnectionConfigurationRepository connectionRepository, ConfigurationRepository configurationRepository,
    SonarLintRpcClient client) {
    this(System2.INSTANCE, CommandExecutor.create(), Paths.get(System.getProperty("user.home")), System.getenv(),
      connectionRepository, configurationRepository, client);
  }

  @VisibleForTesting
  public AiIntegrationService(System2 system2, CommandExecutor commandExecutor, Path userHome, Map<String, String> environment,
    ConnectionConfigurationRepository connectionRepository, ConfigurationRepository configurationRepository, SonarLintRpcClient client) {
    var search = new OsExecutableSearch(system2, commandExecutor, environment, OsSearchPath.MAC_OS_PATH_HELPER);
    this.locator = new SonarQubeCliLocator(search, userHome);
    this.agentCliLocator = new AgentCliLocator(search, userHome);
    this.connectionRepository = connectionRepository;
    this.configurationRepository = configurationRepository;
    this.client = client;
    this.tokenAuthenticationRunner = new CliTokenAuthenticationRunner();
  }

  @VisibleForTesting
  AiIntegrationService(SonarQubeCliLocator locator, AgentCliLocator agentCliLocator,
    ConnectionConfigurationRepository connectionRepository, ConfigurationRepository configurationRepository,
    SonarLintRpcClient client, CliTokenAuthenticationRunner tokenAuthenticationRunner) {
    this.locator = locator;
    this.agentCliLocator = agentCliLocator;
    this.connectionRepository = connectionRepository;
    this.configurationRepository = configurationRepository;
    this.client = client;
    this.tokenAuthenticationRunner = tokenAuthenticationRunner;
  }

  public GetAiIntegrationStateResponse getIntegrationState(GetAiIntegrationStateParams params) {
    var cli = locator.find();
    var status = cli.installationStatus() == CliInstallationStatus.INSTALLED && cli.path() != null
      ? locator.readStatus(cli.path()) : CliStatus.unknown();
    var cliState = toCliState(cli, status);
    var agentsBySource = new LinkedHashMap<AiAgent, LinkedHashSet<AiAgentDetectionSource>>();
    params.getDetectedAgents().forEach(agent -> addDetectionSource(agentsBySource, agent, AiAgentDetectionSource.IDE));
    if (params.isDiscoverLocalAgentClis()) {
      agentCliLocator.discover()
        .forEach(agent -> addDetectionSource(agentsBySource, agent, AiAgentDetectionSource.CLI));
    }
    var agentCapabilities = agentsBySource.entrySet().stream()
      .map(entry -> AiAgentCapabilities.of(params.getIdeHost(), entry.getKey(), params.getScope(), List.copyOf(entry.getValue())))
      .toList();
    var connectionChoices = cliState.getAuthenticationStatus().offersConnectionPrefill()
      ? availableConnections()
      : List.<AiIntegrationConnection>of();
    return new GetAiIntegrationStateResponse(cliState, agentCapabilities, connectionChoices,
      recommendedConnectionId(params, connectionChoices), status.cliIntegrations());
  }

  private static void addDetectionSource(Map<AiAgent, LinkedHashSet<AiAgentDetectionSource>> agentsBySource,
    AiAgent agent, AiAgentDetectionSource source) {
    agentsBySource.computeIfAbsent(agent, ignored -> new LinkedHashSet<>()).add(source);
  }

  public UninstallCliResponse uninstallCli(SonarLintCancelMonitor cancelMonitor) {
    cancelMonitor.checkCanceled();
    var cli = locator.find();
    var directory = locator.installationDirectory(cli);
    if (directory == null) {
      return new UninstallCliResponse(UninstallCliResponse.Status.NOT_AVAILABLE, "", "", "Only an official per-user CLI installation can be uninstalled.");
    }
    var reset = locator.reset(cli.path().toAbsolutePath().normalize());
    if (reset.exitCode() != 0) {
      return new UninstallCliResponse(UninstallCliResponse.Status.FAILED, reset.stdout(), reset.stderr(), "SonarQube CLI reset failed.");
    }
    try {
      FileUtils.deleteRecursively(directory);
      return new UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, reset.stdout(), reset.stderr(), null);
    } catch (IllegalStateException e) {
      return new UninstallCliResponse(UninstallCliResponse.Status.FAILED, reset.stdout(), reset.stderr(), "Could not delete the SonarQube CLI installation folder.");
    }
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

  public AuthenticateCliWithConnectionResponse authenticateCliWithConnection(AuthenticateCliWithConnectionParams params,
    SonarLintCancelMonitor cancelMonitor) {
    var connectionId = params.getConnectionId();
    if (connectionId == null || connectionId.isBlank()) {
      return new AuthenticateCliWithConnectionResponse(Status.FAILED, "A SonarQube connection is required.");
    }
    var connection = connectionRepository.getConnectionById(connectionId);
    if (connection == null) {
      return new AuthenticateCliWithConnectionResponse(Status.FAILED, "The selected SonarQube connection no longer exists.");
    }
    GetCredentialsResponse credentialsResponse;
    try {
      credentialsResponse = awaitCredentials(connectionId, cancelMonitor);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new AuthenticateCliWithConnectionResponse(Status.FAILED, CREDENTIALS_ERROR);
    } catch (ExecutionException | TimeoutException | RuntimeException e) {
      cancelMonitor.checkCanceled();
      return new AuthenticateCliWithConnectionResponse(Status.FAILED, CREDENTIALS_ERROR);
    }
    cancelMonitor.checkCanceled();
    if (credentialsResponse == null) {
      return new AuthenticateCliWithConnectionResponse(Status.FAILED, CREDENTIALS_ERROR);
    }
    var credentials = credentialsResponse.getCredentials();
    if (credentials == null || credentials.isRight() || credentials.getLeft() == null ||
      credentials.getLeft().getToken() == null || credentials.getLeft().getToken().isBlank()) {
      return new AuthenticateCliWithConnectionResponse(Status.INTERACTIVE_LOGIN_REQUIRED, null);
    }

    var cli = locator.find();
    if (cli.installationStatus() != CliInstallationStatus.INSTALLED || cli.path() == null) {
      return new AuthenticateCliWithConnectionResponse(Status.FAILED, "A working SonarQube CLI installation is required.");
    }
    if (!supportsTokenAuthentication(cli.version())) {
      return new AuthenticateCliWithConnectionResponse(Status.UPGRADE_REQUIRED, "Update SonarQube CLI to the latest version to reuse a saved connection token.");
    }

    var selectedConnection = asConnection(connection);
    return tokenAuthenticationRunner.authenticate(cli.path(), selectedConnection.getServerUrl(), selectedConnection.getOrganization(),
      credentials.getLeft().getToken(), cancelMonitor);
  }

  private GetCredentialsResponse awaitCredentials(String connectionId, SonarLintCancelMonitor cancelMonitor)
    throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<GetCredentialsResponse> future = client.getCredentials(new GetCredentialsParams(connectionId));
    cancelMonitor.onCancel(() -> future.cancel(true));
    try {
      return future.get(30, TimeUnit.SECONDS);
    } catch (InterruptedException | TimeoutException e) {
      future.cancel(true);
      throw e;
    }
  }

  private static boolean supportsTokenAuthentication(@Nullable String version) {
    if (version == null) {
      return false;
    }
    try {
      return Version.create(version.split("\\+", 2)[0]).compareTo(MIN_TOKEN_AUTHENTICATION_CLI_VERSION) >= 0;
    } catch (NumberFormatException e) {
      return false;
    }
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

  private static SonarQubeCliState toCliState(SonarQubeCliLocator.CliLookup cli, CliStatus status) {
    if (cli.installationStatus() != CliInstallationStatus.INSTALLED || cli.path() == null) {
      return new SonarQubeCliState(cli.installationStatus(), CliAuthenticationStatus.UNKNOWN,
        cli.path() == null ? null : cli.path().toString(), cli.version(), null, null);
    }

    return new SonarQubeCliState(CliInstallationStatus.INSTALLED, status.authenticationStatus(),
      cli.path().toString(), status.version().orElse(cli.version()), status.serverUrl(), status.organization(), locator.installationDirectory(cli) != null);
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
