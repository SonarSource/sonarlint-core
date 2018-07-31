/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.plugin.PluginIndex.PluginReference;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;

public class PluginCacheLoader {

  private static final ImmutableSet<String> PLUGIN_WHITELIST = ImmutableSet.of("xoo", "java", "javascript", "php", "python", "cobol", "abap", "plsql", "swift",
    "rpg", "cpp", "pli", "typescript", "web", "html");
  private static final String IMPLEMENTED_SQ_API = "7.1";

  private static final Logger LOG = Loggers.get(PluginCacheLoader.class);

  private final PluginCache fileCache;
  private final PluginIndex pluginIndex;
  private final Set<String> excludedPlugins;
  private final PluginVersionChecker pluginVersionChecker;

  /**
   * This constructor will be used in standalone mode
   */
  public PluginCacheLoader(PluginVersionChecker pluginVersionChecker, PluginCache fileCache, PluginIndex pluginIndex) {
    this.pluginVersionChecker = pluginVersionChecker;
    this.fileCache = fileCache;
    this.pluginIndex = pluginIndex;
    this.excludedPlugins = Collections.emptySet();
  }

  public PluginCacheLoader(PluginVersionChecker pluginVersionChecker, PluginCache fileCache, PluginIndex pluginIndex, ConnectedGlobalConfiguration globalConfiguration) {
    this.pluginVersionChecker = pluginVersionChecker;
    this.fileCache = fileCache;
    this.pluginIndex = pluginIndex;
    this.excludedPlugins = globalConfiguration.getExcludedCodeAnalyzers();
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

      if (!shouldSkip(info)) {
        infosByKey.put(info.getKey(), info);
      }
    }

    profiler.stopDebug();
    return infosByKey;
  }

  private boolean shouldSkip(PluginInfo info) {
    if (!info.isCompatibleWith(IMPLEMENTED_SQ_API)) {
      LOG.warn("Code analyzer '{}' needs SonarQube plugin API {} while SonarLint supports only up to {}. Skip loading it.", info.getName(), info.getMinimalSqVersion(),
        IMPLEMENTED_SQ_API);
      return true;
    }
    if (excludedPlugins.contains(info.getKey())) {
      LOG.warn("Code analyzer '{}' is excluded in this version of SonarLint. Skip loading it.", info.getName());
      return true;
    }
    if (info.getRequiredPlugins().stream().anyMatch(required -> excludedPlugins.contains(required.getKey()))) {
      LOG.debug("Code analyzer '{}' is excluded in this version of SonarLint. Skip loading it.", info.getName());
      return true;
    }
    String minVersion = pluginVersionChecker.getMinimumVersion(info.getKey());
    if (!pluginVersionChecker.isVersionSupported(info.getKey(), info.getVersion())) {
      LOG.warn("Code analyzer '{}' version '{}' is not supported (minimal version is '{}'). Skip loading it.",
        info.getName(), info.getVersion(), minVersion);
      return true;
    }
    Boolean sonarLintSupported = info.isSonarLintSupported();
    if ((sonarLintSupported == null || !sonarLintSupported.booleanValue()) && !isWhitelisted(info.getKey())) {
      LOG.warn("Code analyzer '{}' is not compatible with SonarLint. Skip loading it.", info.getName());
      return true;
    }

    return false;
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
