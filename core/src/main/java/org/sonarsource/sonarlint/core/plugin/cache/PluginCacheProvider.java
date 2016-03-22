/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin.cache;

import java.nio.file.Path;
import org.picocontainer.injectors.ProviderAdapter;
import org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration;

public class PluginCacheProvider extends ProviderAdapter {
  private PluginCache cache;

  public PluginCache provide(AbstractGlobalConfiguration globalConfiguration) {
    if (cache == null) {
      Path cacheDir = globalConfiguration.getSonarLintUserHome().resolve("plugins");
      cache = PluginCache.create(cacheDir);
    }
    return cache;
  }

}
