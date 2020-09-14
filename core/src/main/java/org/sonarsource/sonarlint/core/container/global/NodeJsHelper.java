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
package org.sonarsource.sonarlint.core.container.global;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import org.sonar.api.Startable;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandException;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration;
import org.sonarsource.sonarlint.core.plugin.Version;

public class NodeJsHelper implements Startable {

  private static final Logger LOG = Loggers.get(NodeJsHelper.class);
  private static final Pattern NODEJS_VERSION_PATTERN = Pattern.compile("v?(\\d+\\.\\d+\\.\\d+(-.*)?)");
  private final System2 system2;
  private final AbstractGlobalConfiguration config;
  private final Path pathHelperLocationOnMac;
  private final CommandExecutor commandExecutor;

  private Path nodeJsPath;
  private Version nodeJsVersion;

  public NodeJsHelper(AbstractGlobalConfiguration config, System2 system2) {
    this(config, system2, Paths.get("/usr/libexec/path_helper"), CommandExecutor.create());
  }

  // For testing
  NodeJsHelper(AbstractGlobalConfiguration config, System2 system2, Path pathHelperLocationOnMac, CommandExecutor commandExecutor) {
    this.config = config;
    this.system2 = system2;
    this.pathHelperLocationOnMac = pathHelperLocationOnMac;
    this.commandExecutor = commandExecutor;
  }

  @Override
  public void start() {
    nodeJsPath = locateNode(config);
    if (nodeJsPath != null) {
      LOG.debug("Checking node version...");
      Command command = Command.create(nodeJsPath.toString()).addArgument("-v");
      String nodeVersionStr = runSimpleCommand(command);
      if (nodeVersionStr != null) {
        Matcher matcher = NODEJS_VERSION_PATTERN.matcher(nodeVersionStr);
        if (matcher.matches()) {
          String version = matcher.group(1);
          nodeJsVersion = Version.create(version);
          LOG.debug("Detected node version: {}", nodeJsVersion);
        } else {
          LOG.debug("Unable to parse node version: {}", nodeVersionStr);
        }
      }
      if (nodeJsVersion == null) {
        LOG.warn("Unable to query node version");
      }
    }
  }

  @CheckForNull
  public Path getNodeJsPath() {
    return nodeJsPath;
  }

  @CheckForNull
  public Version getNodeJsVersion() {
    return nodeJsVersion;
  }

  @CheckForNull
  private Path locateNode(AbstractGlobalConfiguration config) {
    Path configuredNodejsPath = config.getNodejsPath();
    if (configuredNodejsPath != null) {
      LOG.debug("Node.js path provided by configuration: {}", configuredNodejsPath);
      return configuredNodejsPath;
    }
    LOG.debug("Looking for node in the PATH");

    String result;
    if (system2.isOsWindows()) {
      result = runSimpleCommand(Command.create("where").addArgument("node"));
    } else {
      Command which = Command.create("which").addArgument("node");
      computePathEnvForMacOs(which);
      result = runSimpleCommand(which);
    }
    if (result != null) {
      LOG.debug("Found node at {}", result);
      return Paths.get(result);
    } else {
      LOG.debug("Unable to locate node");
      return null;
    }
  }

  private void computePathEnvForMacOs(Command which) {
    if (system2.isOsMac() && Files.exists(pathHelperLocationOnMac)) {
      Command command = Command.create(pathHelperLocationOnMac.toString()).addArgument("-s");
      String pathHelperOutput = runSimpleCommand(command);
      if (pathHelperOutput != null) {
        Pattern regex = Pattern.compile(".*PATH=\"(.*)\"; export PATH;.*");
        Matcher matchResult = regex.matcher(pathHelperOutput);
        if (matchResult.matches()) {
          which.setEnvironmentVariable("PATH", matchResult.group(1));
        }
      }
    }
  }

  /**
   * Run a simple command that should return a single line on stdout
   * @param command
   * @return
   */
  @CheckForNull
  private String runSimpleCommand(Command command) {
    List<String> stdOut = new ArrayList<>();
    List<String> stdErr = new ArrayList<>();
    LOG.debug("Execute command '{}'...", command);
    int exitCode;
    try {
      exitCode = commandExecutor.execute(command, stdOut::add, stdErr::add, 10_000);
    } catch (CommandException e) {
      LOG.debug("Unable to execute the command", e);
      return null;
    }
    StringBuilder msg = new StringBuilder(String.format("Command '%s' exited with %s", command.toString(), exitCode));
    if (!stdOut.isEmpty()) {
      msg.append("\nstdout: ").append(String.join("\n", stdOut));
    }
    if (!stdErr.isEmpty()) {
      msg.append("\nstderr: ").append(String.join("\n", stdErr));
    }
    LOG.debug(msg.toString());
    if (exitCode != 0 || stdOut.size() != 1) {
      return null;
    }
    return stdOut.get(0);
  }

  @Override
  public void stop() {
    // Nothing to do
  }

}
