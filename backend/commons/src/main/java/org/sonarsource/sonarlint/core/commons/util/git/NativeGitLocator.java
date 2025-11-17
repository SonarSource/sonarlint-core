/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.util.git;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.lang3.SystemUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class NativeGitLocator {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  // So we only have to make the expensive call once (or at most twice) to get the native Git executable
  private boolean checkedForNativeGitExecutable = false;
  private NativeGit nativeGitExecutable = null;

  /**
   * Get the native Git executable by checking for the version of both `git` and `git.exe`. We cache this information
   * to not run these expensive processes more than once (or twice in case of Windows).
   */
  public Optional<NativeGit> getNativeGitExecutable() {
    if (checkedForNativeGitExecutable) {
      return Optional.ofNullable(nativeGitExecutable);
    }

    var nativeGit = getGitExecutable()
      .map(NativeGit::new)
      .filter(NativeGit::isSupportedVersion);
    checkedForNativeGitExecutable = true;
    nativeGitExecutable = nativeGit.orElse(null);
    return nativeGit;
  }

  Optional<String> getGitExecutable() {
    return SystemUtils.IS_OS_WINDOWS ? locateGitOnWindows() : Optional.of("git");
  }

  private static Optional<String> locateGitOnWindows() {
    var lines = new ArrayList<String>();
    var result = callWhereTool(lines::add);
    return locateGitOnWindows(result, String.join("\n", lines));
  }

  static Optional<String> locateGitOnWindows(ProcessWrapperFactory.ProcessExecutionResult result, String lines) {
    // Windows will search current directory in addition to the PATH variable, which is unsecure.
    // To avoid it we use where.exe to find git binary only in PATH.

    if (result.exitCode() == 0 && lines.contains("git.exe")) {
      var out = Arrays.stream(lines.split(System.lineSeparator())).map(String::trim).findFirst();
      LOG.debug("Found git.exe at {}", out);
      return out;
    }
    LOG.debug("git.exe not found in PATH. PATH value was: " + System.getProperty("PATH"));
    return Optional.empty();
  }

  private static ProcessWrapperFactory.ProcessExecutionResult callWhereTool(Consumer<String> lineConsumer) {
    LOG.debug("Looking for git command in the PATH using where.exe (Windows)");
    return new ProcessWrapperFactory()
      .create(null, lineConsumer, "C:\\Windows\\System32\\where.exe", "$PATH:git.exe")
      .execute();
  }
}
