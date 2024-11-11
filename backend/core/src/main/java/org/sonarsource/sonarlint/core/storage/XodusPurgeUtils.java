/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class XodusPurgeUtils {

  private XodusPurgeUtils() {
    // Static class
  }

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static void purgeOldTemporaryFiles(Path workDir, Integer purgeDays, String pattern) {
    if (Files.exists(workDir)) {
      try (var stream = Files.newDirectoryStream(workDir, pattern)) {
        for (var path : stream) {
          var file = path.toFile();
          var diff = new Date().getTime() - file.lastModified();
          if (diff > purgeDays * 24 * 60 * 60 * 1000) {
            FileUtils.deleteQuietly(file);
            LOG.debug("Successfully purged " + path);
          }
        }
      } catch (Exception e) {
        LOG.error("Unable to purge old temporary files for pattern " + pattern);
      }
    }
  }

}
