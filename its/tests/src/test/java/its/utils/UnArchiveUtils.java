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
import org.apache.commons.lang3.SystemUtils;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;

public class UnArchiveUtils {

  public  static void unarchiveDistribution(String inputFilePath, Path destionationPath, FileSelector[] fileSelectors) {
    var unArchiver = SystemUtils.IS_OS_WINDOWS ? new ZipUnArchiver() : new TarGZipUnArchiver();
    var outputDirectory = destionationPath.toFile();
    outputDirectory.mkdirs();
    var inputFile = Path.of(inputFilePath).toFile();
    unArchiver.setSourceFile(inputFile);
    unArchiver.setFileSelectors(fileSelectors);
    unArchiver.setDestDirectory(outputDirectory);
    unArchiver.extract();
  }

  public  static void unarchiveDistribution(String inputFilePath, Path destionationPath) {
    unarchiveDistribution(inputFilePath, destionationPath, new FileSelector[]{});
  }
}
