/*
 * SonarLint Core - Plugin Commons
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
package org.sonarsource.sonarlint.core.plugin.commons;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sonar.api.Plugin;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInstancesLoader;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginLocation;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginRequirementsCheckResult;
import org.sonarsource.sonarlint.core.plugin.commons.loading.SonarPluginRequirementsChecker;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;

/**
 * Orchestrates the loading and instantiation of plugins
 */
public class PluginInstancesRepository implements AutoCloseable {
  private static final Logger LOG = Loggers.get(PluginInstancesRepository.class);

  private final SonarPluginRequirementsChecker pluginRequirementChecker;
  private final PluginInstancesLoader pluginInstancesLoader;
  private final Configuration configuration;
  private final System2 system2;

  private Map<String, Plugin> pluginInstancesByKeys;
  private Map<String, PluginRequirementsCheckResult> pluginCheckResultByKeys;

  public static class Configuration {
    private final List<PluginLocation> pluginJarLocations;
    private final Set<Language> enabledLanguages;
    private final Optional<Version> nodeCurrentVersion;

    public Configuration(List<PluginLocation> pluginJarLocations, Set<Language> enabledLanguages, Optional<Version> nodeCurrentVersion) {
      this.pluginJarLocations = pluginJarLocations;
      this.enabledLanguages = enabledLanguages;
      this.nodeCurrentVersion = nodeCurrentVersion;
    }
  }

  public PluginInstancesRepository(Configuration configuration) {
    this(configuration, new SonarPluginRequirementsChecker(), new PluginInstancesLoader(), System2.INSTANCE);
  }

  PluginInstancesRepository(Configuration configuration, SonarPluginRequirementsChecker pluginRequirementChecker, PluginInstancesLoader pluginInstancesLoader, System2 system2) {
    this.configuration = configuration;
    this.pluginRequirementChecker = pluginRequirementChecker;
    this.pluginInstancesLoader = pluginInstancesLoader;
    this.system2 = system2;
    init();
  }

  public void init() {
    String javaSpecVersion = Objects.requireNonNull(system2.property("java.specification.version"), "Missing Java property 'java.specification.version'");
    pluginCheckResultByKeys = pluginRequirementChecker.checkRequirements(configuration.pluginJarLocations, configuration.enabledLanguages, Version.create(javaSpecVersion),
      configuration.nodeCurrentVersion);
    Collection<PluginInfo> nonSkippedPlugins = getNonSkippedPlugins();
    pluginInstancesByKeys = pluginInstancesLoader.instantiatePluginClasses(nonSkippedPlugins);

    logPlugins(nonSkippedPlugins);
  }

  private static void logPlugins(Collection<PluginInfo> nonSkippedPlugins) {
    LOG.debug("Loaded {} plugins", nonSkippedPlugins.size());
    for (PluginInfo p : nonSkippedPlugins) {
      LOG.debug("  * {} {} ({})", p.getName(), p.getVersion(), p.getKey());
    }
  }

  @Override
  public void close() throws Exception {
    if (pluginInstancesByKeys != null && !pluginInstancesByKeys.isEmpty()) {
      LOG.debug("Unloading plugins");
      // close plugins classloaders
      pluginInstancesLoader.unload(pluginInstancesByKeys.values());

      pluginInstancesByKeys.clear();
      pluginCheckResultByKeys.clear();
    }
  }

  private Collection<PluginInfo> getNonSkippedPlugins() {
    return pluginCheckResultByKeys.values().stream()
      .filter(not(PluginRequirementsCheckResult::isSkipped))
      .map(PluginRequirementsCheckResult::getPlugin)
      .collect(toList());
  }

  public Map<String, PluginRequirementsCheckResult> getPluginCheckResultByKeys() {
    return Map.copyOf(pluginCheckResultByKeys);
  }

  public Map<String, Plugin> getPluginInstancesByKeys() {
    return Map.copyOf(pluginInstancesByKeys);
  }

}
