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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareAuthenticateCliCommandParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareIntegrateCliCommandParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiIntegrationServiceTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  private Path tempDir;

  @Test
  void should_report_missing_cli_and_capabilities_for_detected_agents() {
    var service = newService(false, Map.of(), commandReturning(1));

    var response = service.getIntegrationState(new GetAiIntegrationStateParams(List.of(
      AiAgent.CLAUDE_CODE,
      AiAgent.GITHUB_COPILOT,
      AiAgent.CLAUDE_CODE)));

    assertThat(response.getCli().getInstallationStatus()).isEqualTo(CliInstallationStatus.NOT_INSTALLED);
    assertThat(response.getCli().getAuthenticationStatus()).isEqualTo(CliAuthenticationStatus.UNKNOWN);
    assertThat(response.getAgents()).hasSize(2);
    assertThat(response.getAgents().get(0).getAgent()).isEqualTo(AiAgent.CLAUDE_CODE);
    assertThat(response.getAgents().get(0).isCliIntegrationSupported()).isTrue();
    assertThat(response.getAgents().get(0).isStandaloneMcpSupported()).isTrue();
    assertThat(response.getAgents().get(1).getAgent()).isEqualTo(AiAgent.GITHUB_COPILOT);
    assertThat(response.getAgents().get(1).isCliIntegrationSupported()).isFalse();
    assertThat(response.getAgents().get(1).isStandaloneMcpSupported()).isTrue();
  }

  @Test
  void should_report_cli_and_mcp_capabilities_for_each_detected_agent() {
    var service = newService(false, Map.of(), commandReturning(1));

    var response = service.getIntegrationState(new GetAiIntegrationStateParams(List.of(
      AiAgent.CURSOR,
      AiAgent.CLAUDE_CODE,
      AiAgent.CODEX,
      AiAgent.GITHUB_COPILOT,
      AiAgent.WINDSURF,
      AiAgent.KIRO)));

    assertThat(response.getAgents()).extracting(capability -> capability.getAgent())
      .containsExactly(AiAgent.CURSOR, AiAgent.CLAUDE_CODE, AiAgent.CODEX, AiAgent.GITHUB_COPILOT, AiAgent.WINDSURF, AiAgent.KIRO);
    assertThat(response.getAgents()).extracting(capability -> capability.isCliIntegrationSupported())
      .containsExactly(true, true, true, false, false, false);
    assertThat(response.getAgents()).extracting(capability -> capability.isStandaloneMcpSupported())
      .containsExactly(true, true, false, true, true, true);
  }

  @Test
  void should_report_cli_authentication_from_json_status() throws IOException {
    var executable = createExecutable("bin/sonar");
    var executor = commandReturning((command, stdout) -> {
      if (command.toCommandLine().endsWith("--version")) {
        stdout.consumeLine("SonarQube CLI 1.4.2");
      } else {
        stdout.consumeLine("{\"version\":\"1.4.2\",\"auth\":{\"status\":\"authenticated\",\"server\":\"https://sonar.example\",\"org\":\"acme\",\"token\":\"active\"}}");
      }
      return 0;
    });
    var service = newServiceForCurrentOs(Map.of("PATH", executable.getParent().toString()), executor);

    var cli = service.getIntegrationState(new GetAiIntegrationStateParams(List.of())).getCli();

    assertThat(cli.getInstallationStatus()).isEqualTo(CliInstallationStatus.INSTALLED);
    assertThat(cli.getAuthenticationStatus()).isEqualTo(CliAuthenticationStatus.AUTHENTICATED);
    assertThat(cli.getExecutablePath()).isEqualTo(executable.toString());
    assertThat(cli.getVersion()).isEqualTo("1.4.2");
    assertThat(cli.getServerUrl()).isEqualTo("https://sonar.example");
    assertThat(cli.getOrganization()).isEqualTo("acme");
  }

  @Test
  void should_distinguish_invalid_and_unverified_authentication() throws IOException {
    var executable = createExecutable("bin/sonar");
    var tokenStatus = new String[] {"invalid"};
    var executor = commandReturning((command, stdout) -> {
      if (command.toCommandLine().endsWith("--version")) {
        stdout.consumeLine("1.0.0");
      } else {
        stdout.consumeLine("{\"version\":\"1.0.0\",\"auth\":{\"status\":\"authenticated\",\"token\":\"" + tokenStatus[0] + "\"}}");
      }
      return 0;
    });
    var service = newServiceForCurrentOs(Map.of("PATH", executable.getParent().toString()), executor);

    assertThat(service.getIntegrationState(new GetAiIntegrationStateParams(List.of())).getCli().getAuthenticationStatus())
      .isEqualTo(CliAuthenticationStatus.INVALID);
    tokenStatus[0] = "set_unverified";
    assertThat(service.getIntegrationState(new GetAiIntegrationStateParams(List.of())).getCli().getAuthenticationStatus())
      .isEqualTo(CliAuthenticationStatus.UNVERIFIED);
  }

  @Test
  void should_report_unknown_authentication_when_status_json_has_no_auth_object() throws IOException {
    var executable = createExecutable("bin/sonar");
    var executor = commandReturning((command, stdout) -> {
      if (command.toCommandLine().endsWith("--version")) {
        stdout.consumeLine("1.0.0");
      } else {
        stdout.consumeLine("{\"version\":\"1.0.0\"}");
      }
      return 0;
    });
    var service = newServiceForCurrentOs(Map.of("PATH", executable.getParent().toString()), executor);

    var cli = service.getIntegrationState(new GetAiIntegrationStateParams(List.of())).getCli();

    assertThat(cli.getInstallationStatus()).isEqualTo(CliInstallationStatus.INSTALLED);
    assertThat(cli.getAuthenticationStatus()).isEqualTo(CliAuthenticationStatus.UNKNOWN);
    assertThat(cli.getVersion()).isEqualTo("1.0.0");
  }

  @Test
  void should_report_unknown_authentication_when_status_payload_is_not_an_object() throws IOException {
    var executable = createExecutable("bin/sonar");
    var executor = commandReturning((command, stdout) -> {
      if (command.toCommandLine().endsWith("--version")) {
        stdout.consumeLine("1.0.0");
      } else {
        stdout.consumeLine("[]");
      }
      return 0;
    });
    var service = newServiceForCurrentOs(Map.of("PATH", executable.getParent().toString()), executor);

    assertThat(service.getIntegrationState(new GetAiIntegrationStateParams(List.of())).getCli().getAuthenticationStatus())
      .isEqualTo(CliAuthenticationStatus.UNKNOWN);
  }

  @Test
  void should_report_unauthenticated_when_cli_status_is_explicitly_unauthenticated() throws IOException {
    var executable = createExecutable("bin/sonar");
    var executor = commandReturning((command, stdout) -> {
      if (command.toCommandLine().endsWith("--version")) {
        stdout.consumeLine("1.0.0");
      } else {
        stdout.consumeLine("{\"version\":\"1.0.0\",\"auth\":{\"status\":\"unauthenticated\"}}");
      }
      return 0;
    });
    var service = newServiceForCurrentOs(Map.of("PATH", executable.getParent().toString()), executor);

    var cli = service.getIntegrationState(new GetAiIntegrationStateParams(List.of())).getCli();

    assertThat(cli.getAuthenticationStatus()).isEqualTo(CliAuthenticationStatus.UNAUTHENTICATED);
    assertThat(cli.getVersion()).isEqualTo("1.0.0");
  }

  @Test
  void should_report_unusable_executable_when_version_cannot_be_read() throws IOException {
    var executable = createExecutable("bin/sonar");
    var service = newServiceForCurrentOs(Map.of("PATH", executable.getParent().toString()), commandReturning(1));

    var cli = service.getIntegrationState(new GetAiIntegrationStateParams(List.of())).getCli();

    assertThat(cli.getInstallationStatus()).isEqualTo(CliInstallationStatus.UNUSABLE);
    assertThat(cli.getExecutablePath()).isEqualTo(executable.toString());
  }

  @Test
  void should_find_cli_on_windows_when_path_has_mixed_case_key() throws IOException {
    var executable = createExecutable("bin/sonar.exe");
    var service = newService(true, Map.of("Path", executable.getParent().toString()), versionCommandExecutor());

    var cli = service.getIntegrationState(new GetAiIntegrationStateParams(List.of())).getCli();

    assertThat(cli.getInstallationStatus()).isEqualTo(CliInstallationStatus.INSTALLED);
    assertThat(cli.getExecutablePath()).isEqualTo(executable.toString());
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void should_find_cli_using_mac_os_path_helper() throws IOException {
    var executable = createExecutable("homebrew/bin/sonar");
    var executor = commandReturning((command, stdout) -> {
      if (command.toCommandLine().contains("path_helper")) {
        stdout.consumeLine("PATH=\"" + executable.getParent() + "\"; export PATH;");
      } else {
        stdout.consumeLine("SonarQube CLI 1.0.0");
      }
      return 0;
    });
    var service = newMacOsService(Map.of("PATH", "/usr/bin"), executor);

    var cli = service.getIntegrationState(new GetAiIntegrationStateParams(List.of())).getCli();

    assertThat(cli.getInstallationStatus()).isEqualTo(CliInstallationStatus.INSTALLED);
    assertThat(cli.getExecutablePath()).isEqualTo(executable.toString());
  }

  @Test
  void should_prepare_login_with_ide_connection_details_but_without_credentials() throws IOException {
    var executable = createExecutable("bin/sonar");
    var service = newServiceForCurrentOs(Map.of("PATH", executable.getParent().toString()), versionCommandExecutor());

    var command = service.prepareAuthenticateCommand(new PrepareAuthenticateCliCommandParams(
      " https://sonar.example ", " acme "));

    assertThat(command.getExecutable()).isEqualTo(executable.toString());
    assertThat(command.getArguments()).containsExactly("auth", "login", "--server", "https://sonar.example", "--org", "acme");
    assertThat(command.getArguments()).noneMatch(argument -> argument.toLowerCase().contains("token"));
    assertThat(command.isInteractive()).isTrue();
  }

  @Test
  void should_prepare_login_without_connection_details() throws IOException {
    var executable = createExecutable("bin/sonar");
    var service = newServiceForCurrentOs(Map.of("PATH", executable.getParent().toString()), versionCommandExecutor());

    var blankDetails = service.prepareAuthenticateCommand(new PrepareAuthenticateCliCommandParams("  ", null));
    var missingDetails = service.prepareAuthenticateCommand(new PrepareAuthenticateCliCommandParams(null, null));

    assertThat(blankDetails.getArguments()).containsExactly("auth", "login");
    assertThat(missingDetails.getArguments()).containsExactly("auth", "login");
  }

  @Test
  void should_prepare_supported_global_integration() throws IOException {
    var executable = createExecutable("bin/sonar");
    var service = newServiceForCurrentOs(Map.of("PATH", executable.getParent().toString()), versionCommandExecutor());

    var command = service.prepareIntegrateCommand(new PrepareIntegrateCliCommandParams(AiAgent.CLAUDE_CODE));

    assertThat(command.getExecutable()).isEqualTo(executable.toString());
    assertThat(command.getArguments()).containsExactly("integrate", "claude", "--global");
  }

  @Test
  void should_prepare_cursor_and_codex_global_integration() throws IOException {
    var executable = createExecutable("bin/sonar");
    var service = newServiceForCurrentOs(Map.of("PATH", executable.getParent().toString()), versionCommandExecutor());

    var cursorCommand = service.prepareIntegrateCommand(new PrepareIntegrateCliCommandParams(AiAgent.CURSOR));
    var codexCommand = service.prepareIntegrateCommand(new PrepareIntegrateCliCommandParams(AiAgent.CODEX));

    assertThat(cursorCommand.getArguments()).containsExactly("integrate", "cursor", "--global");
    assertThat(codexCommand.getArguments()).containsExactly("integrate", "codex", "--global");
  }

  @Test
  void should_reject_integrate_when_agent_is_missing() throws IOException {
    var executable = createExecutable("bin/sonar");
    var service = newServiceForCurrentOs(Map.of("PATH", executable.getParent().toString()), versionCommandExecutor());
    var params = new PrepareIntegrateCliCommandParams(null);

    assertThatThrownBy(() -> service.prepareIntegrateCommand(params))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("An AI agent is required");
  }

  @Test
  void should_reject_authenticate_and_integrate_when_cli_is_missing() {
    var service = newService(false, Map.of(), commandReturning(1));
    var authenticate = new PrepareAuthenticateCliCommandParams(null, null);
    var integrate = new PrepareIntegrateCliCommandParams(AiAgent.CLAUDE_CODE);

    assertThatThrownBy(() -> service.prepareAuthenticateCommand(authenticate))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("A working SonarQube CLI installation is required");
    assertThatThrownBy(() -> service.prepareIntegrateCommand(integrate))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("A working SonarQube CLI installation is required");
  }

  @Test
  void should_reject_cli_integration_for_copilot_in_vscode() throws IOException {
    var executable = createExecutable("bin/sonar");
    var service = newServiceForCurrentOs(Map.of("PATH", executable.getParent().toString()), versionCommandExecutor());

    var params = new PrepareIntegrateCliCommandParams(AiAgent.GITHUB_COPILOT);

    assertThatThrownBy(() -> service.prepareIntegrateCommand(params))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("not supported");
  }

  @Test
  void should_prepare_official_installers_for_the_current_operating_system() {
    var unixCommand = newService(false, Map.of(), commandReturning(1)).prepareInstallCommand();
    var windowsCommand = newService(true, Map.of(), commandReturning(1)).prepareInstallCommand();

    assertThat(unixCommand.getExecutable()).isEqualTo("/bin/bash");
    assertThat(unixCommand.getArguments()).containsExactly("-o", "pipefail", "-c",
      "curl --fail --silent --show-error --location " +
        "https://raw.githubusercontent.com/SonarSource/sonarqube-cli/refs/heads/master/user-scripts/install.sh | bash");
    assertThat(windowsCommand.getExecutable()).isEqualTo("powershell.exe");
    assertThat(windowsCommand.getArguments()).containsExactly("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command",
      "irm https://raw.githubusercontent.com/SonarSource/sonarqube-cli/refs/heads/master/user-scripts/install.ps1 | iex");
  }

  @Test
  void should_find_cli_in_standard_unix_location_when_path_does_not_contain_it() throws IOException {
    var executable = createExecutable(".local/share/sonarqube-cli/bin/sonar");
    var service = newService(false, Map.of("PATH", "/no-cli-on-path"), versionCommandExecutor());

    var cli = service.getIntegrationState(new GetAiIntegrationStateParams(List.of())).getCli();

    assertThat(cli.getInstallationStatus()).isEqualTo(CliInstallationStatus.INSTALLED);
    assertThat(cli.getExecutablePath()).isEqualTo(executable.toString());
  }

  @Test
  void should_find_cli_in_standard_windows_location_when_path_does_not_contain_it() throws IOException {
    var executable = createExecutable("sonarqube-cli/bin/sonar.exe");
    var service = newService(true, Map.of("LOCALAPPDATA", tempDir.toString()), versionCommandExecutor());

    var cli = service.getIntegrationState(new GetAiIntegrationStateParams(List.of())).getCli();

    assertThat(cli.getInstallationStatus()).isEqualTo(CliInstallationStatus.INSTALLED);
    assertThat(cli.getExecutablePath()).isEqualTo(executable.toString());
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void should_prefer_path_candidate_over_standard_install_location() throws IOException {
    var pathExecutable = createExecutable("path-bin/sonar");
    createExecutable(".local/share/sonarqube-cli/bin/sonar");
    var service = newService(false, Map.of("PATH", pathExecutable.getParent().toString()), versionCommandExecutor());

    var cli = service.getIntegrationState(new GetAiIntegrationStateParams(List.of())).getCli();

    assertThat(cli.getInstallationStatus()).isEqualTo(CliInstallationStatus.INSTALLED);
    assertThat(cli.getExecutablePath()).isEqualTo(pathExecutable.toString());
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void should_use_standard_install_location_when_path_candidate_is_unusable() throws IOException {
    var pathExecutable = createExecutable("path-bin/sonar");
    var standardExecutable = createExecutable(".local/share/sonarqube-cli/bin/sonar");
    var executor = commandReturning((command, stdout) -> {
      if (command.toCommandLine().contains("path-bin")) {
        return 1;
      }
      stdout.consumeLine("SonarQube CLI 1.0.0");
      return 0;
    });
    var service = newService(false, Map.of("PATH", pathExecutable.getParent().toString()), executor);

    var cli = service.getIntegrationState(new GetAiIntegrationStateParams(List.of())).getCli();

    assertThat(cli.getInstallationStatus()).isEqualTo(CliInstallationStatus.INSTALLED);
    assertThat(cli.getExecutablePath()).isEqualTo(standardExecutable.toString());
  }

  @Test
  void should_ignore_malformed_path_entries() throws IOException {
    var executable = createExecutable("bin/sonar");
    var path = "\0" + File.pathSeparator + executable.getParent();
    var service = newServiceForCurrentOs(Map.of("PATH", path), versionCommandExecutor());

    var cli = service.getIntegrationState(new GetAiIntegrationStateParams(List.of())).getCli();

    assertThat(cli.getInstallationStatus()).isEqualTo(CliInstallationStatus.INSTALLED);
    assertThat(cli.getExecutablePath()).isEqualTo(executable.toString());
  }

  private AiIntegrationService newServiceForCurrentOs(Map<String, String> environment, CommandExecutor executor) {
    return newService(System2.INSTANCE.isOsWindows(), environment, executor);
  }

  private AiIntegrationService newService(boolean windows, Map<String, String> environment, CommandExecutor executor) {
    var system2 = mock(System2.class);
    when(system2.isOsWindows()).thenReturn(windows);
    return new AiIntegrationService(system2, executor, tempDir, environment);
  }

  private AiIntegrationService newMacOsService(Map<String, String> environment, CommandExecutor executor) {
    var system2 = mock(System2.class);
    when(system2.isOsMac()).thenReturn(true);
    return new AiIntegrationService(system2, executor, tempDir, environment);
  }

  private Path createExecutable(String relativePath) throws IOException {
    var executable = tempDir.resolve(relativePath);
    Files.createDirectories(executable.getParent());
    Files.createFile(executable);
    assertThat(executable.toFile().setExecutable(true)).isTrue();
    return executable;
  }

  private static CommandExecutor versionCommandExecutor() {
    return commandReturning((command, stdout) -> {
      stdout.consumeLine("SonarQube CLI 1.0.0");
      return 0;
    });
  }

  private static CommandExecutor commandReturning(int exitCode) {
    return commandReturning((command, stdout) -> exitCode);
  }

  private static CommandExecutor commandReturning(CommandAnswer answer) {
    var executor = mock(CommandExecutor.class);
    when(executor.execute(any(Command.class), any(), any(), anyLong())).thenAnswer(invocation ->
      answer.execute(invocation.getArgument(0), invocation.getArgument(1)));
    return executor;
  }

  @FunctionalInterface
  private interface CommandAnswer {
    int execute(Command command, StreamConsumer stdout);
  }
}
