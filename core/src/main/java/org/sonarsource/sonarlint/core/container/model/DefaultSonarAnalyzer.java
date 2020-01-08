/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.model;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;

public class DefaultSonarAnalyzer implements SonarAnalyzer {
  private String key;
  private String filename;
  private String version;
  private String hash;
  private boolean sonarlintCompatible;
  private String minimumVersion;
  private boolean versionSupported = true;

  public DefaultSonarAnalyzer(String key, String filename, String hash, String version, boolean sonarlintCompatible) {
    this(key, filename, hash, sonarlintCompatible);
    this.version = version;
  }

  public DefaultSonarAnalyzer(String key, String filename, String hash, boolean sonarlintCompatible) {
    this.key = key;
    this.filename = filename;
    this.hash = hash;
    this.sonarlintCompatible = sonarlintCompatible;
  }

  public String key() {
    return key;
  }

  public DefaultSonarAnalyzer key(String key) {
    this.key = key;
    return this;
  }

  public String filename() {
    return filename;
  }

  public DefaultSonarAnalyzer filename(String filename) {
    this.filename = filename;
    return this;
  }

  @CheckForNull
  public String version() {
    return version;
  }

  public DefaultSonarAnalyzer version(@Nullable String version) {
    this.version = version;
    return this;
  }

  public String hash() {
    return hash;
  }

  public DefaultSonarAnalyzer hash(String hash) {
    this.hash = hash;
    return this;
  }

  public boolean sonarlintCompatible() {
    return sonarlintCompatible;
  }

  public DefaultSonarAnalyzer sonarlintCompatible(boolean sonarlintCompatible) {
    this.sonarlintCompatible = sonarlintCompatible;
    return this;
  }

  @CheckForNull
  public String minimumVersion() {
    return minimumVersion;
  }

  public DefaultSonarAnalyzer minimumVersion(@Nullable String minimumVersion) {
    this.minimumVersion = minimumVersion;
    return this;
  }

  @Override
  public boolean versionSupported() {
    return versionSupported;
  }

  public DefaultSonarAnalyzer versionSupported(boolean b) {
    this.versionSupported = b;
    return this;
  }

}
