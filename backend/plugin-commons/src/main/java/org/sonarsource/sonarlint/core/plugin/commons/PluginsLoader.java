/*
 * SonarLint Core - Plugin Commons
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.plugin.commons;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sonar.api.utils.System2;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInstancesLoader;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginRequirementsCheckResult;
import org.sonarsource.sonarlint.core.plugin.commons.loading.SonarPluginRequirementsChecker;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;

/**
 * Orchestrates the loading and instantiation of plugins
 */
public class PluginsLoader {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final SonarPluginRequirementsChecker requirementsChecker = new SonarPluginRequirementsChecker();

  public static class Configuration {
    private final Set<Path> pluginJarLocations;
    private final Set<SonarLanguage> enabledLanguages;
    private final boolean shouldCheckNodeVersion;
    private final Optional<Version> nodeCurrentVersion;
    private final boolean enableDataflowBugDetection;

    public Configuration(Set<Path> pluginJarLocations, Set<SonarLanguage> enabledLanguages, boolean enableDataflowBugDetection) {
      this.pluginJarLocations = pluginJarLocations;
      this.enabledLanguages = enabledLanguages;
      this.nodeCurrentVersion = Optional.empty();
      this.enableDataflowBugDetection = enableDataflowBugDetection;
      this.shouldCheckNodeVersion = false;
    }

    public Configuration(Set<Path> pluginJarLocations, Set<SonarLanguage> enabledLanguages, boolean enableDataflowBugDetection, Optional<Version> nodeCurrentVersion) {
      this.pluginJarLocations = pluginJarLocations;
      this.enabledLanguages = enabledLanguages;
      this.nodeCurrentVersion = nodeCurrentVersion;
      this.enableDataflowBugDetection = enableDataflowBugDetection;
      this.shouldCheckNodeVersion = true;
    }
  }

  public PluginsLoadResult load(Configuration configuration) {
    var javaSpecVersion = Objects.requireNonNull(System2.INSTANCE.property("java.specification.version"), "Missing Java property 'java.specification.version'");
    var pluginCheckResultByKeys = requirementsChecker.checkRequirements(configuration.pluginJarLocations, configuration.enabledLanguages, Version.create(javaSpecVersion),
      configuration.shouldCheckNodeVersion, configuration.nodeCurrentVersion, configuration.enableDataflowBugDetection);

    var nonSkippedPlugins = getNonSkippedPlugins(pluginCheckResultByKeys);
    logPlugins(nonSkippedPlugins);

    var instancesLoader = new PluginInstancesLoader();
    var pluginInstancesByKeys = instancesLoader.instantiatePluginClasses(nonSkippedPlugins);

    return new PluginsLoadResult(new LoadedPlugins(pluginInstancesByKeys, instancesLoader, additionalAllowedPlugins(configuration)),
      pluginCheckResultByKeys);
  }

  private static Set<String> additionalAllowedPlugins(Configuration configuration) {
    var allowedPluginsIds = new HashSet<String>();
    allowedPluginsIds.add("textenterprise");
    allowedPluginsIds.addAll(maybeDbdAllowedPlugins(configuration.enableDataflowBugDetection));
    return Collections.unmodifiableSet(allowedPluginsIds);
  }

  private static Set<String> maybeDbdAllowedPlugins(boolean enableDataflowBugDetection) {
    return DataflowBugDetection.getPluginAllowList(enableDataflowBugDetection);
  }

  private static void logPlugins(Collection<PluginInfo> nonSkippedPlugins) {
    LOG.debug("Loaded {} plugins", nonSkippedPlugins.size());
    for (PluginInfo p : nonSkippedPlugins) {
      LOG.debug("  * {} {} ({})", p.getName(), p.getVersion(), p.getKey());
    }
  }

  private static Collection<PluginInfo> getNonSkippedPlugins(Map<String, PluginRequirementsCheckResult> pluginCheckResultByKeys) {
    return pluginCheckResultByKeys.values().stream()
      .filter(not(PluginRequirementsCheckResult::isSkipped))
      .map(PluginRequirementsCheckResult::getPlugin)
      .collect(toList());
  }
}
