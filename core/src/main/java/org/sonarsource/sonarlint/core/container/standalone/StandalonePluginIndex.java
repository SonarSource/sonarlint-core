/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.standalone;

import java.util.List;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.plugin.PluginIndex;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;

/**
 * Provides the list of plugins provided by the client.
 * The plugins are cached before being used.
 */
public class StandalonePluginIndex implements PluginIndex {
  private final StandalonePluginUrls pluginUrls;
  private final PluginCache fileCache;

  public StandalonePluginIndex(StandalonePluginUrls pluginUrls, PluginCache fileCache) {
    this.pluginUrls = pluginUrls;
    this.fileCache = fileCache;
  }

  @Override
  public List<PluginReference> references() {
    return pluginUrls.urls().stream()
      .map(fileCache::getFromCacheOrCopy)
      .collect(Collectors.toList());
  }

}
