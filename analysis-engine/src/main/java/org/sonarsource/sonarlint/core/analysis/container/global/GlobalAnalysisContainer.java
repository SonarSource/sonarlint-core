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

import java.time.Clock;
import org.sonar.api.Plugin;
import org.sonar.api.SonarQubeVersion;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.UriReader;
import org.sonar.api.utils.Version;
import org.sonarsource.sonarlint.core.analysis.api.GlobalAnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.container.ComponentContainer;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleRegistry;
import org.sonarsource.sonarlint.core.analysis.plugin.DefaultPluginJarExploder;
import org.sonarsource.sonarlint.core.analysis.plugin.PluginClassloaderFactory;
import org.sonarsource.sonarlint.core.analysis.plugin.PluginInfo;
import org.sonarsource.sonarlint.core.analysis.plugin.PluginInfosLoader;
import org.sonarsource.sonarlint.core.analysis.plugin.PluginInstancesLoader;
import org.sonarsource.sonarlint.core.analysis.plugin.PluginRepository;
import org.sonarsource.sonarlint.core.analysis.plugin.PluginVersionChecker;
import org.sonarsource.sonarlint.core.analysis.plugin.cache.PluginCacheProvider;

public class GlobalAnalysisContainer extends ComponentContainer {

  private GlobalExtensionContainer globalExtensionContainer;
  private ModuleRegistry moduleRegistry;
  private final GlobalAnalysisConfiguration globalConfig;

  public GlobalAnalysisContainer(GlobalAnalysisConfiguration globalConfig) {
    this.globalConfig = globalConfig;
  }

  @Override
  protected void doBeforeStart() {
    Version sonarPluginApiVersion = MetadataLoader.loadSonarPluginApiVersion();
    Version sonarlintPluginApiVersion = MetadataLoader.loadSonarLintPluginApiVersion();

    add(
      globalConfig,
      new StandalonePluginUrls(globalConfig.getPluginUrls()),
      StandalonePluginIndex.class,
      PluginRepository.class,
      PluginVersionChecker.class,
      PluginInfosLoader.class,
      PluginInstancesLoader.class,
      PluginClassloaderFactory.class,
      DefaultPluginJarExploder.class,
      new PluginApiGlobalSettingsProvider(),
      new PluginApiConfigurationProvider(),
      ExtensionInstaller.class,
      new SonarQubeVersion(sonarPluginApiVersion),
      new SonarLintRuntimeImpl(sonarPluginApiVersion, sonarlintPluginApiVersion, globalConfig.getClientPid()),

      new GlobalTempFolderProvider(),
      UriReader.class,
      new PluginCacheProvider(),
      Clock.systemDefaultZone(),
      System2.INSTANCE);
  }

  @Override
  protected void doAfterStart() {
    installPlugins();
    globalExtensionContainer = new GlobalExtensionContainer(this);
    globalExtensionContainer.startComponents();
    GlobalAnalysisConfiguration globalConfiguration = this.getComponentByType(GlobalAnalysisConfiguration.class);
    this.moduleRegistry = new ModuleRegistry(globalExtensionContainer, globalConfiguration.getModulesProvider());
  }

  @Override
  public ComponentContainer stopComponents(boolean swallowException) {
    try {
      if (moduleRegistry != null) {
        moduleRegistry.stopAll();
      }
      if (globalExtensionContainer != null) {
        globalExtensionContainer.stopComponents(swallowException);
      }
    } finally {
      super.stopComponents(swallowException);
    }
    return this;
  }

  private void installPlugins() {
    PluginRepository pluginRepository = getComponentByType(PluginRepository.class);
    for (PluginInfo pluginInfo : pluginRepository.getActivePluginInfos()) {
      Plugin instance = pluginRepository.getPluginInstance(pluginInfo.getKey());
      addExtension(pluginInfo, instance);
    }
  }

  public ModuleRegistry getModuleRegistry() {
    return moduleRegistry;
  }

}
