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

import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.nodejs.InstalledNodeJs;
import org.sonarsource.sonarlint.core.nodejs.NodeJsHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutableLocatorTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void it_should_prefer_nodejs_when_available() {
    var system2 = mock(System2.class);
    var commandExecutor = mock(CommandExecutor.class);
    var nodeJsHelper = mock(NodeJsHelper.class);
    var pathHelper = Paths.get("/usr/libexec/path_helper");

    when(nodeJsHelper.autoDetect()).thenReturn(new InstalledNodeJs(Paths.get("/usr/bin/node"), Version.create("18.0.0")));

    var locator = new ExecutableLocator(system2, pathHelper, commandExecutor, nodeJsHelper);
    var result = locator.detectBestExecutable();

    assertThat(result)
      .isPresent()
      .contains(ExecutableType.NODEJS);
  }

  @Test
  void it_should_prefer_python_when_nodejs_not_available() {
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
      .contains(ExecutableType.PYTHON);
  }

  @Test
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
      .contains(ExecutableType.BASH);
  }

  @Test
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
    assertThat(result1).contains(ExecutableType.NODEJS);
    assertThat(result2).contains(ExecutableType.NODEJS);

    // Verify autoDetect was only called once due to caching
    verify(nodeJsHelper, times(1)).autoDetect();
  }

  @Test
  void it_should_detect_python3_before_python() {
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
          return "/usr/bin/python3";
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
      .contains(ExecutableType.PYTHON);
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
      .contains(ExecutableType.PYTHON);
  }

  @Test
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
      .contains(ExecutableType.BASH);
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
      .contains(ExecutableType.PYTHON);
  }
}

