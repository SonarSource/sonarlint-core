/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.telemetry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintPathManager;

public class TelemetryPathManager {

  private TelemetryPathManager() {
    // utility class, forbidden constructor
  }

  /**
   * Get the telemetry storage path.
   *
   * @param productKey short name of the product, for example "idea", "eclipse"
   * @return path to telemetry storage
   */
  public static Path getPath(String productKey) {
    return SonarLintPathManager.home().resolve("telemetry").resolve(productKey).resolve("usage");
  }

  /**
   * If telemetry storage doesn't exist at the current location,
   * copy from the specified old path.
   *
   * @param productKey short name of the product, for example "idea", "eclipse"
   * @param oldPath old path of telemetry storage
   */
  public static void migrate(String productKey, Path oldPath) {
    Path newPath = getPath(productKey);
    if (newPath.toFile().isFile() || !oldPath.toFile().exists()) {
      return;
    }

    try {
      if (!newPath.getParent().toFile().exists()) {
        Files.createDirectories(newPath.getParent());
      }
      Files.copy(oldPath, newPath);
    } catch (IOException e) {
      // ignore
    }
  }
}
