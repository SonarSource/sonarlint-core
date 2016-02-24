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
package org.sonarsource.sonarlint.core.container.global;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.sonar.api.SonarPlugin;
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
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.PluginInfo;
import org.sonarsource.sonarlint.core.container.analysis.AnalysisContainer;
import org.sonarsource.sonarlint.core.container.analysis.DefaultAnalysisResult;
import org.sonarsource.sonarlint.core.container.rule.OfflineRuleRepositoryContainer;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.container.storage.StoragePluginIndexProvider;
import org.sonarsource.sonarlint.core.container.unconnected.ClientPluginIndexProvider;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginJarExploder;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginRepository;
import org.sonarsource.sonarlint.core.plugin.PluginClassloaderFactory;
import org.sonarsource.sonarlint.core.plugin.PluginDownloader;
import org.sonarsource.sonarlint.core.plugin.PluginLoader;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;

public class GlobalContainer extends ComponentContainer {

  private Rules rules;
  private ActiveRules activeRules;
  private Context rulesDefinitions;

  public static GlobalContainer create(GlobalConfiguration globalConfig, List<?> extensions) {
    GlobalContainer container = new GlobalContainer();
    container.add(globalConfig);
    String serverId = globalConfig.getServerId();
    if (serverId != null) {
      container.add(StorageManager.class);
      container.add(StoragePluginIndexProvider.class);
    } else {
      container.add(new ClientPluginIndexProvider(globalConfig.getPluginUrls()));
    }
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
    rulesDefinitions = container.getRulesDefinitions();
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

  public RuleDetails getRuleDetails(String ruleKeyStr) {
    RuleKey ruleKey = RuleKey.parse(ruleKeyStr);
    Rule rule = rules.find(ruleKey);
    if (rule == null) {
      throw new IllegalArgumentException("Unable to find rule with key " + ruleKey);
    }
    return new DefaultRuleDetails(rule);
  }

  public Collection<String> getActiveRuleKeys() {
    List<String> result = new ArrayList<>();
    for (ActiveRule ar : activeRules.findAll()) {
      result.add(ar.ruleKey().toString());
    }
    return result;
  }

  private class DefaultRuleDetails implements RuleDetails {

    private final Rule rule;
    private String language;
    private Set<String> tags;

    public DefaultRuleDetails(Rule rule) {
      this.rule = rule;
      Repository repo = rulesDefinitions.repository(rule.key().repository());
      this.language = repo.language();
      this.tags = repo.rule(rule.key().rule()).tags();
    }

    @Override
    public String getName() {
      return rule.name();
    }

    @Override
    public String getHtmlDescription() {
      return rule.description();
    }

    @Override
    public String getLanguage() {
      return language;
    }

    @Override
    public String getSeverity() {
      return rule.severity();
    }

    @Override
    public String[] getTags() {
      return tags.toArray(new String[0]);
    }

  }

}
