/*
 * SonarLint Core - ITs - Tests
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
package its;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonarsource.sonarlint.core.ai.ide.AiIntegrationService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationScope;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.PrepareIntegrateCliCommandParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@DisabledOnOs(OS.WINDOWS)
@Tag("CliCompatibility")
class LatestSonarQubeCliCompatibilityTests {
  private static final long INSTALL_TIMEOUT_MINUTES = 2L;

  @TempDir
  private Path tempDir;

  @Test
  void should_be_compatible_with_the_latest_published_cli() throws Exception {
    var isolatedHome = tempDir.resolve("home");
    Files.createDirectories(isolatedHome);
    installLatestCli(isolatedHome);

    var cliPath = isolatedHome.resolve(".local/share/sonarqube-cli/bin/sonar");
    var environment = isolatedEnvironment(isolatedHome, cliPath.getParent());
    var service = newIsolatedService(isolatedHome, environment);

    var response = service.getIntegrationState(new GetAiIntegrationStateParams(AiIntegrationHost.OTHER,
      List.of(AiAgent.CLAUDE_CODE), AiIntegrationScope.GLOBAL, null));

    assertThat(response.getCli().getInstallationStatus()).isEqualTo(CliInstallationStatus.INSTALLED);
    assertThat(response.getCli().getAuthenticationStatus()).isEqualTo(CliAuthenticationStatus.UNAUTHENTICATED);
    assertThat(response.getCli().getExecutablePath()).isEqualTo(cliPath.toString());
    assertThat(response.getCli().getVersion()).matches("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?");
    assertThat(response.getAgents()).singleElement()
      .satisfies(capability -> assertThat(capability.isCliIntegrationSupported()).isTrue());

    var integrateCommand = service.prepareIntegrateCommand(new PrepareIntegrateCliCommandParams(AiAgent.CLAUDE_CODE));
    assertThat(integrateCommand.getExecutable()).isEqualTo(cliPath.toString());
    assertThat(integrateCommand.getArguments()).containsExactly("integrate", "claude", "--global");
    var command = new ArrayList<String>();
    command.add(integrateCommand.getExecutable());
    command.addAll(integrateCommand.getArguments());
    var commandResult = run(command, environment, isolatedHome, tempDir.resolve("cli-command.log"),
      Duration.ofSeconds(30), "SonarQube CLI command timed out.");
    assertThat(commandResult.exitCode()).isEqualTo(1);
    assertThat(commandResult.output()).contains("Not authenticated", "sonar auth login");
  }

  private void installLatestCli(Path isolatedHome) throws IOException, InterruptedException {
    var installLog = tempDir.resolve("cli-install.log");
    var installerEnvironment = isolatedEnvironment(isolatedHome, null);
    installerEnvironment.put("PATH", "/usr/bin:/bin");
    var service = newIsolatedService(isolatedHome, installerEnvironment);
    var installCommand = service.prepareInstallCommand();
    var command = new ArrayList<String>();
    command.add(installCommand.getExecutable());
    command.addAll(installCommand.getArguments());
    var result = run(command, installerEnvironment, isolatedHome, installLog,
      Duration.ofMinutes(INSTALL_TIMEOUT_MINUTES), "Latest SonarQube CLI installation timed out.");
    assertThat(result.exitCode())
      .as("Latest SonarQube CLI installation output:%n%s", result.output())
      .isZero();
    assertThat(isolatedHome.resolve(".local/share/sonarqube-cli/bin/sonar"))
      .as("Latest SonarQube CLI installation output:%n%s", result.output())
      .isExecutable();
  }

  private static AiIntegrationService newIsolatedService(Path isolatedHome, Map<String, String> environment) {
    return new AiIntegrationService(System2.INSTANCE, CommandExecutor.create(), isolatedHome, environment,
      new ConnectionConfigurationRepository(), new ConfigurationRepository());
  }

  private CommandResult run(List<String> command, Map<String, String> environment, Path workDir, Path log,
    Duration timeout, String timeoutMessage) throws IOException, InterruptedException {
    var processBuilder = new ProcessBuilder(command)
      .directory(workDir.toFile())
      .redirectErrorStream(true)
      .redirectOutput(log.toFile());
    processBuilder.environment().clear();
    processBuilder.environment().putAll(environment);

    var process = processBuilder.start();
    if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
      process.destroyForcibly();
      fail("%s Output: %s", timeoutMessage, Files.readString(log));
    }
    return new CommandResult(process.exitValue(), Files.readString(log));
  }

  private static HashMap<String, String> isolatedEnvironment(Path isolatedHome, @Nullable Path cliDirectory) {
    var environment = new HashMap<>(System.getenv());
    environment.put("HOME", isolatedHome.toString());
    environment.put("PWD", isolatedHome.toString());
    environment.put("PROFILE", "/dev/null");
    environment.put("ZDOTDIR", isolatedHome.toString());
    environment.put("XDG_CONFIG_HOME", isolatedHome.resolve(".config").toString());
    environment.put("XDG_DATA_HOME", isolatedHome.resolve(".local/share").toString());
    environment.put("XDG_CACHE_HOME", isolatedHome.resolve(".cache").toString());
    environment.put("CLAUDE_CONFIG_DIR", isolatedHome.resolve(".claude").toString());
    environment.put("SONAR_USER_HOME", isolatedHome.resolve(".sonar").toString());
    environment.put("SONAR_TOKEN", "");
    environment.put("SONAR_HOST_URL", "");
    environment.put("SONARQUBE_CLI_TOKEN", "");
    environment.put("SONARQUBE_CLI_SERVER", "");
    environment.put("SONARQUBE_CLI_ORG", "");
    environment.put("SONARQUBE_CLI_KEYCHAIN_FILE", isolatedHome.resolve("cli-keychain.json").toString());
    environment.put("SONARQUBE_CLI_KEYCHAIN_SERVICE", "sonarlint-core-compatibility-test");
    if (cliDirectory != null) {
      var currentPath = environment.getOrDefault("PATH", "");
      environment.put("PATH", currentPath.isBlank()
        ? cliDirectory.toString()
        : cliDirectory + File.pathSeparator + currentPath);
    }
    return environment;
  }

  private record CommandResult(int exitCode, String output) {
  }
}
