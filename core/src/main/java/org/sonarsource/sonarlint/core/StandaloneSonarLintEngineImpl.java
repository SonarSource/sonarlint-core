/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.client.api.common.ClientModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.analysis.DefaultClientIssue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRule;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository.Configuration;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

public final class StandaloneSonarLintEngineImpl extends AbstractSonarLintEngine implements StandaloneSonarLintEngine {

  private final StandaloneGlobalConfiguration globalConfig;
  private final Collection<PluginDetails> pluginDetails;
  private final Map<String, SonarLintRuleDefinition> allRulesDefinitionsByKey;
  private final AnalysisEngine analysisEngine;

  public StandaloneSonarLintEngineImpl(StandaloneGlobalConfiguration globalConfig) {
    super(globalConfig.getLogOutput());
    this.globalConfig = globalConfig;
    setLogging(null);

    var pluginInstancesRepository = createPluginInstancesRepository();
    pluginDetails = pluginInstancesRepository.getPluginCheckResultByKeys().values().stream()
      .map(c -> new PluginDetails(c.getPlugin().getKey(), c.getPlugin().getName(), c.getPlugin().getVersion().toString(), c.getSkipReason().orElse(null)))
      .collect(Collectors.toList());

    allRulesDefinitionsByKey = loadPluginMetadata(pluginInstancesRepository, globalConfig.getEnabledLanguages(), false);

    var analysisGlobalConfigBuilder = AnalysisEngineConfiguration.builder()
      .addEnabledLanguages(globalConfig.getEnabledLanguages())
      .setClientPid(globalConfig.getClientPid())
      .setExtraProperties(globalConfig.extraProperties())
      .setNodeJs(globalConfig.getNodeJsPath())
      .setWorkDir(globalConfig.getWorkDir());
    var modulesProvider = globalConfig.getModulesProvider();
    if (modulesProvider != null) {
      analysisGlobalConfigBuilder
        .setModulesFileSystemsProvider(() -> modulesProvider.getModules().stream().collect(Collectors.toMap(ClientModuleInfo::key, ClientModuleInfo::fileSystem)));
    }
    this.analysisEngine = new AnalysisEngine(analysisGlobalConfigBuilder.build(), pluginInstancesRepository, logOutput);
  }

  @Override
  public AnalysisEngine getAnalysisEngine() {
    return analysisEngine;
  }

  private PluginInstancesRepository createPluginInstancesRepository() {
    var config = new Configuration(globalConfig.getPluginPaths(), globalConfig.getEnabledLanguages(), Optional.ofNullable(globalConfig.getNodeJsVersion()));
    return new PluginInstancesRepository(config);
  }

  @Override
  public Optional<StandaloneRuleDetails> getRuleDetails(String ruleKey) {
    return Optional.ofNullable(allRulesDefinitionsByKey.get(ruleKey)).map(StandaloneRule::new);
  }

  @Override
  public Collection<StandaloneRuleDetails> getAllRuleDetails() {
    return allRulesDefinitionsByKey.values().stream().map(StandaloneRule::new).collect(Collectors.toList());
  }

  @Override
  public AnalysisResults analyze(StandaloneAnalysisConfiguration configuration, IssueListener issueListener, @Nullable ClientLogOutput logOutput,
    @Nullable ClientProgressMonitor monitor) {
    requireNonNull(configuration);
    requireNonNull(issueListener);
    setLogging(logOutput);

    var analysisConfig = AnalysisConfiguration.builder()
      .addInputFiles(configuration.inputFiles())
      .putAllExtraProperties(configuration.extraProperties())
      .addActiveRules(identifyActiveRules(configuration))
      .setBaseDir(configuration.baseDir())
      .build();

    var analyzeCommand = new AnalyzeCommand(configuration.moduleKey(), analysisConfig,
      i -> issueListener.handle(new DefaultClientIssue(i, allRulesDefinitionsByKey.get(i.getRuleKey()))),
      logOutput);
    return postAnalysisCommandAndGetResult(analyzeCommand, monitor);
  }

  private Collection<ActiveRule> identifyActiveRules(StandaloneAnalysisConfiguration configuration) {
    Set<String> excludedRules = configuration.excludedRules().stream().map(RuleKey::toString).collect(toSet());
    Set<String> includedRules = configuration.includedRules().stream().map(RuleKey::toString)
      .filter(r -> !excludedRules.contains(r))
      .collect(toSet());

    Collection<SonarLintRuleDefinition> filteredActiveRules = new ArrayList<>();

    filteredActiveRules.addAll(allRulesDefinitionsByKey.values().stream()
      .filter(SonarLintRuleDefinition::isActiveByDefault)
      .filter(isExcludedByConfiguration(excludedRules))
      .collect(Collectors.toList()));
    filteredActiveRules.addAll(allRulesDefinitionsByKey.values().stream()
      .filter(r -> !r.isActiveByDefault())
      .filter(isIncludedByConfiguration(includedRules))
      .collect(Collectors.toList()));

    return filteredActiveRules.stream().map(rd -> {
      var activeRule = new ActiveRule(rd.getKey(), rd.getLanguage().getLanguageKey());
      Map<String, String> effectiveParams = new HashMap<>(rd.getDefaultParams());
      Optional.ofNullable(configuration.ruleParameters().get(RuleKey.parse(rd.getKey()))).ifPresent(effectiveParams::putAll);
      activeRule.setParams(effectiveParams);
      return activeRule;
    }).collect(Collectors.toList());
  }

  private static Predicate<? super SonarLintRuleDefinition> isExcludedByConfiguration(Set<String> excludedRules) {
    return r -> {
      if (excludedRules.contains(r.getKey())) {
        return false;
      }
      for (String deprecatedKey : r.getDeprecatedKeys()) {
        if (excludedRules.contains(deprecatedKey)) {
          LOG.warn("Rule '{}' was excluded using its deprecated key '{}'. Please fix your configuration.", r.getKey(), deprecatedKey);
          return false;
        }
      }
      return true;
    };
  }

  private static Predicate<? super SonarLintRuleDefinition> isIncludedByConfiguration(Set<String> includedRules) {
    return r -> {
      if (includedRules.contains(r.getKey())) {
        return true;
      }
      for (String deprecatedKey : r.getDeprecatedKeys()) {
        if (includedRules.contains(deprecatedKey)) {
          LOG.warn("Rule '{}' was included using its deprecated key '{}'. Please fix your configuration.", r.getKey(), deprecatedKey);
          return true;
        }
      }
      return false;
    };
  }

  @Override
  public void stop() {
    setLogging(null);
    try {
      allRulesDefinitionsByKey.clear();
      analysisEngine.stop();
    } catch (Exception e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  @Override
  public Collection<PluginDetails> getPluginDetails() {
    return pluginDetails;
  }

}
