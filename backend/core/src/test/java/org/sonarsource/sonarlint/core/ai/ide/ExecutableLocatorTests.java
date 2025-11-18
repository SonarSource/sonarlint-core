/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import java.nio.file.Files;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.nodejs.InstalledNodeJs;
import org.sonarsource.sonarlint.core.nodejs.NodeJsHelper;
import org.sonar.api.utils.command.StreamConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutableLocatorTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void it_should_find_nodejs_when_available() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(nodeJsHelper.autoDetect()).thenReturn(new InstalledNodeJs(Paths.get("/usr/bin/node"), Version.create("18.0.0")));

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper);
    var result = locator.detectBestExecutable();

    assertThat(result)
      .isPresent()
      .contains(HookScriptType.NODEJS);
  }

  @Test
  void it_should_find_python_when_nodejs_not_available() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(nodeJsHelper.autoDetect()).thenReturn(null);
    when(system2.isOsWindows()).thenReturn(false);
    when(commandExecutor.execute(any(Command.class), any(), any(), anyLong())).thenReturn(0);

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper) {
      @Override
      String runSimpleCommand(@NotNull Command command) {
        if (command.toCommandLine().contains("python3")) {
          return "/usr/bin/python3";
        }
        return null;
      }
    };

    var result = locator.detectBestExecutable();

    assertThat(result)
      .isPresent()
      .contains(HookScriptType.PYTHON);
  }

  @Test
  @EnabledOnOs(value = OS.LINUX)
  void it_should_fallback_to_bash_on_unix_when_no_nodejs_or_python() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(nodeJsHelper.autoDetect()).thenReturn(null);
    when(system2.isOsWindows()).thenReturn(false);

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper) {
      @Override
      String runSimpleCommand(@NotNull Command command) {
        return null; // Python not found
      }
    };

    var result = locator.detectBestExecutable();

    assertThat(result)
      .isPresent()
      .contains(HookScriptType.BASH);
  }

  @Test
  @EnabledOnOs(value = OS.WINDOWS)
  void it_should_return_empty_on_windows_when_only_bash_available() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(nodeJsHelper.autoDetect()).thenReturn(null);
    when(system2.isOsWindows()).thenReturn(true);

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper) {
      @Override
      String runSimpleCommand(@NotNull Command command) {
        return null; // Neither Python nor Bash found on Windows
      }
    };

    var result = locator.detectBestExecutable();

    assertThat(result).isEmpty();
  }

  @Test
  void it_should_cache_detection_result() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(nodeJsHelper.autoDetect()).thenReturn(new InstalledNodeJs(Paths.get("/usr/bin/node"), Version.create("18.0.0")));

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper);
    
    // Call twice
    var result1 = locator.detectBestExecutable();
    var result2 = locator.detectBestExecutable();

    assertThat(result1).isPresent();
    assertThat(result2).isPresent();
    assertThat(result1).contains(HookScriptType.NODEJS);
    assertThat(result2).contains(HookScriptType.NODEJS);

    // Verify autoDetect was only called once due to caching
    verify(nodeJsHelper, times(1)).autoDetect();
  }

  @Test
  void it_should_fallback_to_python_when_python3_not_found() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(nodeJsHelper.autoDetect()).thenReturn(null);
    when(system2.isOsWindows()).thenReturn(false);

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper) {
      @Override
      String runSimpleCommand(@NotNull Command command) {
        if (command.toCommandLine().contains("python3")) {
          return null; // python3 not found
        }
        if (command.toCommandLine().contains("python")) {
          return "/usr/bin/python";
        }
        return null;
      }
    };

    var result = locator.detectBestExecutable();

    assertThat(result)
      .isPresent()
      .contains(HookScriptType.PYTHON);
  }

  @Test
  @EnabledOnOs(value = OS.WINDOWS)
  void it_should_detect_bash_on_windows_when_available() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(nodeJsHelper.autoDetect()).thenReturn(null);
    when(system2.isOsWindows()).thenReturn(true);

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper) {
      @Override
      String runSimpleCommand(@NotNull Command command) {
        if (command.toCommandLine().contains("bash.exe")) {
          return "C:\\Program Files\\Git\\bin\\bash.exe";
        }
        return null;
      }
    };

    var result = locator.detectBestExecutable();

    assertThat(result)
      .isPresent()
      .contains(HookScriptType.BASH);
  }

  @Test
  void it_should_handle_nodejs_detection_error_gracefully() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(nodeJsHelper.autoDetect()).thenThrow(new RuntimeException("Node.js detection failed"));
    when(system2.isOsWindows()).thenReturn(false);

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper) {
      @Override
      String runSimpleCommand(@NotNull Command command) {
        if (command.toCommandLine().contains("python3")) {
          return "/usr/bin/python3";
        }
        return null;
      }
    };

    var result = locator.detectBestExecutable();

    // Should fall back to Python
    assertThat(result)
      .isPresent()
      .contains(HookScriptType.PYTHON);
  }

  @Test
  void runSimpleCommand_should_return_first_line_of_stdout_on_success() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(commandExecutor.execute(any(Command.class), any(), any(), anyLong())).thenAnswer(invocation -> {
      var stdOutConsumer = (StreamConsumer) invocation.getArgument(1);
      stdOutConsumer.consumeLine("/usr/bin/python3");
      stdOutConsumer.consumeLine("additional output");
      return 0;
    });

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper);
    var command = Command.create("which").addArgument("python3");
    var result = locator.runSimpleCommand(command);

    assertThat(result).isEqualTo("/usr/bin/python3");
  }

  @Test
  void runSimpleCommand_should_return_null_on_non_zero_exit_code() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(commandExecutor.execute(any(Command.class), any(), any(), anyLong())).thenAnswer(invocation -> {
      var stdOutConsumer = (StreamConsumer) invocation.getArgument(1);
      stdOutConsumer.consumeLine("some output");
      return 1; // Non-zero exit code
    });

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper);
    var command = Command.create("which").addArgument("nonexistent");
    var result = locator.runSimpleCommand(command);

    assertThat(result).isNull();
  }

  @Test
  void runSimpleCommand_should_return_null_on_empty_stdout() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(commandExecutor.execute(any(Command.class), any(), any(), anyLong())).thenReturn(0);

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper);
    var command = Command.create("echo").addArgument("");
    var result = locator.runSimpleCommand(command);

    assertThat(result).isNull();
  }

  @Test
  void runSimpleCommand_should_return_null_on_command_exception() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(commandExecutor.execute(any(Command.class), any(), any(), anyLong()))
      .thenThrow(new org.sonar.api.utils.command.CommandException(Command.create("test"), "Command failed", null));

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper);
    var command = Command.create("invalid_command");
    var result = locator.runSimpleCommand(command);

    assertThat(result).isNull();
  }

  @Test
  void runSimpleCommand_should_log_stdout_and_stderr() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(commandExecutor.execute(any(Command.class), any(), any(), anyLong())).thenAnswer(invocation -> {
      var stdOutConsumer = (StreamConsumer) invocation.getArgument(1);
      var stdErrConsumer = (StreamConsumer) invocation.getArgument(2);
      stdOutConsumer.consumeLine("/usr/bin/test");
      stdErrConsumer.consumeLine("warning message");
      return 0;
    });

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper);
    var command = Command.create("test_command");
    var result = locator.runSimpleCommand(command);

    assertThat(result).isEqualTo("/usr/bin/test");
    assertThat(logTester.logs()).anyMatch(log -> log.contains("stdout: /usr/bin/test"));
    assertThat(logTester.logs()).anyMatch(log -> log.contains("stderr: warning message"));
  }

  @Test
  void it_should_return_empty_when_no_executable_found() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(nodeJsHelper.autoDetect()).thenReturn(null);
    when(system2.isOsWindows()).thenReturn(true);

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper) {
      @Override
      String runSimpleCommand(@NotNull Command command) {
        // Simulate no executable found for any command
        return null;
      }
    };

    var result = locator.detectBestExecutable();

    assertThat(result).isEmpty();
    assertThat(logTester.logs()).anyMatch(log -> 
      log.contains("No suitable executable found") || 
      log.contains("not found") ||
      log.contains("not available"));
  }

  @Test
  void it_should_not_set_path_env_when_path_helper_does_not_exist() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/nonexistent/path_helper");

    when(system2.isOsMac()).thenReturn(true);

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper);

    var testCommand = Command.create("test");
    var commandLineBefore = testCommand.toCommandLine();
    locator.computePathEnvForMacOs(testCommand);
    var commandLineAfter = testCommand.toCommandLine();

    // PATH should not be set
    assertThat(commandLineAfter).isEqualTo(commandLineBefore);
  }

  @Test
  void it_should_not_set_path_env_when_not_on_macos(@TempDir File tempDir) {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = new File(tempDir, "path_helper").toPath();

    when(system2.isOsMac()).thenReturn(false);

    try (var filesMock = mockStatic(Files.class)) {
      filesMock.when(() -> Files.exists(pathHelper)).thenReturn(true);

      var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper);

      var testCommand = Command.create("test");
      var commandLineBefore = testCommand.toCommandLine();
      locator.computePathEnvForMacOs(testCommand);
      var commandLineAfter = testCommand.toCommandLine();

      // PATH should not be set when not on macOS
      assertThat(commandLineAfter).isEqualTo(commandLineBefore);
    }
  }

}

