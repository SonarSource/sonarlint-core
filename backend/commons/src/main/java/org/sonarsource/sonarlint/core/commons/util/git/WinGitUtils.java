/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.util.git;

import java.io.IOException;
import java.util.LinkedList;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class WinGitUtils {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private WinGitUtils() {
    // utils
  }

  public static String locateGitOnWindows() throws IOException {
    // Windows will search current directory in addition to the PATH variable, which is unsecure.
    // To avoid it we use where.exe to find git binary only in PATH.
    LOG.debug("Looking for git command in the PATH using where.exe (Windows)");
    var whereCommandResult = new LinkedList<String>();
    new ProcessWrapperFactory()
      .create(null, whereCommandResult::add, "C:\\Windows\\System32\\where.exe", "$PATH:git.exe")
      .execute();

    if (!whereCommandResult.isEmpty()) {
      var out = whereCommandResult.get(0).trim();
      LOG.debug("Found git.exe at {}", out);
      return out;
    }
    throw new IllegalStateException("git.exe not found in PATH. PATH value was: " + System.getProperty("PATH"));
  }
}
