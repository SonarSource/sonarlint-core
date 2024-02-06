/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

public class JreLocator {
  private static final String JRE_WINDOWS_PATH = "target/jre-windows/";
  private static final String JRE_LINUX_PATH = "target/jre-linux/";

  public static Path getWindowsJrePath() {
    return getJrePath(JRE_WINDOWS_PATH);
  }

  public static Path getLinuxJrePath() {
    return getJrePath(JRE_LINUX_PATH);
  }

  @NotNull
  private static Path getJrePath(String jreLinuxPath) {
    var jreDir = Paths.get(jreLinuxPath).toAbsolutePath().normalize().toFile();
    return Arrays.stream(requireNonNull(jreDir.listFiles())).findFirst().get().toPath();
  }
}
