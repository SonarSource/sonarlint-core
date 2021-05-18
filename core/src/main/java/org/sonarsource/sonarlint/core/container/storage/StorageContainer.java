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
package org.sonarsource.sonarlint.core.container.storage;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import javax.annotation.Nullable;
import org.sonar.api.Plugin;
import org.sonar.api.SonarQubeVersion;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.UriReader;
import org.sonar.api.utils.Version;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.analysis.SonarLintRules;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.global.ExtensionInstaller;
import org.sonarsource.sonarlint.core.container.global.GlobalConfigurationProvider;
import org.sonarsource.sonarlint.core.container.global.GlobalExtensionContainer;
import org.sonarsource.sonarlint.core.container.global.GlobalSettings;
import org.sonarsource.sonarlint.core.container.global.GlobalTempFolderProvider;
import org.sonarsource.sonarlint.core.container.global.MetadataLoader;
import org.sonarsource.sonarlint.core.container.global.SonarLintRuntimeImpl;
import org.sonarsource.sonarlint.core.container.module.ModuleRegistry;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRule;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRuleRepositoryContainer;
import org.sonarsource.sonarlint.core.container.storage.partialupdate.PartialUpdaterFactory;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginJarExploder;
import org.sonarsource.sonarlint.core.plugin.PluginClassloaderFactory;
import org.sonarsource.sonarlint.core.plugin.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.PluginInfosLoader;
import org.sonarsource.sonarlint.core.plugin.PluginInstancesLoader;
import org.sonarsource.sonarlint.core.plugin.PluginRepository;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules.ActiveRule;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class StorageContainer extends ComponentContainer {
  private static final Logger LOG = Loggers.get(StorageContainer.class);
  private static final DateFormat DATE_FORMAT = new SimpleDateFormat();

  public static StorageContainer create(ConnectedGlobalConfiguration globalConfig) {
    StorageContainer container = new StorageContainer();
    container.add(globalConfig);
    return container;
  }

  private GlobalExtensionContainer globalExtensionContainer;
  private ModuleRegistry moduleRegistry;
  private SonarLintRules rules;

  @Override
  protected void doBeforeStart() {
    Version sonarPluginApiVersion = MetadataLoader.loadSonarPluginApiVersion();
    Version sonarlintPluginApiVersion = MetadataLoader.loadSonarLintPluginApiVersion();
    add(
      StorageContainerHandler.class,
      PartialUpdaterFactory.class,

      // storage directories and tmp
      StoragePaths.class,
      StorageReader.class,
      IssueStorePaths.class,
      new GlobalTempFolderProvider(),

      // plugins
      PluginRepository.class,
      PluginInfosLoader.class,
      PluginVersionChecker.class,
      PluginInstancesLoader.class,
      PluginClassloaderFactory.class,
      DefaultPluginJarExploder.class,
      StoragePluginIndexProvider.class,
      new PluginCacheProvider(),

      // storage readers
      AllProjectReader.class,
      IssueStoreReader.class,
      GlobalUpdateStatusReader.class,
      ProjectStorageStatusReader.class,
      IssueStoreFactory.class,

      // analysis
      StorageAnalyzer.class,
      StorageFileExclusions.class,

      // needed during analysis (immutable)
      UriReader.class,
      GlobalSettings.class,
      NodeJsHelper.class,
      new GlobalConfigurationProvider(),
      ExtensionInstaller.class,
      new StorageRulesProvider(),
      new StorageQProfilesProvider(),
      new SonarLintRulesProvider(),
      new SonarQubeVersion(sonarPluginApiVersion),
      new SonarLintRuntimeImpl(sonarPluginApiVersion, sonarlintPluginApiVersion),
      System2.INSTANCE);
  }

  @Override
  protected void doAfterStart() {
    ConnectedGlobalConfiguration config = getComponentByType(ConnectedGlobalConfiguration.class);
    GlobalStorageStatus updateStatus = getComponentByType(StorageContainerHandler.class).getGlobalStorageStatus();

    if (updateStatus != null) {
      LOG.info("Using storage for connection '{}' (last update {})", config.getConnectionId(), DATE_FORMAT.format(updateStatus.getLastUpdateDate()));
      installPlugins();
      loadRulesFromPlugins();
    } else {
      LOG.warn("No storage for connection '{}'. Please update.", config.getConnectionId());
    }

    this.globalExtensionContainer = new GlobalExtensionContainer(this);
    globalExtensionContainer.startComponents();
    this.moduleRegistry = new ModuleRegistry(globalExtensionContainer, config.getModulesProvider());
  }

  private void loadRulesFromPlugins() {
    StandaloneRuleRepositoryContainer container = new StandaloneRuleRepositoryContainer(this);
    container.execute();
    rules = container.getRules();
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

  protected void installPlugins() {
    PluginRepository pluginRepository = getComponentByType(PluginRepository.class);
    for (PluginInfo pluginInfo : pluginRepository.getActivePluginInfos()) {
      Plugin instance = pluginRepository.getPluginInstance(pluginInfo.getKey());
      addExtension(pluginInfo, instance);
    }
  }

  public GlobalExtensionContainer getGlobalExtensionContainer() {
    return globalExtensionContainer;
  }

  public ModuleRegistry getModuleRegistry() {
    return moduleRegistry;
  }

  public StorageContainerHandler getHandler() {
    return getComponentByType(StorageContainerHandler.class);
  }

  public ConnectedRuleDetails getRuleDetails(String ruleKeyStr) {
    return getRuleDetailsWithSeverity(ruleKeyStr, null);
  }

  private ConnectedRuleDetails getRuleDetailsWithSeverity(String ruleKeyStr, @Nullable String overridenSeverity) {
    Sonarlint.Rules.Rule ruleFromStorage = getHandler().readRuleFromStorage(ruleKeyStr);
    String type = StringUtils.isEmpty(ruleFromStorage.getType()) ? null : ruleFromStorage.getType();

    Language language = Language.forKey(ruleFromStorage.getLang())
      .orElseThrow(() -> new IllegalArgumentException("Unknown language for rule " + ruleKeyStr + ": " + ruleFromStorage.getLang()));
    ConnectedGlobalConfiguration config = getComponentByType(ConnectedGlobalConfiguration.class);
    if (config.getEmbeddedPluginUrlsByKey().containsKey(language.getPluginKey())) {
      // Favor loading rule details from the embedded plugin
      StandaloneRule ruleFromPlugin = (StandaloneRule) rules.find(RuleKey.parse(ruleKeyStr));
      if (ruleFromPlugin != null) {
        return new DefaultRuleDetails(ruleKeyStr, ruleFromPlugin.name(), ruleFromPlugin.description(), overridenSeverity != null ? overridenSeverity : ruleFromPlugin.severity(),
          ruleFromPlugin.type().toString(), language,
          ruleFromStorage.getHtmlNote());
      }
    }

    return new DefaultRuleDetails(ruleKeyStr, ruleFromStorage.getName(), ruleFromStorage.getHtmlDesc(),
      overridenSeverity != null ? overridenSeverity : ruleFromStorage.getSeverity(), type, language,
      ruleFromStorage.getHtmlNote());
  }

  public ConnectedRuleDetails getRuleDetails(String ruleKeyStr, @Nullable String projectKey) {
    ActiveRule readActiveRuleFromStorage = getHandler().readActiveRuleFromStorage(ruleKeyStr, projectKey);
    return getRuleDetailsWithSeverity(ruleKeyStr, readActiveRuleFromStorage.getSeverity());
  }
}
