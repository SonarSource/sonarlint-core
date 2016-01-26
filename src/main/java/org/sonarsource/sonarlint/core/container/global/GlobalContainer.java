/*
 * SonarLint Core Library
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.global;

import java.util.List;
import org.sonar.api.SonarPlugin;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.UriReader;
import org.sonarsource.sonarlint.core.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.AnalysisResults;
import org.sonarsource.sonarlint.core.IssueListener;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.PluginInfo;
import org.sonarsource.sonarlint.core.container.analysis.AnalysisContainer;
import org.sonarsource.sonarlint.core.container.analysis.DefaultAnalysisResult;
import org.sonarsource.sonarlint.core.container.rule.OfflineRuleRepositoryContainer;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginJarExploder;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginRepository;
import org.sonarsource.sonarlint.core.plugin.PluginClassloaderFactory;
import org.sonarsource.sonarlint.core.plugin.PluginDownloader;
import org.sonarsource.sonarlint.core.plugin.PluginLoader;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;

public class GlobalContainer extends ComponentContainer {

  private Rules rules;
  private ActiveRules activeRules;

  public static GlobalContainer create(List<?> extensions) {
    GlobalContainer container = new GlobalContainer();
    container.add(extensions);
    return container;
  }

  @Override
  protected void doBeforeStart() {
    add(
      DefaultPluginRepository.class,
      PluginDownloader.class,
      PluginLoader.class,
      PluginClassloaderFactory.class,
      DefaultPluginJarExploder.class,
      ExtensionInstaller.class,

      new GlobalTempFolderProvider(),
      UriReader.class,
      new PluginCacheProvider(),
      System2.INSTANCE);
  }

  @Override
  protected void doAfterStart() {
    installPlugins();

    loadRulesAndActiveRulesFromPlugins();
  }

  private void loadRulesAndActiveRulesFromPlugins() {
    OfflineRuleRepositoryContainer container = new OfflineRuleRepositoryContainer(this);
    container.execute();
    rules = container.getRules();
    activeRules = container.getActiveRules();
  }

  private void installPlugins() {
    DefaultPluginRepository pluginRepository = getComponentByType(DefaultPluginRepository.class);
    for (PluginInfo pluginInfo : pluginRepository.getPluginInfos()) {
      SonarPlugin instance = pluginRepository.getPluginInstance(pluginInfo.getKey());
      addExtension(pluginInfo, instance);
    }
  }

  public AnalysisResults analyze(AnalysisConfiguration configuration, IssueListener issueListener) {
    AnalysisContainer analysisContainer = new AnalysisContainer(this);
    analysisContainer.add(configuration);
    analysisContainer.add(issueListener);
    analysisContainer.add(rules);
    analysisContainer.add(activeRules);
    DefaultAnalysisResult defaultAnalysisResult = new DefaultAnalysisResult();
    analysisContainer.add(defaultAnalysisResult);
    analysisContainer.execute();
    return defaultAnalysisResult;
  }

}
