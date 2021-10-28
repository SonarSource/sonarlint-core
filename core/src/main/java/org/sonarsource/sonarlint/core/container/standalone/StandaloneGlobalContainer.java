/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.standalone;

import java.time.Clock;
import java.util.Collection;
import org.sonar.api.Plugin;
import org.sonar.api.SonarQubeVersion;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.UriReader;
import org.sonar.api.utils.Version;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.global.ExtensionInstaller;
import org.sonarsource.sonarlint.core.container.global.GlobalConfigurationProvider;
import org.sonarsource.sonarlint.core.container.global.GlobalSettings;
import org.sonarsource.sonarlint.core.container.global.GlobalTempFolderProvider;
import org.sonarsource.sonarlint.core.container.global.MetadataLoader;
import org.sonarsource.sonarlint.core.container.global.SonarLintRuntimeImpl;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneActiveRules;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRuleRepositoryContainer;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginJarExploder;
import org.sonarsource.sonarlint.core.plugin.PluginClassloaderFactory;
import org.sonarsource.sonarlint.core.plugin.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.PluginInfosLoader;
import org.sonarsource.sonarlint.core.plugin.PluginInstancesLoader;
import org.sonarsource.sonarlint.core.plugin.PluginRepository;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;

public class StandaloneGlobalContainer extends ComponentContainer {

  private Rules rules;
  private StandaloneActiveRules standaloneActiveRules;
  private final StandaloneGlobalConfiguration globalConfig;
  private Collection<PluginDetails> pluginDetails;

  public StandaloneGlobalContainer(StandaloneGlobalConfiguration globalConfig) {
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
      GlobalSettings.class,
      NodeJsHelper.class,
      new GlobalConfigurationProvider(),
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
    loadRulesAndActiveRulesFromPlugins();
  }

  private void installPlugins() {
    PluginRepository pluginRepository = getComponentByType(PluginRepository.class);
    for (PluginInfo pluginInfo : pluginRepository.getActivePluginInfos()) {
      Plugin instance = pluginRepository.getPluginInstance(pluginInfo.getKey());
      addExtension(pluginInfo, instance);
    }
  }

  private void loadRulesAndActiveRulesFromPlugins() {
    StandaloneRuleRepositoryContainer container = new StandaloneRuleRepositoryContainer(this);
    container.execute();
    rules = container.getRules();
    standaloneActiveRules = container.getStandaloneActiveRules();
    pluginDetails = getComponentByType(PluginRepository.class).getPluginDetails();
  }

  public Collection<PluginDetails> getPluginDetails() {
    return pluginDetails;
  }

  public Rules getRules() {
    return rules;
  }

  public StandaloneActiveRules getStandaloneActiveRules() {
    return standaloneActiveRules;
  }

}
