/*
 * SonarLint Core - Plugin Commons
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.plugin.commons.loading;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.PluginsMinVersions;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.plugin.commons.ApiVersions;
import org.sonarsource.sonarlint.core.plugin.commons.SkipReason;
import org.sonarsource.sonarlint.core.plugin.commons.SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement;
import org.sonarsource.sonarlint.core.plugin.commons.loading.SonarPluginManifest.RequiredPlugin;

public class SonarPluginRequirementsChecker {

  private static final String OLD_SONARTS_PLUGIN_KEY = "typescript";

  private static final Logger LOG = Loggers.get(SonarPluginRequirementsChecker.class);

  private final PluginsMinVersions pluginMinVersions;
  private final Version implementedPluginApiVersion;

  public SonarPluginRequirementsChecker() {
    this(new PluginsMinVersions(), ApiVersions.loadSonarPluginApiVersion());
  }

  SonarPluginRequirementsChecker(PluginsMinVersions pluginMinVersions, org.sonar.api.utils.Version pluginApiVersion) {
    this.pluginMinVersions = pluginMinVersions;
    this.implementedPluginApiVersion = Version.create(pluginApiVersion.toString());
  }

  /**
   * Attempt to read JAR manifests, load metadata, and check all requirements to ensure the plugin can be instantiated.
   */
  public Map<String, PluginRequirementsCheckResult> checkRequirements(Set<Path> pluginJarLocations, Set<Language> enabledLanguages, Version jreCurrentVersion,
    boolean shouldCheckNodeVersion, Optional<Version> nodeCurrentVersion) {
    Map<String, PluginRequirementsCheckResult> resultsByKey = new HashMap<>();

    for (Path jarLocation : pluginJarLocations) {
      PluginInfo plugin;

      try {
        plugin = PluginInfo.create(jarLocation);
      } catch (Exception e) {
        LOG.error("Unable to load plugin " + jarLocation, e);
        continue;
      }
      if (resultsByKey.containsKey(plugin.getKey())) {
        throw new IllegalStateException(
          "Duplicate plugin key '" + plugin.getKey() + "' from '" + plugin.getJarFile() + "' and '" + resultsByKey.get(plugin.getKey()).getPlugin().getJarFile() + "'");
      }
      resultsByKey.put(plugin.getKey(), checkIfSkippedAndPopulateReason(plugin, enabledLanguages, jreCurrentVersion, shouldCheckNodeVersion, nodeCurrentVersion));
    }
    // Second pass of checks
    for (PluginRequirementsCheckResult result : resultsByKey.values()) {
      if (!result.isSkipped()) {
        resultsByKey.put(result.getPlugin().getKey(), checkUnsatisfiedPluginDependency(result, resultsByKey));
      }
    }
    return resultsByKey;
  }

  private PluginRequirementsCheckResult checkIfSkippedAndPopulateReason(PluginInfo plugin, Set<Language> enabledLanguages, Version jreCurrentVersion,
    boolean shouldCheckNodeVersion, Optional<Version> nodeCurrentVersion) {
    var pluginKey = plugin.getKey();
    var languages = Language.getLanguagesByPluginKey(pluginKey);
    if (!languages.isEmpty() && enabledLanguages.stream().noneMatch(languages::contains)) {
      if (languages.size() > 1) {
        LOG.debug("Plugin '{}' is excluded because none of languages '{}' are enabled. Skip loading it.", plugin.getName(),
          languages.stream().map(Language::toString).collect(Collectors.joining(",")));
      } else {
        LOG.debug("Plugin '{}' is excluded because language '{}' is not enabled. Skip loading it.", plugin.getName(),
          languages.iterator().next());
      }
      return new PluginRequirementsCheckResult(plugin, new SkipReason.LanguagesNotEnabled(languages));
    }

    if (!isCompatibleWith(plugin, implementedPluginApiVersion)) {
      LOG.debug("Plugin '{}' requires plugin API {} while SonarLint supports only up to {}. Skip loading it.", plugin.getName(),
        plugin.getMinimalSqVersion(), implementedPluginApiVersion.removeQualifier().toString());
      return new PluginRequirementsCheckResult(plugin, SkipReason.IncompatiblePluginApi.INSTANCE);
    }
    var pluginMinVersion = pluginMinVersions.getMinimumVersion(pluginKey);
    if (pluginMinVersion != null && !pluginMinVersions.isVersionSupported(pluginKey, plugin.getVersion())) {
      LOG.debug("Plugin '{}' version '{}' is not supported (minimal version is '{}'). Skip loading it.", plugin.getName(), plugin.getVersion(),
        pluginMinVersion);
      return new PluginRequirementsCheckResult(plugin, new SkipReason.IncompatiblePluginVersion(pluginMinVersion));
    }
    var jreMinVersion = plugin.getJreMinVersion();
    if (jreMinVersion != null) {
      if (!jreCurrentVersion.satisfiesMinRequirement(jreMinVersion)) {
        LOG.debug("Plugin '{}' requires JRE {} while current is {}. Skip loading it.", plugin.getName(), jreMinVersion, jreCurrentVersion);
        return new PluginRequirementsCheckResult(plugin,
          new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, jreCurrentVersion.toString(), jreMinVersion.toString()));
      }
    }
    if (shouldCheckNodeVersion) {
      var nodeMinVersion = plugin.getNodeJsMinVersion();
      if (nodeMinVersion != null) {
        if (nodeCurrentVersion.isEmpty()) {
          LOG.debug("Plugin '{}' requires Node.js {}. Skip loading it.", plugin.getName(), nodeMinVersion);
          return new PluginRequirementsCheckResult(plugin, new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.NODEJS, null, nodeMinVersion.toString()));
        } else if (!nodeCurrentVersion.get().satisfiesMinRequirement(nodeMinVersion)) {
          LOG.debug("Plugin '{}' requires Node.js {} while current is {}. Skip loading it.", plugin.getName(), nodeMinVersion, nodeCurrentVersion.get());
          return new PluginRequirementsCheckResult(plugin,
            new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.NODEJS, nodeCurrentVersion.get().toString(), nodeMinVersion.toString()));
        }
      }
    }
    return new PluginRequirementsCheckResult(plugin, null);
  }

  /**
   * Find out if this plugin is compatible with a given version of the sonar-plugin-api.
   * The version of the API must be greater than or equal to the minimal version
   * needed by the plugin.
   */
  static boolean isCompatibleWith(PluginInfo plugin, Version implementedApiVersion) {
    var sonarMinVersion = plugin.getMinimalSqVersion();
    if (sonarMinVersion == null) {
      // no constraint defined on the plugin
      return true;
    }

    // Ignore patch and build numbers since this should not change API compatibility
    var requestedApi = Version.create(sonarMinVersion.getMajor() + "." + sonarMinVersion.getMinor());
    return implementedApiVersion.satisfiesMinRequirement(requestedApi);
  }

  private static PluginRequirementsCheckResult checkUnsatisfiedPluginDependency(PluginRequirementsCheckResult currentResult,
    Map<String, PluginRequirementsCheckResult> currentResultsByKey) {
    var plugin = currentResult.getPlugin();
    for (RequiredPlugin required : plugin.getRequiredPlugins()) {
      if ("license".equals(required.getKey()) || (Language.JS.getPluginKey().equals(plugin.getKey()) && OLD_SONARTS_PLUGIN_KEY.equals(required.getKey()))) {
        // Workaround for SLCORE-259
        // This dependency was added to ease migration on SonarQube, but can be ignored on SonarLint
        // Note: The dependency was removed in SonarJS 6.3 but we should still keep the workaround as long as we want to support older
        // versions
        continue;
      }
      var depInfo = currentResultsByKey.get(required.getKey());
      // We could possibly have a problem with transitive dependencies, since we evaluate in no specific order.
      // A -> B -> C
      // If C is skipped, then B should be skipped, then A should be skipped
      // If we evaluate A before B, then A might be wrongly included
      // But I'm not aware of such case in real life.
      if (depInfo == null || depInfo.isSkipped()) {
        LOG.debug("Plugin '{}' dependency on '{}' is unsatisfied. Skip loading it.", currentResult.getPlugin().getName(), required.getKey());
        return new PluginRequirementsCheckResult(currentResult.getPlugin(), new SkipReason.UnsatisfiedDependency(required.getKey()));
      }
    }
    var basePluginKey = plugin.getBasePlugin();
    if (basePluginKey != null) {
      var baseInfo = currentResultsByKey.get(basePluginKey);
      if (baseInfo == null || baseInfo.isSkipped()) {
        LOG.debug("Plugin '{}' dependency on '{}' is unsatisfied. Skip loading it.", currentResult.getPlugin().getName(), basePluginKey);
        return new PluginRequirementsCheckResult(currentResult.getPlugin(), new SkipReason.UnsatisfiedDependency(basePluginKey));
      }
    }
    return currentResult;
  }

}
