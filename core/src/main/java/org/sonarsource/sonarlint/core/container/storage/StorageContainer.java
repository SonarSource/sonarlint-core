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

import java.net.URL;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.picocontainer.injectors.ProviderAdapter;
import org.sonar.api.SonarQubeVersion;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.UriReader;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.container.AnalysisExtensionInstaller;
import org.sonarsource.sonarlint.core.container.analysis.SonarLintRules;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.global.GlobalConfigurationProvider;
import org.sonarsource.sonarlint.core.container.global.GlobalExtensionContainer;
import org.sonarsource.sonarlint.core.container.global.GlobalSettings;
import org.sonarsource.sonarlint.core.container.global.GlobalTempFolderProvider;
import org.sonarsource.sonarlint.core.container.module.ModuleRegistry;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRule;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRuleRepositoryContainer;
import org.sonarsource.sonarlint.core.container.storage.partialupdate.PartialUpdaterFactory;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.plugin.commons.ApiVersions;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository.Configuration;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginLocation;
import org.sonarsource.sonarlint.core.plugin.commons.pico.ComponentContainer;
import org.sonarsource.sonarlint.core.plugin.commons.sonarapi.SonarLintRuntimeImpl;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerRules;
import org.sonarsource.sonarlint.core.storage.ProjectStorage;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class StorageContainer extends ComponentContainer {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final DateFormat DATE_FORMAT = new SimpleDateFormat();
  private final ConnectedGlobalConfiguration globalConfig;
  private final GlobalStores globalStores;
  private final ProjectStorage projectStorage;
  private final GlobalUpdateStatusReader globalUpdateStatusReader;

  public StorageContainer(ConnectedGlobalConfiguration globalConfig, GlobalStores globalStores, ProjectStorage projectStorage, GlobalUpdateStatusReader globalUpdateStatusReader) {
    this.globalConfig = globalConfig;
    this.globalStores = globalStores;
    this.projectStorage = projectStorage;
    this.globalUpdateStatusReader = globalUpdateStatusReader;
  }

  private GlobalExtensionContainer globalExtensionContainer;
  private ModuleRegistry moduleRegistry;
  private SonarLintRules rulesFromPlugins;

  @Override
  protected void doBeforeStart() {
    var sonarPluginApiVersion = ApiVersions.loadSonarPluginApiVersion();
    var sonarlintPluginApiVersion = ApiVersions.loadSonarLintPluginApiVersion();

    Path cacheDir = globalConfig.getSonarLintUserHome().resolve("plugins");
    var fileCache = PluginCache.create(cacheDir);

    var pluginReferenceStore = globalStores.getPluginReferenceStore();
    List<PluginLocation> plugins = new ArrayList<>();
    Map<String, URL> extraPluginsUrlsByKey = globalConfig.getExtraPluginsUrlsByKey();
    Map<String, URL> embeddedPluginsUrlsByKey = globalConfig.getEmbeddedPluginUrlsByKey();

    Sonarlint.PluginReferences protoReferences;
    try {
      protoReferences = pluginReferenceStore.getAll();
    } catch (StorageException e) {
      LOG.debug("Unable to read plugins references from storage", e);
      protoReferences = Sonarlint.PluginReferences.newBuilder().build();
    }
    protoReferences.getReferenceList().forEach(r -> {
      if (embeddedPluginsUrlsByKey.containsKey(r.getKey())) {
        var ref = fileCache.getFromCacheOrCopy(embeddedPluginsUrlsByKey.get(r.getKey()));
        var jarPath = Objects.requireNonNull(fileCache.get(ref.getFilename(), r.getHash()), "Error reading plugin from cache");
        plugins.add(new PluginLocation(jarPath, true));
      } else {
        var jarPath = fileCache.get(r.getFilename(), r.getHash());
        if (jarPath == null) {
          throw new StorageException("The plugin " + r.getFilename() + " was not found in the local storage.");
        }
        plugins.add(new PluginLocation(jarPath, false));
      }
    });
    extraPluginsUrlsByKey.values().stream().map(fileCache::getFromCacheOrCopy).forEach(r -> {
      var jarPath = fileCache.get(r.getFilename(), r.getHash());
      plugins.add(new PluginLocation(jarPath, true));
    });

    var config = new Configuration(plugins, globalConfig.getEnabledLanguages(), Optional.ofNullable(globalConfig.getNodeJsVersion()));
    var pluginInstancesRepository = new PluginInstancesRepository(config);

    add(
      pluginInstancesRepository,
      globalConfig,
      globalStores,
      globalStores.getGlobalStorage(),
      globalStores.getRulesStore(),
      globalStores.getStorageStatusStore(),
      globalStores.getPluginReferenceStore(),
      globalStores.getServerInfoStore(),
      globalStores.getServerProjectsStore(),
      projectStorage,
      StorageContainerHandler.class,
      PartialUpdaterFactory.class,

      // storage directories and tmp
      ProjectStoragePaths.class,
      StorageReader.class,
      IssueStorePaths.class,
      new GlobalTempFolderProvider(),

      // storage readers
      IssueStoreReader.class,
      globalUpdateStatusReader,
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
      AnalysisExtensionInstaller.class,
      new SonarLintRulesProvider(),
      new SonarQubeVersion(sonarPluginApiVersion),
      new SonarLintRuntimeImpl(sonarPluginApiVersion, sonarlintPluginApiVersion, globalConfig.getClientPid()),
      Clock.systemDefaultZone(),
      System2.INSTANCE);
  }

  @Override
  protected void doAfterStart() {
    GlobalStorageStatus updateStatus = globalUpdateStatusReader.read();

    SonarLintRules rulesFromStorage = null;
    if (updateStatus != null) {
      rulesFromStorage = getComponentByType(SonarLintRules.class);
      LOG.info("Using storage for connection '{}' (last update {})", globalConfig.getConnectionId(), DATE_FORMAT.format(updateStatus.getLastUpdateDate()));
      declarePluginProperties();
      loadRulesFromPlugins();
    } else {
      LOG.warn("No storage for connection '{}'. Please update.", globalConfig.getConnectionId());
    }

    this.globalExtensionContainer = new GlobalExtensionContainer(this);
    globalExtensionContainer.startComponents();
    this.moduleRegistry = new ModuleRegistry(globalExtensionContainer, globalConfig.getModulesProvider());
    if (rulesFromStorage != null) {
      SonarLintRules mergedRules = merge(rulesFromPlugins, rulesFromStorage);
      this.globalExtensionContainer.add(new MergedSonarLintRulesProvider(mergedRules));
    }
  }

  private SonarLintRules merge(SonarLintRules rulesFromPlugins, SonarLintRules rulesFromStorage) {
    var merged = new SonarLintRules();
    rulesFromStorage.findAll().forEach(merged::add);
    rulesFromPlugins.findAll().forEach(merged::add);
    return merged;
  }

  private void loadRulesFromPlugins() {
    var container = new StandaloneRuleRepositoryContainer(this);
    container.execute();
    rulesFromPlugins = container.getRules();
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
    var pluginInstancesRepository = getComponentByType(PluginInstancesRepository.class);
    pluginInstancesRepository.getPluginInstancesByKeys().values().forEach(this::declareProperties);
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
    Optional<Sonarlint.Rules.Rule> optionalRule = globalStores.getRulesStore().getRuleWithKey(ruleKeyStr);
    StandaloneRule ruleFromPlugin = (StandaloneRule) rulesFromPlugins.find(RuleKey.parse(ruleKeyStr));
    return optionalRule.map(ruleFromStorage -> {
      String type = StringUtils.isEmpty(ruleFromStorage.getType()) ? null : ruleFromStorage.getType();

      var language = Language.forKey(ruleFromStorage.getLang())
        .orElseThrow(() -> new IllegalArgumentException("Unknown language for rule " + ruleKeyStr + ": " + ruleFromStorage.getLang()));
      ConnectedGlobalConfiguration config = getComponentByType(ConnectedGlobalConfiguration.class);
      if (config.getEmbeddedPluginUrlsByKey().containsKey(language.getPluginKey()) && ruleFromPlugin != null) {
        // Favor loading rule details from the embedded plugin
        return new DefaultRuleDetails(ruleKeyStr, ruleFromPlugin.name(), ruleFromPlugin.description(), overridenSeverity != null ? overridenSeverity : ruleFromPlugin.severity(),
          ruleFromPlugin.type().toString(), language,
          ruleFromStorage.getHtmlNote());
      }

      return new DefaultRuleDetails(ruleKeyStr, ruleFromStorage.getName(), ruleFromStorage.getHtmlDesc(),
        overridenSeverity != null ? overridenSeverity : ruleFromStorage.getSeverity(), type, language,
        ruleFromStorage.getHtmlNote());
    }).orElseGet(() -> new DefaultRuleDetails(ruleKeyStr, ruleFromPlugin.getName(), ruleFromPlugin.getHtmlDescription(),
      overridenSeverity != null ? overridenSeverity : ruleFromPlugin.getSeverity(), ruleFromPlugin.getType(), ruleFromPlugin.getLanguage(),
      ""));
  }

  public ConnectedRuleDetails getRuleDetails(String ruleKeyStr, @Nullable String projectKey) {
    var analyzerConfiguration = projectStorage.getAnalyzerConfiguration(projectKey);
    // for extra plugins there will be no rule in the storage
    String severity = analyzerConfiguration.getRuleSetByLanguageKey().values().stream().flatMap(s -> s.getRules().stream())
      .filter(r -> r.getRuleKey().equals(ruleKeyStr)).findFirst().map(ServerRules.ActiveRule::getSeverity).orElse(null);
    return getRuleDetailsWithSeverity(ruleKeyStr, severity);
  }

  public static class MergedSonarLintRulesProvider extends ProviderAdapter {

    private final SonarLintRules mergedRules;

    public MergedSonarLintRulesProvider(SonarLintRules mergedRules) {
      this.mergedRules = mergedRules;
    }

    public SonarLintRules provide() {
      return mergedRules;
    }
  }
}
