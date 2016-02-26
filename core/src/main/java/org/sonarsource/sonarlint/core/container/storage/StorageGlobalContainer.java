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
package org.sonarsource.sonarlint.core.container.storage;

import java.text.SimpleDateFormat;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.UriReader;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalSyncStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ModuleSyncStatus;
import org.sonarsource.sonarlint.core.container.analysis.AnalysisContainer;
import org.sonarsource.sonarlint.core.container.analysis.DefaultAnalysisResult;
import org.sonarsource.sonarlint.core.container.global.DefaultRuleDetails;
import org.sonarsource.sonarlint.core.container.global.ExtensionInstaller;
import org.sonarsource.sonarlint.core.container.global.GlobalContainer;
import org.sonarsource.sonarlint.core.container.global.GlobalTempFolderProvider;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginJarExploder;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginRepository;
import org.sonarsource.sonarlint.core.plugin.PluginClassloaderFactory;
import org.sonarsource.sonarlint.core.plugin.PluginDownloader;
import org.sonarsource.sonarlint.core.plugin.PluginLoader;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class StorageGlobalContainer extends GlobalContainer {

  private static final Logger LOG = LoggerFactory.getLogger(StorageGlobalContainer.class);

  public static StorageGlobalContainer create(GlobalConfiguration globalConfig) {
    StorageGlobalContainer container = new StorageGlobalContainer();
    container.add(globalConfig);
    container.add(StorageManager.class);
    container.add(StoragePluginIndexProvider.class);
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
    GlobalSyncStatus syncStatus = getSyncStatus();
    if (syncStatus != null) {
      LOG.info("Using storage for server '{}' (last sync {})", getComponentByType(GlobalConfiguration.class).getServerId(),
        new SimpleDateFormat().format(syncStatus.getLastSyncDate()));
      installPlugins();
    } else {
      LOG.warn("No storage for server '{}'. Please sync.", getComponentByType(GlobalConfiguration.class).getServerId());
    }
  }

  @Override
  public AnalysisResults analyze(AnalysisConfiguration configuration, IssueListener issueListener) {
    GlobalSyncStatus syncStatus = getSyncStatus();
    if (syncStatus == null) {
      throw new IllegalStateException("Please sync server prior to run the analysis");
    }
    if (configuration.moduleKey() != null) {
      ModuleSyncStatus moduleSyncStatus = getModuleSyncStatus(configuration.moduleKey());
      if (moduleSyncStatus == null) {
        throw new IllegalStateException("Please sync module " + configuration.moduleKey() + " prior to run the analysis");
      }
    }
    AnalysisContainer analysisContainer = new AnalysisContainer(this);
    analysisContainer.add(configuration);
    analysisContainer.add(issueListener);
    analysisContainer.add(new StorageRulesProvider());
    analysisContainer.add(new SonarQubeRulesProvider());
    analysisContainer.add(new SonarQubeActiveRulesProvider());
    DefaultAnalysisResult defaultAnalysisResult = new DefaultAnalysisResult();
    analysisContainer.add(defaultAnalysisResult);
    analysisContainer.execute();
    return defaultAnalysisResult;
  }

  @Override
  public RuleDetails getRuleDetails(String ruleKeyStr) {
    Sonarlint.Rules rulesFromStorage = getComponentByType(StorageManager.class).readRulesFromStorage();
    RuleKey ruleKey = RuleKey.parse(ruleKeyStr);
    Sonarlint.Rules.Rule rule = rulesFromStorage.getRulesByKey().get(ruleKeyStr);
    if (rule == null) {
      throw new IllegalArgumentException("Unable to find rule with key " + ruleKey);
    }
    return new DefaultRuleDetails(rule.getName(), rule.getHtmlDesc(), rule.getSeverity(), rule.getLang(), Collections.<String>emptySet());
  }

  public GlobalSyncStatus getSyncStatus() {
    return getComponentByType(StorageManager.class).getGlobalSyncStatus();
  }

  public ModuleSyncStatus getModuleSyncStatus(String moduleKey) {
    return getComponentByType(StorageManager.class).getModuleSyncStatus(moduleKey);
  }

}
