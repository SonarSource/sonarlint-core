/*
 * SonarLint Core - ITs
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package its.tools;

import net.lingala.zip4j.core.ZipFile;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Attempts to install a specific version of SonarLint Daemon from the local maven repository.
 * The location of the local maven repository can be passed with the environmental variable 'MAVEN_LOCAL_REPOSITORY'.
 */
public class SonarlintInstaller {
  private static final String GROUP_ID = "org.sonarsource.sonarlint.core";
  private static final String ARTIFACT_ID = "sonarlint-daemon";
  private static final Logger LOG = LoggerFactory.getLogger(SonarlintInstaller.class);

  public Path install(Path installPath, String version) {
    if (!isInstalled(installPath, version)) {
      Path zipFile = locateZipInLocalMaven(version);
      installZip(zipFile, installPath);
    }

    return locateScript(version, installPath);
  }

  private static Path locateScript(String version, Path installPath) {
    String directoryName = "sonarlint-daemon-" + version;
    String fileName = SystemUtils.IS_OS_WINDOWS ? "sonarlint-daemon.bat" : "sonarlint-daemon";

    Path script = installPath.resolve(directoryName).resolve("bin").resolve(fileName);

    if (!Files.exists(script)) {
      throw new IllegalStateException("File does not exist: " + script);
    }
    return script;
  }

  private boolean isInstalled(Path installPath, String version) {
    String directoryName = "sonarlint-daemon-" + version;

    Path sonarlint = installPath.resolve(directoryName);

    if (Files.isDirectory(sonarlint)) {
      LOG.debug("SonarLint Daemon {} already exists in {}", version, installPath);
      return true;
    }
    return false;
  }

  private void installZip(Path zipFilePath, Path toDir) {
    try {
      ZipFile zipFile = new ZipFile(zipFilePath.toFile());
      zipFile.extractAll(toDir.toString());
    } catch (Exception e) {
      throw new IllegalStateException("Fail to unzip SonarLint Daemon to" + toDir, e);
    }
  }

  private Path locateZipInLocalMaven(String version) {
    String fileName = "sonarlint-daemon-" + version + ".zip";

    LOG.info("Searching for SonarLint Daemon {} in maven repositories", version);

    Path mvnRepo = getMavenLocalRepository();
    Path file = getMavenFilePath(mvnRepo, GROUP_ID, ARTIFACT_ID, version, fileName);

    if (!Files.exists(file)) {
      throw new IllegalArgumentException("Couldn't find in local repo: SonarLint Daemon " + file.toString());
    }
    return file;
  }

  private static Path getMavenFilePath(Path mvnRepo, String groupId, String artifactId, String version, String fileName) {
    Path p = mvnRepo;
    String[] split = groupId.split("\\.");

    for (String s : split) {
      p = p.resolve(s);
    }

    p = p.resolve(artifactId);
    p = p.resolve(version);
    return p.resolve(fileName);
  }

  private Path getMavenLocalRepository() {
    if (System.getenv("MAVEN_LOCAL_REPOSITORY") != null) {
      Path repo = Paths.get(System.getenv("MAVEN_LOCAL_REPOSITORY"));
      if (!Files.isDirectory(repo)) {
        throw new IllegalArgumentException("Maven local repository is not valid: " + System.getenv("MAVEN_LOCAL_REPOSITORY"));
      }
      return repo;
    }

    String home = System.getProperty("user.home");
    Path repo = Paths.get(home).resolve(".m2").resolve("repository");

    if (!Files.isDirectory(repo)) {
      throw new IllegalArgumentException("Couldn't find maven repository. Please define MAVEN_LOCAL_REPOSITORY in the environment.");
    }

    LOG.info("Using maven repository: {}", repo);
    return repo;
  }
}
