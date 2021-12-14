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

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalAnalysisContainer;
import org.sonarsource.sonarlint.core.analysis.container.global.ModuleRegistry;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.DefaultClientIssue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.client.api.connected.StateListener;
import org.sonarsource.sonarlint.core.client.api.connected.UpdateResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalStorageUpdateRequiredException;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.connected.ConnectedContainer;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectListDownloader;
import org.sonarsource.sonarlint.core.container.model.DefaultLoadedAnalyzer;
import org.sonarsource.sonarlint.core.container.storage.DefaultRuleDetails;
import org.sonarsource.sonarlint.core.container.storage.FileMatcher;
import org.sonarsource.sonarlint.core.container.storage.GlobalStores;
import org.sonarsource.sonarlint.core.container.storage.GlobalUpdateStatusReader;
import org.sonarsource.sonarlint.core.container.storage.IssueStoreReader;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.container.storage.ProjectStorageStatusReader;
import org.sonarsource.sonarlint.core.container.storage.StorageFileExclusions;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.container.storage.partialupdate.PartialUpdaterFactory;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository.Configuration;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginLocation;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerRules;
import org.sonarsource.sonarlint.core.storage.LocalStorageSynchronizer;
import org.sonarsource.sonarlint.core.storage.ProjectStorage;
import org.sonarsource.sonarlint.core.storage.RuleSet;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths.encodeForFs;

public final class ConnectedSonarLintEngineImpl extends AbstractSonarLintEngine implements ConnectedSonarLintEngine {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConnectedGlobalConfiguration globalConfig;
  private final GlobalUpdateStatusReader globalStatusReader;
  private GlobalAnalysisContainer globalAnalysisContainer;
  private final List<StateListener> stateListeners = new CopyOnWriteArrayList<>();
  private volatile State state = State.UNKNOWN;
  private final GlobalStores globalStores;
  private final ProjectStorage projectStorage;
  private final LocalStorageSynchronizer storageSynchronizer;
  private final ProjectStorageStatusReader projectStorageStatusReader;
  private final IssueStoreReader issueStoreReader;
  private final StorageFileExclusions storageFileExclusions;
  private final PartialUpdaterFactory partialUpdaterFactory;

  private boolean containerStarted;

  private PluginInstancesRepository pluginInstancesRepository;

  private final StorageReader storageReader;

  public ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration globalConfig) {
    super(globalConfig.getLogOutput());
    this.globalConfig = globalConfig;
    this.globalStores = new GlobalStores(globalConfig);
    this.globalStatusReader = new GlobalUpdateStatusReader(globalStores.getServerInfoStore(), globalStores.getStorageStatusStore());

    var projectStoragePaths = new ProjectStoragePaths(globalConfig);
    this.projectStorageStatusReader = new ProjectStorageStatusReader(projectStoragePaths);

    Path storageRoot = globalConfig.getStorageRoot().resolve(encodeForFs(globalConfig.getConnectionId()));
    Path projectsStorageRoot = storageRoot.resolve("projects");
    projectStorage = new ProjectStorage(projectsStorageRoot);

    var issueStorePaths = new IssueStorePaths();
    this.storageReader = new StorageReader(projectStoragePaths);
    this.issueStoreReader = new IssueStoreReader(new IssueStoreFactory(), issueStorePaths, projectStoragePaths, storageReader);
    this.storageFileExclusions = new StorageFileExclusions(issueStorePaths);

    this.partialUpdaterFactory = new PartialUpdaterFactory(projectStoragePaths, issueStorePaths);

    storageSynchronizer = new LocalStorageSynchronizer(globalConfig.getEnabledLanguages(), projectStorage);
    start();
  }

  @Override
  public State getState() {
    return state;
  }

  @Override
  public void addStateListener(StateListener listener) {
    stateListeners.add(listener);
  }

  @Override
  public void removeStateListener(StateListener listener) {
    stateListeners.remove(listener);
  }

  private void changeState(State state) {
    this.state = state;
    for (StateListener listener : stateListeners) {
      listener.stateChanged(state);
    }
  }

  public GlobalAnalysisContainer getGlobalContainer() {
    if (globalAnalysisContainer == null) {
      throw new IllegalStateException("SonarLint Engine for connection '" + globalConfig.getConnectionId() + "' is stopped.");
    }
    return globalAnalysisContainer;
  }

  @Override
  protected ModuleRegistry getModuleRegistry() {
    return getGlobalContainer().getModuleRegistry();
  }

  public void start() {
    setLogging(null);
    rwl.writeLock().lock();

    try {
      pluginInstancesRepository = createPluginInstanceRepository();

      loadPluginMetadata(pluginInstancesRepository, globalConfig.getEnabledLanguages(), true);

      AnalysisEngineConfiguration analysisGlobalConfig = AnalysisEngineConfiguration.builder()
        .addEnabledLanguages(globalConfig.getEnabledLanguages())
        .setClientPid(globalConfig.getClientPid())
        .setExtraProperties(globalConfig.extraProperties())
        .setNodeJs(globalConfig.getNodeJsPath(), Optional.ofNullable(globalConfig.getNodeJsVersion()).map(v -> Version.create(v.toString())).orElse(null))
        .setWorkDir(globalConfig.getWorkDir())
        .setModulesProvider(globalConfig.getModulesProvider())
        .build();
      this.globalAnalysisContainer = new GlobalAnalysisContainer(analysisGlobalConfig, pluginInstancesRepository);

      globalAnalysisContainer.startComponents();
      containerStarted = true;
      var globalStorageStatus = globalStatusReader.read();
      if (globalStorageStatus == null) {
        changeState(State.NEVER_UPDATED);
      } else if (globalStorageStatus.isStale()) {
        changeState(State.NEED_UPDATE);
      } else {
        changeState(State.UPDATED);
      }
    } catch (StorageException e) {
      LOG.debug(e.getMessage(), e);
      changeState(State.NEED_UPDATE);
    } catch (RuntimeException e) {
      LOG.error("Unable to start the SonarLint engine", e);
      changeState(State.UNKNOWN);
    } finally {
      rwl.writeLock().unlock();
    }
  }

  private PluginInstancesRepository createPluginInstanceRepository() {
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
    return new PluginInstancesRepository(config);
  }

  @Override
  public AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener, @Nullable ClientLogOutput logOutput,
    @Nullable ClientProgressMonitor monitor) {
    requireNonNull(configuration);
    requireNonNull(issueListener);

    var analysisConfigBuilder = AnalysisConfiguration.builder()
      .addInputFiles(configuration.inputFiles());
    String projectKey = configuration.projectKey();
    if (projectKey != null) {
      analysisConfigBuilder.putAllExtraProperties(projectStorage.getAnalyzerConfiguration(projectKey).getSettings().getAll());
      analysisConfigBuilder.putAllExtraProperties(globalConfig.extraProperties());
    }
    analysisConfigBuilder.putAllExtraProperties(configuration.extraProperties())
      .addActiveRules(buildActiveRules(configuration))
      .setBaseDir(configuration.baseDir())
      .build();

    var analysisConfiguration = analysisConfigBuilder.build();

    return withReadLock(() -> {
      setLogging(logOutput);
      return withModule(configuration, moduleContainer -> {
        try {
          checkStatus(configuration.projectKey());
          return getGlobalContainer().analyze(moduleContainer, analysisConfiguration,
            i -> issueListener.handle(new DefaultClientIssue(i, getRuleDetails(i.getRuleKey(), projectKey))), new ProgressMonitor(monitor));
        } catch (RuntimeException e) {
          throw SonarLintWrappedException.wrap(e);
        }
      });

    });
  }

  public List<ActiveRule> buildActiveRules(ConnectedAnalysisConfiguration configuration) {
    List<ActiveRule> activeRulesList = new ArrayList<>();
    // could be empty before the first sync
    var projectKey = configuration.projectKey();
    if (projectKey == null) {
      // this should be forbidden by client side
      LOG.debug("No project key provided, no rules will be used for analysis");
      return Collections.emptyList();
    }

    for (Map.Entry<String, RuleSet> entry : projectStorage.getAnalyzerConfiguration(projectKey).getRuleSetByLanguageKey().entrySet()) {
      String languageKey = entry.getKey();
      var ruleSet = entry.getValue();
      Optional<Language> languageOpt = Language.forKey(languageKey);
      if (languageOpt.isEmpty() || !globalConfig.getEnabledLanguages().contains(languageOpt.get())) {
        continue;
      }

      LOG.debug("  * {}: '{}' ({} active rules)", languageKey, ruleSet.getProfileKey(), ruleSet.getRules().size());
      for (ServerRules.ActiveRule activeRuleFromStorage : ruleSet.getRules()) {
        String ruleDefinitionKey = StringUtils.isNotBlank(activeRuleFromStorage.getTemplateKey()) ? activeRuleFromStorage.getTemplateKey() : activeRuleFromStorage.getRuleKey();
        SonarLintRuleDefinition ruleDefinition = allRulesDefinitionsByKey.get(ruleDefinitionKey);
        if (ruleDefinition == null) {
          LOG.debug("Rule {} is enabled on the server, but not available in SonarLint", activeRuleFromStorage.getRuleKey());
          continue;
        }
        var activeRuleForAnalysis = new ActiveRule(activeRuleFromStorage.getRuleKey(), languageKey);
        activeRuleForAnalysis.setTemplateRuleKey(trimToNull(activeRuleFromStorage.getTemplateKey()));
        Map<String, String> effectiveParams = new HashMap<>();
        ruleDefinition.getParams().forEach((paramKey, paramDef) -> {
          String defaultValue = paramDef.defaultValue();
          if (defaultValue != null) {
            effectiveParams.put(paramKey, defaultValue);
          }
        });
        activeRuleFromStorage.getParams().forEach(p -> effectiveParams.put(p.getKey(), p.getValue()));
        activeRuleForAnalysis.setParams(effectiveParams);
        activeRulesList.add(activeRuleForAnalysis);
      }
    }

    allRulesDefinitionsByKey.values().stream()
      .filter(ruleDefinition -> isRuleFromExtraPlugin(ruleDefinition.getLanguage(), globalConfig))
      .map(rule -> {
        var activeRuleForAnalysis = new ActiveRule(rule.getKey(), rule.getLanguage().getLanguageKey());
        Map<String, String> effectiveParams = new HashMap<>();
        rule.getParams().forEach((paramKey, paramDef) -> {
          if (paramDef.defaultValue() != null) {
            effectiveParams.put(paramKey, paramDef.defaultValue());
          }
        });
        activeRuleForAnalysis.setParams(effectiveParams);
        return activeRuleForAnalysis;
      })
      .forEach(activeRulesList::add);

    return activeRulesList;

  }

  private static boolean isRuleFromExtraPlugin(Language ruleLanguage, ConnectedGlobalConfiguration config) {
    return config.getExtraPluginsUrlsByKey().keySet()
      .stream().anyMatch(extraPluginKey -> ruleLanguage.getLanguageKey().equals(extraPluginKey));
  }

  private void checkStatus(@Nullable String projectKey) {
    GlobalStorageStatus updateStatus = globalStatusReader.read();
    if (updateStatus == null) {
      throw new StorageException("Missing global data. Please update server.", false);
    }
    if (projectKey != null) {
      var moduleUpdateStatus = getProjectStorageStatus(projectKey);
      if (moduleUpdateStatus == null) {
        throw new StorageException(String.format("No data stored for project '%s'. Please update the binding.", projectKey), false);
      } else if (moduleUpdateStatus.isStale()) {
        throw new StorageException(String.format("Stored data for project '%s' is stale because "
          + "it was created with a different version of SonarLint. Please update the binding.", projectKey), false);
      }
    }
  }

  @Override
  public GlobalStorageStatus getGlobalStorageStatus() {
    return wrapErrors(globalStatusReader::read);
  }

  @Override
  public void sync(EndpointParams endpoint, HttpClient client, Set<String> projectKeys, @Nullable ClientProgressMonitor monitor) {
    var serverApi = new ServerApi(new ServerApiHelper(endpoint, client));
    storageSynchronizer.synchronize(serverApi, projectKeys, new ProgressMonitor(monitor));
  }

  @Override
  public UpdateResult update(EndpointParams endpoint, HttpClient client, @Nullable ClientProgressMonitor monitor) {
    requireNonNull(endpoint);
    setLogging(null);
    return withRwLock(() -> {
      stop(false);
      changeState(State.UPDATING);
      List<SonarAnalyzer> analyzers;
      try {
        analyzers = runInConnectedContainer(endpoint, client, container -> container.update(new ProgressMonitor(monitor)));
      } finally {
        start();
      }
      return new UpdateResult(globalStatusReader.read(), analyzers);
    });
  }

  @Override
  public ConnectedRuleDetails getRuleDetails(String ruleKey) {
    return withReadLock(() -> getRuleDetails(ruleKey, null));
  }

  @Override
  public ConnectedRuleDetails getActiveRuleDetails(String ruleKey, @Nullable String projectKey) {
    return withReadLock(() -> getRuleDetails(ruleKey, projectKey));
  }

  private ConnectedRuleDetails getRuleDetails(String ruleKeyStr, @Nullable String projectKey) {
    SonarLintRuleDefinition ruleDefFromPlugin = Optional.ofNullable(allRulesDefinitionsByKey.get(ruleKeyStr)).orElse(null);
    if (ruleDefFromPlugin != null && (globalConfig.getExtraPluginsUrlsByKey().containsKey(ruleDefFromPlugin.getLanguage().getPluginKey()) || projectKey == null)) {
      // if no project key, or for rules from extra plugins there will be no rules metadata in the storage
      return new DefaultRuleDetails(ruleKeyStr, ruleDefFromPlugin.getName(), ruleDefFromPlugin.getHtmlDescription(), ruleDefFromPlugin.getSeverity(), ruleDefFromPlugin.getType(),
        ruleDefFromPlugin.getLanguage(), "");
    }
    if (projectKey != null) {
      var analyzerConfiguration = projectStorage.getAnalyzerConfiguration(projectKey);
      Optional<ServerRules.ActiveRule> storageActiveRule = analyzerConfiguration.getRuleSetByLanguageKey().values().stream()
        .flatMap(s -> s.getRules().stream())
        .filter(r -> r.getRuleKey().equals(ruleKeyStr)).findFirst();
      if (storageActiveRule.isPresent()) {
        var activeRuleFromStorage = storageActiveRule.get();
        String serverSeverity = activeRuleFromStorage.getSeverity();
        if (StringUtils.isNotBlank(activeRuleFromStorage.getTemplateKey())) {
          SonarLintRuleDefinition templateRuleDefFromPlugin = Optional.ofNullable(allRulesDefinitionsByKey.get(activeRuleFromStorage.getTemplateKey()))
            .orElseThrow(() -> new IllegalStateException("Unable to find rule definition for rule template " + activeRuleFromStorage.getTemplateKey()));
          // FIXME Rule template name and description should come from the server
          return new DefaultRuleDetails(ruleKeyStr, "FIXME", "FIXME", serverSeverity, templateRuleDefFromPlugin.getType(), templateRuleDefFromPlugin.getLanguage(), "");
        } else {
          if (ruleDefFromPlugin == null) {
            throw new IllegalStateException("Unable to find rule definition for rule " + ruleKeyStr);
          }
          // FIXME deal with extended rule description
          return new DefaultRuleDetails(ruleKeyStr, ruleDefFromPlugin.getName(), ruleDefFromPlugin.getHtmlDescription(),
            serverSeverity != null ? serverSeverity : ruleDefFromPlugin.getSeverity(), ruleDefFromPlugin.getType(), ruleDefFromPlugin.getLanguage(),
            "");
        }
      }
    }
    throw new IllegalStateException("Unable to find rule description for rule " + ruleKeyStr);
  }

  @Override
  public Collection<PluginDetails> getPluginDetails() {
    return pluginInstancesRepository.getPluginCheckResultByKeys().values().stream().map(p -> new DefaultLoadedAnalyzer(p.getPlugin().getKey(), p.getPlugin().getName(),
      Optional.ofNullable(p.getPlugin().getVersion()).map(Version::toString).orElse(null), p.getSkipReason().orElse(null))).collect(Collectors.toList());
  }

  @Override
  public Map<String, ServerProject> allProjectsByKey() {
    return checkUpToDateThen(() -> globalStores.getServerProjectsStore().getAll());
  }

  @Override
  public Map<String, ServerProject> downloadAllProjects(EndpointParams endpoint, HttpClient client, @Nullable ClientProgressMonitor monitor) {
    return wrapErrors(() -> {
      try {
        return new ProjectListDownloader(new ServerApiHelper(endpoint, client), globalStores.getServerProjectsStore()).fetch(new ProgressMonitor(monitor));
      } catch (Exception e) {
        // null as cause so that it doesn't get wrapped
        throw new DownloadException("Failed to update module list: " + e.getMessage(), null);
      }
    });
  }

  private void checkUpdateStatus() {
    if (state != State.UPDATED) {
      throw new GlobalStorageUpdateRequiredException(globalConfig.getConnectionId());
    }
  }

  @Override
  public List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String ideFilePath) {
    return withReadLock(() -> issueStoreReader.getServerIssues(projectBinding, ideFilePath));
  }

  @Override
  public <G> List<G> getExcludedFiles(ProjectBinding projectBinding, Collection<G> files, Function<G, String> ideFilePathExtractor, Predicate<G> testFilePredicate) {
    return withReadLock(() -> storageFileExclusions.getExcludedFiles(projectStorage, projectBinding, files, ideFilePathExtractor, testFilePredicate));
  }

  @Override
  public List<ServerIssue> downloadServerIssues(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath,
    boolean fetchTaintVulnerabilities, @Nullable ClientProgressMonitor monitor) {
    return withRwLock(() -> {
      checkUpdateStatus();
      return downloadServerIssues(endpoint, client, projectBinding, ideFilePath, fetchTaintVulnerabilities, new ProgressMonitor(monitor));
    });
  }

  @Override
  public void downloadServerIssues(EndpointParams endpoint, HttpClient client, String projectKey, boolean fetchTaintVulnerabilities, @Nullable ClientProgressMonitor monitor) {
    withRwLock(() -> {
      downloadServerIssues(endpoint, client, projectKey, fetchTaintVulnerabilities, new ProgressMonitor(monitor));
      return null;
    });
  }

  @Override
  public ProjectBinding calculatePathPrefixes(String projectKey, Collection<String> ideFilePaths) {
    List<Path> idePathList = ideFilePaths.stream()
      .map(Paths::get)
      .collect(Collectors.toList());
    List<Path> sqPathList = withReadLock(() -> storageReader.readProjectComponents(projectKey)
      .getComponentList().stream()
      .map(Paths::get)
      .collect(Collectors.toList()));
    var fileMatcher = new FileMatcher();
    FileMatcher.Result match = fileMatcher.match(sqPathList, idePathList);
    return new ProjectBinding(projectKey, FilenameUtils.separatorsToUnix(match.sqPrefix().toString()),
      FilenameUtils.separatorsToUnix(match.idePrefix().toString()));
  }

  private List<ServerIssue> downloadServerIssues(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath,
    boolean fetchTaintVulnerabilities, ProgressMonitor progress) {
    var updater = partialUpdaterFactory.create(endpoint, client);
    Sonarlint.ProjectConfiguration configuration = storageReader.readProjectConfig(projectBinding.projectKey());
    updater.updateFileIssues(projectBinding, configuration, ideFilePath, fetchTaintVulnerabilities, progress);
    return getServerIssues(projectBinding, ideFilePath);
  }

  private void downloadServerIssues(EndpointParams endpoint, HttpClient client, String projectKey, boolean fetchTaintVulnerabilities, ProgressMonitor progress) {
    var updater = partialUpdaterFactory.create(endpoint, client);
    Sonarlint.ProjectConfiguration configuration = storageReader.readProjectConfig(projectKey);
    updater.updateFileIssues(projectKey, configuration, fetchTaintVulnerabilities, progress);
  }

  @Override
  public void updateProject(EndpointParams endpoint, HttpClient client, String projectKey, boolean fetchTaintVulnerabilities, @Nullable ClientProgressMonitor monitor) {
    requireNonNull(endpoint);
    requireNonNull(projectKey);
    setLogging(null);
    rwl.writeLock().lock();
    checkUpdateStatus();
    var connectedContainer = new ConnectedContainer(globalConfig, globalStores, endpoint, client);
    try {
      changeState(State.UPDATING);
      connectedContainer.startComponents();
      connectedContainer.updateProject(projectKey, fetchTaintVulnerabilities, globalStatusReader.read(), new ProgressMonitor(monitor));
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      try {
        connectedContainer.stopComponents(false);
      } catch (Exception e) {
        // Ignore
      }
      changeState(globalStatusReader.read() != null ? State.UPDATED : State.NEVER_UPDATED);
      rwl.writeLock().unlock();
    }
  }

  @Override
  public ProjectStorageStatus getProjectStorageStatus(String projectKey) {
    requireNonNull(projectKey);
    return withReadLock(() -> projectStorageStatusReader.apply(projectKey), false);
  }

  @Override
  public void stop(boolean deleteStorage) {
    setLogging(null);
    rwl.writeLock().lock();
    try {
      if (deleteStorage) {
        globalStores.deleteAll();
      }
      if (globalAnalysisContainer != null && containerStarted) {
        globalAnalysisContainer.stopComponents(false);
      }
      if (pluginInstancesRepository != null) {
        pluginInstancesRepository.close();
      }
    } catch (Exception e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      this.globalAnalysisContainer = null;
      this.pluginInstancesRepository = null;
      changeState(State.UNKNOWN);
      rwl.writeLock().unlock();
    }
  }

  private <U> U runInConnectedContainer(EndpointParams endpoint, HttpClient client, Function<ConnectedContainer, U> func) {
    var connectedContainer = new ConnectedContainer(globalConfig, globalStores, endpoint, client);
    try {
      connectedContainer.startComponents();
      return func.apply(connectedContainer);
    } finally {
      try {
        connectedContainer.stopComponents(false);
      } catch (Exception e) {
        // Ignore
      }
    }
  }

  private <T> T checkUpToDateThen(Supplier<T> callable) {
    setLogging(null);
    try {
      checkUpdateStatus();
      return callable.get();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  private <T> T wrapErrors(Supplier<T> callable) {
    setLogging(null);
    try {
      return callable.get();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  private <T> T withReadLock(Supplier<T> callable) {
    return withReadLock(callable, true);
  }

  private <T> T withReadLock(Supplier<T> callable, boolean checkUpdateStatus) {
    setLogging(null);
    rwl.readLock().lock();
    try {
      if (checkUpdateStatus) {
        checkUpdateStatus();
      }
      return callable.get();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      rwl.readLock().unlock();
    }
  }
}
