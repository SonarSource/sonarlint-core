/*
 * SonarLint Core - ITs
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
package its.tools;

import org.apache.commons.io.FileUtils;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class SonarlintProject extends ExternalResource {
  private Path project;

  /**
   * Copies project to a temporary location and returns its root path.
   */
  public Path deployProject(String location) throws IOException {
    Path originalLoc = Paths.get("projects").resolve(location);
    String projectName = originalLoc.getFileName().toString();

    if (!Files.isDirectory(originalLoc)) {
      throw new IllegalArgumentException("Couldn't find project directory: " + originalLoc.toAbsolutePath().toString());
    }

    cleanProject();
    project = Files.createTempDirectory(projectName);
    FileUtils.copyDirectory(originalLoc.toFile(), project.toFile());
    return project;
  }

  private void cleanProject() {
    if (project != null) {
      FileUtils.deleteQuietly(project.toFile());
      project = null;
    }
  }

  @Override
  protected void after() {
    cleanProject();
  }

  public List<Path> collectAllFiles(Path path) throws IOException {
    InputFileFinder fileFinder = new InputFileFinder(null);
    return fileFinder.collect(path);
  }
}
