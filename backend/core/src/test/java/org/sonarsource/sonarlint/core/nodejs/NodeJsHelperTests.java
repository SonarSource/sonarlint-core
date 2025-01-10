/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.nodejs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.stubbing.Answer;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeJsHelperTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  private static final Path DUMMY_FILE_HELPER_LOCATION = Paths.get("");

  private static final Path FAKE_NODE_PATH = Paths.get("foo/node");

  private final System2 system2 = mock(System2.class);

  private CommandExecutor commandExecutor;

  private final Map<Predicate<Command>, BiFunction<StreamConsumer, StreamConsumer, Integer>> registeredCommandAnswers = new LinkedHashMap<>();

  @BeforeEach
  void prepare() {
    commandExecutor = mock(CommandExecutor.class);
    when(commandExecutor.execute(any(), any(), any(), anyLong())).thenAnswer((Answer<Integer>) invocation -> {
      var c = invocation.getArgument(0, Command.class);
      for (Entry<Predicate<Command>, BiFunction<StreamConsumer, StreamConsumer, Integer>> answer : registeredCommandAnswers.entrySet()) {
        if (answer.getKey().test(c)) {
          var stdOut = invocation.getArgument(1, StreamConsumer.class);
          var stdErr = invocation.getArgument(2, StreamConsumer.class);
          return answer.getValue().apply(stdOut, stdErr);
        }
      }
      return fail("No answers registered for command: " + c.toString());
    });
  }

  @Test
  void usePropertyWhenProvidedToResolveNodePath() {

    registerNodeVersionAnswer("v10.5.4");

    var underTest = new NodeJsHelper(system2, DUMMY_FILE_HELPER_LOCATION, commandExecutor);
    var result = underTest.detect(FAKE_NODE_PATH);

    assertThat(logTester.logs()).containsExactly(
      "Node.js path provided by configuration: " + FAKE_NODE_PATH,
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH + " -v'...",
      "Command '" + FAKE_NODE_PATH + " -v' exited with 0\nstdout: v10.5.4",
      "Detected node version: 10.5.4");
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(result.getVersion()).isEqualTo(Version.create("10.5.4"));
  }

  @Test
  void supportNightlyBuilds() {

    registerNodeVersionAnswer("v15.0.0-nightly20200921039c274dde");

    var underTest = new NodeJsHelper(system2, DUMMY_FILE_HELPER_LOCATION, commandExecutor);
    var result = underTest.detect(FAKE_NODE_PATH);

    assertThat(logTester.logs()).containsExactly(
      "Node.js path provided by configuration: " + FAKE_NODE_PATH,
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH + " -v'...",
      "Command '" + FAKE_NODE_PATH + " -v' exited with 0\nstdout: v15.0.0-nightly20200921039c274dde",
      "Detected node version: 15.0.0-nightly20200921039c274dde");
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(result.getVersion()).isEqualTo(Version.create("15.0.0-nightly20200921039c274dde"));
  }

  @Test
  void ignoreCommandExecutionError() {
    registeredCommandAnswers.put(c -> true, (stdOut, stdErr) -> {
      stdErr.consumeLine("error");
      return -1;
    });

    var underTest = new NodeJsHelper(system2, DUMMY_FILE_HELPER_LOCATION, commandExecutor);
    var result = underTest.detect(FAKE_NODE_PATH);

    assertThat(logTester.logs()).containsExactly(
      "Node.js path provided by configuration: " + FAKE_NODE_PATH,
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH + " -v'...",
      "Command '" + FAKE_NODE_PATH + " -v' exited with -1\nstderr: error",
      "Unable to query node version");
    assertThat(result).isNull();
  }

  @Test
  void handleErrorDuringVersionCheck() {
    registerNodeVersionAnswer("wrong_version");

    var underTest = new NodeJsHelper(system2, DUMMY_FILE_HELPER_LOCATION, commandExecutor);
    var result = underTest.detect(FAKE_NODE_PATH);

    assertThat(logTester.logs()).containsExactly(
      "Node.js path provided by configuration: " + FAKE_NODE_PATH,
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH + " -v'...",
      "Command '" + FAKE_NODE_PATH + " -v' exited with 0\nstdout: wrong_version",
      "Unable to parse node version: wrong_version",
      "Unable to query node version");
    assertThat(result).isNull();
  }

  @Test
  void useWhichOnLinuxToResolveNodePath() {
    registerWhichAnswer(FAKE_NODE_PATH.toString());
    registerNodeVersionAnswer("v10.5.4");

    var underTest = new NodeJsHelper(system2, DUMMY_FILE_HELPER_LOCATION, commandExecutor);
    var result = underTest.detect(null);

    assertThat(logTester.logs()).containsExactly(
      "Looking for node in the PATH",
      "Execute command '/usr/bin/which node'...",
      "Command '/usr/bin/which node' exited with 0\nstdout: " + FAKE_NODE_PATH,
      "Found node at " + FAKE_NODE_PATH,
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH + " -v'...",
      "Command '" + FAKE_NODE_PATH + " -v' exited with 0\nstdout: v10.5.4",
      "Detected node version: 10.5.4");
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(result.getVersion()).isEqualTo(Version.create("10.5.4"));
  }

  @Test
  void handleErrorDuringPathCheck() {
    registeredCommandAnswers.put(c -> true, (stdOut, stdErr) -> {
      stdErr.consumeLine("error");
      return -1;
    });

    var underTest = new NodeJsHelper(system2, DUMMY_FILE_HELPER_LOCATION, commandExecutor);
    var result = underTest.detect(null);

    assertThat(logTester.logs()).containsExactly(
      "Looking for node in the PATH",
      "Execute command '/usr/bin/which node'...",
      "Command '/usr/bin/which node' exited with -1\nstderr: error",
      "Unable to locate node");
    assertThat(result).isNull();
  }

  @Test
  void handleEmptyResponseDuringPathCheck() {
    when(system2.isOsWindows()).thenReturn(true);

    registerWhereAnswer();

    var underTest = new NodeJsHelper(system2, DUMMY_FILE_HELPER_LOCATION, commandExecutor);
    var result = underTest.detect(null);

    assertThat(logTester.logs()).containsExactly(
      "Looking for node in the PATH",
      "Execute command 'C:\\Windows\\System32\\where.exe $PATH:node.exe'...",
      "Command 'C:\\Windows\\System32\\where.exe $PATH:node.exe' exited with 0",
      "Unable to locate node");
    assertThat(result).isNull();
  }

  @Test
  void useWhereOnWindowsToResolveNodePath() {
    when(system2.isOsWindows()).thenReturn(true);

    registerWhereAnswer(FAKE_NODE_PATH.toString());
    registerNodeVersionAnswer("v10.5.4");

    var underTest = new NodeJsHelper(system2, DUMMY_FILE_HELPER_LOCATION, commandExecutor);
    var result = underTest.detect(null);

    assertThat(logTester.logs()).containsExactly(
      "Looking for node in the PATH",
      "Execute command 'C:\\Windows\\System32\\where.exe $PATH:node.exe'...",
      "Command 'C:\\Windows\\System32\\where.exe $PATH:node.exe' exited with 0\nstdout: " + FAKE_NODE_PATH,
      "Found node at " + FAKE_NODE_PATH,
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH + " -v'...",
      "Command '" + FAKE_NODE_PATH + " -v' exited with 0\nstdout: v10.5.4",
      "Detected node version: 10.5.4");
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(result.getVersion()).isEqualTo(Version.create("10.5.4"));
  }

  // SLCORE-281
  @Test
  void whereOnWindowsCanReturnMultipleCandidates() {
    when(system2.isOsWindows()).thenReturn(true);

    var fake_node_path2 = Paths.get("foo2/node");

    registerWhereAnswer(FAKE_NODE_PATH.toString(), fake_node_path2.toString());
    registerNodeVersionAnswer("v10.5.4");

    var underTest = new NodeJsHelper(system2, DUMMY_FILE_HELPER_LOCATION, commandExecutor);
    var result = underTest.detect(null);

    assertThat(logTester.logs()).containsExactly(
      "Looking for node in the PATH",
      "Execute command 'C:\\Windows\\System32\\where.exe $PATH:node.exe'...",
      "Command 'C:\\Windows\\System32\\where.exe $PATH:node.exe' exited with 0\nstdout: "
        + FAKE_NODE_PATH + "\n" + fake_node_path2,
      "Found node at " + FAKE_NODE_PATH,
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH + " -v'...",
      "Command '" + FAKE_NODE_PATH + " -v' exited with 0\nstdout: v10.5.4",
      "Detected node version: 10.5.4");
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(result.getVersion()).isEqualTo(Version.create("10.5.4"));
  }

  @Test
  void usePathHelperOnMacToResolveNodePath(@TempDir Path tempDir) throws IOException {
    when(system2.isOsMac()).thenReturn(true);

    registerPathHelperAnswer("PATH=\"/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin/node\"; export PATH;");
    registerWhichAnswerIfPathIsSet(FAKE_NODE_PATH.toString(), "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin/node");
    registerNodeVersionAnswer("v10.5.4");

    // Need a true file since we are checking if file exists
    var fakePathHelper = tempDir.resolve("path_helper.sh");
    Files.createFile(fakePathHelper);
    var underTest = new NodeJsHelper(system2, fakePathHelper, commandExecutor);
    var result = underTest.detect(null);

    assertThat(logTester.logs()).containsExactly(
      "Looking for node in the PATH",
      "Execute command '" + fakePathHelper + " -s'...",
      "Command '" + fakePathHelper + " -s' exited with 0\nstdout: PATH=\"/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin/node\"; export PATH;",
      "Execute command '/usr/bin/which node'...",
      "Command '/usr/bin/which node' exited with 0\nstdout: " + FAKE_NODE_PATH,
      "Found node at " + FAKE_NODE_PATH,
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH + " -v'...",
      "Command '" + FAKE_NODE_PATH + " -v' exited with 0\nstdout: v10.5.4",
      "Detected node version: 10.5.4");
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(result.getVersion()).isEqualTo(Version.create("10.5.4"));
  }

  @Test
  void ignoreWrongPathHelperOutputOnMac(@TempDir Path tempDir) throws IOException {
    when(system2.isOsMac()).thenReturn(true);
    registerPathHelperAnswer("wrong \n output");
    registerWhichAnswerIfPathIsSet(FAKE_NODE_PATH.toString(), System.getenv("PATH"));
    registerNodeVersionAnswer("v10.5.4");

    // Need a true file since we are checking if file exists
    var fakePathHelper = tempDir.resolve("path_helper.sh");
    Files.createFile(fakePathHelper);
    var underTest = new NodeJsHelper(system2, fakePathHelper, commandExecutor);
    var result = underTest.detect(null);

    assertThat(logTester.logs()).containsExactly(
      "Looking for node in the PATH",
      "Execute command '" + fakePathHelper + " -s'...",
      "Command '" + fakePathHelper + " -s' exited with 0\nstdout: wrong \n output",
      "Execute command '/usr/bin/which node'...",
      "Command '/usr/bin/which node' exited with 0\nstdout: " + FAKE_NODE_PATH,
      "Found node at " + FAKE_NODE_PATH,
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH + " -v'...",
      "Command '" + FAKE_NODE_PATH + " -v' exited with 0\nstdout: v10.5.4",
      "Detected node version: 10.5.4");
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(result.getVersion()).isEqualTo(Version.create("10.5.4"));
  }

  @Test
  void ignorePathHelperOnMacIfMissing() {
    when(system2.isOsMac()).thenReturn(true);

    registerPathHelperAnswer("wrong \n output");
    registerWhichAnswerIfPathIsSet(FAKE_NODE_PATH.toString(), System.getenv("PATH"));
    registerNodeVersionAnswer("v10.5.4");

    var underTest = new NodeJsHelper(system2, Paths.get("not_exists"), commandExecutor);
    var result = underTest.detect(null);

    assertThat(logTester.logs()).containsExactly(
      "Looking for node in the PATH",
      "Execute command '/usr/bin/which node'...",
      "Command '/usr/bin/which node' exited with 0\nstdout: " + FAKE_NODE_PATH,
      "Found node at " + FAKE_NODE_PATH,
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH + " -v'...",
      "Command '" + FAKE_NODE_PATH + " -v' exited with 0\nstdout: v10.5.4",
      "Detected node version: 10.5.4");
    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(result.getVersion()).isEqualTo(Version.create("10.5.4"));
  }

  @Test
  void logWhenUnableToGetNodeVersion() {
    var underTest = new NodeJsHelper();
    var result = underTest.detect(Paths.get("not_node"));

    assertThat(logTester.logs()).anyMatch(s -> s.startsWith("Unable to execute the command"));
    assertThat(result).isNull();
  }

  private void registerNodeVersionAnswer(String version) {
    registeredCommandAnswers.put(c -> c.toString().endsWith(FAKE_NODE_PATH + " -v"), (stdOut, stdErr) -> {
      stdOut.consumeLine(version);
      return 0;
    });
  }

  private void registerWhichAnswer(String whichOutput) {
    registeredCommandAnswers.put(c -> c.toString().endsWith("which node"), (stdOut, stdErr) -> {
      stdOut.consumeLine(whichOutput);
      return 0;
    });
  }

  private void registerWhichAnswerIfPathIsSet(String whichOutput, @Nullable String expectedPath) {
    registeredCommandAnswers.put(c -> c.toString().endsWith("which node") && Objects.equals(expectedPath, c.getEnvironmentVariables().get("PATH")), (stdOut, stdErr) -> {
      stdOut.consumeLine(whichOutput);
      return 0;
    });
  }

  private void registerWhereAnswer(String... whereOutput) {
    registeredCommandAnswers.put(c -> c.toString().endsWith("C:\\Windows\\System32\\where.exe $PATH:node.exe"), (stdOut, stdErr) -> {
      Stream.of(whereOutput).forEach(stdOut::consumeLine);
      return 0;
    });
  }

  private void registerPathHelperAnswer(String output) {
    registeredCommandAnswers.put(c -> c.toString().endsWith("path_helper.sh -s"), (stdOut, stdErr) -> {
      stdOut.consumeLine(output);
      return 0;
    });
  }

}
