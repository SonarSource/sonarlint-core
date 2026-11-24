/*
 * SonarLint Core - Server Connection
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipUtils {

  private ZipUtils() {
  }

  public static void extractZip(Path zipFile, Path destinationDir) throws IOException {
    var destination = destinationDir.toAbsolutePath().normalize();
    try (var zip = new ZipInputStream(Files.newInputStream(zipFile))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        var extracted = destination.resolve(entry.getName()).normalize();
        if (!extracted.startsWith(destination)) {
          throw new IOException("Entry is outside of the target dir: " + entry.getName());
        }
        if (entry.isDirectory()) {
          Files.createDirectories(extracted);
        } else {
          Files.createDirectories(extracted.getParent());
          Files.copy(zip, extracted, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

}
