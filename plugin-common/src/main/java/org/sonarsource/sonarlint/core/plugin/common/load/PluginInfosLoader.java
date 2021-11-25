/*
 * SonarLint Core - Plugin Common
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.plugin.common.load;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.plugin.common.Language;
import org.sonarsource.sonarlint.core.plugin.common.PluginMinVersions;
import org.sonarsource.sonarlint.core.plugin.common.SkipReason;
import org.sonarsource.sonarlint.core.plugin.common.SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement;
import org.sonarsource.sonarlint.core.plugin.common.Version;
import org.sonarsource.sonarlint.core.plugin.common.load.PluginManifest.RequiredPlugin;

public class PluginInfosLoader {

  private static final String OLD_SONARTS_PLUGIN_KEY = "typescript";

  private static final String IMPLEMENTED_SQ_API = "8.9";

  private static final Logger LOG = Loggers.get(PluginInfosLoader.class);

  private final PluginMinVersions pluginMinVersions;

  public PluginInfosLoader(PluginMinVersions pluginMinVersions) {
    this.pluginMinVersions = pluginMinVersions;
  }

  public Map<String, PluginInfo> loadPlugins(Set<Path> pluginJarLocations, Set<Language> enabledLanguages, Version jreCurrentVersion,
    Optional<Version> nodeCurrentVersion) {
    Map<String, PluginInfo> infosByKey = new HashMap<>();

    for (Path jarLocation : pluginJarLocations) {
      PluginInfo info = PluginInfo.create(jarLocation);
      PluginManifest manifest = info.getManifest();
      if (infosByKey.containsKey(manifest.getKey())) {
        throw new IllegalStateException(
          "Duplicate plugin key '" + manifest.getKey() + "' from '" + info.getJarPath() + "' and '" + infosByKey.get(manifest.getKey()).getJarPath() + "'");
      }
      checkIfSkippedAndPopulateReason(info, enabledLanguages, jreCurrentVersion, nodeCurrentVersion);
      infosByKey.put(manifest.getKey(), info);
    }
    for (PluginInfo info : infosByKey.values()) {
      if (!info.isSkipped()) {
        checkUnsatisfiedPluginDependency(info, infosByKey);
      }
    }
    return infosByKey;
  }

  private void checkIfSkippedAndPopulateReason(PluginInfo info, Set<Language> enabledLanguages, Version jreCurrentVersion, Optional<Version> nodeCurrentVersion) {
    String pluginKey = info.getManifest().getKey();
    Set<Language> languages = Language.getLanguagesByPluginKey(pluginKey);
    if (!languages.isEmpty() && enabledLanguages.stream().noneMatch(languages::contains)) {
      if (languages.size() > 1) {
        LOG.debug("Plugin '{}' is excluded because none of languages '{}' are enabled. Skip loading it.", info.getName(),
          languages.stream().map(Language::toString).collect(Collectors.joining(",")));
      } else {
        LOG.debug("Plugin '{}' is excluded because language '{}' is not enabled. Skip loading it.", info.getName(),
          languages.iterator().next());
      }
      info.setSkipReason(new SkipReason.LanguagesNotEnabled(languages));
      return;
    }
    if (!info.isCompatibleWith(IMPLEMENTED_SQ_API)) {
      LOG.debug("Plugin '{}' requires plugin API {} while SonarLint supports only up to {}. Skip loading it.", info.getName(),
        info.getManifest().getSonarMinVersion().get(), IMPLEMENTED_SQ_API);
      info.setSkipReason(SkipReason.IncompatiblePluginApi.INSTANCE);
      return;
    }
    String pluginMinVersion = pluginMinVersions.getMinimumVersion(pluginKey);
    if (pluginMinVersion != null && !pluginMinVersions.isVersionSupported(pluginKey, info.getManifest().getVersion())) {
      LOG.debug("Plugin '{}' version '{}' is not supported (minimal version is '{}'). Skip loading it.", info.getName(), info.getManifest().getVersion(),
        pluginMinVersion);
      info.setSkipReason(new SkipReason.IncompatiblePluginVersion(pluginMinVersion));
      return;
    }
    Optional<Version> jreMinVersion = info.getManifest().getJreMinVersion();
    if (jreMinVersion.isPresent()) {
      if (!jreCurrentVersion.satisfiesMinRequirement(jreMinVersion.get())) {
        LOG.debug("Plugin '{}' requires JRE {} while current is {}. Skip loading it.", info.getName(), jreMinVersion.get(), jreCurrentVersion);
        info.setSkipReason(
          new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, jreCurrentVersion.toString(), jreMinVersion.get().toString()));
        return;
      }
    }
    Optional<Version> nodeMinVersion = info.getManifest().getNodeJsMinVersion();
    if (nodeMinVersion.isPresent()) {
      if (nodeCurrentVersion.isEmpty()) {
        LOG.debug("Plugin '{}' requires Node.js {}. Skip loading it.", info.getName(), nodeMinVersion.get());
        info.setSkipReason(new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.NODEJS, null, nodeMinVersion.get().toString()));
      } else if (!nodeCurrentVersion.get().satisfiesMinRequirement(nodeMinVersion.get())) {
        LOG.debug("Plugin '{}' requires Node.js {} while current is {}. Skip loading it.", info.getName(), nodeMinVersion.get(), nodeCurrentVersion.get());
        info.setSkipReason(new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.NODEJS, nodeCurrentVersion.get().toString(), nodeMinVersion.get().toString()));
      }
    }
  }

  private static void checkUnsatisfiedPluginDependency(PluginInfo info, Map<String, PluginInfo> infosByKey) {
    for (RequiredPlugin required : info.getManifest().getRequiredPlugins()) {
      if ("license".equals(required.getKey())) {
        continue;
      }
      if (Language.JS.getPluginKey().equals(info.getManifest().getKey()) && OLD_SONARTS_PLUGIN_KEY.equals(required.getKey())) {
        // Workaround for SLCORE-259
        // This dependency was added to ease migration on SonarQube, but can be ignored on SonarLint
        // Note: The dependency was removed in SonarJS 6.3 but we should still keep the workaround as long as we want to support older
        // versions
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
    String basePluginKey = info.getManifest().getBasePluginKey();
    if (basePluginKey != null) {
      PluginInfo baseInfo = infosByKey.get(basePluginKey);
      if (baseInfo == null || baseInfo.isSkipped()) {
        LOG.debug("Plugin '{}' dependency on '{}' is unsatisfied. Skip loading it.", info.getName(), basePluginKey);
        info.setSkipReason(new SkipReason.UnsatisfiedDependency(basePluginKey));
      }
    }
  }

}
