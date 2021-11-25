/*
 * SonarLint Core - Rule Extractor
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
package org.sonarsource.sonarlint.core.rule.extractor;

import java.time.Clock;
import java.util.Collection;
import org.sonar.api.Plugin;
import org.sonar.api.SonarQubeVersion;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.UriReader;
import org.sonar.api.utils.Version;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.global.GlobalConfigurationProvider;
import org.sonarsource.sonarlint.core.container.global.GlobalSettings;
import org.sonarsource.sonarlint.core.container.global.GlobalTempFolderProvider;
import org.sonarsource.sonarlint.core.container.global.SonarLintRuntimeImpl;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneActiveRules;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRuleRepositoryContainer;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;
import org.sonarsource.sonarlint.core.plugin.common.DefaultPluginJarExploder;
import org.sonarsource.sonarlint.core.plugin.common.ApiVersions;
import org.sonarsource.sonarlint.core.plugin.common.PluginInstancesRepository;
import org.sonarsource.sonarlint.core.plugin.common.load.PluginClassloaderFactory;
import org.sonarsource.sonarlint.core.plugin.common.load.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.common.load.PluginInfosLoader;
import org.sonarsource.sonarlint.core.plugin.common.load.PluginInstancesLoader;
import org.sonarsource.sonarlint.core.plugin.common.PluginMinVersions;
import org.sonarsource.sonarlint.core.plugin.common.pico.ComponentContainer;

public class StandaloneGlobalContainer extends ComponentContainer {

  private Rules rules;
  private StandaloneActiveRules standaloneActiveRules;
  private final StandaloneGlobalConfiguration globalConfig;

  public StandaloneGlobalContainer(StandaloneGlobalConfiguration globalConfig) {
    this.globalConfig = globalConfig;
  }

  @Override
  protected void doBeforeStart() {
    Version sonarPluginApiVersion = ApiVersions.loadSonarPluginApiVersion();
    Version sonarlintPluginApiVersion = ApiVersions.loadSonarLintPluginApiVersion();

    add(
      globalConfig,
      new StandalonePluginUrls(globalConfig.getPluginUrls()),
      StandalonePluginIndex.class,
      PluginInstancesRepository.class,
      PluginMinVersions.class,
      PluginInfosLoader.class,
      PluginInstancesLoader.class,
      PluginClassloaderFactory.class,
      DefaultPluginJarExploder.class,
      GlobalSettings.class,
      new GlobalConfigurationProvider(),
      ExtensionInstaller.class,
      new SonarQubeVersion(sonarPluginApiVersion),
      new SonarLintRuntimeImpl(sonarPluginApiVersion, sonarlintPluginApiVersion, globalConfig.getClientPid()),

      new GlobalTempFolderProvider(),
      UriReader.class,
      new PluginCacheProvider(),
      Clock.systemDefaultZone(),
      System2.INSTANCE,
      StandaloneRuleDefinitionsLoader.class,
      new StandaloneSonarLintRulesProvider(),
      new EmptyConfiguration());

    getComponentByType(ExtensionInstaller.class).installEmbeddedOnly(this, ContainerLifespan.ANALYSIS);
  }

  @Override
  protected void doAfterStart() {
    installPlugins();
    loadRulesAndActiveRulesFromPlugins();
  }

  private void installPlugins() {
    PluginInstancesRepository pluginRepository = getComponentByType(PluginInstancesRepository.class);
    for (PluginInfo pluginInfo : pluginRepository.getActivePluginInfos()) {
      Plugin instance = pluginRepository.getPluginInstance(pluginInfo.getKey());
      addExtension(pluginInfo.getKey(), instance);
    }
  }

  private void loadRulesAndActiveRulesFromPlugins() {
    StandaloneRuleRepositoryContainer container = new StandaloneRuleRepositoryContainer(this);
    container.execute();
    rules = container.getRules();
    standaloneActiveRules = container.getStandaloneActiveRules();
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
