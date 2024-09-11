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
package org.sonarsource.sonarlint.core.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import org.apache.commons.lang.ArrayUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class WindowsShortcutUtils {
  // Based on Windows specification the magic number is 0x0000004C that must be tested with both big and little endian
  // as it might differ based on the architecture / OS.
  private static final byte[] WINDOWS_SHORTCUT_MAGIC_NUMBER = new byte[] {0, 0, 0, 76};
  
  private WindowsShortcutUtils() {
    // utility class
  }

  /**
   *  Checks whether a file provided by URI is a Windows shortcut or not. These differ from actual (sym)links on
   *  Windows as they are like an object containing the pointer to the other resource instead of pointing to the
   *  resource directly.
   */
  public static boolean isWindowsShortcut(URI uri) {
    // Based on the Windows specification the shortcuts have this file suffix, when changing the file suffix they won't
    // work anymore. So if users would create a shortcut, then rename it and have it in the scope of SonarLint this
    // would fail but that is fine.
    if (!uri.toString().contains(".lnk")) {
      return false;
    }
    
    // If the filename ends with ".lnk" we check the magic number in order to determine it to actually be a windows
    // shortcut. This is expensive and therefore will actually only do it on files that match this filter!
    var magicNumber = new byte[4];
    try (var is = new FileInputStream(new File(uri))) {
      if (is.read(magicNumber) != magicNumber.length) {
        // We can only read 0-3 bytes, therefore it cannot be a Windows shortcut. No idea what kind of file this might
        // be (e.g. a text file with 3 characters?) but hey, they gave it a ".lnk" suffix and mimicked a shortcut so
        // they probably know better.
        return false;
      }
      
      // Check big endian
      if (Arrays.equals(WINDOWS_SHORTCUT_MAGIC_NUMBER, magicNumber)) {
        return true;
      }

      // Check little endian
      ArrayUtils.reverse(magicNumber);
      return Arrays.equals(WINDOWS_SHORTCUT_MAGIC_NUMBER, magicNumber);
    } catch (IOException err) {
      SonarLintLogger.get().debug("Cannot check whether '" + uri + "' is a Windows shortcut, assuming it is not.");
    }
    
    return false;
  }
}
