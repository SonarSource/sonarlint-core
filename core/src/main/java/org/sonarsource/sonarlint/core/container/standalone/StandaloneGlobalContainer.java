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

import java.nio.file.Path;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonar.api.SonarQubeVersion;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.UriReader;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.analyzer.sensor.SensorsExecutor;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.AnalysisExtensionInstaller;
import org.sonarsource.sonarlint.core.container.analysis.AnalysisContainer;
import org.sonarsource.sonarlint.core.container.global.GlobalConfigurationProvider;
import org.sonarsource.sonarlint.core.container.global.GlobalExtensionContainer;
import org.sonarsource.sonarlint.core.container.global.GlobalSettings;
import org.sonarsource.sonarlint.core.container.global.GlobalTempFolderProvider;
import org.sonarsource.sonarlint.core.container.model.DefaultAnalysisResult;
import org.sonarsource.sonarlint.core.container.model.DefaultLoadedAnalyzer;
import org.sonarsource.sonarlint.core.container.module.ModuleRegistry;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneActiveRules;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRuleRepositoryContainer;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.plugin.commons.ApiVersions;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository.Configuration;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginLocation;
import org.sonarsource.sonarlint.core.plugin.commons.pico.ComponentContainer;
import org.sonarsource.sonarlint.core.plugin.commons.sonarapi.SonarLintRuntimeImpl;

public class StandaloneGlobalContainer extends ComponentContainer {

  private Rules rules;
  private StandaloneActiveRules standaloneActiveRules;
  private GlobalExtensionContainer globalExtensionContainer;
  private ModuleRegistry moduleRegistry;
  private final StandaloneGlobalConfiguration globalConfig;

  public StandaloneGlobalContainer(StandaloneGlobalConfiguration globalConfig) {
    this.globalConfig = globalConfig;
  }

  @Override
  protected void doBeforeStart() {
    var sonarPluginApiVersion = ApiVersions.loadSonarPluginApiVersion();
    var sonarlintPluginApiVersion = ApiVersions.loadSonarLintPluginApiVersion();

    Path cacheDir = globalConfig.getSonarLintUserHome().resolve("plugins");
    var fileCache = PluginCache.create(cacheDir);

    var plugins = globalConfig.getPluginUrls().stream()
      .map(fileCache::getFromCacheOrCopy)
      .map(r -> fileCache.get(r.getFilename(), r.getHash()))
      .map(p -> new PluginLocation(p, true))
      .collect(Collectors.toList());

    var config = new Configuration(plugins, globalConfig.getEnabledLanguages(), Optional.ofNullable(globalConfig.getNodeJsVersion()));
    var pluginInstancesRepository = new PluginInstancesRepository(config);

    add(
      globalConfig,
      pluginInstancesRepository,
      GlobalSettings.class,
      NodeJsHelper.class,
      new GlobalConfigurationProvider(),
      AnalysisExtensionInstaller.class,
      new SonarQubeVersion(sonarPluginApiVersion),
      new SonarLintRuntimeImpl(sonarPluginApiVersion, sonarlintPluginApiVersion, globalConfig.getClientPid()),

      new GlobalTempFolderProvider(),
      UriReader.class,
      Clock.systemDefaultZone(),
      System2.INSTANCE);
  }

  @Override
  protected void doAfterStart() {
    declarePluginProperties();
    loadRulesAndActiveRulesFromPlugins();
    globalExtensionContainer = new GlobalExtensionContainer(this);
    globalExtensionContainer.startComponents();
    StandaloneGlobalConfiguration globalConfiguration = this.getComponentByType(StandaloneGlobalConfiguration.class);
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

  private void declarePluginProperties() {
    PluginInstancesRepository pluginRepository = getComponentByType(PluginInstancesRepository.class);
    pluginRepository.getPluginInstancesByKeys().values().forEach(this::declareProperties);
  }

  private void loadRulesAndActiveRulesFromPlugins() {
    StandaloneRuleRepositoryContainer container = new StandaloneRuleRepositoryContainer(this);
    container.execute();
    rules = container.getRules();
    standaloneActiveRules = container.getStandaloneActiveRules();
  }

  public AnalysisResults analyze(ComponentContainer moduleContainer, StandaloneAnalysisConfiguration configuration, IssueListener issueListener, ProgressMonitor progress) {
    AnalysisContainer analysisContainer = new AnalysisContainer(moduleContainer, progress);
    analysisContainer.add(configuration);
    analysisContainer.add(issueListener);
    analysisContainer.add(rules);
    Set<String> excludedRules = configuration.excludedRules().stream().map(RuleKey::toString).collect(Collectors.toSet());
    Set<String> includedRules = configuration.includedRules().stream()
      .map(RuleKey::toString)
      .filter(r -> !excludedRules.contains(r))
      .collect(Collectors.toSet());
    Map<String, Map<String, String>> ruleParameters = new HashMap<>();
    configuration.ruleParameters().forEach((k, v) -> ruleParameters.put(k.toString(), v));
    analysisContainer.add(standaloneActiveRules.filtered(excludedRules, includedRules, ruleParameters));
    analysisContainer.add(SensorsExecutor.class);
    DefaultAnalysisResult defaultAnalysisResult = new DefaultAnalysisResult();
    analysisContainer.add(defaultAnalysisResult);
    analysisContainer.execute();
    return defaultAnalysisResult;
  }

  public Collection<PluginDetails> getPluginDetails() {
    PluginInstancesRepository pluginRepository = getComponentByType(PluginInstancesRepository.class);
    return pluginRepository.getPluginCheckResultByKeys().values().stream().map(p -> new DefaultLoadedAnalyzer(p.getPlugin().getKey(), p.getPlugin().getName(),
      Optional.ofNullable(p.getPlugin().getVersion()).map(Version::toString).orElse(null), p.getSkipReason().orElse(null))).collect(Collectors.toList());
  }

  @CheckForNull
  public StandaloneRuleDetails getRuleDetails(String ruleKeyStr) {
    return standaloneActiveRules.ruleDetails(ruleKeyStr);
  }

  public Collection<String> getActiveRuleKeys() {
    return standaloneActiveRules.getActiveRuleKeys();
  }

  public Collection<StandaloneRuleDetails> getAllRuleDetails() {
    return standaloneActiveRules.allRuleDetails();
  }

  public ModuleRegistry getModuleRegistry() {
    return moduleRegistry;
  }

}
