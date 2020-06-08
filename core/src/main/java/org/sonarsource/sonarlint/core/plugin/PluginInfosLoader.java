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
import java.util.Optional;
import java.util.Set;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.plugin.PluginIndex.PluginReference;
import org.sonarsource.sonarlint.core.plugin.PluginInfo.RequiredPlugin;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;

public class PluginInfosLoader {

  private static final String IMPLEMENTED_SQ_API = "8.2";

  private static final Logger LOG = Loggers.get(PluginInfosLoader.class);

  private final PluginCache pluginCache;
  private final PluginIndex pluginIndex;
  private final Set<Language> enabledLanguages;
  private final PluginVersionChecker pluginVersionChecker;

  public PluginInfosLoader(PluginVersionChecker pluginVersionChecker, PluginCache pluginCache, PluginIndex pluginIndex, AbstractGlobalConfiguration globalConfiguration) {
    this.pluginVersionChecker = pluginVersionChecker;
    this.pluginCache = pluginCache;
    this.pluginIndex = pluginIndex;
    this.enabledLanguages = globalConfiguration.getEnabledLanguages();
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
      if (sonarLintSupported == null || !sonarLintSupported.booleanValue()) {
        LOG.debug("Code analyzer '{}' is not compatible with SonarLint. Skip loading it.", info.getName());
        continue;
      }
      checkIfSkippedAndPopulateReason(info);
      infosByKey.put(info.getKey(), info);
    }
    for (PluginInfo info : infosByKey.values()) {
      if (!info.isSkipped()) {
        checkUnsatisfiedPluginDependency(info, infosByKey);
      }
    }

    profiler.stopDebug();
    return infosByKey;
  }

  private void checkIfSkippedAndPopulateReason(PluginInfo info) {
    String pluginKey = info.getKey();
    Optional<Language> language = Language.getLanguageByPluginKey(pluginKey);
    if (language.isPresent() && !enabledLanguages.contains(language.get())) {
      LOG.debug("Plugin '{}' is excluded because language '{}' is not enabled. Skip loading it.", info.getName(), language.get().getLanguageKey());
      info.setSkipReason(new SkipReason.LanguageNotEnabled(language.get().getLanguageKey()));
      return;
    }
    if (!info.isCompatibleWith(IMPLEMENTED_SQ_API)) {
      LOG.debug("Plugin '{}' requires plugin API {} while SonarLint supports only up to {}. Skip loading it.", info.getName(), info.getMinimalSqVersion(), IMPLEMENTED_SQ_API);
      info.setSkipReason(SkipReason.IncompatiblePluginApi.INSTANCE);
      return;
    }
    String minVersion = pluginVersionChecker.getMinimumVersion(pluginKey);
    if (minVersion != null && !pluginVersionChecker.isVersionSupported(pluginKey, info.getVersion())) {
      LOG.debug("Code analyzer '{}' version '{}' is not supported (minimal version is '{}'). Skip loading it.", info.getName(), info.getVersion(), minVersion);
      info.setSkipReason(new SkipReason.IncompatiblePluginVersion(minVersion));
    }
  }

  private static void checkUnsatisfiedPluginDependency(PluginInfo info, Map<String, PluginInfo> infosByKey) {
    for (RequiredPlugin required : info.getRequiredPlugins()) {
      if ("license".equals(required.getKey())) {
        continue;
      }
      if (Language.JS.getPluginKey().equals(info.getKey()) && Language.TS.getPluginKey().equals(required.getKey())) {
        // Workaround for SLCORE-259
        // This dependency was added to ease migration on SonarQube, but can be ignored on SonarLint
        continue;
      }
      PluginInfo depInfo = infosByKey.get(required.getKey());
      // We could possibly have a problem with transitive dependencies, since we evaluate in no specific order.
      // A -> B -> C
      // If C is skipped, then B should be skipped, then A should be skipped
      // If we evaluate A before B, then A might be wrongly included
      // But I'm not aware of such case in real life.
      if (depInfo == null || depInfo.isSkipped()) {
        LOG.debug("Plugin '{}' dependency on '{}' is unsatisfied. Skip loading it.", info.getName(), required.getKey());
        info.setSkipReason(new SkipReason.UnsatisfiedDependency(required.getKey()));
        return;
      }
    }
    String basePluginKey = info.getBasePlugin();
    if (basePluginKey != null) {
      PluginInfo baseInfo = infosByKey.get(basePluginKey);
      if (baseInfo == null || baseInfo.isSkipped()) {
        LOG.debug("Plugin '{}' dependency on '{}' is unsatisfied. Skip loading it.", info.getName(), basePluginKey);
        info.setSkipReason(new SkipReason.UnsatisfiedDependency(basePluginKey));
      }
    }
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
