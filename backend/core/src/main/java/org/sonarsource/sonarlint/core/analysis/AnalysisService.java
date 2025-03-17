/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.analysis.sonarapi.MultivalueProperty;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedWithBindingEvent;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.fs.FileOpenedEvent;
import org.sonarsource.sonarlint.core.fs.FileSystemUpdatedEvent;
import org.sonarsource.sonarlint.core.fs.OpenFilesRepository;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.nodejs.InstalledNodeJs;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.progress.RpcProgressMonitor;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.ActiveRuleDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetAnalysisConfigResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetGlobalConfigurationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.NodeJsDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.DidChangeAnalysisReadinessParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.DidDetectSecretParams;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.rules.NewRulesActivatedOnServer;
import org.sonarsource.sonarlint.core.rules.RulesService;
import org.sonarsource.sonarlint.core.rules.StandaloneRulesConfigurationChanged;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.AnalyzerConfigurationSynchronized;
import org.sonarsource.sonarlint.core.sync.ConfigurationScopesSynchronizedEvent;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.LanguageDetection.sanitizeExtension;
import static org.sonarsource.sonarlint.core.commons.util.git.GitService.getVSCChangedFiles;

public class AnalysisService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String SONAR_INTERNAL_BUNDLE_PATH_ANALYSIS_PROP = "sonar.js.internal.bundlePath";

  private final SonarLintRpcClient client;
  private final ConfigurationRepository configurationRepository;
  private final LanguageSupportRepository languageSupportRepository;
  private final StorageService storageService;
  private final PluginsService pluginsService;
  private final RulesService rulesService;
  private final RulesRepository rulesRepository;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final boolean hotspotEnabled;
  private final NodeJsService nodeJsService;
  private final AnalysisSchedulerCache schedulerCache;
  private final ApplicationEventPublisher eventPublisher;
  private final UserAnalysisPropertiesRepository userAnalysisPropertiesRepository;
  private final boolean isDataflowBugDetectionEnabled;
  private final Map<String, Boolean> analysisReadinessByConfigScopeId = new ConcurrentHashMap<>();
  private final OpenFilesRepository openFilesRepository;
  private final ClientFileSystemService clientFileSystemService;
  private final Path esLintBridgeServerPath;
  private boolean automaticAnalysisEnabled;

  public AnalysisService(SonarLintRpcClient client, ConfigurationRepository configurationRepository, LanguageSupportRepository languageSupportRepository,
    StorageService storageService, PluginsService pluginsService, RulesService rulesService, RulesRepository rulesRepository,
    ConnectionConfigurationRepository connectionConfigurationRepository, InitializeParams initializeParams, NodeJsService nodeJsService, AnalysisSchedulerCache schedulerCache,
    ApplicationEventPublisher eventPublisher, UserAnalysisPropertiesRepository clientAnalysisPropertiesRepository, OpenFilesRepository openFilesRepository,
    ClientFileSystemService clientFileSystemService) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.languageSupportRepository = languageSupportRepository;
    this.storageService = storageService;
    this.pluginsService = pluginsService;
    this.rulesService = rulesService;
    this.rulesRepository = rulesRepository;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.hotspotEnabled = initializeParams.getFeatureFlags().isEnableSecurityHotspots();
    this.isDataflowBugDetectionEnabled = initializeParams.getFeatureFlags().isEnableDataflowBugDetection();
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

  @NotNull
  private static org.sonarsource.sonarlint.core.rpc.protocol.common.Language toDto(SonarLanguage language) {
    return org.sonarsource.sonarlint.core.rpc.protocol.common.Language.valueOf(language.name());
  }

  private static Map<String, String> getEffectiveParams(SonarLintRuleDefinition ruleOrTemplateDefinition, ServerActiveRule activeRule) {
    Map<String, String> effectiveParams = new HashMap<>(ruleOrTemplateDefinition.getDefaultParams());
    activeRule.getParams().forEach((paramName, paramValue) -> {
      if (!ruleOrTemplateDefinition.getParams().containsKey(paramName)) {
        LOG.debug("Rule parameter '{}' for rule '{}' does not exist in embedded analyzer, ignoring.", paramName, ruleOrTemplateDefinition.getKey());
        return;
      }
      effectiveParams.put(paramName, paramValue);
    });
    return effectiveParams;
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

  public GetGlobalConfigurationResponse getGlobalStandaloneConfiguration() {
    var enabledLanguages = languageSupportRepository.getEnabledLanguagesInStandaloneMode();
    var pluginPaths = pluginsService.getEmbeddedPluginPaths();
    var activeNodeJs = nodeJsService.getActiveNodeJs();
    var nodeJsDetailsDto = activeNodeJs == null ? null : new NodeJsDetailsDto(activeNodeJs.getPath(), activeNodeJs.getVersion().toString());
    return new GetGlobalConfigurationResponse(pluginPaths, enabledLanguages.stream().map(AnalysisService::toDto).toList(), nodeJsDetailsDto, false);
  }

  public GetGlobalConfigurationResponse getGlobalConnectedConfiguration(String connectionId) {
    var enabledLanguages = languageSupportRepository.getEnabledLanguagesInConnectedMode();
    var pluginPaths = pluginsService.getConnectedPluginPaths(connectionId);
    var activeNodeJs = nodeJsService.getActiveNodeJs();
    var nodeJsDetailsDto = activeNodeJs == null ? null : new NodeJsDetailsDto(activeNodeJs.getPath(), activeNodeJs.getVersion().toString());
    return new GetGlobalConfigurationResponse(pluginPaths, enabledLanguages.stream().map(AnalysisService::toDto).toList(), nodeJsDetailsDto,
      isDataflowBugDetectionEnabled);
  }

  public GetAnalysisConfigResponse getAnalysisConfig(String configScopeId, boolean hotspotsOnly) {
    var bindingOpt = configurationRepository.getEffectiveBinding(configScopeId);
    var activeNodeJs = nodeJsService.getActiveNodeJs();
    var userAnalysisProperties = userAnalysisPropertiesRepository.getUserProperties(configScopeId);
    // if client (IDE) has specified a bundle path, use it
    if (this.esLintBridgeServerPath != null) {
      userAnalysisProperties.put(SONAR_INTERNAL_BUNDLE_PATH_ANALYSIS_PROP, this.esLintBridgeServerPath.toString());
    }
    var nodeJsDetailsDto = activeNodeJs == null ? null : new NodeJsDetailsDto(activeNodeJs.getPath(), activeNodeJs.getVersion().toString());
    return bindingOpt.map(binding -> {
      var serverProperties = storageService.binding(binding).analyzerConfiguration().read().getSettings().getAll();
      var analysisProperties = new HashMap<>(serverProperties);
      analysisProperties.putAll(userAnalysisProperties);
      return new GetAnalysisConfigResponse(buildConnectedActiveRules(binding, hotspotsOnly), analysisProperties, nodeJsDetailsDto,
        Set.copyOf(pluginsService.getConnectedPluginPaths(binding.connectionId())));
    })
      .orElseGet(() -> new GetAnalysisConfigResponse(buildStandaloneActiveRules(), userAnalysisProperties, nodeJsDetailsDto,
        Set.copyOf(pluginsService.getEmbeddedPluginPaths())));
  }

  public void setUserAnalysisProperties(String configScopeId, Map<String, String> properties) {
    userAnalysisPropertiesRepository.setUserProperties(configScopeId, properties);
  }

  public void didChangePathToCompileCommands(String configScopeId, @Nullable String pathToCompileCommands) {
    userAnalysisPropertiesRepository.setOrUpdatePathToCompileCommands(configScopeId, pathToCompileCommands);
    var openFiles = openFilesRepository.getOpenFilesForConfigScope(configScopeId);
    if (!openFiles.isEmpty()) {
      scheduleAutomaticAnalysis(configScopeId, openFiles);
    }
  }

  private List<ActiveRuleDto> buildConnectedActiveRules(Binding binding, boolean hotspotsOnly) {
    var analyzerConfig = storageService.binding(binding).analyzerConfiguration().read();
    var ruleSetByLanguageKey = analyzerConfig.getRuleSetByLanguageKey();
    var result = new ArrayList<ActiveRuleDto>();
    ruleSetByLanguageKey.entrySet()
      .stream().filter(e -> SonarLanguage.forKey(e.getKey()).filter(l -> languageSupportRepository.getEnabledLanguagesInConnectedMode().contains(l)).isPresent())
      .forEach(e -> {
        var languageKey = e.getKey();
        var ruleSet = e.getValue();

        LOG.debug("  * {}: {} active rules", languageKey, ruleSet.getRules().size());
        for (ServerActiveRule possiblyDeprecatedActiveRuleFromStorage : ruleSet.getRules()) {
          var activeRuleFromStorage = tryConvertDeprecatedKeys(binding.connectionId(), possiblyDeprecatedActiveRuleFromStorage);
          SonarLintRuleDefinition ruleOrTemplateDefinition;
          if (StringUtils.isNotBlank(activeRuleFromStorage.getTemplateKey())) {
            ruleOrTemplateDefinition = rulesRepository.getRule(binding.connectionId(), activeRuleFromStorage.getTemplateKey()).orElse(null);
            if (ruleOrTemplateDefinition == null) {
              LOG.debug("Rule {} is enabled on the server, but its template {} is not available in SonarLint", activeRuleFromStorage.getRuleKey(),
                activeRuleFromStorage.getTemplateKey());
              continue;
            }
          } else {
            ruleOrTemplateDefinition = rulesRepository.getRule(binding.connectionId(), activeRuleFromStorage.getRuleKey()).orElse(null);
            if (ruleOrTemplateDefinition == null) {
              LOG.debug("Rule {} is enabled on the server, but not available in SonarLint", activeRuleFromStorage.getRuleKey());
              continue;
            }
          }
          if (shouldIncludeRuleForAnalysis(binding.connectionId(), ruleOrTemplateDefinition, hotspotsOnly)) {
            result.add(buildActiveRuleDto(ruleOrTemplateDefinition, activeRuleFromStorage));
          }
        }
      });
    if (languageSupportRepository.getEnabledLanguagesInConnectedMode().contains(SonarLanguage.IPYTHON)) {
      // Jupyter Notebooks are not yet fully supported in connected mode, use standalone rule configuration in the meantime
      var iPythonRules = buildStandaloneActiveRules()
        .stream().filter(rule -> rule.getLanguageKey().equals(SonarLanguage.IPYTHON.getSonarLanguageKey()))
        .toList();
      result.addAll(iPythonRules);
    }
    return result;
  }

  public ActiveRuleDto buildActiveRuleDto(SonarLintRuleDefinition ruleOrTemplateDefinition, ServerActiveRule activeRule) {
    return new ActiveRuleDto(activeRule.getRuleKey(),
      ruleOrTemplateDefinition.getLanguage().getSonarLanguageKey(),
      getEffectiveParams(ruleOrTemplateDefinition, activeRule),
      trimToNull(activeRule.getTemplateKey()));
  }

  private boolean shouldIncludeRuleForAnalysis(String connectionId, SonarLintRuleDefinition ruleDefinition, boolean hotspotsOnly) {
    var isHotspot = ruleDefinition.getType().equals(RuleType.SECURITY_HOTSPOT);
    return (!isHotspot && !hotspotsOnly) || (isHotspot && hotspotEnabled && isHotspotTrackingPossible(connectionId));
  }

  public boolean isHotspotTrackingPossible(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    if (connection == null) {
      // Connection is gone
      return false;
    }
    // when storage is not present, consider hotspots should not be detected
    return storageService.connection(connectionId).serverInfo().read().isPresent();
  }

  private ServerActiveRule tryConvertDeprecatedKeys(String connectionId, ServerActiveRule possiblyDeprecatedActiveRuleFromStorage) {
    SonarLintRuleDefinition ruleOrTemplateDefinition;
    if (StringUtils.isNotBlank(possiblyDeprecatedActiveRuleFromStorage.getTemplateKey())) {
      ruleOrTemplateDefinition = rulesRepository.getRule(connectionId, possiblyDeprecatedActiveRuleFromStorage.getTemplateKey()).orElse(null);
      if (ruleOrTemplateDefinition == null) {
        // The rule template is not known among our loaded analyzers, so return it untouched, to let calling code take appropriate decision
        return possiblyDeprecatedActiveRuleFromStorage;
      }
      var ruleKeyPossiblyWithDeprecatedRepo = RuleKey.parse(possiblyDeprecatedActiveRuleFromStorage.getRuleKey());
      var templateRuleKeyWithCorrectRepo = RuleKey.parse(ruleOrTemplateDefinition.getKey());
      var ruleKey = new RuleKey(templateRuleKeyWithCorrectRepo.repository(), ruleKeyPossiblyWithDeprecatedRepo.rule()).toString();
      return new ServerActiveRule(ruleKey, possiblyDeprecatedActiveRuleFromStorage.getSeverity(), possiblyDeprecatedActiveRuleFromStorage.getParams(),
        ruleOrTemplateDefinition.getKey(), possiblyDeprecatedActiveRuleFromStorage.getOverriddenImpacts());
    } else {
      ruleOrTemplateDefinition = rulesRepository.getRule(connectionId, possiblyDeprecatedActiveRuleFromStorage.getRuleKey()).orElse(null);
      if (ruleOrTemplateDefinition == null) {
        // The rule is not known among our loaded analyzers, so return it untouched, to let calling code take appropriate decision
        return possiblyDeprecatedActiveRuleFromStorage;
      }
      return new ServerActiveRule(ruleOrTemplateDefinition.getKey(), possiblyDeprecatedActiveRuleFromStorage.getSeverity(), possiblyDeprecatedActiveRuleFromStorage.getParams(),
        null, possiblyDeprecatedActiveRuleFromStorage.getOverriddenImpacts());
    }
  }

  private List<ActiveRuleDto> buildStandaloneActiveRules() {
    var standaloneRuleConfig = rulesService.getStandaloneRuleConfig();
    Set<String> excludedRules = standaloneRuleConfig.entrySet().stream().filter(not(e -> e.getValue().isActive())).map(Map.Entry::getKey).collect(toSet());
    Set<String> includedRules = standaloneRuleConfig.entrySet().stream().filter(e -> e.getValue().isActive())
      .map(Map.Entry::getKey)
      .filter(r -> !excludedRules.contains(r))
      .collect(toSet());

    var filteredActiveRules = new ArrayList<SonarLintRuleDefinition>();

    var allRulesDefinitions = rulesRepository.getEmbeddedRules().stream()
      .filter(rule -> !rule.getType().equals(RuleType.SECURITY_HOTSPOT))
      .toList();

    filteredActiveRules.addAll(allRulesDefinitions.stream()
      .filter(SonarLintRuleDefinition::isActiveByDefault)
      .filter(isExcludedByConfiguration(excludedRules))
      .toList());
    filteredActiveRules.addAll(allRulesDefinitions.stream()
      .filter(r -> !r.isActiveByDefault())
      .filter(isIncludedByConfiguration(includedRules))
      .toList());

    return filteredActiveRules.stream().map(rd -> {
      Map<String, String> effectiveParams = new HashMap<>(rd.getDefaultParams());
      ofNullable(standaloneRuleConfig.get(rd.getKey())).ifPresent(config -> effectiveParams.putAll(config.getParamValueByKey()));
      // No template rules in standalone mode
      return new ActiveRuleDto(rd.getKey(), rd.getLanguage().getSonarLanguageKey(), effectiveParams, null);
    })
      .toList();
  }

  private static Predicate<? super SonarLintRuleDefinition> isExcludedByConfiguration(Set<String> excludedRules) {
    return r -> {
      if (excludedRules.contains(r.getKey())) {
        return false;
      }
      for (String deprecatedKey : r.getDeprecatedKeys()) {
        if (excludedRules.contains(deprecatedKey)) {
          LOG.warn("Rule '{}' was excluded using its deprecated key '{}'. Please fix your configuration.", r.getKey(), deprecatedKey);
          return false;
        }
      }
      return true;
    };
  }

  private static Predicate<? super SonarLintRuleDefinition> isIncludedByConfiguration(Set<String> includedRules) {
    return r -> {
      if (includedRules.contains(r.getKey())) {
        return true;
      }
      for (String deprecatedKey : r.getDeprecatedKeys()) {
        if (includedRules.contains(deprecatedKey)) {
          LOG.warn("Rule '{}' was included using its deprecated key '{}'. Please fix your configuration.", r.getKey(), deprecatedKey);
          return true;
        }
      }
      return false;
    };
  }

  @EventListener
  public void onPluginsSynchronized(PluginsSynchronizedEvent event) {
    var connectionId = event.connectionId();
    checkIfReadyForAnalysis(configurationRepository.getBoundScopesToConnection(connectionId)
      .stream().map(BoundScope::getConfigScopeId).collect(Collectors.toSet()));
    configurationRepository.getBoundScopesToConnection(connectionId)
      .stream().map(BoundScope::getConfigScopeId).forEach(this::autoAnalyzeOpenFiles);
  }

  @EventListener
  public void onConfigurationScopeAdded(ConfigurationScopesAddedWithBindingEvent event) {
    var configScopeIds = event.getConfigScopeIds();
    configScopeIds.forEach(schedulerCache::registerModuleIfLeafConfigScope);
    checkIfReadyForAnalysis(configScopeIds);
    configScopeIds.forEach(this::autoAnalyzeOpenFiles);
  }

  @EventListener
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent event) {
    var removedConfigurationScopeId = event.getRemovedConfigurationScopeId();
    analysisReadinessByConfigScopeId.remove(removedConfigurationScopeId);
    schedulerCache.unregisterModule(removedConfigurationScopeId);
  }

  @EventListener
  public void onBindingConfigurationChanged(BindingConfigChangedEvent event) {
    var configScopeId = event.configScopeId();
    analysisReadinessByConfigScopeId.remove(configScopeId);
    if (!checkIfReadyForAnalysis(configScopeId)) {
      client.didChangeAnalysisReadiness(new DidChangeAnalysisReadinessParams(Set.of(configScopeId), false));
    }
  }

  @EventListener
  public void onAnalyzerConfigurationSynchronized(AnalyzerConfigurationSynchronized event) {
    checkIfReadyForAnalysis(event.getConfigScopeIds());
    event.getConfigScopeIds().forEach(this::autoAnalyzeOpenFiles);
  }

  @EventListener
  public void onConfigurationScopesSynchronized(ConfigurationScopesSynchronizedEvent event) {
    checkIfReadyForAnalysis(event.getConfigScopeIds());
    event.getConfigScopeIds().forEach(this::autoAnalyzeOpenFiles);
  }

  @EventListener
  public void onFileSystemUpdated(FileSystemUpdatedEvent event) {
    sendModuleEvents(event.getAdded(), ModuleFileEvent.Type.CREATED);
    sendModuleEvents(event.getUpdated(), ModuleFileEvent.Type.MODIFIED);
    sendModuleEvents(event.getRemoved(), ModuleFileEvent.Type.DELETED);
    var updatedFileUrisByConfigScope = event.getUpdated().stream().collect(groupingBy(ClientFile::getConfigScopeId, mapping(ClientFile::getUri, toSet())));
    updatedFileUrisByConfigScope.forEach((configScopeId, fileUris) -> {
      var openFileUris = openFilesRepository.getOpenFilesAmong(configScopeId, fileUris);
      if (!openFileUris.isEmpty()) {
        scheduleAutomaticAnalysis(configScopeId, openFileUris);
      }
    });

  }

  @EventListener
  public void onFileOpened(FileOpenedEvent event) {
    scheduleAutomaticAnalysis(event.configurationScopeId(), List.of(event.fileUri()));
  }

  @EventListener
  public void onStandaloneRulesConfigurationChanged(StandaloneRulesConfigurationChanged event) {
    if (event.isOnlyDeactivated()) {
      // if no rules were enabled (only disabled), trigger only a new reporting, removing issues of disabled rules
      configurationRepository.getConfigScopeIds().stream()
        .filter(this::isStandalone)
        .forEach(configScopeId -> rulesService.updateAndReportFindings(configScopeId, event.getDeactivatedRules()));
    } else {
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
  public void onNewRulesActivatedOnServer(NewRulesActivatedOnServer event) {
    reanalyseOpenFiles(not(this::isStandalone));
  }

  private boolean isStandalone(String configScopeId) {
    return configurationRepository.getEffectiveBinding(configScopeId).isEmpty();
  }

  private void sendModuleEvents(List<ClientFile> filesToProcess, ModuleFileEvent.Type type) {
    var filesByScopeId = filesToProcess.stream().collect(groupingBy(ClientFile::getConfigScopeId));
    filesByScopeId.forEach((scopeId, files) -> {
      var scheduler = schedulerCache.getAnalysisSchedulerIfStarted(scopeId);
      if (scheduler != null) {
        files.forEach(file -> scheduler.notifyModuleEvent(scopeId, file, type));
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

  private void streamIssue(String configScopeId, UUID analysisId, RawIssue rawIssue) {
    if (rawIssue.getRuleKey().contains("secrets")) {
      client.didDetectSecret(new DidDetectSecretParams(configScopeId));
    }
    eventPublisher.publishEvent(new RawIssueDetectedEvent(configScopeId, analysisId, rawIssue));
  }

  private void checkIfReadyForAnalysis(Set<String> configurationScopeIds) {
    var readyConfigScopeIds = configurationScopeIds.stream()
      .filter(this::isReadyForAnalysis)
      .collect(toSet());
    saveAndNotifyReadyForAnalysis(readyConfigScopeIds);
  }

  private boolean checkIfReadyForAnalysis(String configurationScopeId) {
    if (isReadyForAnalysis(configurationScopeId)) {
      saveAndNotifyReadyForAnalysis(Set.of(configurationScopeId));
      return true;
    }
    return false;
  }

  private void saveAndNotifyReadyForAnalysis(Set<String> configScopeIds) {
    var scopeIdsThatBecameReady = new HashSet<String>();
    configScopeIds.forEach(configScopeId -> {
      if (analysisReadinessByConfigScopeId.get(configScopeId) != Boolean.TRUE) {
        analysisReadinessByConfigScopeId.put(configScopeId, Boolean.TRUE);
        scopeIdsThatBecameReady.add(configScopeId);
      }
    });
    if (!scopeIdsThatBecameReady.isEmpty()) {
      reanalyseOpenFiles(scopeIdsThatBecameReady::contains);
      scopeIdsThatBecameReady.forEach(scopeId -> {
        var scheduler = schedulerCache.getOrCreateAnalysisScheduler(scopeId);
        if (scheduler != null) {
          scheduler.notifyScopeReady(scopeId);
        }
      });
      client.didChangeAnalysisReadiness(new DidChangeAnalysisReadinessParams(scopeIdsThatBecameReady, true));
    }
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
    if (!previouslyEnabled) {
      triggerAnalysisForOpenFiles();
    }
  }

  public UUID analyzeFullProject(String configScopeId, boolean hotspotsOnly) {
    var files = clientFileSystemService.getFiles(configScopeId);
    return scheduleForcedAnalysis(configScopeId, files.stream().map(ClientFile::getUri).toList(), hotspotsOnly);
  }

  public UUID analyzeFileList(String configScopeId, List<URI> filesToAnalyze) {
    return scheduleForcedAnalysis(configScopeId, filesToAnalyze, false);
  }

  public UUID analyzeVCSChangedFiles(String configScopeId) {
    var changedFiles = getVSCChangedFiles(clientFileSystemService.getBaseDir(configScopeId));
    return scheduleForcedAnalysis(configScopeId, changedFiles, false);
  }

  private void triggerAnalysisForOpenFiles() {
    openFilesRepository.getOpenFilesByConfigScopeId()
      .forEach((configurationScopeId, files) -> scheduleForcedAnalysis(configurationScopeId, files, false));
  }

  public UUID scheduleForcedAnalysis(String configurationScopeId, List<URI> files, boolean hotspotsOnly) {
    var analysisId = UUID.randomUUID();
    schedule(configurationScopeId, getAnalysisTask(configurationScopeId, files, hotspotsOnly, TriggerType.FORCED))
      .exceptionally(e -> {
        LOG.error("Error during analysis", e);
        return null;
      });
    return analysisId;
  }

  public CompletableFuture<AnalysisResult> scheduleAnalysis(String configurationScopeId, UUID analysisId, List<URI> files, Map<String, String> extraProperties,
    long startTime, boolean shouldFetchServerIssues, TriggerType triggerType) {
    var progressMonitor = new RpcProgressMonitor(client, new SonarLintCancelMonitor(), configurationScopeId, analysisId);
    var analysisTask = new AnalysisTask(analysisId, triggerType, configurationScopeId, files, extraProperties, false, progressMonitor,
      issue -> streamIssue(configurationScopeId, analysisId, issue), startTime, shouldFetchServerIssues);
    return schedule(configurationScopeId, analysisTask);
  }

  private void scheduleAutomaticAnalysis(String configScopeId, List<URI> filesToAnalyze) {
    if (automaticAnalysisEnabled) {
      var task = getAnalysisTask(configScopeId, filesToAnalyze, false, TriggerType.AUTO);
      schedulerCache.getOrCreateAnalysisScheduler(configScopeId).schedule(task);
    }
  }

  private CompletableFuture<AnalysisResult> schedule(String configScopeId, AnalysisTask analysisTask) {
    return schedulerCache.getOrCreateAnalysisScheduler(configScopeId).schedule(analysisTask);
  }

  private AnalysisTask getAnalysisTask(String configurationScopeId, List<URI> files, boolean hotspotsOnly, TriggerType triggerType) {
    var analysisId = UUID.randomUUID();
    var progressMonitor = new RpcProgressMonitor(client, new SonarLintCancelMonitor(), configurationScopeId, analysisId);
    return new AnalysisTask(analysisId, triggerType, configurationScopeId, files, Map.of(), hotspotsOnly, progressMonitor,
      issue -> streamIssue(configurationScopeId, analysisId, issue), Instant.now().toEpochMilli(), false);
  }

  private void reanalyseOpenFiles(Predicate<String> configScopeFilter) {
    openFilesRepository.getOpenFilesByConfigScopeId()
      .entrySet()
      .stream().filter(entry -> configScopeFilter.test(entry.getKey()))
      .forEach(entry -> scheduleAutomaticAnalysis(entry.getKey(), entry.getValue()));
  }
}
