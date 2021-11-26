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
import org.sonarsource.sonarlint.core.plugin.common.load.SonarPluginManifest.RequiredPlugin;

public class SonarPluginRequirementsChecker {

  private static final String OLD_SONARTS_PLUGIN_KEY = "typescript";

  private static final String IMPLEMENTED_SQ_API = "8.9";

  private static final Logger LOG = Loggers.get(SonarPluginRequirementsChecker.class);

  private final PluginMinVersions pluginMinVersions;

  public SonarPluginRequirementsChecker() {
    this(new PluginMinVersions());
  }

  SonarPluginRequirementsChecker(PluginMinVersions pluginMinVersions) {
    this.pluginMinVersions = pluginMinVersions;
  }

  /**
   * Attempt to read JAR manifests, load metadata, and check all requirements to ensure the plugin can be instantiated.
   */
  public Map<String, PluginRequirementsCheckResult> checkRequirements(Set<Path> pluginJarLocations, Set<Language> enabledLanguages, Version jreCurrentVersion,
    Optional<Version> nodeCurrentVersion) {
    Map<String, PluginRequirementsCheckResult> resultsByKey = new HashMap<>();

    for (Path jarLocation : pluginJarLocations) {
      SonarPluginManifestAndJarPath plugin = SonarPluginManifestAndJarPath.create(jarLocation);
      SonarPluginManifest manifest = plugin.getManifest();
      if (resultsByKey.containsKey(manifest.getKey())) {
        throw new IllegalStateException(
          "Duplicate plugin key '" + manifest.getKey() + "' from '" + plugin.getJarPath() + "' and '" + resultsByKey.get(manifest.getKey()).getPlugin().getJarPath() + "'");
      }
      resultsByKey.put(manifest.getKey(), checkIfSkippedAndPopulateReason(plugin, enabledLanguages, jreCurrentVersion, nodeCurrentVersion));
    }
    // Second pass of checks
    for (PluginRequirementsCheckResult result : resultsByKey.values()) {
      if (!result.isSkipped()) {
        resultsByKey.put(result.getPlugin().getKey(), checkUnsatisfiedPluginDependency(result, resultsByKey));
      }
    }
    return resultsByKey;
  }

  private PluginRequirementsCheckResult checkIfSkippedAndPopulateReason(SonarPluginManifestAndJarPath plugin, Set<Language> enabledLanguages, Version jreCurrentVersion,
    Optional<Version> nodeCurrentVersion) {
    String pluginKey = plugin.getManifest().getKey();
    Set<Language> languages = Language.getLanguagesByPluginKey(pluginKey);
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
    if (!isCompatibleWith(plugin.getManifest(), IMPLEMENTED_SQ_API)) {
      LOG.debug("Plugin '{}' requires plugin API {} while SonarLint supports only up to {}. Skip loading it.", plugin.getName(),
        plugin.getManifest().getSonarMinVersion().get(), IMPLEMENTED_SQ_API);
      return new PluginRequirementsCheckResult(plugin, SkipReason.IncompatiblePluginApi.INSTANCE);
    }
    String pluginMinVersion = pluginMinVersions.getMinimumVersion(pluginKey);
    if (pluginMinVersion != null && !pluginMinVersions.isVersionSupported(pluginKey, plugin.getManifest().getVersion())) {
      LOG.debug("Plugin '{}' version '{}' is not supported (minimal version is '{}'). Skip loading it.", plugin.getName(), plugin.getManifest().getVersion(),
        pluginMinVersion);
      return new PluginRequirementsCheckResult(plugin, new SkipReason.IncompatiblePluginVersion(pluginMinVersion));
    }
    Optional<Version> jreMinVersion = plugin.getManifest().getJreMinVersion();
    if (jreMinVersion.isPresent()) {
      if (!jreCurrentVersion.satisfiesMinRequirement(jreMinVersion.get())) {
        LOG.debug("Plugin '{}' requires JRE {} while current is {}. Skip loading it.", plugin.getName(), jreMinVersion.get(), jreCurrentVersion);
        return new PluginRequirementsCheckResult(plugin,
          new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, jreCurrentVersion.toString(), jreMinVersion.get().toString()));
      }
    }
    Optional<Version> nodeMinVersion = plugin.getManifest().getNodeJsMinVersion();
    if (nodeMinVersion.isPresent()) {
      if (nodeCurrentVersion.isEmpty()) {
        LOG.debug("Plugin '{}' requires Node.js {}. Skip loading it.", plugin.getName(), nodeMinVersion.get());
        return new PluginRequirementsCheckResult(plugin, new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.NODEJS, null, nodeMinVersion.get().toString()));
      } else if (!nodeCurrentVersion.get().satisfiesMinRequirement(nodeMinVersion.get())) {
        LOG.debug("Plugin '{}' requires Node.js {} while current is {}. Skip loading it.", plugin.getName(), nodeMinVersion.get(), nodeCurrentVersion.get());
        return new PluginRequirementsCheckResult(plugin,
          new SkipReason.UnsatisfiedRuntimeRequirement(RuntimeRequirement.NODEJS, nodeCurrentVersion.get().toString(), nodeMinVersion.get().toString()));
      }
    }
    return new PluginRequirementsCheckResult(plugin, null);
  }

  /**
   * Find out if this plugin is compatible with a given version of SonarQube.
   * The version of SQ must be greater than or equal to the minimal version
   * needed by the plugin.
   */
  static boolean isCompatibleWith(SonarPluginManifest manifest, String implementedApi) {
    Optional<Version> sonarMinVersion = manifest.getSonarMinVersion();
    if (sonarMinVersion.isEmpty()) {
      // no constraint defined on the plugin
      return true;
    }

    // Ignore patch and build numbers since this should not change API compatibility
    Version requestedApi = Version.create(sonarMinVersion.get().getMajor() + "." + sonarMinVersion.get().getMinor());
    Version implementedApiVersion = Version.create(implementedApi);
    return implementedApiVersion.compareToIgnoreQualifier(requestedApi) >= 0;
  }

  private static PluginRequirementsCheckResult checkUnsatisfiedPluginDependency(PluginRequirementsCheckResult currentResult,
    Map<String, PluginRequirementsCheckResult> currentResultsByKey) {
    SonarPluginManifest manifest = currentResult.getPlugin().getManifest();
    for (RequiredPlugin required : manifest.getRequiredPlugins()) {
      if ("license".equals(required.getKey())) {
        continue;
      }
      if (Language.JS.getPluginKey().equals(manifest.getKey()) && OLD_SONARTS_PLUGIN_KEY.equals(required.getKey())) {
        // Workaround for SLCORE-259
        // This dependency was added to ease migration on SonarQube, but can be ignored on SonarLint
        // Note: The dependency was removed in SonarJS 6.3 but we should still keep the workaround as long as we want to support older
        // versions
        continue;
      }
      PluginRequirementsCheckResult depInfo = currentResultsByKey.get(required.getKey());
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
    String basePluginKey = manifest.getBasePluginKey();
    if (basePluginKey != null) {
      PluginRequirementsCheckResult baseInfo = currentResultsByKey.get(basePluginKey);
      if (baseInfo == null || baseInfo.isSkipped()) {
        LOG.debug("Plugin '{}' dependency on '{}' is unsatisfied. Skip loading it.", currentResult.getPlugin().getName(), basePluginKey);
        return new PluginRequirementsCheckResult(currentResult.getPlugin(), new SkipReason.UnsatisfiedDependency(basePluginKey));
      }
    }
    return currentResult;
  }

}
