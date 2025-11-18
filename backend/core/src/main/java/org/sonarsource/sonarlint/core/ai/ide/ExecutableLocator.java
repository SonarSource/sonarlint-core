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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandException;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.nodejs.NodeJsHelper;

public class ExecutableLocator {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final System2 system2;
  private final Path pathHelperLocationOnMac;
  private final CommandExecutor commandExecutor;
  private final NodeJsHelper nodeJsHelper;

  private boolean checkedForExecutable = false;
  private ExecutableType detectedExecutable = null;

  public ExecutableLocator() {
    this(System2.INSTANCE, Paths.get("/usr/libexec/path_helper"), CommandExecutor.create(), new NodeJsHelper());
  }

  // For testing
  ExecutableLocator(System2 system2, Path pathHelperLocationOnMac, CommandExecutor commandExecutor, NodeJsHelper nodeJsHelper) {
    this.system2 = system2;
    this.pathHelperLocationOnMac = pathHelperLocationOnMac;
    this.commandExecutor = commandExecutor;
    this.nodeJsHelper = nodeJsHelper;
  }

  public Optional<ExecutableType> detectBestExecutable() {
    if (checkedForExecutable) {
      return Optional.ofNullable(detectedExecutable);
    }

    // Priority: Node.js > Python > Bash
    if (isNodeJsAvailable()) {
      LOG.debug("Detected Node.js for hook scripts");
      detectedExecutable = ExecutableType.NODEJS;
    } else if (isPythonAvailable()) {
      LOG.debug("Detected Python for hook scripts");
      detectedExecutable = ExecutableType.PYTHON;
    } else if (isBashAvailable()) {
      LOG.debug("Detected Bash for hook scripts");
      detectedExecutable = ExecutableType.BASH;
    } else {
      LOG.debug("No suitable executable found for hook scripts");
      detectedExecutable = null;
    }

    checkedForExecutable = true;
    return Optional.ofNullable(detectedExecutable);
  }

  private boolean isNodeJsAvailable() {
    try {
      var installedNodeJs = nodeJsHelper.autoDetect();
      return installedNodeJs != null;
    } catch (Exception e) {
      LOG.debug("Error detecting Node.js", e);
      return false;
    }
  }

  private boolean isPythonAvailable() {
    // Try python3 first, then python
    var python3Path = locatePythonExecutable("python3");
    if (python3Path != null) {
      return true;
    }
    var pythonPath = locatePythonExecutable("python");
    return pythonPath != null;
  }

  @CheckForNull
  private String locatePythonExecutable(String executable) {
    LOG.debug("Looking for {} in the PATH", executable);
    
    String result;
    if (system2.isOsWindows()) {
      result = runSimpleCommand(Command.create("C:\\Windows\\System32\\where.exe").addArgument("$PATH:" + executable + ".exe"));
    } else {
      var which = Command.create("/usr/bin/which").addArgument(executable);
      computePathEnvForMacOs(which);
      result = runSimpleCommand(which);
    }
    
    if (result != null) {
      LOG.debug("Found {} at {}", executable, result);
      return result;
    } else {
      LOG.debug("Unable to locate {}", executable);
      return null;
    }
  }

  private boolean isBashAvailable() {
    if (system2.isOsWindows()) {
      // On Windows, try to locate bash.exe (Git Bash, WSL, etc.)
      var bashPath = runSimpleCommand(Command.create("C:\\Windows\\System32\\where.exe").addArgument("$PATH:bash.exe"));
      return bashPath != null;
    } else {
      // On Unix/Mac, bash is always available
      return Files.exists(Paths.get("/bin/bash"));
    }
  }

  void computePathEnvForMacOs(Command command) {
    if (system2.isOsMac() && Files.exists(pathHelperLocationOnMac)) {
      var pathHelperCommand = Command.create(pathHelperLocationOnMac.toString()).addArgument("-s");
      var pathHelperOutput = runSimpleCommand(pathHelperCommand);
      if (pathHelperOutput != null) {
        var regex = Pattern.compile("^\\s*PATH=\"([^\"]+)\"; export PATH;?\\s*$");
        var matchResult = regex.matcher(pathHelperOutput);
        if (matchResult.matches()) {
          command.setEnvironmentVariable("PATH", matchResult.group(1));
        }
      }
    }
  }

  @CheckForNull
  String runSimpleCommand(Command command) {
    var stdOut = new ArrayList<String>();
    var stdErr = new ArrayList<String>();
    LOG.debug("Execute command '{}'...", command);
    int exitCode;
    try {
      exitCode = commandExecutor.execute(command, stdOut::add, stdErr::add, 10_000);
    } catch (CommandException e) {
      LOG.debug("Unable to execute the command", e);
      return null;
    }
    var msg = new StringBuilder(String.format("Command '%s' exited with %s", command, exitCode));
    if (!stdOut.isEmpty()) {
      msg.append("\nstdout: ").append(String.join("\n", stdOut));
    }
    if (!stdErr.isEmpty()) {
      msg.append("\nstderr: ").append(String.join("\n", stdErr));
    }
    LOG.debug("{}", msg);
    if (exitCode != 0 || stdOut.isEmpty()) {
      return null;
    }
    return stdOut.get(0);
  }

}

