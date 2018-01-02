/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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

  public DefaultSonarAnalyzer(String key, String filename, String hash, @Nullable String version, boolean sonarlintCompatible, String minimumVersion) {
    this.key = key;
    this.filename = filename;
    this.hash = hash;
    this.version = version;
    this.sonarlintCompatible = sonarlintCompatible;
    this.minimumVersion = minimumVersion;
  }

  public DefaultSonarAnalyzer(String key, String filename, String hash, @Nullable String version, boolean sonarlintCompatible) {
    this(key, filename, hash, version, sonarlintCompatible, null);
  }

  public String key() {
    return key;
  }

  public void key(String key) {
    this.key = key;
  }

  public String filename() {
    return filename;
  }

  public void filename(String filename) {
    this.filename = filename;
  }

  @CheckForNull
  public String version() {
    return version;
  }

  public void version(@Nullable String version) {
    this.version = version;
  }

  public String hash() {
    return hash;
  }

  public void hash(String hash) {
    this.hash = hash;
  }

  public boolean sonarlintCompatible() {
    return sonarlintCompatible;
  }

  public void sonarlintCompatible(boolean sonarlintCompatible) {
    this.sonarlintCompatible = sonarlintCompatible;
  }

  @CheckForNull
  public String minimumVersion() {
    return minimumVersion;
  }

  public void minimumVersion(@Nullable String minimumVersion) {
    this.minimumVersion = minimumVersion;
  }

}
