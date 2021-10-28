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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.Rules;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.GlobalAnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.api.Language;
import org.sonarsource.sonarlint.core.analysis.api.Version;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.container.analysis.SonarLintRule;
import org.sonarsource.sonarlint.core.container.standalone.StandaloneGlobalContainer;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneActiveRules;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

public final class StandaloneSonarLintEngineImpl extends AbstractSonarLintEngine implements StandaloneSonarLintEngine {

  private final StandaloneGlobalConfiguration globalConfig;
  private Collection<PluginDetails> pluginDetails;
  private Rules rules;
  private StandaloneActiveRules standaloneActiveRules;

  public StandaloneSonarLintEngineImpl(StandaloneGlobalConfiguration globalConfig) {
    super(globalConfig.getLogOutput());
    this.globalConfig = globalConfig;

    loadPluginMetadata();

    GlobalAnalysisConfiguration analysisGlobalConfig = GlobalAnalysisConfiguration.builder()
      .addEnabledLanguages(globalConfig.getEnabledLanguages().stream().map(l -> Language.forKey(l.getLanguageKey()).get()).collect(toList()))
      .addPlugins(globalConfig.getPluginUrls())
      .setClientPid(globalConfig.getClientPid())
      // TODO Convert LogOutput
      .setExtraProperties(globalConfig.extraProperties())
      .setNodeJs(globalConfig.getNodeJsPath(), Optional.ofNullable(globalConfig.getNodeJsVersion()).map(v -> Version.create(v.toString())).orElse(null))
      .setSonarLintUserHome(globalConfig.getSonarLintUserHome())
      .setWorkDir(globalConfig.getWorkDir())
      .setClientFileSystem(globalConfig.getClientFileSystem())
      .build();
    startAnalysisEngine(analysisGlobalConfig);
  }

  public void loadPluginMetadata() {
    StandaloneGlobalContainer globalContainer = new StandaloneGlobalContainer(globalConfig);
    try {
      globalContainer.execute();
      pluginDetails = globalContainer.getPluginDetails();
      rules = globalContainer.getRules();
      standaloneActiveRules = globalContainer.getStandaloneActiveRules();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  @Override
  public Optional<StandaloneRuleDetails> getRuleDetails(String ruleKey) {
    return Optional.ofNullable(standaloneActiveRules.ruleDetails(ruleKey));
  }

  @Override
  public Collection<StandaloneRuleDetails> getAllRuleDetails() {
    return standaloneActiveRules.allRuleDetails();
  }

  @Override
  public AnalysisResults analyze(StandaloneAnalysisConfiguration configuration, Consumer<Issue> issueListener, @Nullable LogOutput logOutput, @Nullable ProgressMonitor monitor) {
    requireNonNull(configuration);
    requireNonNull(issueListener);

    Set<String> excludedRules = configuration.excludedRules().stream().map(RuleKey::toString).collect(Collectors.toSet());
    Set<String> includedRules = configuration.includedRules().stream()
      .map(RuleKey::toString)
      .filter(r -> !excludedRules.contains(r))
      .collect(Collectors.toSet());
    Map<String, Map<String, String>> ruleParameters = new HashMap<>();
    configuration.ruleParameters().forEach((k, v) -> ruleParameters.put(k.toString(), v));
    ActiveRules sqApiActiveRules = standaloneActiveRules.filtered(excludedRules, includedRules, ruleParameters);
    List<ActiveRule> activeRules = sqApiActiveRules.findAll().stream().map(ar -> {
      SonarLintRule rule = (SonarLintRule) rules.find(ar.ruleKey());
      ActiveRule activeRule = new ActiveRule(convert(ar.ruleKey()), rule.type().toString(), ar.severity(), rule.name(), ar.language());
      activeRule.setInternalKey(ar.internalKey());
      activeRule.setTemplateRuleKey(ar.templateRuleKey());
      activeRule.setParams(ar.params());
      return activeRule;
    })
      .collect(Collectors.toList());

    AnalysisConfiguration analysisConfig = AnalysisConfiguration.builder()
      .addInputFiles(configuration.inputFiles())
      .putAllExtraProperties(configuration.extraProperties())
      .addActiveRules(activeRules)
      .setBaseDir(configuration.baseDir())
      .setModuleId(configuration.moduleId())
      .build();
    return analysisEngine.analyze(analysisConfig, issueListener, null, null);
  }

  private static org.sonarsource.sonarlint.core.analysis.api.RuleKey convert(org.sonar.api.rule.RuleKey ruleKey) {
    return new org.sonarsource.sonarlint.core.analysis.api.RuleKey(ruleKey.repository(), ruleKey.rule());
  }

  @Override
  public void stop() {
    analysisEngine.stop();
  }

  @Override
  public Collection<PluginDetails> getPluginDetails() {
    return pluginDetails;
  }

}
