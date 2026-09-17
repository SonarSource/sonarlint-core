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
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandException;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.os.OsSearchPath;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCliLocatorTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  private Path tempDir;

  @Test
  void should_probe_discoverable_agents_in_stable_order() {
    assertThat(AgentProfiles.cliDiscoverable()).extracting(AgentProfile::agent)
      .containsExactly(AiAgent.CLAUDE_CODE, AiAgent.CODEX, AiAgent.GITHUB_COPILOT_CLI, AiAgent.CURSOR, AiAgent.ANTIGRAVITY);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void should_reject_unverified_executable_names_and_never_invoke_antigravity_desktop() throws IOException {
    var claude = createExecutable("agents/claude");
    var codex = createExecutable("agents/codex");
    var copilot = createExecutable("agents/copilot");
    var cursorAgent = createExecutable("agents/cursor-agent");
    var agent = createExecutable("agents/agent");
    var agy = createExecutable("agents/agy");
    var antigravityDesktop = createExecutable("agents/antigravity");
    var commands = new ArrayList<String>();
    var locator = newLocator(false, Map.of("PATH", claude.getParent().toString()), commandReturning((command, stdout) -> {
      commands.add(command.toCommandLine());
      if (command.toCommandLine().contains(codex.toString())) {
        stdout.consumeLine("Codex companion tool");
      } else if (command.toCommandLine().contains(copilot.toString())) {
        stdout.consumeLine("Copilot helper");
      } else {
        stdout.consumeLine("Unrelated command");
      }
      return 0;
    }));

    assertThat(locator.discover()).isEmpty();
    assertThat(commands)
      .noneMatch(command -> command.contains(antigravityDesktop.toString()))
      .noneMatch(command -> command.contains(agent.toString()))
      .anyMatch(command -> command.contains(codex.toString()))
      .anyMatch(command -> command.contains(copilot.toString()))
      .anyMatch(command -> command.contains(cursorAgent.toString()))
      .anyMatch(command -> command.contains(agy.toString()));
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void should_ignore_nonzero_and_throwing_agent_probes() throws IOException {
    var claude = createExecutable("agents/claude");
    createExecutable("agents/codex");
    var locator = newLocator(false, Map.of("PATH", claude.getParent().toString()), commandReturning((command, stdout) -> {
      if (command.toCommandLine().contains(claude.toString())) {
        return 1;
      }
      throw new CommandException(command, "Probe failed", null);
    }));

    assertThat(locator.discover()).isEmpty();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void should_only_probe_the_first_executable_for_each_command_name() throws IOException {
    var shadowingClaude = createExecutable("first/claude");
    var hiddenClaude = createExecutable("second/claude");
    var commands = new ArrayList<String>();
    var path = shadowingClaude.getParent() + File.pathSeparator + hiddenClaude.getParent();
    var locator = newLocator(false, Map.of("PATH", path), commandReturning((command, stdout) -> {
      commands.add(command.toCommandLine());
      if (command.toCommandLine().contains(hiddenClaude.toString())) {
        stdout.consumeLine("Claude Code 2.1.0");
      } else {
        stdout.consumeLine("Unrelated command");
      }
      return 0;
    }));

    assertThat(locator.discover()).isEmpty();
    assertThat(commands)
      .anyMatch(command -> command.contains(shadowingClaude.toString()))
      .noneMatch(command -> command.contains(hiddenClaude.toString()));
  }

  @Test
  void should_discover_agent_cli_from_local_bin() throws IOException {
    createExecutable(".local/bin/codex");
    var locator = newLocator(false, Map.of(), commandReturning((command, stdout) -> {
      stdout.consumeLine("codex-cli 0.87.0");
      return 0;
    }));

    assertThat(locator.discover()).containsExactly(AiAgent.CODEX);
  }

  @Test
  void should_not_search_local_bin_on_windows() throws IOException {
    createExecutable(".local/bin/codex.cmd");
    var locator = newLocator(true, Map.of(), commandReturning((command, stdout) -> {
      throw new AssertionError("Windows discovery should not probe ~/.local/bin");
    }));

    assertThat(locator.discover()).isEmpty();
  }

  @Test
  void should_discover_windows_agent_cli_executable_suffixes() throws IOException {
    var copilot = createExecutable("agents/copilot.cmd");
    var locator = newLocator(true, Map.of("Path", copilot.getParent().toString()), commandReturning((command, stdout) -> {
      stdout.consumeLine("GitHub Copilot CLI 0.0.350");
      return 0;
    }));

    assertThat(locator.discover()).containsExactly(AiAgent.GITHUB_COPILOT_CLI);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void should_reuse_cached_discovery_on_repeated_calls() throws IOException {
    var claude = createExecutable("agents/claude");
    var commands = new ArrayList<String>();
    var locator = newLocator(false, Map.of("PATH", claude.getParent().toString()), commandReturning((command, stdout) -> {
      commands.add(command.toCommandLine());
      stdout.consumeLine("Claude Code 2.1.0");
      return 0;
    }));

    assertThat(locator.discover()).containsExactly(AiAgent.CLAUDE_CODE);
    assertThat(locator.discover()).containsExactly(AiAgent.CLAUDE_CODE);
    assertThat(commands).hasSize(1);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void should_discover_agent_cli_using_mac_os_path_helper() throws IOException {
    var claude = createExecutable("homebrew/bin/claude");
    var pathHelper = createPathHelper();
    var executor = commandReturning((command, stdout) -> {
      if (command.toCommandLine().contains("path_helper")) {
        stdout.consumeLine("PATH=\"" + claude.getParent() + "\"; export PATH;");
      } else {
        assertThat(command.getEnvironmentVariables()).containsEntry("PATH", claude.getParent().toString());
        stdout.consumeLine("Claude Code 2.1.0");
      }
      return 0;
    });
    var system2 = mock(System2.class);
    when(system2.isOsMac()).thenReturn(true);
    var locator = newLocator(system2, Map.of("PATH", "/usr/bin"), executor, pathHelper);

    assertThat(locator.discover()).containsExactly(AiAgent.CLAUDE_CODE);
  }

  private AgentCliLocator newLocator(boolean windows, Map<String, String> environment, CommandExecutor executor) {
    var system2 = mock(System2.class);
    when(system2.isOsWindows()).thenReturn(windows);
    return newLocator(system2, environment, executor, OsSearchPath.MAC_OS_PATH_HELPER);
  }

  private AgentCliLocator newLocator(System2 system2, Map<String, String> environment, CommandExecutor executor,
    Path pathHelper) {
    return new AgentCliLocator(new OsExecutableSearch(system2, executor, environment, pathHelper), tempDir);
  }

  private Path createPathHelper() throws IOException {
    var pathHelper = tempDir.resolve("path_helper");
    Files.createFile(pathHelper);
    return pathHelper;
  }

  private Path createExecutable(String relativePath) throws IOException {
    var executable = tempDir.resolve(relativePath);
    Files.createDirectories(executable.getParent());
    Files.createFile(executable);
    assertThat(executable.toFile().setExecutable(true)).isTrue();
    return executable;
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
