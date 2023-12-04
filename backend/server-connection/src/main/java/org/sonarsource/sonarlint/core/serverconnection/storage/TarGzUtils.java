/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;

public class TarGzUtils {

  private TarGzUtils() {

  }

  public static void extractTarGz(Path tarGzFile, Path destinationDir) throws IOException {
    try (var fi = Files.newInputStream(tarGzFile);
      var bi = new BufferedInputStream(fi);
      var gzi = new GzipCompressorInputStream(bi);
      var o = new TarArchiveInputStream(gzi)) {
      ArchiveEntry entry = null;
      while ((entry = o.getNextEntry()) != null) {
        if (!o.canReadEntryData(entry)) {
          throw new IllegalStateException("Unable to read entry data from " + tarGzFile);
        }
        Path f = fileName(destinationDir, entry);
        if (entry.isDirectory()) {
          Files.createDirectories(f);
        } else {
          Path parent = f.getParent();
          Files.createDirectories(parent);

          try (var os = Files.newOutputStream(f)) {
            IOUtils.copy(o, os);
          }
        }
      }
    }
  }

  private static Path fileName(Path destinationDir, ArchiveEntry zipEntry) throws IOException {
    var destFile = destinationDir.resolve(zipEntry.getName());

    if (!destFile.normalize().startsWith(destinationDir)) {
      throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
    }

    return destFile;
  }

}
