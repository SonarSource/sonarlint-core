/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.utils.PathUtils;

/**
 * This is a simple placeholder. Issues on directories will be reported as project level issues.
 */
public class SonarLintInputDir implements InputDir {

  private final Path path;

  public SonarLintInputDir(Path path) {
    this.path = path;
  }

  @Override
  public String relativePath() {
    return absolutePath();
  }

  @Override
  public String absolutePath() {
    return PathUtils.sanitize(path().toString());
  }

  @Override
  public File file() {
    return path().toFile();
  }

  @Override
  public Path path() {
    return path;
  }

  @Override
  public String key() {
    return absolutePath();
  }

  @Override
  public URI uri() {
    return path.toUri();
  }

  @Override
  public boolean isFile() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof SonarLintInputDir)) {
      return false;
    }

    var that = (SonarLintInputDir) o;
    return path().equals(that.path());
  }

  @Override
  public int hashCode() {
    return path().hashCode();
  }

  @Override
  public String toString() {
    return "[path=" + path() + "]";
  }

}
