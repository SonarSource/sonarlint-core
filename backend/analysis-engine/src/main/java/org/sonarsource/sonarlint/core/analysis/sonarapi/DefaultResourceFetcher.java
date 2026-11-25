/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import java.nio.file.Path;
import org.sonar.api.scanner.ResourceFetcher;

/**
 * Placeholder so sensors can be constructed. Downloading static resources is implemented in a later ticket.
 */
public class DefaultResourceFetcher implements ResourceFetcher {

  @Override
  public Path fetch(String pluginKey, String resourcePath) {
    throw unsupported(pluginKey, resourcePath);
  }

  @Override
  public Path fetchAndExtract(String pluginKey, String resourcePath) {
    throw unsupported(pluginKey, resourcePath);
  }

  private static IllegalArgumentException unsupported(String pluginKey, String resourcePath) {
    return new IllegalArgumentException(
      "Cannot fetch '" + resourcePath + "' for plugin '" + pluginKey + "' because resource fetching is not supported in SonarLint");
  }

}
