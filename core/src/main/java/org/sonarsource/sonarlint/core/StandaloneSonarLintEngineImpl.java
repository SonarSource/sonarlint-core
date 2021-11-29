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
package org.sonarsource.sonarlint.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.GlobalAnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.model.DefaultLoadedAnalyzer;
import org.sonarsource.sonarlint.core.plugin.common.Version;
import org.sonarsource.sonarlint.core.plugin.common.log.LogOutput;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractor;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

public final class StandaloneSonarLintEngineImpl extends AbstractSonarLintEngine {

  private static final Logger LOG = Loggers.get(StandaloneSonarLintEngineImpl.class);

  private final StandaloneGlobalConfiguration globalConfig;
  private Map<String, SonarLintRuleDefinition> allRulesDefinitionsByKey;

  public StandaloneSonarLintEngineImpl(StandaloneGlobalConfiguration globalConfig) {
    super(globalConfig.getLogOutput());
    this.globalConfig = globalConfig;

    loadPluginMetadata();

    GlobalAnalysisConfiguration analysisGlobalConfig = GlobalAnalysisConfiguration.builder()
      .addEnabledLanguages(globalConfig.getEnabledLanguages())
      .addPlugins(globalConfig.getPluginJarPaths())
      .setClientPid(globalConfig.getClientPid())
      .setLogOutput(globalConfig.getLogOutput())
      .setExtraProperties(globalConfig.extraProperties())
      .setNodeJs(globalConfig.getNodeJsPath(), Optional.ofNullable(globalConfig.getNodeJsVersion()).map(v -> Version.create(v.toString())).orElse(null))
      .setWorkDir(globalConfig.getWorkDir())
      .setClientFileSystem(globalConfig.getClientFileSystem())
      .build();
    startAnalysisEngine(analysisGlobalConfig);
  }

  public void loadPluginMetadata() {
    RulesDefinitionExtractor ruleExtractor = new RulesDefinitionExtractor();
    allRulesDefinitionsByKey = ruleExtractor.extractRules(globalConfig.getPluginJarPaths(), globalConfig.getEnabledLanguages()).stream()
      .collect(toMap(r -> r.getKey().toString(), r -> r));
  }

  public Optional<SonarLintRuleDefinition> getRuleDetails(String ruleKey) {
    return Optional.ofNullable(allRulesDefinitionsByKey.get(ruleKey));
  }

  public Collection<SonarLintRuleDefinition> getAllRuleDetails() {
    return allRulesDefinitionsByKey.values();
  }

  public AnalysisResults analyze(StandaloneAnalysisConfiguration configuration, Consumer<Issue> issueListener, @Nullable LogOutput logOutput, @Nullable ProgressMonitor monitor) {
    requireNonNull(configuration);
    requireNonNull(issueListener);

    Set<String> excludedRules = Set.copyOf(configuration.excludedRules());
    Set<String> includedRules = configuration.includedRules().stream()
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

    Collection<ActiveRule> activeRules = filteredActiveRules.stream().map(rd -> {
      ActiveRule activeRule = new ActiveRule(rd.getKey(), rd.getLanguage().getLanguageKey());
      activeRule.setInternalKey(rd.getInternalKey());
      Map<String, String> effectiveParams = new HashMap<>();
      rd.getParams().forEach((paramKey, paramDef) -> {
        effectiveParams.put(paramKey, paramDef.defaultValue());
      });
      Optional.ofNullable(configuration.ruleParameters().get(rd.getKey())).ifPresent(params -> params.forEach((k, v) -> effectiveParams.put(k.toString(), v)));
      activeRule.setParams(effectiveParams);
      return activeRule;
    }).collect(Collectors.toList());

    AnalysisConfiguration analysisConfig = AnalysisConfiguration.builder()
      .addInputFiles(configuration.inputFiles())
      .putAllExtraProperties(configuration.extraProperties())
      .addActiveRules(activeRules)
      .setBaseDir(configuration.baseDir())
      .setModuleId(configuration.moduleId())
      .build();
    return analysisEngine.analyze(analysisConfig, issueListener, null, null);
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

  public void stop() {
    analysisEngine.stop();
  }

  @Override
  public Collection<PluginDetails> getPluginDetails() {
    return analysisEngine.getGlobalContainer().getPluginCheckResultByKeys().values().stream()
      .map(c -> new DefaultLoadedAnalyzer(c.getPlugin().getKey(), c.getPlugin().getName(), c.getPlugin().getManifest().getVersion(), c.getSkipReason().orElse(null)))
      .collect(Collectors.toList());
  }

}
