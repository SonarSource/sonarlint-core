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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.analysis.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.DefaultClientIssue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBranches;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.UpdateResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.perform.GlobalStorageUpdateExecutor;
import org.sonarsource.sonarlint.core.container.connected.update.perform.ProjectStorageUpdateExecutor;
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
import org.sonarsource.sonarlint.core.events.EventDispatcher;
import org.sonarsource.sonarlint.core.events.ServerEventsAutoSubscriber;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository.Configuration;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverapi.push.RuleSetChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.storage.LocalStorageSynchronizer;
import org.sonarsource.sonarlint.core.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.storage.ProjectStorage;
import org.sonarsource.sonarlint.core.storage.UpdateStorageOnRuleSetChanged;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths.encodeForFs;

public final class ConnectedSonarLintEngineImpl extends AbstractSonarLintEngine implements ConnectedSonarLintEngine {

  private final ConnectedGlobalConfiguration globalConfig;
  private final GlobalUpdateStatusReader globalStatusReader;
  private final PluginsStorage pluginsStorage;
  private final GlobalStores globalStores;
  private final ProjectStorage projectStorage;
  private final LocalStorageSynchronizer storageSynchronizer;
  private final ProjectStorageStatusReader projectStorageStatusReader;
  private final IssueStoreReader issueStoreReader;
  private final StorageFileExclusions storageFileExclusions;
  private final PartialUpdaterFactory partialUpdaterFactory;
  private final GlobalStorageUpdateExecutor globalStorageUpdateExecutor;
  private final ProjectStorageUpdateExecutor projectStorageUpdateExecutor;
  private final AtomicReference<AnalysisContext> analysisContext = new AtomicReference<>();
  private final ServerEventsAutoSubscriber serverEventsAutoSubscriber;

  private final StorageReader storageReader;

  public ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration globalConfig) {
    super(globalConfig.getLogOutput());
    this.globalConfig = globalConfig;
    this.globalStores = new GlobalStores(globalConfig);
    this.globalStatusReader = new GlobalUpdateStatusReader(globalStores.getServerInfoStore(), globalStores.getStorageStatusStore());

    var projectStoragePaths = new ProjectStoragePaths(globalConfig);
    this.projectStorageStatusReader = new ProjectStorageStatusReader(projectStoragePaths);

    var storageRoot = globalConfig.getStorageRoot().resolve(encodeForFs(globalConfig.getConnectionId()));
    var projectsStorageRoot = storageRoot.resolve("projects");
    projectStorage = new ProjectStorage(projectsStorageRoot);
    var issueStorePaths = new IssueStorePaths();
    this.storageReader = new StorageReader(projectStoragePaths);
    this.issueStoreReader = new IssueStoreReader(new IssueStoreFactory(), issueStorePaths, projectStoragePaths, storageReader);
    this.storageFileExclusions = new StorageFileExclusions(issueStorePaths);

    this.partialUpdaterFactory = new PartialUpdaterFactory(projectStoragePaths, issueStorePaths);

    pluginsStorage = new PluginsStorage(storageRoot.resolve("plugins"));
    storageSynchronizer = new LocalStorageSynchronizer(globalConfig.getEnabledLanguages(), globalConfig.getEmbeddedPluginPathsByKey().keySet(), pluginsStorage, projectStorage);
    globalStorageUpdateExecutor = new GlobalStorageUpdateExecutor(globalStores.getGlobalStorage());
    projectStorageUpdateExecutor = new ProjectStorageUpdateExecutor(projectStoragePaths);
    pluginsStorage.cleanUp();
    var eventRouter = new EventDispatcher()
      .dispatch(RuleSetChangedEvent.class, new UpdateStorageOnRuleSetChanged(projectStorage));
    serverEventsAutoSubscriber = new ServerEventsAutoSubscriber(eventRouter);
    start();
  }

  @Override
  public AnalysisEngine getAnalysisEngine() {
    return analysisContext.get().analysisEngine;
  }

  public AnalysisContext start() {
    setLogging(null);
    return analysisContext.getAndSet(loadAnalysisContext());
  }

  private AnalysisContext loadAnalysisContext() {
    var pluginInstancesRepository = createPluginInstanceRepository();
    var pluginDetails = pluginInstancesRepository.getPluginCheckResultByKeys().values().stream().map(p -> new PluginDetails(p.getPlugin().getKey(), p.getPlugin().getName(),
      Optional.ofNullable(p.getPlugin().getVersion()).map(Version::toString).orElse(null), p.getSkipReason().orElse(null))).collect(Collectors.toList());

    var allRulesDefinitionsByKey = loadPluginMetadata(pluginInstancesRepository, globalConfig.getEnabledLanguages(), true);

    var analysisGlobalConfig = AnalysisEngineConfiguration.builder()
      .addEnabledLanguages(globalConfig.getEnabledLanguages())
      .setClientPid(globalConfig.getClientPid())
      .setExtraProperties(globalConfig.extraProperties())
      .setNodeJs(globalConfig.getNodeJsPath())
      .setWorkDir(globalConfig.getWorkDir())
      .setModulesProvider(globalConfig.getModulesProvider())
      .build();
    var analysisEngine = new AnalysisEngine(analysisGlobalConfig, pluginInstancesRepository, logOutput);
    return new AnalysisContext(pluginDetails, allRulesDefinitionsByKey, analysisEngine);
  }

  private PluginInstancesRepository createPluginInstanceRepository() {
    Map<String, Path> pluginsToLoadByKey = new HashMap<>();
    // order is important as e.g. embedded takes precedence over stored
    pluginsToLoadByKey.putAll(globalConfig.getExtraPluginsPathsByKey());
    pluginsToLoadByKey.putAll(pluginsStorage.getStoredPluginPathsByKey());
    pluginsToLoadByKey.putAll(globalConfig.getEmbeddedPluginPathsByKey());
    Set<Path> plugins = new HashSet<>(pluginsToLoadByKey.values());

    var config = new Configuration(plugins, globalConfig.getEnabledLanguages(), Optional.ofNullable(globalConfig.getNodeJsVersion()));
    return new PluginInstancesRepository(config);
  }

  private static class ActiveRulesContext {
    private final List<ActiveRule> activeRules = new ArrayList<>();
    private final Map<String, ActiveRuleMetadata> activeRulesMetadata = new HashMap<>();

    public void includeRule(SonarLintRuleDefinition ruleDefinition, ServerActiveRule activeRule) {
      var activeRuleForAnalysis = new ActiveRule(activeRule.getRuleKey(), ruleDefinition.getLanguage().getLanguageKey());
      activeRuleForAnalysis.setTemplateRuleKey(trimToNull(activeRule.getTemplateKey()));
      Map<String, String> effectiveParams = new HashMap<>(ruleDefinition.getDefaultParams());
      effectiveParams.putAll(activeRule.getParams());
      activeRuleForAnalysis.setParams(effectiveParams);
      activeRules.add(activeRuleForAnalysis);
      activeRulesMetadata.put(activeRule.getRuleKey(), new ActiveRuleMetadata(activeRule.getSeverity(), ruleDefinition.getType()));
    }

    public void includeRule(SonarLintRuleDefinition rule) {
      var activeRuleForAnalysis = new ActiveRule(rule.getKey(), rule.getLanguage().getLanguageKey());
      activeRuleForAnalysis.setParams(rule.getDefaultParams());
      activeRules.add(activeRuleForAnalysis);
      activeRulesMetadata.put(activeRuleForAnalysis.getRuleKey(), new ActiveRuleMetadata(rule.getSeverity(), rule.getType()));
    }

    private ActiveRuleMetadata getRuleMetadata(String ruleKey) {
      return activeRulesMetadata.get(ruleKey);
    }

    private static class ActiveRuleMetadata {
      private final String severity;
      private final String type;

      private ActiveRuleMetadata(String severity, String type) {
        this.severity = severity;
        this.type = type;
      }
    }
  }

  @Override
  public AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener, @Nullable ClientLogOutput logOutput,
    @Nullable ClientProgressMonitor monitor) {
    requireNonNull(configuration);
    requireNonNull(issueListener);

    setLogging(logOutput);
    checkStatus(configuration.projectKey());

    var analysisConfigBuilder = AnalysisConfiguration.builder()
      .addInputFiles(configuration.inputFiles());
    var projectKey = configuration.projectKey();
    if (projectKey != null) {
      analysisConfigBuilder.putAllExtraProperties(projectStorage.getAnalyzerConfiguration(projectKey).getSettings().getAll());
      analysisConfigBuilder.putAllExtraProperties(globalConfig.extraProperties());
    }
    var activeRulesContext = buildActiveRulesContext(configuration);
    analysisConfigBuilder.putAllExtraProperties(configuration.extraProperties())
      .addActiveRules(activeRulesContext.activeRules)
      .setBaseDir(configuration.baseDir())
      .build();

    var analysisConfiguration = analysisConfigBuilder.build();

    try {
      var analysisResults = getAnalysisEngine().post(new AnalyzeCommand(configuration.moduleKey(), analysisConfiguration,
        issue -> streamIssue(issueListener, issue, activeRulesContext), logOutput), new ProgressMonitor(monitor)).get();
      return analysisResults == null ? new AnalysisResults() : analysisResults;
    } catch (Exception e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  private static void streamIssue(IssueListener issueListener, Issue newIssue, ActiveRulesContext activeRulesContext) {
    var ruleMetadata = activeRulesContext.getRuleMetadata(newIssue.getRuleKey());
    issueListener.handle(new DefaultClientIssue(newIssue, ruleMetadata.severity, ruleMetadata.type));
  }

  private ActiveRulesContext buildActiveRulesContext(ConnectedAnalysisConfiguration configuration) {
    var analysisRulesContext = new ActiveRulesContext();
    // could be empty before the first sync
    var projectKey = configuration.projectKey();
    if (projectKey == null) {
      // this should be forbidden by client side
      LOG.debug("No project key provided, no rules will be used for analysis");
      return analysisRulesContext;
    }

    var allRulesDefinitionsByKey = analysisContext.get().allRulesDefinitionsByKey;
    projectStorage.getAnalyzerConfiguration(projectKey).getRuleSetByLanguageKey().entrySet()
      .stream().filter(e -> Language.forKey(e.getKey()).filter(l -> globalConfig.getEnabledLanguages().contains(l)).isPresent())
      .forEach(e -> {
        var languageKey = e.getKey();
        var ruleSet = e.getValue();

        LOG.debug("  * {}: {} active rules", languageKey, ruleSet.getRules().size());
        for (ServerActiveRule activeRuleFromStorage : ruleSet.getRules()) {
          var ruleDefinitionKey = StringUtils.isNotBlank(activeRuleFromStorage.getTemplateKey()) ? activeRuleFromStorage.getTemplateKey() : activeRuleFromStorage.getRuleKey();
          var ruleDefinition = allRulesDefinitionsByKey.get(ruleDefinitionKey);
          if (ruleDefinition == null) {
            LOG.debug("Rule {} is enabled on the server, but not available in SonarLint", activeRuleFromStorage.getRuleKey());
            continue;
          }
          analysisRulesContext.includeRule(ruleDefinition, activeRuleFromStorage);
        }
      });

    allRulesDefinitionsByKey.values().stream()
      .filter(ruleDefinition -> isRuleFromExtraPlugin(ruleDefinition.getLanguage(), globalConfig))
      .forEach(analysisRulesContext::includeRule);

    return analysisRulesContext;

  }

  private static boolean isRuleFromExtraPlugin(Language ruleLanguage, ConnectedGlobalConfiguration config) {
    return config.getExtraPluginsPathsByKey().keySet()
      .stream().anyMatch(extraPluginKey -> ruleLanguage.getLanguageKey().equals(extraPluginKey));
  }

  private void checkStatus(@Nullable String projectKey) {
    var updateStatus = globalStatusReader.read();
    if (updateStatus == null) {
      throw new StorageException("Missing storage for connection");
    }
    if (updateStatus.isStale()) {
      throw new StorageException("Outdated storage for connection");
    }
    if (projectKey != null) {
      var projectUpdateStatus = getProjectStorageStatus(projectKey);
      if (projectUpdateStatus == null) {
        throw new StorageException(String.format("No storage for project '%s'. Please update the binding.", projectKey));
      } else if (projectUpdateStatus.isStale()) {
        throw new StorageException(String.format("Stored data for project '%s' is stale because "
          + "it was created with a different version of SonarLint. Please update the binding.", projectKey));
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
    var result = storageSynchronizer.synchronize(serverApi, projectKeys, new ProgressMonitor(monitor));
    if (result.hasAnalyzerBeenUpdated()) {
      restartAnalysisEngine();
    }
  }

  @Override
  public UpdateResult update(EndpointParams endpoint, HttpClient client, @Nullable ClientProgressMonitor monitor) {
    requireNonNull(endpoint);
    setLogging(null);
    globalStorageUpdateExecutor.update(new ServerApiHelper(endpoint, client), new ProgressMonitor(monitor));
    return new UpdateResult(globalStatusReader.read());
  }

  private void restartAnalysisEngine() {
    var oldAnalysisContext = start();
    oldAnalysisContext.finishGracefully();
  }

  @Override
  public CompletableFuture<ConnectedRuleDetails> getActiveRuleDetails(EndpointParams endpoint, HttpClient client, String ruleKey, @Nullable String projectKey) {
    var allRulesDefinitionsByKey = analysisContext.get().allRulesDefinitionsByKey;
    var ruleDefFromPlugin = allRulesDefinitionsByKey.get(ruleKey);
    if (ruleDefFromPlugin != null && (globalConfig.getExtraPluginsPathsByKey().containsKey(ruleDefFromPlugin.getLanguage().getPluginKey()) || projectKey == null)) {
      // if no project key, or for rules from extra plugins there will be no rules metadata in the storage
      return CompletableFuture.completedFuture(
        new DefaultRuleDetails(ruleKey, ruleDefFromPlugin.getName(), ruleDefFromPlugin.getHtmlDescription(), ruleDefFromPlugin.getSeverity(), ruleDefFromPlugin.getType(),
          ruleDefFromPlugin.getLanguage(), ""));
    }
    if (projectKey != null) {
      var analyzerConfiguration = projectStorage.getAnalyzerConfiguration(projectKey);
      var storageActiveRule = analyzerConfiguration.getRuleSetByLanguageKey().values().stream()
        .flatMap(s -> s.getRules().stream())
        .filter(r -> r.getRuleKey().equals(ruleKey)).findFirst();
      if (storageActiveRule.isPresent()) {
        var activeRuleFromStorage = storageActiveRule.get();
        var serverSeverity = activeRuleFromStorage.getSeverity();
        if (StringUtils.isNotBlank(activeRuleFromStorage.getTemplateKey())) {
          var templateRuleDefFromPlugin = Optional.ofNullable(allRulesDefinitionsByKey.get(activeRuleFromStorage.getTemplateKey()))
            .orElseThrow(() -> new IllegalStateException("Unable to find rule definition for rule template " + activeRuleFromStorage.getTemplateKey()));
          return new ServerApi(new ServerApiHelper(endpoint, client)).rules().getRule(ruleKey)
            .thenApply(
              serverRule -> new DefaultRuleDetails(
                ruleKey,
                serverRule.getName(),
                serverRule.getHtmlDesc(),
                serverSeverity,
                templateRuleDefFromPlugin.getType(),
                templateRuleDefFromPlugin.getLanguage(),
                serverRule.getHtmlNote()));
        } else {
          if (ruleDefFromPlugin == null) {
            throw new IllegalStateException("Unable to find rule definition for rule " + ruleKey);
          }
          return new ServerApi(new ServerApiHelper(endpoint, client)).rules().getRule(ruleKey)
            .thenApply(serverRule -> new DefaultRuleDetails(ruleKey, ruleDefFromPlugin.getName(), ruleDefFromPlugin.getHtmlDescription(),
              serverSeverity != null ? serverSeverity : ruleDefFromPlugin.getSeverity(), ruleDefFromPlugin.getType(), ruleDefFromPlugin.getLanguage(),
              serverRule.getHtmlNote()));
        }
      }
    }
    throw new IllegalStateException("Unable to find rule details for '" + ruleKey + "'");
  }

  @Override
  public Collection<PluginDetails> getPluginDetails() {
    return analysisContext.get().pluginDetails;
  }

  @Override
  public Map<String, ServerProject> allProjectsByKey() {
    try {
      return globalStores.getServerProjectsStore().getAll();
    } catch (StorageException e) {
      LOG.error("Unable to read projects keys from the storage", e);
      return Map.of();
    }
  }

  @Override
  public Map<String, ServerProject> downloadAllProjects(EndpointParams endpoint, HttpClient client, @Nullable ClientProgressMonitor monitor) {
    return wrapErrors(() -> {
      try {
        return new ProjectListDownloader(new ServerApiHelper(endpoint, client), globalStores.getServerProjectsStore()).fetch(new ProgressMonitor(monitor));
      } catch (Exception e) {
        // null as cause so that it doesn't get wrapped
        throw new DownloadException("Failed to update project list: " + e.getMessage(), null);
      }
    });
  }

  @Override
  public ProjectBranches getServerBranches(String projectKey) {
    try {
      var projectBranchesFromStorage = projectStorage.getProjectBranches(projectKey);
      return new ProjectBranches(projectBranchesFromStorage.getBranchNames(), projectBranchesFromStorage.getMainBranchName());
    } catch (StorageException e) {
      LOG.error("Unable to read projects branches from the storage", e);
      return new ProjectBranches(Set.of(), Optional.empty());
    }
  }

  @Override
  public List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String ideFilePath) {
    return issueStoreReader.getServerIssues(projectBinding, ideFilePath);
  }

  @Override
  public <G> List<G> getExcludedFiles(ProjectBinding projectBinding, Collection<G> files, Function<G, String> ideFilePathExtractor, Predicate<G> testFilePredicate) {
    return storageFileExclusions.getExcludedFiles(projectStorage, projectBinding, files, ideFilePathExtractor, testFilePredicate);
  }

  @Override
  public void subscribeForEvents(EndpointParams endpoint, HttpClient client, Set<String> projectKeys, @Nullable ClientLogOutput clientLogOutput) {
    var logOutput = clientLogOutput == null ? this.logOutput : clientLogOutput;
    if (logOutput == null) {
      logOutput = (message, level) -> {
      };
    }
    serverEventsAutoSubscriber.subscribePermanently(new ServerApi(new ServerApiHelper(endpoint, client)), projectKeys, globalConfig.getEnabledLanguages(), logOutput);
  }

  @Override
  public List<ServerIssue> downloadServerIssues(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath,
    boolean fetchTaintVulnerabilities, @Nullable String branchName, @Nullable ClientProgressMonitor monitor) {
    return downloadServerIssues(endpoint, client, projectBinding, ideFilePath, fetchTaintVulnerabilities, branchName, new ProgressMonitor(monitor));
  }

  @Override
  public void downloadServerIssues(EndpointParams endpoint, HttpClient client, String projectKey, boolean fetchTaintVulnerabilities,
    @Nullable String branchName, @Nullable ClientProgressMonitor monitor) {
    downloadServerIssues(endpoint, client, projectKey, fetchTaintVulnerabilities, branchName, new ProgressMonitor(monitor));
  }

  @Override
  public ProjectBinding calculatePathPrefixes(String projectKey, Collection<String> ideFilePaths) {
    List<Path> idePathList = ideFilePaths.stream()
      .map(Paths::get)
      .collect(Collectors.toList());
    List<Path> sqPathList = storageReader.readProjectComponents(projectKey)
      .getComponentList().stream()
      .map(Paths::get)
      .collect(Collectors.toList());
    var fileMatcher = new FileMatcher();
    var match = fileMatcher.match(sqPathList, idePathList);
    return new ProjectBinding(projectKey, FilenameUtils.separatorsToUnix(match.sqPrefix().toString()),
      FilenameUtils.separatorsToUnix(match.idePrefix().toString()));
  }

  private List<ServerIssue> downloadServerIssues(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath,
    boolean fetchTaintVulnerabilities, @Nullable String branchName, ProgressMonitor progress) {
    var updater = partialUpdaterFactory.create();
    var configuration = storageReader.readProjectConfig(projectBinding.projectKey());
    updater.updateFileIssues(new ServerApiHelper(endpoint, client), projectBinding, configuration, ideFilePath, fetchTaintVulnerabilities, branchName, progress);
    return getServerIssues(projectBinding, ideFilePath);
  }

  private void downloadServerIssues(EndpointParams endpoint, HttpClient client, String projectKey,
    boolean fetchTaintVulnerabilities, @Nullable String branchName, ProgressMonitor progress) {
    var updater = partialUpdaterFactory.create();
    var configuration = storageReader.readProjectConfig(projectKey);
    updater.updateFileIssues(new ServerApiHelper(endpoint, client), projectKey, configuration, fetchTaintVulnerabilities, branchName, progress);
  }

  @Override
  public void updateProject(EndpointParams endpoint, HttpClient client, String projectKey, boolean fetchTaintVulnerabilities,
    @Nullable String branchName, @Nullable ClientProgressMonitor monitor) {
    requireNonNull(endpoint);
    requireNonNull(projectKey);
    setLogging(null);

    var globalStorageStatus = globalStatusReader.read();
    if (globalStorageStatus == null || globalStorageStatus.isStale()) {
      throw new StorageException("Missing or outdated storage for connection '" + globalConfig.getConnectionId() + "'");
    }
    projectStorageUpdateExecutor.update(new ServerApiHelper(endpoint, client), projectKey, fetchTaintVulnerabilities, branchName, new ProgressMonitor(monitor));
  }

  @Override
  public ProjectStorageStatus getProjectStorageStatus(String projectKey) {
    requireNonNull(projectKey);
    return projectStorageStatusReader.apply(projectKey);
  }

  @Override
  public void stop(boolean deleteStorage) {
    setLogging(null);
    try {
      serverEventsAutoSubscriber.stop();
      analysisContext.get().destroy();
      if (deleteStorage) {
        globalStores.deleteAll();
      }
    } catch (Exception e) {
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

  private static class AnalysisContext {
    private final Collection<PluginDetails> pluginDetails;
    private final Map<String, SonarLintRuleDefinition> allRulesDefinitionsByKey;
    private final AnalysisEngine analysisEngine;

    public AnalysisContext(List<PluginDetails> pluginDetails, Map<String, SonarLintRuleDefinition> allRulesDefinitionsByKey, AnalysisEngine analysisEngine) {
      this.pluginDetails = pluginDetails;
      this.allRulesDefinitionsByKey = allRulesDefinitionsByKey;
      this.analysisEngine = analysisEngine;
    }

    public void destroy() {
      analysisEngine.stop();
    }

    public void finishGracefully() {
      analysisEngine.finishGracefully();
    }
  }

}
