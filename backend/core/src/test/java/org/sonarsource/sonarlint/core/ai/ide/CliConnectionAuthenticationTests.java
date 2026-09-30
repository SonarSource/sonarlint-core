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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;
import org.sonarsource.sonarlint.core.SonarCloudRegion;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.os.OsSearchPath;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionResponse.Status;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CliConnectionAuthenticationTests {

  @TempDir
  Path tempDir;

  private final ConnectionConfigurationRepository connections = new ConnectionConfigurationRepository();
  private final SonarLintRpcClient client = mock(SonarLintRpcClient.class);
  private final CliTokenAuthenticationRunner runner = mock(CliTokenAuthenticationRunner.class);
  private final SonarLintCancelMonitor cancelMonitor = new SonarLintCancelMonitor();
  private final Map<String, GetCredentialsResponse> credentialResponses = new HashMap<>();

  @BeforeEach
  void addConnections() {
    when(client.getCredentials(any(GetCredentialsParams.class))).thenAnswer(invocation -> {
      GetCredentialsParams params = invocation.getArgument(0);
      return CompletableFuture.completedFuture(credentialResponses.get(params.getConnectionId()));
    });
    connections.addOrReplace(new SonarQubeConnectionConfiguration("server", "https://server.example", false));
    connections.addOrReplace(new SonarCloudConnectionConfiguration(URI.create("https://sonarcloud.io"),
      URI.create("https://api.sonarcloud.io"), "eu", "europe", SonarCloudRegion.EU, false));
    connections.addOrReplace(new SonarCloudConnectionConfiguration(URI.create("https://sonarqube.us"),
      URI.create("https://api.sonarqube.us"), "us", "america", SonarCloudRegion.US, false));
  }

  @Test
  void uses_the_selected_server_token_and_endpoint() throws IOException {
    var service = service("1.9.0");
    credentials("server", new GetCredentialsResponse(new TokenDto("saved-secret")));
    when(runner.authenticate(any(), eq("https://server.example"), eq(null), eq("saved-secret"), eq(cancelMonitor)))
      .thenReturn(new AuthenticateCliWithConnectionResponse(Status.AUTHENTICATED, null));

    var result = service.authenticateCliWithConnection(new AuthenticateCliWithConnectionParams("server"), cancelMonitor);

    assertThat(result.getStatus()).isEqualTo(Status.AUTHENTICATED);
    verify(runner).authenticate(any(), eq("https://server.example"), eq(null), eq("saved-secret"), eq(cancelMonitor));
  }

  @Test
  void uses_the_organization_and_endpoint_of_both_cloud_regions() throws IOException {
    var service = service("1.9.0");
    credentials("eu", new GetCredentialsResponse(new TokenDto("eu-token")));
    credentials("us", new GetCredentialsResponse(new TokenDto("us-token")));
    when(runner.authenticate(any(), any(), any(), any(), eq(cancelMonitor)))
      .thenReturn(new AuthenticateCliWithConnectionResponse(Status.AUTHENTICATED, null));

    assertThat(service.authenticateCliWithConnection(new AuthenticateCliWithConnectionParams("eu"), cancelMonitor).getStatus())
      .isEqualTo(Status.AUTHENTICATED);
    assertThat(service.authenticateCliWithConnection(new AuthenticateCliWithConnectionParams("us"), cancelMonitor).getStatus())
      .isEqualTo(Status.AUTHENTICATED);
    verify(runner).authenticate(any(), eq("https://sonarcloud.io"), eq("europe"), eq("eu-token"), eq(cancelMonitor));
    verify(runner).authenticate(any(), eq("https://sonarqube.us"), eq("america"), eq("us-token"), eq(cancelMonitor));
  }

  @Test
  void requests_interactive_login_for_missing_or_password_credentials() throws IOException {
    var service = service("1.9.0");
    credentials("server", new GetCredentialsResponse((Either<TokenDto, UsernamePasswordDto>) null));
    credentials("eu", new GetCredentialsResponse(new UsernamePasswordDto("user", "password")));

    assertThat(service.authenticateCliWithConnection(new AuthenticateCliWithConnectionParams("server"), cancelMonitor).getStatus())
      .isEqualTo(Status.INTERACTIVE_LOGIN_REQUIRED);
    assertThat(service.authenticateCliWithConnection(new AuthenticateCliWithConnectionParams("eu"), cancelMonitor).getStatus())
      .isEqualTo(Status.INTERACTIVE_LOGIN_REQUIRED);
    verify(runner, never()).authenticate(any(), any(), any(), any(), any());
  }

  @Test
  void rejects_unknown_connections_before_requesting_credentials() {
    var service = serviceWithoutCli();

    var result = service.authenticateCliWithConnection(new AuthenticateCliWithConnectionParams("missing"), cancelMonitor);

    assertThat(result.getStatus()).isEqualTo(Status.FAILED);
    verify(client, never()).getCredentials(any());
  }

  @Test
  void requests_an_upgrade_for_older_cli_versions() throws IOException {
    var service = service("1.8.9");
    credentials("server", new GetCredentialsResponse(new TokenDto("saved-secret")));

    var result = service.authenticateCliWithConnection(new AuthenticateCliWithConnectionParams("server"), cancelMonitor);

    assertThat(result.getStatus()).isEqualTo(Status.UPGRADE_REQUIRED);
    verify(runner, never()).authenticate(any(), any(), any(), any(), any());
  }

  @Test
  void reports_credential_retrieval_failure_without_leaking_the_error() {
    var service = serviceWithoutCli();
    doReturn(CompletableFuture.failedFuture(new RuntimeException("saved-secret"))).when(client).getCredentials(any(GetCredentialsParams.class));

    var result = service.authenticateCliWithConnection(new AuthenticateCliWithConnectionParams("server"), cancelMonitor);

    assertThat(result.getStatus()).isEqualTo(Status.FAILED);
    assertThat(result.getMessage()).doesNotContain("saved-secret");
  }

  private void credentials(String id, GetCredentialsResponse response) {
    credentialResponses.put(id, response);
  }

  private AiIntegrationService service(String version) throws IOException {
    var executable = tempDir.resolve("sonar");
    Files.createFile(executable);
    assertThat(executable.toFile().setExecutable(true)).isTrue();
    var commandExecutor = mock(CommandExecutor.class);
    when(commandExecutor.execute(any(Command.class), any(), any(), anyLong())).thenAnswer(invocation -> {
      StreamConsumer stdout = invocation.getArgument(1);
      stdout.consumeLine("SonarQube CLI " + version);
      return 0;
    });
    return service(commandExecutor, Map.of("PATH", tempDir.toString()));
  }

  private AiIntegrationService serviceWithoutCli() {
    return service(mock(CommandExecutor.class), Map.of());
  }

  private AiIntegrationService service(CommandExecutor commandExecutor, Map<String, String> environment) {
    var search = new OsExecutableSearch(System2.INSTANCE, commandExecutor, environment, OsSearchPath.MAC_OS_PATH_HELPER);
    return new AiIntegrationService(new SonarQubeCliLocator(search, tempDir), new AgentCliLocator(search, tempDir),
      connections, new ConfigurationRepository(), client, runner);
  }
}
