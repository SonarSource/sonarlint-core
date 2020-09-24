/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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
package org.sonarsource.sonarlint.core;

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
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.client.api.common.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeJsHelperTests {

  private static final Path FAKE_NODE_PATH = Paths.get("foo/node");

  @RegisterExtension
  LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private System2 system2 = mock(System2.class);

  private CommandExecutor commandExecutor;

  private Map<Predicate<Command>, BiFunction<StreamConsumer, StreamConsumer, Integer>> registeredCommandAnswers = new LinkedHashMap<>();

  @BeforeEach
  public void prepare() {
    commandExecutor = mock(CommandExecutor.class);
    when(commandExecutor.execute(any(), any(), any(), anyLong())).thenAnswer(new Answer<Integer>() {

      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        Command c = invocation.getArgument(0, Command.class);
        for (Entry<Predicate<Command>, BiFunction<StreamConsumer, StreamConsumer, Integer>> answer : registeredCommandAnswers.entrySet()) {
          if (answer.getKey().test(c)) {
            StreamConsumer stdOut = invocation.getArgument(1, StreamConsumer.class);
            StreamConsumer stdErr = invocation.getArgument(2, StreamConsumer.class);
            return answer.getValue().apply(stdOut, stdErr);
          }
        }
        return fail("No answers registered for command: " + c.toString());
      }
    });
  }

  @Test
  void usePropertyWhenProvidedToResolveNodePath() throws IOException {

    registerNodeVersionAnswer("v10.5.4");

    NodeJsHelper underTest = new NodeJsHelper(system2, null, commandExecutor);
    underTest.detect(FAKE_NODE_PATH);

    assertThat(logTester.logs()).containsExactly(
      "Node.js path provided by configuration: " + FAKE_NODE_PATH.toString(),
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH.toString() + " -v'...",
      "Command '" + FAKE_NODE_PATH.toString() + " -v' exited with 0\nstdout: v10.5.4",
      "Detected node version: 10.5.4");
    assertThat(underTest.getNodeJsPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(underTest.getNodeJsVersion()).isEqualTo(Version.create("10.5.4"));
  }

  @Test
  void supportNightlyBuilds() throws IOException {

    registerNodeVersionAnswer("v15.0.0-nightly20200921039c274dde");

    NodeJsHelper underTest = new NodeJsHelper(system2, null, commandExecutor);
    underTest.detect(FAKE_NODE_PATH);

    assertThat(logTester.logs()).containsExactly(
      "Node.js path provided by configuration: " + FAKE_NODE_PATH.toString(),
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH.toString() + " -v'...",
      "Command '" + FAKE_NODE_PATH.toString() + " -v' exited with 0\nstdout: v15.0.0-nightly20200921039c274dde",
      "Detected node version: 15.0.0-nightly20200921039c274dde");
    assertThat(underTest.getNodeJsPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(underTest.getNodeJsVersion()).isEqualTo(Version.create("15.0.0-nightly20200921039c274dde"));
  }

  @Test
  void ignoreCommandExecutionError() throws IOException {
    registeredCommandAnswers.put(c -> true, (stdOut, stdErr) -> {
      stdErr.consumeLine("error");
      return -1;
    });

    NodeJsHelper underTest = new NodeJsHelper(system2, null, commandExecutor);
    underTest.detect(FAKE_NODE_PATH);

    assertThat(logTester.logs()).containsExactly(
      "Node.js path provided by configuration: " + FAKE_NODE_PATH.toString(),
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH.toString() + " -v'...",
      "Command '" + FAKE_NODE_PATH.toString() + " -v' exited with -1\nstderr: error",
      "Unable to query node version");
    assertThat(underTest.getNodeJsPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(underTest.getNodeJsVersion()).isNull();
  }

  @Test
  void handleErrorDuringVersionCheck() throws IOException {

    registerNodeVersionAnswer("wrong_version");

    NodeJsHelper underTest = new NodeJsHelper(system2, null, commandExecutor);
    underTest.detect(FAKE_NODE_PATH);

    assertThat(logTester.logs()).containsExactly(
      "Node.js path provided by configuration: " + FAKE_NODE_PATH.toString(),
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH.toString() + " -v'...",
      "Command '" + FAKE_NODE_PATH.toString() + " -v' exited with 0\nstdout: wrong_version",
      "Unable to parse node version: wrong_version",
      "Unable to query node version");
    assertThat(underTest.getNodeJsPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(underTest.getNodeJsVersion()).isNull();
  }

  @Test
  void useWhichOnLinuxToResolveNodePath() throws IOException {
    registerWhichAnswer(FAKE_NODE_PATH.toString());
    registerNodeVersionAnswer("v10.5.4");

    NodeJsHelper underTest = new NodeJsHelper(system2, null, commandExecutor);
    underTest.detect(null);

    assertThat(logTester.logs()).containsExactly(
      "Looking for node in the PATH",
      "Execute command 'which node'...",
      "Command 'which node' exited with 0\nstdout: " + FAKE_NODE_PATH.toString(),
      "Found node at " + FAKE_NODE_PATH.toString(),
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH.toString() + " -v'...",
      "Command '" + FAKE_NODE_PATH.toString() + " -v' exited with 0\nstdout: v10.5.4",
      "Detected node version: 10.5.4");
    assertThat(underTest.getNodeJsPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(underTest.getNodeJsVersion()).isEqualTo(Version.create("10.5.4"));
  }

  @Test
  void handleErrorDuringPathCheck() throws IOException {
    registeredCommandAnswers.put(c -> true, (stdOut, stdErr) -> {
      stdErr.consumeLine("error");
      return -1;
    });

    NodeJsHelper underTest = new NodeJsHelper(system2, null, commandExecutor);
    underTest.detect(null);

    assertThat(logTester.logs()).containsExactly(
      "Looking for node in the PATH",
      "Execute command 'which node'...",
      "Command 'which node' exited with -1\nstderr: error",
      "Unable to locate node");
    assertThat(underTest.getNodeJsPath()).isNull();
    assertThat(underTest.getNodeJsVersion()).isNull();
  }

  @Test
  void useWhereOnWindowsToResolveNodePath() throws IOException {
    when(system2.isOsWindows()).thenReturn(true);

    registerWhereAnswer(FAKE_NODE_PATH.toString());
    registerNodeVersionAnswer("v10.5.4");

    NodeJsHelper underTest = new NodeJsHelper(system2, null, commandExecutor);
    underTest.detect(null);

    assertThat(logTester.logs()).containsExactly(
      "Looking for node in the PATH",
      "Execute command 'where node'...",
      "Command 'where node' exited with 0\nstdout: " + FAKE_NODE_PATH.toString(),
      "Found node at " + FAKE_NODE_PATH.toString(),
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH.toString() + " -v'...",
      "Command '" + FAKE_NODE_PATH.toString() + " -v' exited with 0\nstdout: v10.5.4",
      "Detected node version: 10.5.4");
    assertThat(underTest.getNodeJsPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(underTest.getNodeJsVersion()).isEqualTo(Version.create("10.5.4"));
  }

  @Test
  void usePathHelperOnMacToResolveNodePath(@TempDir Path tempDir) throws IOException {
    when(system2.isOsMac()).thenReturn(true);

    registerPathHelperAnswer("PATH=\"/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin/node\"; export PATH;");
    registerWhichAnswerIfPathIsSet(FAKE_NODE_PATH.toString(), "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin/node");
    registerNodeVersionAnswer("v10.5.4");

    // Need a true file since we are checking if file exists
    Path fakePathHelper = tempDir.resolve("path_helper.sh");
    Files.createFile(fakePathHelper);
    NodeJsHelper underTest = new NodeJsHelper(system2, fakePathHelper, commandExecutor);
    underTest.detect(null);

    assertThat(logTester.logs()).containsExactly(
      "Looking for node in the PATH",
      "Execute command '" + fakePathHelper.toString() + " -s'...",
      "Command '" + fakePathHelper.toString() + " -s' exited with 0\nstdout: PATH=\"/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin/node\"; export PATH;",
      "Execute command 'which node'...",
      "Command 'which node' exited with 0\nstdout: " + FAKE_NODE_PATH.toString(),
      "Found node at " + FAKE_NODE_PATH.toString(),
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH.toString() + " -v'...",
      "Command '" + FAKE_NODE_PATH.toString() + " -v' exited with 0\nstdout: v10.5.4",
      "Detected node version: 10.5.4");
    assertThat(underTest.getNodeJsPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(underTest.getNodeJsVersion()).isEqualTo(Version.create("10.5.4"));
  }

  @Test
  void ignoreWrongPathHelperOutputOnMac(@TempDir Path tempDir) throws IOException {
    when(system2.isOsMac()).thenReturn(true);
    registerPathHelperAnswer("wrong \n output");
    registerWhichAnswerIfPathIsSet(FAKE_NODE_PATH.toString(), System.getenv("PATH"));
    registerNodeVersionAnswer("v10.5.4");

    // Need a true file since we are checking if file exists
    Path fakePathHelper = tempDir.resolve("path_helper.sh");
    Files.createFile(fakePathHelper);
    NodeJsHelper underTest = new NodeJsHelper(system2, fakePathHelper, commandExecutor);
    underTest.detect(null);

    assertThat(logTester.logs()).containsExactly(
      "Looking for node in the PATH",
      "Execute command '" + fakePathHelper.toString() + " -s'...",
      "Command '" + fakePathHelper.toString() + " -s' exited with 0\nstdout: wrong \n output",
      "Execute command 'which node'...",
      "Command 'which node' exited with 0\nstdout: " + FAKE_NODE_PATH.toString(),
      "Found node at " + FAKE_NODE_PATH.toString(),
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH.toString() + " -v'...",
      "Command '" + FAKE_NODE_PATH.toString() + " -v' exited with 0\nstdout: v10.5.4",
      "Detected node version: 10.5.4");
    assertThat(underTest.getNodeJsPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(underTest.getNodeJsVersion()).isEqualTo(Version.create("10.5.4"));
  }

  @Test
  void ignorePathHelperOnMacIfMissing() throws IOException {
    when(system2.isOsMac()).thenReturn(true);

    registerPathHelperAnswer("wrong \n output");
    registerWhichAnswerIfPathIsSet(FAKE_NODE_PATH.toString(), System.getenv("PATH"));
    registerNodeVersionAnswer("v10.5.4");

    NodeJsHelper underTest = new NodeJsHelper(system2, Paths.get("not_exists"), commandExecutor);
    underTest.detect(null);

    assertThat(logTester.logs()).containsExactly(
      "Looking for node in the PATH",
      "Execute command 'which node'...",
      "Command 'which node' exited with 0\nstdout: " + FAKE_NODE_PATH.toString(),
      "Found node at " + FAKE_NODE_PATH.toString(),
      "Checking node version...",
      "Execute command '" + FAKE_NODE_PATH.toString() + " -v'...",
      "Command '" + FAKE_NODE_PATH.toString() + " -v' exited with 0\nstdout: v10.5.4",
      "Detected node version: 10.5.4");
    assertThat(underTest.getNodeJsPath()).isEqualTo(FAKE_NODE_PATH);
    assertThat(underTest.getNodeJsVersion()).isEqualTo(Version.create("10.5.4"));
  }

  @Test
  void logWhenUnableToGetNodeVersion() {
    NodeJsHelper underTest = new NodeJsHelper();
    underTest.detect(Paths.get("not_node"));

    assertThat(logTester.logs(LoggerLevel.DEBUG)).anyMatch(s -> s.startsWith("Unable to execute the command"));
    assertThat(underTest.getNodeJsVersion()).isNull();
  }

  private void registerNodeVersionAnswer(String version) {
    registeredCommandAnswers.put(c -> c.toString().endsWith("node -v"), (stdOut, stdErr) -> {
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

  private void registerWhereAnswer(String whereOutput) {
    registeredCommandAnswers.put(c -> c.toString().endsWith("where node"), (stdOut, stdErr) -> {
      stdOut.consumeLine(whereOutput);
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
