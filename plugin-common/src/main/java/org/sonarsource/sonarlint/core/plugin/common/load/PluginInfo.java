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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.plugin.common.SkipReason;
import org.sonarsource.sonarlint.core.plugin.common.Version;

public class PluginInfo {

  private final PluginManifest manifest;
  private final Path jarPath;

  @CheckForNull
  private SkipReason skipReason;

  private PluginInfo(Path jarPath, PluginManifest manifest) {
    this.jarPath = jarPath;
    this.manifest = manifest;
  }

  public PluginManifest getManifest() {
    return manifest;
  }

  public Path getJarPath() {
    return jarPath;
  }

  public Optional<SkipReason> getSkipReason() {
    return Optional.ofNullable(skipReason);
  }

  public boolean isSkipped() {
    return skipReason != null;
  }

  public void setSkipReason(@Nullable SkipReason skipReason) {
    this.skipReason = skipReason;
  }

  /**
   * Find out if this plugin is compatible with a given version of SonarQube.
   * The version of SQ must be greater than or equal to the minimal version
   * needed by the plugin.
   */
  public boolean isCompatibleWith(String implementedApi) {
    Optional<Version> sonarMinVersion = manifest.getSonarMinVersion();
    if (sonarMinVersion.isEmpty()) {
      // no constraint defined on the plugin
      return true;
    }

    // Ignore patch and build numbers since this should not change API compatibility
    Version requestedApi = Version.create(sonarMinVersion.get().getMajor() + "." + sonarMinVersion.get().getMinor());
    Version implementedApiVersion = Version.create(implementedApi);
    return implementedApiVersion.compareToIgnoreQualifier(requestedApi) >= 0;
  }

  public static PluginInfo create(Path jarFile) {
    try {
      PluginManifest manifest = PluginManifest.fromJar(jarFile);
      return create(jarFile, manifest);

    } catch (IOException e) {
      throw new IllegalStateException("Fail to extract plugin metadata from file: " + jarFile, e);
    }
  }

  static PluginInfo create(Path jarPath, PluginManifest manifest) {
    if (StringUtils.isBlank(manifest.getKey())) {
      throw new IllegalStateException(String.format("File '%s' is not a valid Sonar plugin.", jarPath.toAbsolutePath()));
    }
    return new PluginInfo(jarPath, manifest);
  }

  public String getKey() {
    return manifest.getKey();
  }

  public String getName() {
    return Optional.ofNullable(manifest.getName()).orElse(getKey());
  }

}
