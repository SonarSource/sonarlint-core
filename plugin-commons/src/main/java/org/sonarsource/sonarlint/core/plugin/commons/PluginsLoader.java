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
package org.sonarsource.sonarlint.core.plugin.commons;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.Version;
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
  private static final Logger LOG = Loggers.get(PluginsLoader.class);
  private final SonarPluginRequirementsChecker requirementsChecker = new SonarPluginRequirementsChecker();

  public static class Configuration {
    private final Set<Path> pluginJarLocations;
    private final Set<Language> enabledLanguages;
    private final boolean shouldCheckNodeVersion;
    private final Optional<Version> nodeCurrentVersion;

    public Configuration(Set<Path> pluginJarLocations, Set<Language> enabledLanguages) {
      this.pluginJarLocations = pluginJarLocations;
      this.enabledLanguages = enabledLanguages;
      this.nodeCurrentVersion = Optional.empty();
      this.shouldCheckNodeVersion = false;
    }

    public Configuration(Set<Path> pluginJarLocations, Set<Language> enabledLanguages, Optional<Version> nodeCurrentVersion) {
      this.pluginJarLocations = pluginJarLocations;
      this.enabledLanguages = enabledLanguages;
      this.nodeCurrentVersion = nodeCurrentVersion;
      this.shouldCheckNodeVersion = true;
    }
  }

  public PluginsLoadResult load(Configuration configuration) {
    var javaSpecVersion = Objects.requireNonNull(System2.INSTANCE.property("java.specification.version"), "Missing Java property 'java.specification.version'");
    var pluginCheckResultByKeys = requirementsChecker.checkRequirements(configuration.pluginJarLocations, configuration.enabledLanguages, Version.create(javaSpecVersion),
      configuration.shouldCheckNodeVersion, configuration.nodeCurrentVersion);

    var nonSkippedPlugins = getNonSkippedPlugins(pluginCheckResultByKeys);
    logPlugins(nonSkippedPlugins);

    var instancesLoader = new PluginInstancesLoader();
    var pluginInstancesByKeys = instancesLoader.instantiatePluginClasses(nonSkippedPlugins);

    return new PluginsLoadResult(new LoadedPlugins(pluginInstancesByKeys, instancesLoader), pluginCheckResultByKeys);
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
