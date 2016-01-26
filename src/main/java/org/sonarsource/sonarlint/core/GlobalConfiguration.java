/*
 * SonarLint Core Library
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;

public class GlobalConfiguration {

  public static final String DEFAULT_WORK_DIR = "work";
  private final Path sonarLintUserHome;
  private final Path workDir;

  public GlobalConfiguration(@Nullable Path sonarLintUserHome, @Nullable Path workDir) {
    this.sonarLintUserHome = sonarLintUserHome != null ? sonarLintUserHome : findHome();
    this.workDir = workDir != null ? workDir : this.sonarLintUserHome.resolve(DEFAULT_WORK_DIR);
  }

  public Path getSonarLintUserHome() {
    return sonarLintUserHome;
  }

  private static Path findHome() {
    String path = System.getenv("SONARLINT_USER_HOME");
    if (path == null) {
      // Default
      path = System.getProperty("user.home") + File.separator + ".sonarlint";
    }
    return Paths.get(path);
  }

  public Path getWorkDir() {
    return workDir;
  }

}
