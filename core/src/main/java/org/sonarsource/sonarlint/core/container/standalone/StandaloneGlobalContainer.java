/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.standalone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.Rule;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.UriReader;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.analysis.IssueListener;
import org.sonarsource.sonarlint.core.container.analysis.AnalysisContainer;
import org.sonarsource.sonarlint.core.container.analysis.DefaultAnalysisResult;
import org.sonarsource.sonarlint.core.container.global.DefaultRuleDetails;
import org.sonarsource.sonarlint.core.container.global.ExtensionInstaller;
import org.sonarsource.sonarlint.core.container.global.GlobalContainer;
import org.sonarsource.sonarlint.core.container.global.GlobalTempFolderProvider;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRuleRepositoryContainer;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginJarExploder;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginRepository;
import org.sonarsource.sonarlint.core.plugin.PluginClassloaderFactory;
import org.sonarsource.sonarlint.core.plugin.PluginCopier;
import org.sonarsource.sonarlint.core.plugin.PluginLoader;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;

public class StandaloneGlobalContainer extends GlobalContainer {

  private Rules rules;
  private ActiveRules activeRules;
  private Context rulesDefinitions;

  public static StandaloneGlobalContainer create(GlobalConfiguration globalConfig) {
    StandaloneGlobalContainer container = new StandaloneGlobalContainer();
    container.add(globalConfig);
    container.add(new StandalonePluginIndexProvider(globalConfig.getPluginUrls()));
    return container;
  }

  @Override
  protected void doBeforeStart() {
    add(
      DefaultPluginRepository.class,
      PluginCopier.class,
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
    StandaloneRuleRepositoryContainer container = new StandaloneRuleRepositoryContainer(this);
    container.execute();
    rules = container.getRules();
    activeRules = container.getActiveRules();
    rulesDefinitions = container.getRulesDefinitions();
  }

  @Override
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

  @Override
  public RuleDetails getRuleDetails(String ruleKeyStr) {
    RuleKey ruleKey = RuleKey.parse(ruleKeyStr);
    Rule rule = rules.find(ruleKey);
    if (rule == null) {
      throw new IllegalArgumentException("Unable to find rule with key " + ruleKey);
    }
    Repository repo = rulesDefinitions.repository(rule.key().repository());

    return new DefaultRuleDetails(ruleKeyStr, rule.name(), rule.description(), rule.severity(), repo.language(), repo.rule(rule.key().rule()).tags());
  }

  public Collection<String> getActiveRuleKeys() {
    List<String> result = new ArrayList<>();
    for (ActiveRule ar : activeRules.findAll()) {
      result.add(ar.ruleKey().toString());
    }
    return result;
  }

}
