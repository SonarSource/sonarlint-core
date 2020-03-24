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
package org.sonarsource.sonarlint.core.plugin;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.plugin.PluginIndex.PluginReference;
import org.sonarsource.sonarlint.core.plugin.PluginInfo.RequiredPlugin;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;

public class PluginCacheLoader {

  private static final String IMPLEMENTED_SQ_API = "8.2";

  private static final Logger LOG = Loggers.get(PluginCacheLoader.class);

  private final PluginCache pluginCache;
  private final PluginIndex pluginIndex;
  private final Set<Language> enabledLanguages;
  private final PluginVersionChecker pluginVersionChecker;

  /**
   * This constructor will be used in standalone mode
   */
  public PluginCacheLoader(PluginVersionChecker pluginVersionChecker, PluginCache pluginCache, PluginIndex pluginIndex) {
    this(pluginVersionChecker, pluginCache, pluginIndex, (Set<Language>) null);
  }

  /**
   * This constructor will be used in connected mode
   */
  public PluginCacheLoader(PluginVersionChecker pluginVersionChecker, PluginCache pluginCache, PluginIndex pluginIndex, ConnectedGlobalConfiguration globalConfiguration) {
    this(pluginVersionChecker, pluginCache, pluginIndex, globalConfiguration.getEnabledLanguages());
  }

  private PluginCacheLoader(PluginVersionChecker pluginVersionChecker, PluginCache pluginCache, PluginIndex pluginIndex, @Nullable Set<Language> enabledLanguages) {
    this.pluginVersionChecker = pluginVersionChecker;
    this.pluginCache = pluginCache;
    this.pluginIndex = pluginIndex;
    this.enabledLanguages = enabledLanguages;
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
      LOG.warn("Code analyzer '{}' needs plugin API {} while SonarLint supports only up to {}. Skip loading it.", info.getName(), info.getMinimalSqVersion(),
        IMPLEMENTED_SQ_API);
      return true;
    }
    if (excludedSonarSourceAnalyzer(info.getKey())) {
      LOG.warn("Code analyzer '{}' is excluded in this version of SonarLint. Skip loading it.", info.getName());
      return true;
    }
    if (isMandatoryDependencyExcluded(info)) {
      LOG.debug("Code analyzer '{}' is transitively excluded in this version of SonarLint. Skip loading it.", info.getName());
      return true;
    }
    String minVersion = pluginVersionChecker.getMinimumVersion(info.getKey());
    if (!pluginVersionChecker.isVersionSupported(info.getKey(), info.getVersion())) {
      LOG.warn("Code analyzer '{}' version '{}' is not supported (minimal version is '{}'). Skip loading it.",
        info.getName(), info.getVersion(), minVersion);
      return true;
    }
    Boolean sonarLintSupported = info.isSonarLintSupported();
    if (sonarLintSupported == null || !sonarLintSupported.booleanValue()) {
      LOG.warn("Code analyzer '{}' is not compatible with SonarLint. Skip loading it.", info.getName());
      return true;
    }

    return false;
  }

  private boolean isMandatoryDependencyExcluded(PluginInfo info) {
    for (RequiredPlugin required : info.getRequiredPlugins()) {
      if (Language.JS.getPluginKey().equals(info.getKey()) && Language.TS.getPluginKey().equals(required.getKey())) {
        // Workaround for SLCORE-259
        // This dependency was added to ease migration on SonarQube, but can be ignored on SonarLint
        continue;
      }
      if (excludedSonarSourceAnalyzer(required.getKey())) {
        return true;
      }
    }
    String basePlugin = info.getBasePlugin();
    return basePlugin != null && excludedSonarSourceAnalyzer(basePlugin);
  }

  private boolean excludedSonarSourceAnalyzer(String pluginKey) {
    return enabledLanguages != null && Language.containsPlugin(pluginKey) && enabledLanguages.stream().noneMatch(l -> l.getPluginKey().equals(pluginKey));
  }

  private Path getFromCache(final PluginReference pluginReference) {
    Path jar;
    try {
      jar = pluginCache.get(pluginReference.getFilename(), pluginReference.getHash());
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
