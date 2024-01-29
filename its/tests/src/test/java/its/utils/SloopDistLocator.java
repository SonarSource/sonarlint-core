/*
 * SonarLint Core - ITs - Tests
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
package its.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;

public class SloopDistLocator {

  private static final String SLOOP_DIST_PATH = "../../backend/cli/target";
  private static final String WINDOWS_DIST_REGEXP = "^sonarlint-backend-cli-([0-9.]+)(-SNAPSHOT)*-windows.zip$";
  private static final String LINUX_64_DIST_REGEXP = "^sonarlint-backend-cli-([0-9.]+)(-SNAPSHOT)*-linux_x64.tar.gz$";

  public static Path getLinux64DistPath() {
    return getSloopDistPath(LINUX_64_DIST_REGEXP);
  }

  public static Path getWindowsDistPath() {
    return getSloopDistPath(WINDOWS_DIST_REGEXP);
  }

  private static Path getSloopDistPath(String regexp) {
    var sloopDistDir = Paths.get(SLOOP_DIST_PATH).toAbsolutePath().normalize().toFile();
    return FileUtils.listFiles(sloopDistDir, new RegexFileFilter(regexp), FalseFileFilter.FALSE).iterator().next().toPath();
  }
}
