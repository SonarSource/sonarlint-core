/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonar.api.Plugin;
import org.sonar.api.SonarQubeVersion;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.internal.MetadataLoader;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.UriReader;
import org.sonar.api.utils.Version;
import org.sonarsource.sonarlint.core.analyzer.sensor.SensorsExecutor;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.LoadedAnalyzer;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.analysis.AnalysisContainer;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.global.ExtensionInstaller;
import org.sonarsource.sonarlint.core.container.global.GlobalConfigurationProvider;
import org.sonarsource.sonarlint.core.container.global.GlobalExtensionContainer;
import org.sonarsource.sonarlint.core.container.global.GlobalSettings;
import org.sonarsource.sonarlint.core.container.global.GlobalTempFolderProvider;
import org.sonarsource.sonarlint.core.container.model.DefaultAnalysisResult;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneActiveRules;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRuleRepositoryContainer;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginJarExploder;
import org.sonarsource.sonarlint.core.plugin.PluginCacheLoader;
import org.sonarsource.sonarlint.core.plugin.PluginClassloaderFactory;
import org.sonarsource.sonarlint.core.plugin.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.PluginLoader;
import org.sonarsource.sonarlint.core.plugin.PluginRepository;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class StandaloneGlobalContainer extends ComponentContainer {

  private Rules rules;
  private StandaloneActiveRules standaloneActiveRules;
  private GlobalExtensionContainer globalExtensionContainer;

  public static StandaloneGlobalContainer create(StandaloneGlobalConfiguration globalConfig) {
    StandaloneGlobalContainer container = new StandaloneGlobalContainer();
    container.add(globalConfig);
    container.add(new StandalonePluginUrls(globalConfig.getPluginUrls()));
    return container;
  }

  @Override
  protected void doBeforeStart() {
    Version version = MetadataLoader.loadVersion(System2.INSTANCE);
    add(
      StandalonePluginIndex.class,
      PluginRepository.class,
      PluginVersionChecker.class,
      PluginCacheLoader.class,
      PluginLoader.class,
      PluginClassloaderFactory.class,
      DefaultPluginJarExploder.class,
      GlobalSettings.class,
      new GlobalConfigurationProvider(),
      ExtensionInstaller.class,
      new SonarQubeVersion(version),
      SonarRuntimeImpl.forSonarLint(version),

      new GlobalTempFolderProvider(),
      UriReader.class,
      new PluginCacheProvider(),
      System2.INSTANCE);
  }

  @Override
  protected void doAfterStart() {
    installPlugins();
    loadRulesAndActiveRulesFromPlugins();
    globalExtensionContainer = new GlobalExtensionContainer(this);
    globalExtensionContainer.startComponents();
  }

  @Override
  public ComponentContainer stopComponents(boolean swallowException) {
    try {
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
    for (PluginInfo pluginInfo : pluginRepository.getPluginInfos()) {
      Plugin instance = pluginRepository.getPluginInstance(pluginInfo.getKey());
      addExtension(pluginInfo, instance);
    }
  }

  private void loadRulesAndActiveRulesFromPlugins() {
    StandaloneRuleRepositoryContainer container = new StandaloneRuleRepositoryContainer(this);
    container.execute();
    rules = container.getRules();
    standaloneActiveRules = container.getStandaloneActiveRules();
  }

  public AnalysisResults analyze(StandaloneAnalysisConfiguration configuration, IssueListener issueListener, ProgressWrapper progress) {
    AnalysisContainer analysisContainer = new AnalysisContainer(globalExtensionContainer, progress);
    analysisContainer.add(configuration);
    analysisContainer.add(issueListener);
    analysisContainer.add(rules);
    // TODO configuration should be set directly with Strings
    Set<String> excludedRules = configuration.excludedRules().stream().map(RuleKey::toString).collect(Collectors.toSet());
    Set<String> includedRules = configuration.includedRules().stream()
      .map(RuleKey::toString)
      .filter(r -> !excludedRules.contains(r))
      .collect(Collectors.toSet());
    analysisContainer.add(standaloneActiveRules.filtered(excludedRules, includedRules));
    analysisContainer.add(SensorsExecutor.class);
    DefaultAnalysisResult defaultAnalysisResult = new DefaultAnalysisResult();
    analysisContainer.add(defaultAnalysisResult);
    analysisContainer.execute();
    return defaultAnalysisResult;
  }

  public Collection<LoadedAnalyzer> getLoadedAnalyzers() {
    PluginRepository pluginRepository = getComponentByType(PluginRepository.class);
    return pluginRepository.getLoadedAnalyzers();
  }

  @CheckForNull
  public RuleDetails getRuleDetails(String ruleKeyStr) {
    return standaloneActiveRules.ruleDetails(ruleKeyStr);
  }

  public Collection<String> getActiveRuleKeys() {
    return standaloneActiveRules.activeRulesByDefault().stream()
      .map(rule -> rule.ruleKey().toString())
      .collect(Collectors.toList());
  }

  public Collection<RuleDetails> getAllRuleDetails() {
    return standaloneActiveRules.allRuleDetails();
  }
}
