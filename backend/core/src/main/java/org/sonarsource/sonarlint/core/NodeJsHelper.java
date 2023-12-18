/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandException;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;

public class NodeJsHelper {

  private static final Pattern NODEJS_VERSION_PATTERN = Pattern.compile("v?(\\d+\\.\\d+\\.\\d+(-.*)?)");
  private final System2 system2;
  private final Path pathHelperLocationOnMac;
  private final CommandExecutor commandExecutor;
  private final ClientLogOutput logOutput;

  private Path detectedNodePath;
  private Version nodeJsVersion;

  public NodeJsHelper(ClientLogOutput logOutput) {
    this(System2.INSTANCE, Paths.get("/usr/libexec/path_helper"), CommandExecutor.create(), logOutput);
  }

  // For testing
  NodeJsHelper(System2 system2, Path pathHelperLocationOnMac, CommandExecutor commandExecutor, ClientLogOutput logOutput) {
    this.system2 = system2;
    this.pathHelperLocationOnMac = pathHelperLocationOnMac;
    this.commandExecutor = commandExecutor;
    this.logOutput = logOutput;
  }

  public void detect(@Nullable Path configuredNodejsPath) {
    detectedNodePath = locateNode(configuredNodejsPath);
    if (detectedNodePath != null) {
      logOutput.log("Checking node version...", ClientLogOutput.Level.DEBUG);
      String nodeVersionStr;
      var forcedNodeVersion = System.getProperty("sonarlint.internal.nodejs.forcedVersion");
      if (forcedNodeVersion != null) {
        nodeVersionStr = forcedNodeVersion;
      } else {
        var command = Command.create(detectedNodePath.toString()).addArgument("-v");
        nodeVersionStr = runSimpleCommand(command);
      }
      if (nodeVersionStr != null) {
        var matcher = NODEJS_VERSION_PATTERN.matcher(nodeVersionStr);
        if (matcher.matches()) {
          var version = matcher.group(1);
          nodeJsVersion = Version.create(version);
          logOutput.log("Detected node version: " + nodeJsVersion, ClientLogOutput.Level.DEBUG);
        } else {
          logOutput.log("Unable to parse node version: " + nodeVersionStr, ClientLogOutput.Level.DEBUG);
        }
      }
      if (nodeJsVersion == null) {
        logOutput.log("Unable to query node version", ClientLogOutput.Level.WARN);
      }
    }
  }

  @CheckForNull
  public Path getNodeJsPath() {
    return detectedNodePath;
  }

  @CheckForNull
  public Version getNodeJsVersion() {
    return nodeJsVersion;
  }

  @CheckForNull
  private Path locateNode(@Nullable Path configuredNodejsPath) {
    if (configuredNodejsPath != null) {
      logOutput.log("Node.js path provided by configuration: " + configuredNodejsPath, ClientLogOutput.Level.DEBUG);
      return configuredNodejsPath;
    }
    logOutput.log("Looking for node in the PATH", ClientLogOutput.Level.DEBUG);

    var forcedPath = System.getProperty("sonarlint.internal.nodejs.forcedPath");
    String result;
    if (forcedPath != null) {
      result = forcedPath;
    } else if (system2.isOsWindows()) {
      result = runSimpleCommand(Command.create("C:\\Windows\\System32\\where.exe").addArgument("$PATH:node.exe"));
    } else {
      // INFO: Based on the Linux / macOS shell we require the full path as "which" is a built-in on some shells!
      var which = Command.create("/usr/bin/which").addArgument("node");
      computePathEnvForMacOs(which);
      result = runSimpleCommand(which);
    }
    if (result != null) {
      logOutput.log("Found node at " + result, ClientLogOutput.Level.DEBUG);
      return Paths.get(result);
    } else {
      logOutput.log("Unable to locate node", ClientLogOutput.Level.DEBUG);
      return null;
    }
  }

  private void computePathEnvForMacOs(Command which) {
    if (system2.isOsMac() && Files.exists(pathHelperLocationOnMac)) {
      var command = Command.create(pathHelperLocationOnMac.toString()).addArgument("-s");
      var pathHelperOutput = runSimpleCommand(command);
      if (pathHelperOutput != null) {
        var regex = Pattern.compile(".*PATH=\"(.*)\"; export PATH;.*");
        var matchResult = regex.matcher(pathHelperOutput);
        if (matchResult.matches()) {
          which.setEnvironmentVariable("PATH", matchResult.group(1));
        }
      }
    }
  }

  /**
   * Run a simple command that should return a single line on stdout
   *
   * @param command
   * @return
   */
  @CheckForNull
  private String runSimpleCommand(Command command) {
    List<String> stdOut = new ArrayList<>();
    List<String> stdErr = new ArrayList<>();
    logOutput.log("Execute command '" + command + "'...", ClientLogOutput.Level.DEBUG);
    int exitCode;
    try {
      exitCode = commandExecutor.execute(command, stdOut::add, stdErr::add, 10_000);
    } catch (CommandException e) {
      logOutput.log("Unable to execute the command: " + e.getMessage(), ClientLogOutput.Level.DEBUG);
      return null;
    }
    var msg = new StringBuilder(String.format("Command '%s' exited with %s", command, exitCode));
    if (!stdOut.isEmpty()) {
      msg.append("\nstdout: ").append(String.join("\n", stdOut));
    }
    if (!stdErr.isEmpty()) {
      msg.append("\nstderr: ").append(String.join("\n", stdErr));
    }
    logOutput.log(msg.toString(), ClientLogOutput.Level.DEBUG);
    if (exitCode != 0 || stdOut.isEmpty()) {
      return null;
    }
    return stdOut.get(0);
  }

}
