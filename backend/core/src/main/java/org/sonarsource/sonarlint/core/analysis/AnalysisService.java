/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.analysis;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.active.rules.ActiveRuleDetails;
import org.sonarsource.sonarlint.core.active.rules.ActiveRulesService;
import org.sonarsource.sonarlint.core.active.rules.ServerActiveRulesChanged;
import org.sonarsource.sonarlint.core.active.rules.StandaloneRulesConfigurationChanged;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.api.TriggerType;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.command.NotifyModuleEventCommand;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.tracing.Trace;
import org.sonarsource.sonarlint.core.monitoring.MonitoringService;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.progress.TaskManager;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedWithBindingEvent;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.fs.FileExclusionService;
import org.sonarsource.sonarlint.core.fs.FileOpenedEvent;
import org.sonarsource.sonarlint.core.fs.FileSystemUpdatedEvent;
import org.sonarsource.sonarlint.core.fs.OpenFilesRepository;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.nodejs.InstalledNodeJs;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.plugin.commons.MultivalueProperty;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.DidChangeAnalysisReadinessParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.DidDetectSecretParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.GetInferredAnalysisPropertiesParams;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.AnalyzerConfigurationSynchronized;
import org.sonarsource.sonarlint.core.sync.ConfigurationScopesSynchronizedEvent;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.LanguageDetection.sanitizeExtension;
import static org.sonarsource.sonarlint.core.commons.tracing.Trace.startChild;
import static org.sonarsource.sonarlint.core.commons.util.StringUtils.pluralize;
import static org.sonarsource.sonarlint.core.commons.util.git.GitService.getVCSChangedFiles;

public class AnalysisService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String SONAR_INTERNAL_BUNDLE_PATH_ANALYSIS_PROP = "sonar.js.internal.bundlePath";
  private static final String ANALYSIS_CFG_FOR_ENGINE = "getAnalysisConfigForEngine";
  private static final String GET_ANALYSIS_CFG = "getAnalysisConfig";

  private final SonarLintRpcClient client;
  private final ConfigurationRepository configurationRepository;
  private final LanguageSupportRepository languageSupportRepository;
  private final StorageService storageService;
  private final PluginsService pluginsService;
  private final ActiveRulesService activeRulesService;
  private final ClientFileSystemService fileSystemService;
  private final FileExclusionService fileExclusionService;
  private final MonitoringService monitoringService;
  private final TaskManager taskManager;
  private final NodeJsService nodeJsService;
  private final AnalysisSchedulerCache schedulerCache;
  private final ApplicationEventPublisher eventPublisher;
  private final UserAnalysisPropertiesRepository userAnalysisPropertiesRepository;
  private final Map<String, Boolean> analysisReadinessByConfigScopeId = new ConcurrentHashMap<>();
  private final OpenFilesRepository openFilesRepository;
  private final ClientFileSystemService clientFileSystemService;
  private final Path esLintBridgeServerPath;
  private boolean automaticAnalysisEnabled;

  public AnalysisService(SonarLintRpcClient client, ConfigurationRepository configurationRepository, LanguageSupportRepository languageSupportRepository,
    StorageService storageService, PluginsService pluginsService, ActiveRulesService activeRulesService, ClientFileSystemService fileSystemService,
    FileExclusionService fileExclusionService, MonitoringService monitoringService, TaskManager taskManager, InitializeParams initializeParams, NodeJsService nodeJsService,
    AnalysisSchedulerCache schedulerCache, ApplicationEventPublisher eventPublisher, UserAnalysisPropertiesRepository clientAnalysisPropertiesRepository,
    OpenFilesRepository openFilesRepository, ClientFileSystemService clientFileSystemService) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.languageSupportRepository = languageSupportRepository;
    this.storageService = storageService;
    this.pluginsService = pluginsService;
    this.activeRulesService = activeRulesService;
    this.fileSystemService = fileSystemService;
    this.fileExclusionService = fileExclusionService;
    this.monitoringService = monitoringService;
    this.taskManager = taskManager;
    this.nodeJsService = nodeJsService;
    this.schedulerCache = schedulerCache;
    this.eventPublisher = eventPublisher;
    this.userAnalysisPropertiesRepository = clientAnalysisPropertiesRepository;
    this.openFilesRepository = openFilesRepository;
    this.automaticAnalysisEnabled = initializeParams.isAutomaticAnalysisEnabled();
    this.clientFileSystemService = clientFileSystemService;
    this.esLintBridgeServerPath = initializeParams.getLanguageSpecificRequirements() != null && initializeParams.getLanguageSpecificRequirements().getJsTsRequirements() != null
      ? initializeParams.getLanguageSpecificRequirements().getJsTsRequirements().getBundlePath()
      : null;
  }

  @NotNull
  private static List<String> getPatterns(Set<SonarLanguage> enabledLanguages, Map<String, String> analysisSettings) {
    List<String> patterns = new ArrayList<>();

    for (SonarLanguage language : enabledLanguages) {
      String propertyValue = analysisSettings.get(language.getFileSuffixesPropKey());
      String[] extensions;
      if (propertyValue == null) {
        extensions = language.getDefaultFileSuffixes();
      } else {
        extensions = MultivalueProperty.parseAsCsv(language.getFileSuffixesPropKey(), propertyValue);
      }
      for (String suffix : extensions) {
        var sanitizedExtension = sanitizeExtension(suffix);
        patterns.add("**/*." + sanitizedExtension);
      }
    }
    return patterns;
  }

  public List<String> getSupportedFilePatterns(String configScopeId) {
    var effectiveBinding = configurationRepository.getEffectiveBinding(configScopeId);
    Set<SonarLanguage> enabledLanguages;
    Map<String, String> analysisSettings;
    if (effectiveBinding.isEmpty()) {
      enabledLanguages = languageSupportRepository.getEnabledLanguagesInStandaloneMode();
      analysisSettings = Collections.emptyMap();
    } else {
      enabledLanguages = languageSupportRepository.getEnabledLanguagesInConnectedMode();
      analysisSettings = storageService.binding(effectiveBinding.get())
        .analyzerConfiguration().read().getSettings().getAll();
    }
    // TODO merge client side analysis settings
    return getPatterns(enabledLanguages, analysisSettings);
  }

  private AnalysisConfiguration getAnalysisConfigForEngine(String configScopeId, Set<URI> filesUrisToAnalyze, Map<String, String> extraProperties, boolean hotspotsOnly,
    TriggerType triggerType, Trace trace) {
    trace.setData("trigger", triggerType);
    var baseDir = startChild(trace, "getBaseDir", ANALYSIS_CFG_FOR_ENGINE, () -> fileSystemService.getBaseDir(configScopeId));
    var filesToAnalyze = startChild(trace, "refineAnalysisScope", ANALYSIS_CFG_FOR_ENGINE,
      () -> fileExclusionService.refineAnalysisScope(configScopeId, filesUrisToAnalyze, triggerType, baseDir));
    var actualBaseDir = baseDir == null ? findCommonPrefix(filesUrisToAnalyze) : baseDir;
    var analysisConfig = getAnalysisConfig(configScopeId, hotspotsOnly, trace);
    var analysisProperties = analysisConfig.analysisProperties();
    var inferredAnalysisProperties = startChild(trace, "getInferredAnalysisProperties", ANALYSIS_CFG_FOR_ENGINE,
      () -> client.getInferredAnalysisProperties(new GetInferredAnalysisPropertiesParams(
        configScopeId, filesToAnalyze.stream().map(ClientFile::getUri).toList())).join().getProperties());
    analysisProperties.putAll(inferredAnalysisProperties);
    trace.setData("activeRulesCount", analysisConfig.activeRules().size());
    return startChild(trace, "buildAnalysisConfiguration", ANALYSIS_CFG_FOR_ENGINE, () -> AnalysisConfiguration.builder()
      .addInputFiles(filesToAnalyze.stream().map(BackendInputFile::new).toList())
      .putAllExtraProperties(analysisProperties)
      // properties sent by client using new API were merged above
      // but this line is important for backward compatibility for clients directly triggering analysis
      .putAllExtraProperties(extraProperties)
      .addActiveRules(analysisConfig.activeRules())
      .setBaseDir(actualBaseDir)
      .build());
  }

  private AnalysisConfig getAnalysisConfig(String configScopeId, boolean hotspotsOnly, @Nullable Trace trace) {
    var bindingOpt = configurationRepository.getEffectiveBinding(configScopeId);
    var userAnalysisProperties = userAnalysisPropertiesRepository.getUserProperties(configScopeId);
    // If the client (IDE) has specified a bundle path, use it
    if (this.esLintBridgeServerPath != null) {
      userAnalysisProperties.put(SONAR_INTERNAL_BUNDLE_PATH_ANALYSIS_PROP, this.esLintBridgeServerPath.toString());
    }
    if (trace != null) {
      trace.setData("connected", bindingOpt.isPresent());
    }
    if (bindingOpt.isPresent()) {
      var binding = bindingOpt.get();
      var analyzerConfig = storageService.binding(binding).analyzerConfiguration();
      if (analyzerConfig.isValid()) {
        return getConnectedAnalysisConfig(binding, hotspotsOnly, userAnalysisProperties, trace);
      } else {
        // This can happen when a standalone analysis was scheduled and a synchronization happened in between.
        // The config scope is bound, but the config file is not yet created.
        // In this case, we still trigger the analysis as if it was in standalone instead of failing.
        // This log should not appear when a synchronization is not happening.
        LOG.warn("Could not retrieve connected analysis configuration, falling back to standalone configuration");
      }
    }
    return getStandaloneAnalysisConfig(userAnalysisProperties, trace);
  }

  private AnalysisConfig getConnectedAnalysisConfig(Binding binding, boolean hotspotsOnly, Map<String, String> userAnalysisProperties, @Nullable Trace trace) {
    var serverProperties = startChild(trace, "serverProperties", GET_ANALYSIS_CFG,
      () -> storageService.binding(binding).analyzerConfiguration().read().getSettings().getAll());
    var analysisProperties = new HashMap<>(serverProperties);
    analysisProperties.putAll(userAnalysisProperties);
    var connectedActiveRules = startChild(trace, "buildConnectedActiveRules", GET_ANALYSIS_CFG,
      () -> {
        var activeRules = activeRulesService.getConnectedActiveRules(binding);
        return hotspotsOnly ? activeRules.stream().filter(activeRule -> activeRule.type() == RuleType.SECURITY_HOTSPOT).toList()
          : activeRules;
      });
    return new AnalysisConfig(connectedActiveRules, analysisProperties);
  }

  private AnalysisConfig getStandaloneAnalysisConfig(Map<String, String> userAnalysisProperties, @Nullable Trace trace) {
    var standaloneActiveRules = startChild(trace, "buildStandaloneActiveRules", GET_ANALYSIS_CFG, activeRulesService::getStandaloneActiveRules);
    return new AnalysisConfig(standaloneActiveRules, userAnalysisProperties);
  }

  private static Path findCommonPrefix(Set<URI> uris) {
    var paths = uris.stream().map(Paths::get).toList();
    Path currentPrefixCandidate = paths.get(0).getParent();
    while (currentPrefixCandidate.getNameCount() > 0 && !isPrefixForAll(currentPrefixCandidate, paths)) {
      currentPrefixCandidate = currentPrefixCandidate.getParent();
    }
    return currentPrefixCandidate;
  }

  private static boolean isPrefixForAll(Path prefixCandidate, Collection<Path> paths) {
    return paths.stream().allMatch(p -> p.startsWith(prefixCandidate));
  }

  public void setUserAnalysisProperties(String configScopeId, Map<String, String> properties) {
    if (userAnalysisPropertiesRepository.setUserProperties(configScopeId, properties)) {
      autoAnalyzeOpenFiles(configScopeId);
    }
  }

  public void didChangePathToCompileCommands(String configScopeId, @Nullable String pathToCompileCommands) {
    if (userAnalysisPropertiesRepository.setOrUpdatePathToCompileCommands(configScopeId, pathToCompileCommands)) {
      autoAnalyzeOpenFiles(configScopeId);
    }
  }

  @EventListener
  public void onPluginsSynchronized(PluginsSynchronizedEvent event) {
    var connectionId = event.connectionId();
    checkIfReadyForAnalysis(configurationRepository.getBoundScopesToConnection(connectionId)
      .stream().map(BoundScope::getConfigScopeId).collect(Collectors.toSet()));
  }

  @EventListener
  public void onConfigurationScopeAdded(ConfigurationScopesAddedWithBindingEvent event) {
    var configScopeIds = event.getConfigScopeIds();
    configScopeIds.forEach(schedulerCache::registerModuleIfLeafConfigScope);
    checkIfReadyForAnalysis(configScopeIds);
  }

  @EventListener
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent event) {
    var removedConfigurationScopeId = event.getRemovedConfigurationScopeId();
    analysisReadinessByConfigScopeId.remove(removedConfigurationScopeId);
    client.didChangeAnalysisReadiness(new DidChangeAnalysisReadinessParams(Set.of(removedConfigurationScopeId), false));
    schedulerCache.unregisterModule(removedConfigurationScopeId, event.getRemovedBindingConfiguration().connectionId());
  }

  @EventListener
  public void onBindingConfigurationChanged(BindingConfigChangedEvent event) {
    var configScopeId = event.configScopeId();
    checkIfReadyForAnalysis(Set.of(configScopeId));
  }

  @EventListener
  public void onAnalyzerConfigurationSynchronized(AnalyzerConfigurationSynchronized event) {
    checkIfReadyForAnalysis(event.configScopeIds());
  }

  @EventListener
  public void onConfigurationScopesSynchronized(ConfigurationScopesSynchronizedEvent event) {
    checkIfReadyForAnalysis(event.getConfigScopeIds());
  }

  @EventListener
  public void onFileSystemUpdated(FileSystemUpdatedEvent event) {
    sendModuleEvents(event.getAdded(), ModuleFileEvent.Type.CREATED);
    sendModuleEvents(event.getUpdated(), ModuleFileEvent.Type.MODIFIED);
    sendModuleEvents(event.getRemoved(), ModuleFileEvent.Type.DELETED);
    var updatedFileUrisByConfigScope = event.getUpdated().stream().collect(groupingBy(ClientFile::getConfigScopeId, mapping(ClientFile::getUri, toSet())));
    updatedFileUrisByConfigScope.forEach((configScopeId, fileUris) -> {
      var openFileUris = openFilesRepository.getOpenFilesAmong(configScopeId, fileUris);
      scheduleAutomaticAnalysis(configScopeId, openFileUris);
    });

  }

  @EventListener
  public void onFileOpened(FileOpenedEvent event) {
    scheduleAutomaticAnalysis(event.configurationScopeId(), Set.of(event.fileUri()));
  }

  @EventListener
  public void onStandaloneRulesConfigurationChanged(StandaloneRulesConfigurationChanged event) {
    if (!event.isOnlyDeactivated()) {
      // trigger an analysis if any rule was enabled
      reanalyseOpenFiles(this::isStandalone);
    }
  }

  @CheckForNull
  public UUID forceAnalyzeOpenFiles(String configScopeId) {
    var openFiles = openFilesRepository.getOpenFilesForConfigScope(configScopeId);
    if (openFiles.isEmpty()) {
      // we return UUID because one of the callers is RPC client, it should not call it for empty list of files
      return null;
    }
    return scheduleForcedAnalysis(configScopeId, openFiles, false);
  }

  public void autoAnalyzeOpenFiles(String configScopeId) {
    var openFiles = openFilesRepository.getOpenFilesForConfigScope(configScopeId);
    scheduleAutomaticAnalysis(configScopeId, openFiles);
  }

  @EventListener
  public void onServerActiveRulesChanged(ServerActiveRulesChanged event) {
    var activatedRules = event.activatedRules();
    if (!activatedRules.isEmpty()) {
      reanalyseOpenFiles(not(this::isStandalone));
    }
  }

  private boolean isStandalone(String configScopeId) {
    return configurationRepository.getEffectiveBinding(configScopeId).isEmpty();
  }

  private void sendModuleEvents(List<ClientFile> filesToProcess, ModuleFileEvent.Type type) {
    var filesByScopeId = filesToProcess.stream().collect(groupingBy(ClientFile::getConfigScopeId));
    filesByScopeId.forEach((scopeId, files) -> {
      var scheduler = schedulerCache.getAnalysisSchedulerIfStarted(scopeId);
      if (scheduler != null) {
        files.forEach(file -> scheduler.post(new NotifyModuleEventCommand(scopeId, ClientModuleFileEvent.of(new BackendInputFile(file), type))));
      }
    });
  }

  public boolean shouldUseEnterpriseCSharpAnalyzer(String configurationScopeId) {
    var binding = configurationRepository.getEffectiveBinding(configurationScopeId);
    if (binding.isEmpty()) {
      return false;
    } else {
      var connectionId = binding.get().connectionId();
      return pluginsService.shouldUseEnterpriseCSharpAnalyzer(connectionId);
    }
  }

  private void streamIssue(String configScopeId, UUID analysisId, List<RawIssue> rawIssues, Issue issue) {
    var rawIssue = new RawIssue(issue);
    rawIssues.add(rawIssue);
    if (rawIssue.getRuleKey().contains("secrets")) {
      client.didDetectSecret(new DidDetectSecretParams(configScopeId));
    }
    eventPublisher.publishEvent(new RawIssueDetectedEvent(configScopeId, analysisId, rawIssue));
  }

  private void checkIfReadyForAnalysis(Set<String> configurationScopeIds) {
    var readyConfigScopeIds = new HashSet<String>();
    var scopeThatBecameReady = new HashSet<String>();
    var scopeThatBecameNotReady = new HashSet<String>();
    configurationScopeIds.forEach(configScopeId -> {
      var readyForAnalysis = isReadyForAnalysis(configScopeId);
      var childrenScopesWithSameReadiness = configurationRepository.getChildrenWithInheritedBinding(configScopeId);
      var wasReady = analysisReadinessByConfigScopeId.put(configScopeId, readyForAnalysis);
      analysisReadinessByConfigScopeId.putAll(childrenScopesWithSameReadiness.stream().collect(toMap(Function.identity(), k -> readyForAnalysis)));
      if (readyForAnalysis && !Boolean.TRUE.equals(wasReady)) {
        scopeThatBecameReady.add(configScopeId);
        scopeThatBecameReady.addAll(childrenScopesWithSameReadiness);
      } else if (!readyForAnalysis && Boolean.TRUE.equals(wasReady)) {
        scopeThatBecameNotReady.add(configScopeId);
        scopeThatBecameNotReady.addAll(childrenScopesWithSameReadiness);
      }
      if (readyForAnalysis) {
        readyConfigScopeIds.add(configScopeId);
        readyConfigScopeIds.addAll(childrenScopesWithSameReadiness);
      }
    });
    if (!scopeThatBecameReady.isEmpty()) {
      scopeThatBecameReady.forEach(scopeId -> {
        var scheduler = schedulerCache.getOrCreateAnalysisScheduler(scopeId);
        if (scheduler != null) {
          scheduler.wakeUp();
        }
      });
      client.didChangeAnalysisReadiness(new DidChangeAnalysisReadinessParams(scopeThatBecameReady, true));
    }
    if (!scopeThatBecameNotReady.isEmpty()) {
      client.didChangeAnalysisReadiness(new DidChangeAnalysisReadinessParams(scopeThatBecameNotReady, false));
    }
    reanalyseOpenFiles(readyConfigScopeIds::contains);
  }

  private boolean isReadyForAnalysis(String configScopeId) {
    return configurationRepository.getEffectiveBinding(configScopeId)
      .map(this::isReadyForAnalysis)
      // standalone mode
      .orElse(true);
  }

  private boolean isReadyForAnalysis(Binding binding) {
    var pluginsValid = storageService.connection(binding.connectionId()).plugins().isValid();
    var bindingStorage = storageService.binding(binding);
    var analyzerConfigValid = bindingStorage.analyzerConfiguration().isValid();
    var findingsStorageValid = bindingStorage.findings().wasEverUpdated();
    var isReady = pluginsValid
      && analyzerConfigValid
      // this is not strictly for analysis but for tracking
      && findingsStorageValid;
    LOG.debug("isReadyForAnalysis(connectionId: {}, sonarProjectKey: {}, plugins: {}, analyzer config: {}, findings: {}) => {}",
      binding.connectionId(), binding.sonarProjectKey(), pluginsValid, analyzerConfigValid, findingsStorageValid, isReady);
    return isReady;
  }

  public InstalledNodeJs getAutoDetectedNodeJs() {
    return nodeJsService.getAutoDetectedNodeJs();
  }

  public void didChangeAutomaticAnalysisSetting(boolean enabled) {
    var previouslyEnabled = this.automaticAnalysisEnabled;
    this.automaticAnalysisEnabled = enabled;
    if (previouslyEnabled != enabled) {
      LOG.debug("Automatic analysis setting changed to: {}", enabled);
      eventPublisher.publishEvent(new AutomaticAnalysisSettingChangedEvent(enabled));
      if (enabled) {
        triggerAnalysisForOpenFiles();
      }
    }
  }

  public UUID analyzeFullProject(String configScopeId, boolean hotspotsOnly) {
    var files = clientFileSystemService.getFiles(configScopeId);
    return scheduleForcedAnalysis(configScopeId, files.stream().map(ClientFile::getUri).collect(toSet()), hotspotsOnly);
  }

  public UUID analyzeFileList(String configScopeId, List<URI> filesToAnalyze) {
    return scheduleForcedAnalysis(configScopeId, Set.copyOf(filesToAnalyze), false);
  }

  public UUID analyzeVCSChangedFiles(String configScopeId) {
    var changedFiles = getVCSChangedFiles(clientFileSystemService.getBaseDir(configScopeId));
    return scheduleForcedAnalysis(configScopeId, changedFiles, false);
  }

  private void triggerAnalysisForOpenFiles() {
    openFilesRepository.getOpenFilesByConfigScopeId()
      .forEach((configurationScopeId, files) -> scheduleForcedAnalysis(configurationScopeId, files, false));
  }

  public UUID scheduleForcedAnalysis(String configurationScopeId, Set<URI> files, boolean hotspotsOnly) {
    var analysisId = UUID.randomUUID();
    var rawIssues = new ArrayList<RawIssue>();
    schedule(configurationScopeId, getAnalyzeCommand(configurationScopeId, files, rawIssues, hotspotsOnly, TriggerType.FORCED, analysisId), analysisId, rawIssues, true, null)
      .exceptionally(e -> {
        if (!(e instanceof CancellationException)) {
          LOG.error("Error during analysis", e);
        }
        return null;
      });
    return analysisId;
  }

  public CompletableFuture<AnalysisResult> scheduleAnalysis(String configurationScopeId, UUID analysisId, Set<URI> files, Map<String, String> extraProperties,
    boolean shouldFetchServerIssues, TriggerType triggerType, SonarLintCancelMonitor cancelChecker) {
    var rawIssues = new ArrayList<RawIssue>();
    var trace = newAnalysisTrace();
    var analysisTask = new AnalyzeCommand(configurationScopeId, analysisId, triggerType,
      () -> getAnalysisConfigForEngine(configurationScopeId, files, extraProperties, false, triggerType, trace),
      issue -> streamIssue(configurationScopeId, analysisId, rawIssues, issue), trace, cancelChecker,
      taskManager, inputFiles -> analysisStarted(configurationScopeId, analysisId, inputFiles), () -> analysisReadinessByConfigScopeId.getOrDefault(configurationScopeId, false),
      files, extraProperties);
    return schedule(configurationScopeId, analysisTask, analysisId, rawIssues, shouldFetchServerIssues, trace);
  }

  private Trace newAnalysisTrace() {
    var newTrace = monitoringService.newTrace("AnalysisService", "analyze");
    var currentRuntime = Runtime.getRuntime();
    newTrace.setData("availableProcessors", currentRuntime.availableProcessors());
    newTrace.setData("totalMemory", currentRuntime.totalMemory());
    newTrace.setData("maxMemory", currentRuntime.maxMemory());
    return newTrace;
  }

  private void scheduleAutomaticAnalysis(String configScopeId, Set<URI> filesToAnalyze) {
    if (automaticAnalysisEnabled && !filesToAnalyze.isEmpty()) {
      var rawIssues = new ArrayList<RawIssue>();
      var analysisId = UUID.randomUUID();
      var command = getAnalyzeCommand(configScopeId, filesToAnalyze, rawIssues, false, TriggerType.AUTO, analysisId);
      schedule(configScopeId, command, analysisId, rawIssues, true, null)
        .exceptionally(exception -> {
          if (!(exception instanceof CancellationException) && !(exception instanceof CompletionException && exception.getCause() instanceof CancellationException)) {
            LOG.error("Error during automatic analysis", exception);
          }
          return null;
        });
    }
  }

  private void analysisStarted(String configurationScopeId, UUID analysisId, List<ClientInputFile> inputFiles) {
    eventPublisher.publishEvent(new AnalysisStartedEvent(configurationScopeId, analysisId, inputFiles));
  }

  private CompletableFuture<AnalysisResult> schedule(String configScopeId, AnalyzeCommand command, UUID analysisId, ArrayList<RawIssue> rawIssues,
    boolean shouldFetchServerIssues, @Nullable Trace trace) {
    var scheduler = startChild(trace, "getOrCreateAnalysisScheduler", "schedule", () -> schedulerCache.getOrCreateAnalysisScheduler(configScopeId, command.getTrace()));
    startChild(trace, "post", "schedule", () -> scheduler.post(command));
    var result = command.getFutureResult();
    result.exceptionally(exception -> {
      eventPublisher.publishEvent(new AnalysisFailedEvent(analysisId));
      if (exception instanceof CancellationException) {
        LOG.debug("Analysis canceled");
      } else {
        LOG.error("Error during analysis", exception);
      }
      return null;
    });
    return result
      .thenApply(analysisResults -> {
        var languagePerFile = analysisResults.languagePerFile().entrySet().stream().collect(HashMap<URI, SonarLanguage>::new,
          (map, entry) -> map.put(entry.getKey().uri(), entry.getValue()), HashMap::putAll);
        logSummary(rawIssues, analysisResults.getDuration());
        eventPublisher.publishEvent(new AnalysisFinishedEvent(analysisId, configScopeId, analysisResults.getDuration(),
          languagePerFile, analysisResults.failedAnalysisFiles().isEmpty(), rawIssues, shouldFetchServerIssues));
        return new AnalysisResult(
          analysisResults.failedAnalysisFiles().stream().map(ClientInputFile::getClientObject).map(clientObj -> ((ClientFile) clientObj).getUri()).collect(Collectors.toSet()),
          rawIssues);
      });
  }

  private AnalyzeCommand getAnalyzeCommand(String configurationScopeId, Set<URI> files, ArrayList<RawIssue> rawIssues, boolean hotspotsOnly, TriggerType triggerType,
    UUID analysisId) {
    var trace = newAnalysisTrace();
    return new AnalyzeCommand(configurationScopeId, analysisId, triggerType,
      () -> getAnalysisConfigForEngine(configurationScopeId, files, Map.of(), hotspotsOnly, triggerType, trace),
      issue -> streamIssue(configurationScopeId, analysisId, rawIssues, issue), trace,
      new SonarLintCancelMonitor(), taskManager, inputFiles -> analysisStarted(configurationScopeId, analysisId, inputFiles),
      () -> analysisReadinessByConfigScopeId.getOrDefault(configurationScopeId, false), files, Map.of());
  }

  private void reanalyseOpenFiles(Predicate<String> configScopeFilter) {
    openFilesRepository.getOpenFilesByConfigScopeId()
      .entrySet()
      .stream().filter(entry -> configScopeFilter.test(entry.getKey()))
      .forEach(entry -> scheduleAutomaticAnalysis(entry.getKey(), entry.getValue()));
  }

  private static void logSummary(List<RawIssue> rawIssues, Duration analysisDuration) {
    // ignore project-level issues for now
    var fileRawIssues = rawIssues.stream().filter(issue -> issue.getTextRange() != null).toList();
    var issuesCount = fileRawIssues.stream().filter(not(RawIssue::isSecurityHotspot)).count();
    var hotspotsCount = fileRawIssues.stream().filter(RawIssue::isSecurityHotspot).count();
    LOG.info("Analysis detected {} and {} in {}ms", pluralize(issuesCount, "issue"), pluralize(hotspotsCount, "Security Hotspot"), analysisDuration.toMillis());
  }

  private record AnalysisConfig(List<ActiveRuleDetails> activeRules, Map<String, String> analysisProperties) {
  }
}
