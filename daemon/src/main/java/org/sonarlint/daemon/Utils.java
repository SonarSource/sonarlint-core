/*
 * SonarLint Daemon
 * Copyright (C) 2009-2020 SonarSource SA
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
package org.sonarlint.daemon;

import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.CheckForNull;

public class Utils {
  private Utils() {
    // only static
  }

  public static Collection<URL> getAnalyzers(Path home) {
    List<URL> plugins = new ArrayList<>();
    Path analyzerDir = home.resolve("plugins");
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(analyzerDir, "*.jar")) {
      for (Path p : stream) {
        plugins.add(p.toUri().toURL());
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to find analyzers in " + analyzerDir, e);
    }

    if (plugins.isEmpty()) {
      throw new IllegalStateException("Found no analyzers in " + analyzerDir);
    }
    return plugins;
  }

  public static Path getSonarLintInstallationHome() {
    if (System.getProperty("sonarlint.home") == null) {
      throw new IllegalStateException("The system property 'sonarlint.home' must be defined");
    }

    return Paths.get(System.getProperty("sonarlint.home"));
  }

  public static Path getStandaloneHome() {
    Path home;
    Path appData = getAppData();
    if (appData != null) {
      home = appData.resolve("sonarlint");
    } else {
      String userHome = System.getProperty("user.home");
      home = Paths.get(userHome, "sonarlint");
    }

    try {
      Files.createDirectories(home);

      return home;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create sonarlint home", e);
    }
  }

  @CheckForNull
  private static Path getAppData() {
    String osName = System.getProperty("os.name");

    if (osName != null && osName.startsWith("Windows")) {
      String appData = System.getenv("LocalAppData");
      if (appData != null) {
        Path appDataPath = Paths.get(appData);
        if (Files.exists(appDataPath)) {
          return appDataPath;
        }
      }
    }
    return null;
  }
}
