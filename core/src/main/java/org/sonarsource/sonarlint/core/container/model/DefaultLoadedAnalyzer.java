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

import org.sonarsource.sonarlint.core.client.api.connected.LoadedAnalyzer;

public class DefaultLoadedAnalyzer implements LoadedAnalyzer {
  private final String key;
  private final String name;
  private final String version;
  private final boolean supportsContentStream;

  public DefaultLoadedAnalyzer(String key, String name, String version, boolean supportsContentStream) {
    this.key = key;
    this.name = name;
    this.version = version;
    this.supportsContentStream = supportsContentStream;
  }

  @Override
  public String key() {
    return key;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String version() {
    return version;
  }

  @Override
  public boolean supportsContentStream() {
    return supportsContentStream;
  }
}
