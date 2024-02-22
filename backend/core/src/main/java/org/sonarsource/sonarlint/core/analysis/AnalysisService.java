/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.analysis.sonarapi.MultivalueProperty;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.nodejs.InstalledNodeJs;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
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
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.rules.RulesService;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.AnalyzerConfigurationSynchronized;
import org.sonarsource.sonarlint.core.sync.ConfigurationScopesSynchronizedEvent;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.springframework.context.event.EventListener;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.LanguageDetection.sanitizeExtension;

@Named
@Singleton
public class AnalysisService {
  private static final Version SECRET_ANALYSIS_MIN_SQ_VERSION = Version.create("9.9");

  private static final SonarLintLogger LOG = SonarLintLogger.get();

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
  private final boolean isDataflowBugDetectionEnabled;
  private final Map<String, Boolean> analysisReadinessByConfigScopeId = new ConcurrentHashMap<>();

  public AnalysisService(SonarLintRpcClient client, ConfigurationRepository configurationRepository, LanguageSupportRepository languageSupportRepository,
    StorageService storageService,
    PluginsService pluginsService, RulesService rulesService, RulesRepository rulesRepository, ConnectionConfigurationRepository connectionConfigurationRepository,
    InitializeParams initializeParams, NodeJsService nodeJsService) {
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
    return new GetGlobalConfigurationResponse(pluginPaths, enabledLanguages.stream().map(AnalysisService::toDto).collect(Collectors.toList()), nodeJsDetailsDto, false);
  }

  public GetGlobalConfigurationResponse getGlobalConnectedConfiguration(String connectionId) {
    var enabledLanguages = languageSupportRepository.getEnabledLanguagesInConnectedMode();
    var pluginPaths = pluginsService.getConnectedPluginPaths(connectionId);
    var activeNodeJs = nodeJsService.getActiveNodeJs();
    var nodeJsDetailsDto = activeNodeJs == null ? null : new NodeJsDetailsDto(activeNodeJs.getPath(), activeNodeJs.getVersion().toString());
    return new GetGlobalConfigurationResponse(pluginPaths, enabledLanguages.stream().map(AnalysisService::toDto).collect(Collectors.toList()), nodeJsDetailsDto,
      isDataflowBugDetectionEnabled);
  }

  @NotNull
  private static org.sonarsource.sonarlint.core.rpc.protocol.common.Language toDto(SonarLanguage language) {
    return org.sonarsource.sonarlint.core.rpc.protocol.common.Language.valueOf(language.name());
  }

  public GetAnalysisConfigResponse getAnalysisConfig(String configScopeId) {
    var bindingOpt = configurationRepository.getEffectiveBinding(configScopeId);
    var activeNodeJs = nodeJsService.getActiveNodeJs();

    var nodeJsDetailsDto = activeNodeJs == null ? null : new NodeJsDetailsDto(activeNodeJs.getPath(), activeNodeJs.getVersion().toString());
    return bindingOpt.map(binding -> new GetAnalysisConfigResponse(buildConnectedActiveRules(binding),
      storageService.binding(binding).analyzerConfiguration().read().getSettings().getAll(), nodeJsDetailsDto,
      Set.copyOf(pluginsService.getConnectedPluginPaths(binding.getConnectionId()))))
      .orElseGet(() -> new GetAnalysisConfigResponse(buildStandaloneActiveRules(), Map.of(), nodeJsDetailsDto, Set.copyOf(pluginsService.getEmbeddedPluginPaths())));
  }

  private List<ActiveRuleDto> buildConnectedActiveRules(Binding binding) {
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
          var activeRuleFromStorage = tryConvertDeprecatedKeys(binding.getConnectionId(), possiblyDeprecatedActiveRuleFromStorage);
          SonarLintRuleDefinition ruleOrTemplateDefinition;
          if (StringUtils.isNotBlank(activeRuleFromStorage.getTemplateKey())) {
            ruleOrTemplateDefinition = rulesRepository.getRule(binding.getConnectionId(), activeRuleFromStorage.getTemplateKey()).orElse(null);
            if (ruleOrTemplateDefinition == null) {
              LOG.debug("Rule {} is enabled on the server, but its template {} is not available in SonarLint", activeRuleFromStorage.getRuleKey(),
                activeRuleFromStorage.getTemplateKey());
              continue;
            }
          } else {
            ruleOrTemplateDefinition = rulesRepository.getRule(binding.getConnectionId(), activeRuleFromStorage.getRuleKey()).orElse(null);
            if (ruleOrTemplateDefinition == null) {
              LOG.debug("Rule {} is enabled on the server, but not available in SonarLint", activeRuleFromStorage.getRuleKey());
              continue;
            }
          }
          if (shouldIncludeRuleForAnalysis(binding.getConnectionId(), ruleOrTemplateDefinition)) {
            result.add(buildActiveRuleDto(ruleOrTemplateDefinition, activeRuleFromStorage));
          }
        }
      });

    var supportSecretAnalysis = supportsSecretAnalysis(binding.getConnectionId());
    if (!supportSecretAnalysis) {
      rulesRepository.getRules(binding.getConnectionId()).stream()
        .filter(ruleDefinition -> ruleDefinition.getLanguage() == SonarLanguage.SECRETS)
        .filter(r -> shouldIncludeRuleForAnalysis(binding.getConnectionId(), r))
        .forEach(r -> result.add(buildActiveRuleDto(r)));
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

  private boolean shouldIncludeRuleForAnalysis(String connectionId, SonarLintRuleDefinition ruleDefinition) {
    return !ruleDefinition.getType().equals(RuleType.SECURITY_HOTSPOT) ||
      (hotspotEnabled && permitsHotspotTracking(connectionId));
  }

  public boolean permitsHotspotTracking(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    if (connection == null) {
      // Connection is gone
      return false;
    }
    // when storage is not present, consider hotspots should not be detected
    return storageService.connection(connectionId).serverInfo().read()
      .map(serverInfo -> HotspotApi.permitsTracking(connection.getKind() == ConnectionKind.SONARCLOUD, serverInfo::getVersion))
      .orElse(false);
  }

  public boolean supportsSecretAnalysis(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    if (connection == null) {
      // Connection is gone
      return false;
    }
    // when storage is not present, assume that secrets are not supported by server
    return connection.getKind() == ConnectionKind.SONARCLOUD || storageService.connection(connectionId).serverInfo().read()
      .map(serverInfo -> serverInfo.getVersion().compareToIgnoreQualifier(SECRET_ANALYSIS_MIN_SQ_VERSION) >= 0)
      .orElse(false);
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
        ruleOrTemplateDefinition.getKey());
    } else {
      ruleOrTemplateDefinition = rulesRepository.getRule(connectionId, possiblyDeprecatedActiveRuleFromStorage.getRuleKey()).orElse(null);
      if (ruleOrTemplateDefinition == null) {
        // The rule is not known among our loaded analyzers, so return it untouched, to let calling code take appropriate decision
        return possiblyDeprecatedActiveRuleFromStorage;
      }
      return new ServerActiveRule(ruleOrTemplateDefinition.getKey(), possiblyDeprecatedActiveRuleFromStorage.getSeverity(), possiblyDeprecatedActiveRuleFromStorage.getParams(),
        null);
    }
  }

  private List<ActiveRuleDto> buildStandaloneActiveRules() {
    var standaloneRuleConfig = rulesService.getStandaloneRuleConfig();
    Set<String> excludedRules = standaloneRuleConfig.entrySet().stream().filter(Predicate.not(e -> e.getValue().isActive())).map(Map.Entry::getKey).collect(toSet());
    Set<String> includedRules = standaloneRuleConfig.entrySet().stream().filter(e -> e.getValue().isActive())
      .map(Map.Entry::getKey)
      .filter(r -> !excludedRules.contains(r))
      .collect(toSet());

    var filteredActiveRules = new ArrayList<SonarLintRuleDefinition>();

    var allRulesDefinitions = rulesRepository.getEmbeddedRules();

    filteredActiveRules.addAll(allRulesDefinitions.stream()
      .filter(SonarLintRuleDefinition::isActiveByDefault)
      .filter(isExcludedByConfiguration(excludedRules))
      .collect(Collectors.toList()));
    filteredActiveRules.addAll(allRulesDefinitions.stream()
      .filter(r -> !r.isActiveByDefault())
      .filter(isIncludedByConfiguration(includedRules))
      .collect(Collectors.toList()));

    return filteredActiveRules.stream().map(rd -> {
      Map<String, String> effectiveParams = new HashMap<>(rd.getDefaultParams());
      Optional.ofNullable(standaloneRuleConfig.get(rd.getKey())).ifPresent(config -> effectiveParams.putAll(config.getParamValueByKey()));
      // No template rules in standalone mode
      return new ActiveRuleDto(rd.getKey(), rd.getLanguage().getSonarLanguageKey(), effectiveParams, null);
    }).collect(Collectors.toList());
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
  public void onConfigurationScopeAdded(ConfigurationScopesAddedEvent event) {
    checkIfReadyForAnalysis(event.getAddedConfigurationScopeIds());
  }

  @EventListener
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent event) {
    analysisReadinessByConfigScopeId.remove(event.getRemovedConfigurationScopeId());
  }

  @EventListener
  public void onBindingConfigurationChanged(BindingConfigChangedEvent event) {
    var configScopeId = event.getConfigScopeId();
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
    return storageService.connection(binding.getConnectionId()).plugins().isValid()
      && storageService.binding(binding).analyzerConfiguration().isValid()
      // this is not strictly for analysis but for tracking
      && storageService.binding(binding).findings().wasEverUpdated();
  }

  public InstalledNodeJs getAutoDetectedNodeJs() {
    return nodeJsService.getAutoDetectedNodeJs();
  }
}
