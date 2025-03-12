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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.command.Command;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.monitoring.MonitoringService;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.fs.FileExclusionService;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.ActiveRuleDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetAnalysisConfigResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.NodeJsDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.GetInferredAnalysisPropertiesParams;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.rules.RulesService;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.sonarsource.sonarlint.core.commons.util.StringUtils.pluralize;

public class AnalysisExecutor {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String SONAR_INTERNAL_BUNDLE_PATH_ANALYSIS_PROP = "sonar.js.internal.bundlePath";

  private final LogOutput logOutput = SonarLintLogger.get().getTargetForCopy();
  private final ConfigurationRepository configurationRepository;
  private final NodeJsService nodeJsService;
  private final UserAnalysisPropertiesRepository userAnalysisPropertiesRepository;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final boolean hotspotEnabled;
  private final StorageService storageService;
  private final PluginsService pluginsService;
  private final RulesRepository rulesRepository;
  private final RulesService rulesService;
  private final LanguageSupportRepository languageSupportRepository;
  private final ClientFileSystemService fileSystemService;
  private final MonitoringService monitoringService;
  private final FileExclusionService fileExclusionService;
  private final SonarLintRpcClient client;
  private final ApplicationEventPublisher eventPublisher;
  @Nullable
  private final Path esLintBridgeServerPath;
  private final AtomicReference<AnalysisEngine> engine = new AtomicReference<>();

  public AnalysisExecutor(ConfigurationRepository configurationRepository, NodeJsService nodeJsService, UserAnalysisPropertiesRepository userAnalysisPropertiesRepository,
    ConnectionConfigurationRepository connectionConfigurationRepository, boolean hotspotEnabled, StorageService storageService, PluginsService pluginsService,
    RulesRepository rulesRepository, RulesService rulesService, LanguageSupportRepository languageSupportRepository, ClientFileSystemService fileSystemService,
    MonitoringService monitoringService, FileExclusionService fileExclusionService, SonarLintRpcClient client, ApplicationEventPublisher eventPublisher,
    @Nullable Path esLintBridgeServerPath, AnalysisEngine engine) {
    this.configurationRepository = configurationRepository;
    this.nodeJsService = nodeJsService;
    this.userAnalysisPropertiesRepository = userAnalysisPropertiesRepository;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.hotspotEnabled = hotspotEnabled;
    this.storageService = storageService;
    this.pluginsService = pluginsService;
    this.rulesRepository = rulesRepository;
    this.rulesService = rulesService;
    this.languageSupportRepository = languageSupportRepository;
    this.fileSystemService = fileSystemService;
    this.monitoringService = monitoringService;
    this.fileExclusionService = fileExclusionService;
    this.client = client;
    this.eventPublisher = eventPublisher;
    this.esLintBridgeServerPath = esLintBridgeServerPath;
    this.engine.set(engine);
  }

  public void replaceEngine(AnalysisEngine engine) {
    var previousEngine = this.engine.getAndSet(engine);
    previousEngine.stop();
  }

  void execute(AnalysisTask task) {
    try {
      doExecute(task);
    } catch (Exception e) {
      handleAnalysisFailed(task, e);
    }
  }

  void doExecute(AnalysisTask task) {
    var configScopeId = task.getConfigScopeId();
    var baseDir = fileSystemService.getBaseDir(configScopeId);
    var filesToAnalyze = fileExclusionService.refineAnalysisScope(task, baseDir);
    if (filesToAnalyze.isEmpty()) {
      LOG.info("No file to analyze");
      task.getResult().complete(new AnalysisResult(Set.of(), List.of()));
      return;
    }
    var analysisConfigForEngine = getAnalysisConfigForEngine(configScopeId, filesToAnalyze, task.getExtraProperties(), task.isHotspotsOnly(),
      baseDir == null ? findCommonPrefix(task.getFilesToAnalyze()) : baseDir);
    var ruleDetailsCache = new ConcurrentHashMap<String, RuleDetailsForAnalysis>();
    var rawIssues = new ArrayList<RawIssue>();
    var analyzeCommand = new AnalyzeCommand(configScopeId, analysisConfigForEngine,
      issue -> {
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
          task.getIssueStreamingListener().accept(rawIssue);
        }
      }, logOutput, monitoringService.newTrace(
        "AnalysisService", "analyze"));
    eventPublisher.publishEvent(new AnalysisStartedEvent(configScopeId, task.getAnalysisId(), analysisConfigForEngine.inputFiles()));
    try {
      var analysisResults = engine.get().post(analyzeCommand, task.getProgressMonitor()).join();
      var analysisDuration = System.currentTimeMillis() - task.getStartTime();
      var languagePerFile = analysisResults.languagePerFile().entrySet().stream().collect(HashMap<URI, SonarLanguage>::new,
        (map, entry) -> map.put(entry.getKey().uri(), entry.getValue()), HashMap::putAll);
      logSummary(rawIssues, analysisDuration);
      eventPublisher.publishEvent(new AnalysisFinishedEvent(task.getAnalysisId(), configScopeId, analysisDuration,
        languagePerFile, analysisResults.failedAnalysisFiles().isEmpty(), rawIssues, task.isShouldFetchServerIssues()));
      task.getResult()
        .complete(new AnalysisResult(
          analysisResults.failedAnalysisFiles().stream().map(ClientInputFile::getClientObject).map(clientObj -> ((ClientFile) clientObj).getUri()).collect(Collectors.toSet()),
          rawIssues));
    } catch (CompletionException e) {
      handleAnalysisFailed(task, e.getCause());
    } catch (Exception e) {
      handleAnalysisFailed(task, e);
    }
  }

  private void handleAnalysisFailed(AnalysisTask task, Throwable throwable) {
    LOG.error("Error during analysis", throwable);
    task.getResult().completeExceptionally(throwable);
    eventPublisher.publishEvent(new AnalysisFailedEvent(task.getAnalysisId()));
  }

  private static void logSummary(List<RawIssue> rawIssues, long analysisDuration) {
    // ignore project-level issues for now
    var fileRawIssues = rawIssues.stream().filter(issue -> issue.getTextRange() != null).toList();
    var issuesCount = fileRawIssues.stream().filter(not(RawIssue::isSecurityHotspot)).count();
    var hotspotsCount = fileRawIssues.stream().filter(RawIssue::isSecurityHotspot).count();
    LOG.info("Analysis detected {} and {} in {}ms", pluralize(issuesCount, "issue"), pluralize(hotspotsCount, "Security Hotspot"), analysisDuration);
  }

  private AnalysisConfiguration getAnalysisConfigForEngine(String configScopeId, List<ClientFile> filesToAnalyze, Map<String, String> extraProperties, boolean hotspotsOnly,
    Path actualBaseDir) {
    var analysisConfig = getAnalysisConfig(configScopeId, hotspotsOnly);
    var analysisProperties = analysisConfig.getAnalysisProperties();
    var inferredAnalysisProperties = client
      .getInferredAnalysisProperties(new GetInferredAnalysisPropertiesParams(configScopeId, filesToAnalyze.stream().map(ClientFile::getUri).toList())).join().getProperties();
    analysisProperties.putAll(inferredAnalysisProperties);
    return AnalysisConfiguration.builder()
      .addInputFiles(filesToAnalyze.stream().map(BackendInputFile::new).toList())
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

  public ActiveRuleDto buildActiveRuleDto(SonarLintRuleDefinition ruleOrTemplateDefinition, ServerActiveRule activeRule) {
    return new ActiveRuleDto(activeRule.getRuleKey(),
      ruleOrTemplateDefinition.getLanguage().getSonarLanguageKey(),
      getEffectiveParams(ruleOrTemplateDefinition, activeRule),
      trimToNull(activeRule.getTemplateKey()));
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

  public void stop() {
    engine.get().stop();
  }

  public CompletableFuture<Void> post(Command<Void> command, ProgressMonitor progressMonitor) {
    return engine.get().post(command, progressMonitor);
  }
}
