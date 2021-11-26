/*
 * SonarLint Core - Plugin Common
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.plugin.common.load;

import java.nio.file.Path;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public class SonarPluginManifestAndJarPath {

  private final SonarPluginManifest manifest;
  private final Path jarPath;

  SonarPluginManifestAndJarPath(Path jarPath, SonarPluginManifest manifest) {
    this.jarPath = jarPath;
    this.manifest = manifest;
  }

  public SonarPluginManifest getManifest() {
    return manifest;
  }

  public Path getJarPath() {
    return jarPath;
  }

  public static SonarPluginManifestAndJarPath create(Path jarFile) {
    SonarPluginManifest manifest = SonarPluginManifest.fromJar(jarFile);
    if (StringUtils.isBlank(manifest.getKey())) {
      throw new IllegalStateException(String.format("File '%s' is not a valid Sonar plugin.", jarFile.toAbsolutePath()));
    }
    return new SonarPluginManifestAndJarPath(jarFile, manifest);
  }

  public String getKey() {
    return manifest.getKey();
  }

  public String getName() {
    return Optional.ofNullable(manifest.getName()).orElse(getKey());
  }

}
