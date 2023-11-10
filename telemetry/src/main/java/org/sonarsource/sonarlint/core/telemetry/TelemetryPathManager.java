/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.sonarsource.sonarlint.core.commons.SonarLintUserHome;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class TelemetryPathManager {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

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
    return getPath(SonarLintUserHome.get(), productKey);
  }

  public static Path getPath(Path sonarlintUserHome, String productKey) {
    return sonarlintUserHome.resolve("telemetry").resolve(productKey).resolve("usage");
  }

  /**
   * If telemetry storage doesn't exist at the current standard product-related location,
   * copy from the specified old path.
   *
   * @param productKey short name of the product, for example "idea", "eclipse"
   * @param oldPath old path of telemetry storage
   */
  public static void migrate(String productKey, Path oldPath) {
    var newPath = getPath(productKey);
    migrate(oldPath, newPath);
  }

  /**
   * If telemetry storage doesn't exist at the specified new path,
   * copy from the specified old path.
   *
   * @param oldPath old path of telemetry storage
   * @param newPath new path for telemetry storage
   */
  public static void migrate(Path oldPath, Path newPath) {
    if (newPath.toFile().isFile() || !oldPath.toFile().exists()) {
      return;
    }

    try {
      if (!newPath.getParent().toFile().exists()) {
        Files.createDirectories(newPath.getParent());
      }
      Files.copy(oldPath, newPath);
    } catch (IOException e) {
      if (InternalDebug.isEnabled()) {
        LOG.error("Failed to migrate telemetry storage", e);
      }
    }
  }
}
