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

import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.command.NotifyModuleEventCommand;
import org.sonarsource.sonarlint.core.analysis.sonarapi.MultivalueProperty;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.monitoring.MonitoringService;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.util.FailSafeExecutors;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedWithBindingEvent;
import org.sonarsource.sonarlint.core.file.WindowsShortcutUtils;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.fs.FileExclusionService;
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
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.FileEditDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.GetInferredAnalysisPropertiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.QuickFixDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueFlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueLocationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.TextEditDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.rules.NewRulesActivatedOnServer;
import org.sonarsource.sonarlint.core.rules.RuleDetailsAdapter;
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

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.LanguageDetection.sanitizeExtension;
import static org.sonarsource.sonarlint.core.commons.util.StringUtils.pluralize;
import static org.sonarsource.sonarlint.core.commons.util.git.GitService.createSonarLintGitIgnore;
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
  private final AnalysisEngineCache engineCache;
  private final ClientFileSystemService fileSystemService;
  private final FileExclusionService fileExclusionService;
  private final ApplicationEventPublisher eventPublisher;
  private final UserAnalysisPropertiesRepository userAnalysisPropertiesRepository;
  private final boolean isDataflowBugDetectionEnabled;
  private final Map<String, Boolean> analysisReadinessByConfigScopeId = new ConcurrentHashMap<>();
  private final OpenFilesRepository openFilesRepository;
  private final ClientFileSystemService clientFileSystemService;
  private final MonitoringService monitoringService;
  private boolean automaticAnalysisEnabled;
  private final Path esLintBridgeServerPath;
  private final ExecutorService scheduledAnalysisExecutor = FailSafeExecutors.newSingleThreadExecutor("SonarLint Analysis Executor");

  public AnalysisService(SonarLintRpcClient client, ConfigurationRepository configurationRepository, LanguageSupportRepository languageSupportRepository,
    StorageService storageService, PluginsService pluginsService, RulesService rulesService, RulesRepository rulesRepository,
    ConnectionConfigurationRepository connectionConfigurationRepository, InitializeParams initializeParams, NodeJsService nodeJsService,
    AnalysisEngineCache engineCache, ClientFileSystemService fileSystemService, FileExclusionService fileExclusionService,
    ApplicationEventPublisher eventPublisher,
    UserAnalysisPropertiesRepository clientAnalysisPropertiesRepository, OpenFilesRepository openFilesRepository, ClientFileSystemService clientFileSystemService,
    MonitoringService monitoringService) {
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
    this.engineCache = engineCache;
    this.fileSystemService = fileSystemService;
    this.fileExclusionService = fileExclusionService;
    this.eventPublisher = eventPublisher;
    this.userAnalysisPropertiesRepository = clientAnalysisPropertiesRepository;
    this.openFilesRepository = openFilesRepository;
    this.automaticAnalysisEnabled = initializeParams.isAutomaticAnalysisEnabled();
    this.clientFileSystemService = clientFileSystemService;
    this.monitoringService = monitoringService;
    this.esLintBridgeServerPath = initializeParams.getLanguageSpecificRequirements() != null && initializeParams.getLanguageSpecificRequirements().getJsTsRequirements() != null
      ? initializeParams.getLanguageSpecificRequirements().getJsTsRequirements().getBundlePath()
      : null;
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

  @NotNull
  private static org.sonarsource.sonarlint.core.rpc.protocol.common.Language toDto(SonarLanguage language) {
    return org.sonarsource.sonarlint.core.rpc.protocol.common.Language.valueOf(language.name());
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

  private AnalysisConfiguration getAnalysisConfigForEngine(String configScopeId, List<URI> filePathsToAnalyze, Map<String, String> extraProperties, boolean hotspotsOnly) {
    var analysisConfig = getAnalysisConfig(configScopeId, hotspotsOnly);
    var analysisProperties = analysisConfig.getAnalysisProperties();
    var inferredAnalysisProperties = client.getInferredAnalysisProperties(new GetInferredAnalysisPropertiesParams(configScopeId, filePathsToAnalyze)).join().getProperties();
    analysisProperties.putAll(inferredAnalysisProperties);
    var baseDir = fileSystemService.getBaseDir(configScopeId);
    var actualBaseDir = baseDir == null ? findCommonPrefix(filePathsToAnalyze) : baseDir;
    return AnalysisConfiguration.builder()
      .addInputFiles(toInputFiles(configScopeId, actualBaseDir, filePathsToAnalyze))
      .putAllExtraProperties(analysisProperties)
      // properties sent by client using new API were merged above
      // but this line is important for backward compatibility for clients directly triggering analysis
      .putAllExtraProperties(extraProperties)
      .addActiveRules(analysisConfig.getActiveRules().stream().map(r -> {
        var ar = new ActiveRule(r.getRuleKey(), r.getLanguageKey());
        ar.setParams(r.getParams());
        ar.setTemplateRuleKey(r.getTemplateRuleKey());
        return ar;
      }).toList())
      .setBaseDir(actualBaseDir)
      .build();
  }

  public void setUserAnalysisProperties(String configScopeId, Map<String, String> properties) {
    userAnalysisPropertiesRepository.setUserProperties(configScopeId, properties);
  }

  public void didChangePathToCompileCommands(String configScopeId, @Nullable String pathToCompileCommands) {
    userAnalysisPropertiesRepository.setOrUpdatePathToCompileCommands(configScopeId, pathToCompileCommands);
    var openFiles = openFilesRepository.getOpenFilesForConfigScope(configScopeId);
    if (!openFiles.isEmpty()) {
      triggerAnalysis(configScopeId, openFiles);
    }
  }

  private static Path findCommonPrefix(List<URI> uris) {
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

  public ActiveRuleDto buildActiveRuleDto(SonarLintRuleDefinition rule) {
    return new ActiveRuleDto(rule.getKey(), rule.getLanguage().getSonarLanguageKey(), rule.getDefaultParams(), null);
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
    checkIfReadyForAnalysis(configurationRepository.getBoundScopesToConnection(event.getConnectionId())
      .stream().map(BoundScope::getConfigScopeId).collect(Collectors.toSet()));
  }

  @EventListener
  public void onConfigurationScopeAdded(ConfigurationScopesAddedWithBindingEvent event) {
    event.getConfigScopeIds().forEach(engineCache::registerModuleIfLeafConfigScope);
    checkIfReadyForAnalysis(event.getConfigScopeIds());
  }

  @EventListener
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent event) {
    var removedConfigurationScopeId = event.getRemovedConfigurationScopeId();
    analysisReadinessByConfigScopeId.remove(removedConfigurationScopeId);
    engineCache.unregisterModule(removedConfigurationScopeId);
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
  }

  @EventListener
  public void onAnalyzerConfigurationSynchronized(ConfigurationScopesSynchronizedEvent event) {
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
      if (!openFileUris.isEmpty()) {
        triggerAnalysis(configScopeId, openFileUris);
      }
    });

  }

  @EventListener
  public void onFileOpened(FileOpenedEvent event) {
    triggerAnalysis(event.getConfigurationScopeId(), List.of(event.getFileUri()));
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

  private boolean isStandalone(String configScopeId) {
    return configurationRepository.getEffectiveBinding(configScopeId).isEmpty();
  }

  @EventListener
  public void onNewRulesActivatedOnServer(NewRulesActivatedOnServer event) {
    reanalyseOpenFiles(not(this::isStandalone));
  }

  private void reanalyseOpenFiles(Predicate<String> configScopeFilter) {
    openFilesRepository.getOpenFilesByConfigScopeId()
      .entrySet()
      .stream().filter(entry -> configScopeFilter.test(entry.getKey()))
      .forEach(entry -> triggerAnalysis(entry.getKey(), entry.getValue()));
  }

  private void sendModuleEvents(List<ClientFile> filesToProcess, ModuleFileEvent.Type type) {
    var filesByScopeId = filesToProcess.stream().collect(groupingBy(ClientFile::getConfigScopeId));
    filesByScopeId.forEach((scopeId, files) -> {
      var engine = engineCache.getAnalysisEngineIfStarted(scopeId);
      if (engine != null) {
        files.forEach(file -> engine.post(new NotifyModuleEventCommand(scopeId,
          ClientModuleFileEvent.of(new BackendInputFile(file), type)), new ProgressMonitor(null)).join());
      }
    });
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

  public boolean shouldUseEnterpriseCSharpAnalyzer(String configurationScopeId) {
    var binding = configurationRepository.getEffectiveBinding(configurationScopeId);
    if (binding.isEmpty()) {
      return false;
    } else {
      var connectionId = binding.get().connectionId();
      return pluginsService.shouldUseEnterpriseCSharpAnalyzer(connectionId);
    }
  }

  public CompletableFuture<AnalysisResults> analyze(SonarLintCancelMonitor cancelMonitor, String configurationScopeId, UUID analysisId, List<URI> filePathsToAnalyze,
    Map<String, String> extraProperties, long startTime, boolean shouldFetchServerIssues, boolean hotspotsOnly) {
    var analysisEngine = engineCache.getOrCreateAnalysisEngine(configurationScopeId);
    var analysisConfig = getAnalysisConfigForEngine(configurationScopeId, filePathsToAnalyze, extraProperties, hotspotsOnly);

    LOG.info("Triggering analysis with configuration: {}", analysisConfig);
    if (!analysisConfig.inputFiles().iterator().hasNext()) {
      LOG.error("No file to analyze");
      return CompletableFuture.completedFuture(new AnalysisResults());
    }
    var ruleDetailsCache = new ConcurrentHashMap<String, RuleDetailsForAnalysis>();

    cancelMonitor.checkCanceled();
    var raisedIssues = new ArrayList<RawIssue>();
    eventPublisher.publishEvent(new AnalysisStartedEvent(configurationScopeId, analysisId, analysisConfig.inputFiles()));
    var analyzeCommand = new AnalyzeCommand(configurationScopeId, analysisConfig,
      issue -> streamIssue(configurationScopeId, analysisId, issue, ruleDetailsCache, raisedIssues), SonarLintLogger.get().getTargetForCopy(),
      monitoringService.newTrace("AnalysisService", "analyze"));
    var rpcProgressMonitor = new RpcProgressMonitor(client, cancelMonitor, configurationScopeId, analysisId);
    return analysisEngine.post(analyzeCommand, rpcProgressMonitor)
      .whenComplete((results, error) -> {
        long endTime = System.currentTimeMillis();
        if (error == null) {
          var languagePerFile = results.languagePerFile().entrySet().stream().collect(HashMap<URI, SonarLanguage>::new,
            (map, entry) -> map.put(entry.getKey().uri(), entry.getValue()), HashMap::putAll);
          var analysisDuration = endTime - startTime;
          logSummary(raisedIssues, analysisDuration);
          eventPublisher.publishEvent(new AnalysisFinishedEvent(analysisId, configurationScopeId, analysisDuration,
            languagePerFile, results.failedAnalysisFiles().isEmpty(), raisedIssues, shouldFetchServerIssues));
          results.setRawIssues(raisedIssues.stream().map(issue -> toDto(issue.getIssue(), issue.getActiveRule())).toList());
        } else {
          LOG.error("Error during analysis", error);
          eventPublisher.publishEvent(new AnalysisFailedEvent(analysisId));
        }
      });
  }

  private static void logSummary(ArrayList<RawIssue> rawIssues, long analysisDuration) {
    // ignore project-level issues for now
    var fileRawIssues = rawIssues.stream().filter(issue -> issue.getTextRange() != null).toList();
    var issuesCount = fileRawIssues.stream().filter(not(RawIssue::isSecurityHotspot)).count();
    var hotspotsCount = fileRawIssues.stream().filter(RawIssue::isSecurityHotspot).count();
    LOG.info("Analysis detected {} and {} in {}ms", pluralize(issuesCount, "issue"), pluralize(hotspotsCount, "Security Hotspot"), analysisDuration);
  }

  private void streamIssue(String configScopeId, UUID analysisId, Issue issue, ConcurrentHashMap<String, RuleDetailsForAnalysis> ruleDetailsCache, List<RawIssue> rawIssues) {
    var ruleKey = issue.getRuleKey();
    var activeRule = ruleDetailsCache.computeIfAbsent(ruleKey, k -> {
      try {
        return rulesService.getRuleDetailsForAnalysis(configScopeId, k);
      } catch (Exception e) {
        return null;
      }
    });
    if (activeRule != null) {
      var rawIssue = new RawIssue(issue, activeRule);
      rawIssues.add(rawIssue);
      if (ruleKey.contains("secrets")) {
        client.didDetectSecret(new DidDetectSecretParams(configScopeId));
      }
      eventPublisher.publishEvent(new RawIssueDetectedEvent(configScopeId, analysisId, rawIssue));
    }
  }

  static RawIssueDto toDto(Issue issue, RuleDetailsForAnalysis activeRule) {
    var range = issue.getTextRange();
    var textRange = range != null ? adapt(range) : null;
    var impacts = new EnumMap<SoftwareQuality, ImpactSeverity>(SoftwareQuality.class);
    impacts.putAll(activeRule.getImpacts().entrySet().stream()
      .collect(toMap(e -> RuleDetailsAdapter.adapt(e.getKey()), e -> RuleDetailsAdapter.adapt(e.getValue()))));
    impacts
      .putAll(
        issue.getOverriddenImpacts().entrySet().stream().map(entry -> Map.entry(SoftwareQuality.valueOf(entry.getKey().name()), ImpactSeverity.valueOf(entry.getValue().name())))
          .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
    var inputFile = issue.getInputFile();
    var fileUri = inputFile == null ? null : inputFile.uri();
    var flows = issue.flows().stream().map(flow -> {
      var locations = flow.locations().stream().map(location -> {
        var locationTextRange = location.getTextRange();
        var locationTextRangeDto = locationTextRange == null ? null : adapt(locationTextRange);
        var locationInputFile = location.getInputFile();
        var locationFileUri = locationInputFile == null ? null : locationInputFile.uri();
        return new RawIssueLocationDto(locationTextRangeDto, location.getMessage(), locationFileUri);
      }).toList();
      return new RawIssueFlowDto(locations);
    }).toList();
    return new RawIssueDto(
      RuleDetailsAdapter.adapt(activeRule.getSeverity()),
      RuleDetailsAdapter.adapt(activeRule.getType()),
      RuleDetailsAdapter.adapt(activeRule.getCleanCodeAttribute()),
      impacts,
      issue.getRuleKey(),
      requireNonNull(issue.getMessage()),
      fileUri,
      flows,
      issue.quickFixes().stream()
        .map(quickFix -> new QuickFixDto(
          quickFix.inputFileEdits().stream()
            .map(fileEdit -> new FileEditDto(fileEdit.target().uri(),
              fileEdit.textEdits().stream().map(textEdit -> new TextEditDto(adapt(textEdit.range()), textEdit.newText())).toList()))
            .toList(),
          quickFix.message()))
        .toList(),
      textRange,
      issue.getRuleDescriptionContextKey().orElse(null),
      RuleDetailsAdapter.adapt(activeRule.getVulnerabilityProbability()));
  }

  private static TextRangeDto adapt(TextRange textRange) {
    return new TextRangeDto(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset());
  }

  private List<ClientInputFile> toInputFiles(String configScopeId, Path actualBaseDir, List<URI> fileUrisToAnalyze) {
    var sonarLintGitIgnore = createSonarLintGitIgnore(actualBaseDir);
    // INFO: When there are additional filters coming at some point, add them here and log them down below as well!
    var filteredURIsFromExclusionService = new ArrayList<URI>();
    var filteredURIsFromGitIgnore = new ArrayList<URI>();
    var filteredURIsNotUserDefined = new ArrayList<URI>();
    var filteredURIsFromSymbolicLink = new ArrayList<URI>();
    var filteredURIsFromWindowsShortcut = new ArrayList<URI>();
    var filteredURIsNoInputFile = new ArrayList<URI>();

    // Do the actual filtering and in case of a filtered out URI, save them for later logging!
    var actualFilesToAnalyze = fileUrisToAnalyze.stream()
      .filter(uri -> {
        if (fileExclusionService.isExcluded(uri)) {
          filteredURIsFromExclusionService.add(uri);
          return false;
        }
        return true;
      })
      .filter(uri -> {
        var clientFile = clientFileSystemService.getClientFile(uri);
        if (clientFile == null) {
          LOG.error("File to analyze was not found in the file system: {}", uri);
          return false;
        }
        if (sonarLintGitIgnore.isFileIgnored(clientFile.getClientRelativePath())) {
          filteredURIsFromGitIgnore.add(uri);
          return false;
        }
        return true;
      })
      .filter(uri -> {
        if (!isUserDefined(configScopeId, uri)) {
          filteredURIsNotUserDefined.add(uri);
          return false;
        }
        return true;
      })
      .filter(uri -> {
        // On protocols with schemes like "temp" (used by IntelliJ in the integration tests) or "rse" (the Eclipse Remote System Explorer)
        // and maybe others the check for a symbolic link or Windows shortcut will fail as these file systems cannot be resolved for the
        // operations.
        // If this happens, we won't exclude the file as the chance for someone to use a protocol with such a scheme while also using
        // symbolic links or Windows shortcuts should be near zero and this is less error-prone than excluding the
        try {
          if (Files.isSymbolicLink(Path.of(uri))) {
            filteredURIsFromSymbolicLink.add(uri);
            return false;
          } else if (WindowsShortcutUtils.isWindowsShortcut(uri)) {
            filteredURIsFromWindowsShortcut.add(uri);
            return false;
          }
          return true;
        } catch (FileSystemNotFoundException err) {
          LOG.debug("Checking for symbolic links or Windows shortcuts in the file system is not possible for the URI '" + uri
            + "'. Therefore skipping the checks due to the underlying protocol / its scheme.", err);
          return true;
        }
      })
      .map(uri -> {
        var inputFile = toInputFile(configScopeId, uri);
        if (inputFile == null) {
          filteredURIsNoInputFile.add(uri);
        }
        return inputFile;
      })
      .filter(Objects::nonNull)
      .toList();

    // Log all the filtered out URIs but not for the filters where there were none
    logFilteredURIs("Filtered out URIs based on the exclusion service", filteredURIsFromExclusionService);
    logFilteredURIs("Filtered out URIs ignored by Git", filteredURIsFromGitIgnore);
    logFilteredURIs("Filtered out URIs not user-defined", filteredURIsNotUserDefined);
    logFilteredURIs("Filtered out URIs that are symbolic links", filteredURIsFromSymbolicLink);
    logFilteredURIs("Filtered out URIs that are Windows shortcuts", filteredURIsFromWindowsShortcut);
    logFilteredURIs("Filtered out URIs having no input file", filteredURIsNoInputFile);

    return actualFilesToAnalyze;
  }

  private void logFilteredURIs(String reason, ArrayList<URI> uris) {
    if (!uris.isEmpty()) {
      SonarLintLogger.get().debug(reason + ": " + uris.stream().map(Object::toString).collect(Collectors.joining(", ")));
    }
  }

  private boolean isUserDefined(String configurationScopeId, URI uri) {
    return ofNullable(fileSystemService.getClientFiles(configurationScopeId, uri))
      .map(ClientFile::isUserDefined)
      .orElse(false);
  }

  @CheckForNull
  private ClientInputFile toInputFile(String configScopeId, URI fileUriToAnalyze) {
    var clientFile = fileSystemService.getClientFiles(configScopeId, fileUriToAnalyze);
    if (clientFile == null) {
      LOG.error("File to analyze was not found in the file system: {}", fileUriToAnalyze);
      return null;
    }
    return new BackendInputFile(clientFile);
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
    return triggerForcedAnalysis(configScopeId, files.stream().map(ClientFile::getUri).toList(), hotspotsOnly);
  }

  public UUID analyzeFileList(String configScopeId, List<URI> filesToAnalyze) {
    return triggerForcedAnalysis(configScopeId, filesToAnalyze, false);
  }

  public UUID analyzeOpenFiles(String configScopeId) {
    var openFiles = openFilesRepository.getOpenFilesForConfigScope(configScopeId);
    return triggerForcedAnalysis(configScopeId, openFiles, false);
  }

  public UUID analyzeVCSChangedFiles(String configScopeId) {
    var changedFiles = getVSCChangedFiles(clientFileSystemService.getBaseDir(configScopeId));
    return triggerForcedAnalysis(configScopeId, changedFiles, false);
  }

  private void triggerAnalysisForOpenFiles() {
    openFilesRepository.getOpenFilesByConfigScopeId()
      .forEach((configurationScopeId, files) -> triggerForcedAnalysis(configurationScopeId, files, false));
  }

  private UUID triggerForcedAnalysis(String configurationScopeId, List<URI> files, boolean hotspotsOnly) {
    if (isReadyForAnalysis(configurationScopeId)) {
      var analysisId = UUID.randomUUID();
      scheduledAnalysisExecutor
        .execute(() -> analyze(new SonarLintCancelMonitor(), configurationScopeId, analysisId, files, Map.of(), System.currentTimeMillis(), true, hotspotsOnly));
    }
    LOG.debug("Skipping analysis for configuration scope {}. Not ready for analysis", configurationScopeId);
    return null;
  }

  private void triggerAnalysis(String configurationScopeId, List<URI> files) {
    scheduledAnalysisExecutor.execute(() -> {
      if (shouldTriggerAutomaticAnalysis(configurationScopeId)) {
        List<URI> filteredFiles = fileExclusionService.filterOutClientExcludedFiles(configurationScopeId, files);
        analyze(new SonarLintCancelMonitor(), configurationScopeId, UUID.randomUUID(), filteredFiles, Map.of(), System.currentTimeMillis(), true, false);
      }
    });
  }

  private boolean shouldTriggerAutomaticAnalysis(String configurationScopeId) {
    // in the future, if analysis is not ready, we should make it happen later when it becomes ready
    return automaticAnalysisEnabled && isReadyForAnalysis(configurationScopeId);
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(scheduledAnalysisExecutor, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop scheduled analysis executor service in a timely manner");
    }
  }
}
