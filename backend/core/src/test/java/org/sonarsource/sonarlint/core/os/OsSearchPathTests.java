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
package org.sonarsource.sonarlint.core.os;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OsSearchPathTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void should_parse_path_helper_output() {
    assertThat(OsSearchPath.parsePathHelperOutput("PATH=\"/usr/bin:/bin\"; export PATH;"))
      .contains("/usr/bin:/bin");
    assertThat(OsSearchPath.parsePathHelperOutput("wrong")).isEmpty();
  }

  @Test
  void should_split_path_entries() {
    assertThat(OsSearchPath.splitEntries("/usr/bin:/bin", ":")).containsExactly("/usr/bin", "/bin");
    assertThat(OsSearchPath.splitEntries("C:\\Windows;C:\\Tools", ";")).containsExactly("C:\\Windows", "C:\\Tools");
  }

  @Test
  void should_read_windows_environment_variable_case_insensitively() {
    var environment = Map.of("Path", "C:\\Tools");

    assertThat(OsSearchPath.environmentVariableIgnoreCase(environment, "PATH")).isEqualTo("C:\\Tools");
    assertThat(environment.get("PATH")).isNull();
  }

  @Test
  void should_resolve_macos_path_from_path_helper(@TempDir Path tempDir) throws Exception {
    var pathHelper = tempDir.resolve("path_helper");
    Files.createFile(pathHelper);
    var system2 = mock(System2.class);
    when(system2.isOsMac()).thenReturn(true);
    var executor = mock(CommandExecutor.class);
    when(executor.execute(any(Command.class), any(), any(), anyLong())).thenAnswer(invocation -> {
      StreamConsumer stdout = invocation.getArgument(1);
      stdout.consumeLine("PATH=\"/opt/homebrew/bin\"; export PATH;");
      return 0;
    });

    var path = OsSearchPath.resolve(system2, Map.of("PATH", "/usr/bin"), pathHelper, executor, 1_000);

    assertThat(path).isEqualTo("/opt/homebrew/bin");
  }

  @Test
  void should_skip_missing_path_helper_and_use_environment(@TempDir Path tempDir) {
    var system2 = mock(System2.class);
    when(system2.isOsMac()).thenReturn(true);
    var executor = mock(CommandExecutor.class);

    var path = OsSearchPath.resolve(system2, Map.of("PATH", "/usr/bin"), tempDir.resolve("missing"), executor, 1_000);

    assertThat(path).isEqualTo("/usr/bin");
    verify(executor, never()).execute(any(Command.class), any(), any(), anyLong());
  }

  @Test
  void should_read_macos_path_when_not_on_the_first_line(@TempDir Path tempDir) throws Exception {
    var pathHelper = tempDir.resolve("path_helper");
    Files.createFile(pathHelper);
    var system2 = mock(System2.class);
    when(system2.isOsMac()).thenReturn(true);
    var executor = mock(CommandExecutor.class);
    when(executor.execute(any(Command.class), any(), any(), anyLong())).thenAnswer(invocation -> {
      StreamConsumer stdout = invocation.getArgument(1);
      stdout.consumeLine("MANPATH=\"/usr/share/man\"; export MANPATH;");
      stdout.consumeLine("PATH=\"/opt/homebrew/bin\"; export PATH;");
      return 0;
    });

    var path = OsSearchPath.resolve(system2, Map.of("PATH", "/usr/bin"), pathHelper, executor, 1_000);

    assertThat(path).isEqualTo("/opt/homebrew/bin");
  }

  @Test
  void should_log_when_path_helper_exits_non_zero(@TempDir Path tempDir) throws Exception {
    var pathHelper = tempDir.resolve("path_helper");
    Files.createFile(pathHelper);
    var system2 = mock(System2.class);
    when(system2.isOsMac()).thenReturn(true);
    var executor = mock(CommandExecutor.class);
    when(executor.execute(any(Command.class), any(), any(), anyLong())).thenAnswer(invocation -> {
      StreamConsumer stderr = invocation.getArgument(2);
      stderr.consumeLine("permission denied");
      return 1;
    });

    var path = OsSearchPath.resolve(system2, Map.of("PATH", "/usr/bin"), pathHelper, executor, 1_000);

    assertThat(path).isEqualTo("/usr/bin");
    assertThat(logTester.logs()).anyMatch(log -> log.contains("exited with 1") && log.contains("permission denied"));
  }
}
