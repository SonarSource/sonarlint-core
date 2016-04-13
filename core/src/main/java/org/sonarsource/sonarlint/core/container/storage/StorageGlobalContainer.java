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
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.SonarPlugin;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.UriReader;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalUpdateStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ModuleUpdateStatus;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.analysis.AnalysisContainer;
import org.sonarsource.sonarlint.core.container.analysis.DefaultAnalysisResult;
import org.sonarsource.sonarlint.core.container.global.DefaultRuleDetails;
import org.sonarsource.sonarlint.core.container.global.ExtensionInstaller;
import org.sonarsource.sonarlint.core.container.global.GlobalTempFolderProvider;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginJarExploder;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginRepository;
import org.sonarsource.sonarlint.core.plugin.PluginClassloaderFactory;
import org.sonarsource.sonarlint.core.plugin.PluginCopier;
import org.sonarsource.sonarlint.core.plugin.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.PluginLoader;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList.Module;
import org.sonarsource.sonarlint.core.util.FileUtils;

public class StorageGlobalContainer extends ComponentContainer {

  private static final Logger LOG = LoggerFactory.getLogger(StorageGlobalContainer.class);

  public static StorageGlobalContainer create(ConnectedGlobalConfiguration globalConfig) {
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
    GlobalUpdateStatus updateStatus = getUpdateStatus();
    if (updateStatus != null) {
      LOG.info("Using storage for server '{}' (last update {})", getComponentByType(ConnectedGlobalConfiguration.class).getServerId(),
        new SimpleDateFormat().format(updateStatus.getLastUpdateDate()));
      installPlugins();
    } else {
      LOG.warn("No storage for server '{}'. Please update.", getComponentByType(ConnectedGlobalConfiguration.class).getServerId());
    }
  }

  protected void installPlugins() {
    DefaultPluginRepository pluginRepository = getComponentByType(DefaultPluginRepository.class);
    for (PluginInfo pluginInfo : pluginRepository.getPluginInfos()) {
      SonarPlugin instance = pluginRepository.getPluginInstance(pluginInfo.getKey());
      addExtension(pluginInfo, instance);
    }
  }

  public AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener) {
    GlobalUpdateStatus updateStatus = getUpdateStatus();
    if (updateStatus == null) {
      throw new StorageException("Missing global data. Please update server.", null);
    }
    if (configuration.moduleKey() != null) {
      ModuleUpdateStatus moduleUpdateStatus = getModuleUpdateStatus(configuration.moduleKey());
      if (moduleUpdateStatus == null) {
        throw new StorageException("Missing module data. Please update module '" + configuration.moduleKey() + "'.", null);
      } else if(moduleUpdateStatus.isStale()) {
        throw new StorageException("Module data is stale. Please update module '" + configuration.moduleKey() + "'.", null);
      }
    }
    AnalysisContainer analysisContainer = new AnalysisContainer(this);
    analysisContainer.add(configuration);
    analysisContainer.add(issueListener);
    analysisContainer.add(new StorageRulesProvider());
    analysisContainer.add(new StorageQProfilesProvider());
    analysisContainer.add(new SonarQubeRulesProvider());
    analysisContainer.add(new SonarQubeActiveRulesProvider());
    DefaultAnalysisResult defaultAnalysisResult = new DefaultAnalysisResult();
    analysisContainer.add(defaultAnalysisResult);
    analysisContainer.execute();
    return defaultAnalysisResult;
  }

  public RuleDetails getRuleDetails(String ruleKeyStr) {
    Sonarlint.Rules rulesFromStorage = getComponentByType(StorageManager.class).readRulesFromStorage();
    RuleKey ruleKey = RuleKey.parse(ruleKeyStr);
    Sonarlint.Rules.Rule rule = rulesFromStorage.getRulesByKey().get(ruleKeyStr);
    if (rule == null) {
      throw new IllegalArgumentException("Unable to find rule with key " + ruleKey);
    }
    return new DefaultRuleDetails(ruleKeyStr, rule.getName(), rule.getHtmlDesc(), rule.getSeverity(), rule.getLang(), Collections.<String>emptySet());
  }

  public GlobalUpdateStatus getUpdateStatus() {
    return getComponentByType(StorageManager.class).getGlobalUpdateStatus();
  }

  public ModuleUpdateStatus getModuleUpdateStatus(String moduleKey) {
    return getComponentByType(StorageManager.class).getModuleUpdateStatus(moduleKey);
  }

  public Map<String, RemoteModule> allModulesByKey() {
    Map<String, RemoteModule> results = new HashMap<>();
    ModuleList readModuleListFromStorage = getComponentByType(StorageManager.class).readModuleListFromStorage();
    Map<String, Module> modulesByKey = readModuleListFromStorage.getModulesByKey();
    for (Map.Entry<String, Sonarlint.ModuleList.Module> entry : modulesByKey.entrySet()) {
      results.put(entry.getKey(), new DefaultRemoteModule(entry.getValue()));
    }
    return results;
  }

  private static class DefaultRemoteModule implements RemoteModule {

    private final String key;
    private final String name;
    private final boolean root;

    public DefaultRemoteModule(Sonarlint.ModuleList.Module module) {
      this.key = module.getKey();
      this.name = module.getName();
      this.root = "TRK".equals(module.getQu());
    }

    @Override
    public String getKey() {
      return key;
    }

    @Override
    public String getName() {
      return name;
    }
    
    @Override
    public boolean isRoot() {
      return root;
    }

  }

  public void deleteStorage() {
    FileUtils.deleteDirectory(getComponentByType(StorageManager.class).getServerStorageRoot());
  }

}
