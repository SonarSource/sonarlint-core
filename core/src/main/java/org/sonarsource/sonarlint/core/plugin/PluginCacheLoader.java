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
package org.sonarsource.sonarlint.core.plugin;

import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.plugin.PluginIndex.PluginReference;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;

public class PluginCacheLoader {

  private static final ImmutableSet<String> PLUGIN_WHITELIST = ImmutableSet.of("xoo", "java", "javascript", "php", "python", "cobol", "abap", "plsql", "swift", "rpg", "cpp",
    "pli");

  private static final Logger LOG = Loggers.get(PluginCacheLoader.class);

  private final PluginCache fileCache;
  private final PluginIndex pluginIndex;

  public PluginCacheLoader(PluginCache fileCache, PluginIndex pluginIndex) {
    this.fileCache = fileCache;
    this.pluginIndex = pluginIndex;
  }

  public Map<String, PluginInfo> load() {
    return loadPlugins(pluginIndex.references());
  }

  private Map<String, PluginInfo> loadPlugins(List<PluginReference> pluginReferences) {
    Map<String, PluginInfo> infosByKey = new HashMap<>();

    Profiler profiler = Profiler.create(LOG).startDebug("Load plugins");

    for (PluginReference ref : pluginReferences) {
      Path jarFilePath = getFromCache(ref);
      PluginInfo info = PluginInfo.create(jarFilePath);
      Boolean sonarLintSupported = info.isSonarLintSupported();
      if ((sonarLintSupported != null && sonarLintSupported.booleanValue()) || isWhitelisted(info.getKey())) {
        infosByKey.put(info.getKey(), info);
      } else {
        LOG.debug("Plugin {} is not compatible with SonarLint. Skip it.", info.getKey());
      }
    }

    profiler.stopDebug();
    return infosByKey;

  }

  public static boolean isWhitelisted(String pluginKey) {
    return PLUGIN_WHITELIST.contains(pluginKey);
  }

  private Path getFromCache(final PluginReference pluginReference) {
    Path jar;
    try {
      jar = fileCache.get(pluginReference.getFilename(), pluginReference.getHash());
    } catch (StorageException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Fail to find plugin " + pluginReference.getFilename() + " in the cache", e);
    }
    if (jar == null) {
      throw new StorageException("Couldn't find plugin '" + pluginReference.getFilename() + "' in the cache. Please update the binding", false);
    }
    return jar;
  }
}
