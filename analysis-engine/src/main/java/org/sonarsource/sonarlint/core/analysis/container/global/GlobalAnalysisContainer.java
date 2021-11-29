/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.global;

import java.util.Map;
import org.sonar.api.SonarQubeVersion;
import org.sonar.api.utils.Version;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.container.ContainerLifespan;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleRegistry;
import org.sonarsource.sonarlint.core.plugin.common.ApiVersions;
import org.sonarsource.sonarlint.core.plugin.common.PluginInstancesRepository;
import org.sonarsource.sonarlint.core.plugin.common.PluginInstancesRepository.Configuration;
import org.sonarsource.sonarlint.core.plugin.common.load.PluginRequirementsCheckResult;
import org.sonarsource.sonarlint.core.plugin.common.pico.ComponentContainer;
import org.sonarsource.sonarlint.core.plugin.common.sonarapi.SonarLintRuntimeImpl;
import org.sonarsource.sonarlint.plugin.api.SonarLintRuntime;

public class GlobalAnalysisContainer extends ComponentContainer {

  private final ModuleRegistry moduleRegistry;
  private final AnalysisEngineConfiguration globalConfig;
  private Map<String, PluginRequirementsCheckResult> pluginCheckResultByKeys;

  public GlobalAnalysisContainer(AnalysisEngineConfiguration globalConfig) {
    this.globalConfig = globalConfig;
    this.moduleRegistry = new ModuleRegistry(this, globalConfig.getClientFileSystem());
  }

  @Override
  protected void doBeforeStart() {
    Version sonarPluginApiVersion = ApiVersions.loadSonarPluginApiVersion();
    Version sonarlintPluginApiVersion = ApiVersions.loadSonarLintPluginApiVersion();

    Configuration pluginConfiguration = new Configuration(globalConfig.getPluginsJarPaths(), globalConfig.getEnabledLanguages(), globalConfig.getNodeJsVersion());
    PluginInstancesRepository pluginInstancesRepository = new PluginInstancesRepository(pluginConfiguration);
    pluginInstancesRepository.getPluginInstancesByKeys().values().forEach(this::declareProperties);
    pluginCheckResultByKeys = pluginInstancesRepository.getPluginCheckResultByKeys();

    SonarLintRuntime sonarLintRuntime = new SonarLintRuntimeImpl(sonarPluginApiVersion, sonarlintPluginApiVersion, globalConfig.getClientPid());
    MapSettings globalSettings = new MapSettings(getPropertyDefinitions(), globalConfig.getEffectiveConfig());
    AnalysisExtensionInstaller analysisExtensionInstaller = new AnalysisExtensionInstaller(sonarLintRuntime, pluginInstancesRepository, globalSettings.asConfig(), globalConfig);
    add(
      globalConfig,
      analysisExtensionInstaller,
      globalSettings,
      globalSettings.asConfig(),
      pluginInstancesRepository,
      new GlobalTempFolderProvider(),
      new SonarQubeVersion(sonarPluginApiVersion),
      sonarLintRuntime);
    // Add plugin instance level extensions
    analysisExtensionInstaller.install(this, ContainerLifespan.INSTANCE);
  }

  @Override
  public ComponentContainer stopComponents(boolean swallowException) {
    try {
      if (moduleRegistry != null) {
        moduleRegistry.stopAll();
      }
    } finally {
      super.stopComponents(swallowException);
    }
    return this;
  }

  public ModuleRegistry getModuleRegistry() {
    return moduleRegistry;
  }

  public Map<String, PluginRequirementsCheckResult> getPluginCheckResultByKeys() {
    return pluginCheckResultByKeys;
  }

}
