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
package org.sonarsource.sonarlint.core.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.SonarServerEventReceivedEvent;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetRuleDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleParamDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleParamType;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleParamDefinition;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleParamType;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.push.RuleSetChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerRule;
import org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfiguration;
import org.sonarsource.sonarlint.core.serverconnection.RuleSet;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.SynchronizationService;
import org.springframework.context.event.EventListener;

import static org.sonarsource.sonarlint.core.rules.RuleDetailsAdapter.adapt;
import static org.sonarsource.sonarlint.core.rules.RuleDetailsAdapter.toDto;

@Named
@Singleton
public class RulesService {

  private static final Logger LOG = LoggerFactory.getLogger(RulesService.class);
  private final ServerApiProvider serverApiProvider;
  private final ConfigurationRepository configurationRepository;
  private final RulesRepository rulesRepository;
  private final StorageService storageService;
  private final SynchronizationService synchronizationService;
  private static final String COULD_NOT_FIND_RULE = "Could not find rule '";
  private final Map<String, StandaloneRuleConfigDto> standaloneRuleConfig = new ConcurrentHashMap<>();

  @Inject
  public RulesService(ServerApiProvider serverApiProvider, ConfigurationRepository configurationRepository, RulesRepository rulesRepository, StorageService storageService,
    SynchronizationService synchronizationService, InitializeParams params) {
    this(serverApiProvider, configurationRepository, rulesRepository, storageService, synchronizationService, params.getStandaloneRuleConfigByKey());
  }

  RulesService(ServerApiProvider serverApiProvider, ConfigurationRepository configurationRepository, RulesRepository rulesRepository, StorageService storageService,
    SynchronizationService synchronizationService, @Nullable Map<String, StandaloneRuleConfigDto> standaloneRuleConfigByKey) {
    this.serverApiProvider = serverApiProvider;
    this.configurationRepository = configurationRepository;
    this.rulesRepository = rulesRepository;
    this.storageService = storageService;
    this.synchronizationService = synchronizationService;
    if (standaloneRuleConfigByKey != null) {
      this.standaloneRuleConfig.putAll(standaloneRuleConfigByKey);
    }
  }

  public EffectiveRuleDetailsDto getEffectiveRuleDetails(String configurationScopeId, String ruleKey, @Nullable String contextKey,
    SonarLintCancelMonitor cancelMonitor) throws RuleNotFoundException {
    var ruleDetails = getRuleDetails(configurationScopeId, ruleKey, cancelMonitor);
    return buildResponse(ruleDetails, contextKey);
  }

  private RuleDetails getRuleDetails(String configurationScopeId, String ruleKey, SonarLintCancelMonitor cancelMonitor) throws RuleNotFoundException {
    var effectiveBinding = configurationRepository.getEffectiveBinding(configurationScopeId);
    RuleDetails ruleDetails;
    if (effectiveBinding.isEmpty()) {
      var embeddedRule = rulesRepository.getEmbeddedRule(ruleKey);
      if (embeddedRule.isEmpty()) {
        throw new RuleNotFoundException(COULD_NOT_FIND_RULE + ruleKey + "' in embedded rules", ruleKey);
      }
      ruleDetails = RuleDetails.from(embeddedRule.get(), standaloneRuleConfig.get(ruleKey));
    } else {
      ruleDetails = getActiveRuleForBinding(ruleKey, effectiveBinding.get(), cancelMonitor);
    }
    return ruleDetails;
  }

  private RuleDetails getActiveRuleForBinding(String ruleKey, Binding binding, SonarLintCancelMonitor cancelMonitor) {
    var connectionId = binding.getConnectionId();
    var serverApi = serverApiProvider.getServerApi(connectionId);
    if (serverApi.isEmpty()) {
      throw unknownConnection(connectionId);
    }
    boolean skipCleanCodeTaxonomy = synchronizationService.getServerConnection(connectionId, serverApi.get()).shouldSkipCleanCodeTaxonomy();

    return findServerActiveRuleInStorage(binding, ruleKey)
      .map(storageRule -> hydrateDetailsWithServer(connectionId, storageRule, skipCleanCodeTaxonomy, cancelMonitor))
      // try from loaded rules, for e.g. extra analyzers
      .orElseGet(() -> rulesRepository.getRule(connectionId, ruleKey)
        .map(r -> RuleDetails.from(r, standaloneRuleConfig.get(ruleKey)))
        .orElseThrow(() -> ruleNotFoundInPlugins(ruleKey, connectionId)));
  }


  private Optional<ServerActiveRule> findServerActiveRuleInStorage(Binding binding, String ruleKey) {
    AnalyzerConfiguration analyzerConfiguration;
    try {
      analyzerConfiguration = storageService.binding(binding).analyzerConfiguration().read();
    } catch (StorageException e) {
      // XXX we should make sure this situation can not happen (sync should be enforced at least once)
      return Optional.empty();
    }
    return analyzerConfiguration.getRuleSetByLanguageKey().values().stream()
      .flatMap(s -> s.getRules().stream())
      // XXX is it important to migrate the rule repos in tryConvertDeprecatedKeys?
      .filter(r -> tryConvertDeprecatedKeys(r, binding.getConnectionId()).getRuleKey().equals(ruleKey)).findFirst();
  }

  private RuleDetails hydrateDetailsWithServer(String connectionId, ServerActiveRule activeRuleFromStorage, boolean skipCleanCodeTaxonomy, SonarLintCancelMonitor cancelMonitor) {
    var ruleKey = activeRuleFromStorage.getRuleKey();
    var templateKey = activeRuleFromStorage.getTemplateKey();
    var serverApi = serverApiProvider.getServerApiOrThrow(connectionId);
    if (StringUtils.isNotBlank(templateKey)) {
      var templateRule = rulesRepository.getRule(connectionId, templateKey);
      if (templateRule.isEmpty()) {
        throw ruleDefinitionNotFound(templateKey);
      }
      var serverRule = fetchRuleFromServer(connectionId, ruleKey, serverApi, cancelMonitor);
      return RuleDetails.merging(activeRuleFromStorage, serverRule, templateRule.get(), skipCleanCodeTaxonomy);
    } else {
      var serverRule = fetchRuleFromServer(connectionId, ruleKey, serverApi, cancelMonitor);
      var ruleDefFromPluginOpt = rulesRepository.getRule(connectionId, ruleKey);
      return ruleDefFromPluginOpt
        .map(ruleDefFromPlugin -> RuleDetails.merging(serverRule, ruleDefFromPlugin, skipCleanCodeTaxonomy))
        .orElseGet(() -> RuleDetails.merging(activeRuleFromStorage, serverRule));
    }
  }

  @NotNull
  private static ResponseErrorException unknownConnection(String connectionId) {
    var error = new ResponseError(SonarLintRpcErrorCode.CONNECTION_NOT_FOUND, "Connection with ID '" + connectionId + "' does not exist", connectionId);
    return new ResponseErrorException(error);
  }

  private static ServerRule fetchRuleFromServer(String connectionId, String ruleKey, ServerApi serverApi, SonarLintCancelMonitor cancelMonitor) {
    return serverApi.rules().getRule(ruleKey, cancelMonitor).orElseThrow(() -> ruleNotFoundOnServer(ruleKey, connectionId));
  }

  private static ResponseErrorException ruleDefinitionNotFound(String templateKey) {
    var error = new ResponseError(SonarLintRpcErrorCode.RULE_NOT_FOUND, "Unable to find rule definition for rule template " + templateKey, templateKey);
    return new ResponseErrorException(error);
  }

  @NotNull
  private static ResponseErrorException ruleNotFoundInPlugins(String ruleKey, String connectionId) {
    var error = new ResponseError(SonarLintRpcErrorCode.RULE_NOT_FOUND, COULD_NOT_FIND_RULE + ruleKey + "' in plugins loaded from '" + connectionId + "'",
      new Object[]{connectionId, ruleKey});
    return new ResponseErrorException(error);
  }

  private static ResponseErrorException ruleNotFoundOnServer(String ruleKey, String connectionId) {
    var error = new ResponseError(SonarLintRpcErrorCode.RULE_NOT_FOUND, COULD_NOT_FIND_RULE + ruleKey + "' on server '" + connectionId + "'",
      new Object[]{connectionId, ruleKey});
    return new ResponseErrorException(error);
  }

  private ServerActiveRule tryConvertDeprecatedKeys(ServerActiveRule possiblyDeprecatedActiveRuleFromStorage, String connectionId) {
    Optional<SonarLintRuleDefinition> ruleOrTemplateDefinition;
    if (StringUtils.isNotBlank(possiblyDeprecatedActiveRuleFromStorage.getTemplateKey())) {
      ruleOrTemplateDefinition = rulesRepository.getRule(connectionId, possiblyDeprecatedActiveRuleFromStorage.getTemplateKey());
      if (ruleOrTemplateDefinition.isEmpty()) {
        // The rule template is not known among our loaded analyzers, so return it untouched, to let calling code take appropriate decision
        return possiblyDeprecatedActiveRuleFromStorage;
      }
      var ruleKeyPossiblyWithDeprecatedRepo = RuleKey.parse(possiblyDeprecatedActiveRuleFromStorage.getRuleKey());
      var templateRuleKeyWithCorrectRepo = RuleKey.parse(ruleOrTemplateDefinition.get().getKey());
      var ruleKey = new RuleKey(templateRuleKeyWithCorrectRepo.repository(), ruleKeyPossiblyWithDeprecatedRepo.rule()).toString();
      return new ServerActiveRule(ruleKey, possiblyDeprecatedActiveRuleFromStorage.getSeverity(), possiblyDeprecatedActiveRuleFromStorage.getParams(),
        ruleOrTemplateDefinition.get().getKey());
    } else {
      ruleOrTemplateDefinition = rulesRepository.getRule(connectionId, possiblyDeprecatedActiveRuleFromStorage.getRuleKey());
      if (ruleOrTemplateDefinition.isEmpty()) {
        // The rule is not known among our loaded analyzers, so return it untouched, to let calling code take appropriate decision
        return possiblyDeprecatedActiveRuleFromStorage;
      }
      return new ServerActiveRule(ruleOrTemplateDefinition.get().getKey(), possiblyDeprecatedActiveRuleFromStorage.getSeverity(),
        possiblyDeprecatedActiveRuleFromStorage.getParams(),
        null);
    }
  }

  private static EffectiveRuleDetailsDto buildResponse(RuleDetails ruleDetails, @Nullable String contextKey) {
    return RuleDetailsAdapter.transform(ruleDetails, contextKey);
  }

  public Map<String, RuleDefinitionDto> listAllStandaloneRulesDefinitions() {
    return rulesRepository.getEmbeddedRules()
      .stream()
      .map(RulesService::convert)
      .collect(Collectors.toMap(RuleDefinitionDto::getKey, r -> r));
  }

  @NotNull
  private static RuleDefinitionDto convert(SonarLintRuleDefinition r) {
    return new RuleDefinitionDto(r.getKey(), r.getName(), adapt(r.getDefaultSeverity()), adapt(r.getType()),
      r.getCleanCodeAttribute().map(RuleDetailsAdapter::adapt).orElse(null),
      r.getCleanCodeAttribute().map(CleanCodeAttribute::getAttributeCategory).map(RuleDetailsAdapter::adapt).orElse(null),
      toDto(r.getDefaultImpacts()),
      convert(r.getParams()), r.isActiveByDefault(), adapt(r.getLanguage()), r.getVulnerabilityProbability().map(RuleDetailsAdapter::adapt).orElse(null));
  }

  private static Map<String, RuleParamDefinitionDto> convert(Map<String, SonarLintRuleParamDefinition> params) {
    return params.values().stream().map(RulesService::convert).collect(Collectors.toMap(RuleParamDefinitionDto::getKey, r -> r));
  }

  private static RuleParamDefinitionDto convert(SonarLintRuleParamDefinition paramDef) {
    return new RuleParamDefinitionDto(paramDef.key(), paramDef.name(), paramDef.description(), paramDef.defaultValue(), convert(paramDef.type()), paramDef.multiple(),
      paramDef.possibleValues());
  }

  private static RuleParamType convert(SonarLintRuleParamType type) {
    try {
      return RuleParamType.valueOf(type.name());
    } catch (IllegalArgumentException unknownType) {
      LOG.warn("Unknown parameter type: {}", type.name());
      return RuleParamType.STRING;
    }
  }

  public GetStandaloneRuleDescriptionResponse getStandaloneRuleDetails(String ruleKey) {
    var embeddedRule = rulesRepository.getEmbeddedRule(ruleKey);
    if (embeddedRule.isEmpty()) {
      var error = new ResponseError(SonarLintRpcErrorCode.RULE_NOT_FOUND, COULD_NOT_FIND_RULE + ruleKey + "' in embedded rules", new Object[]{ruleKey});
      throw new ResponseErrorException(error);
    }
    var ruleDefinition = embeddedRule.get();
    var ruleDetails = RuleDetails.from(ruleDefinition, standaloneRuleConfig.get(ruleKey));

    return new GetStandaloneRuleDescriptionResponse(convert(ruleDefinition), RuleDetailsAdapter.transformDescriptions(ruleDetails, null));
  }

  public void updateStandaloneRulesConfiguration(Map<String, StandaloneRuleConfigDto> ruleConfigByKey) {
    setStandaloneRuleConfig(ruleConfigByKey);
  }

  private synchronized void setStandaloneRuleConfig(Map<String, StandaloneRuleConfigDto> standaloneRuleConfig) {
    this.standaloneRuleConfig.clear();
    this.standaloneRuleConfig.putAll(standaloneRuleConfig);
  }

  public synchronized Map<String, StandaloneRuleConfigDto> getStandaloneRuleConfig() {
    return Collections.unmodifiableMap(standaloneRuleConfig);
  }

  @EventListener
  public void onServerEventReceived(SonarServerEventReceivedEvent eventReceived) {
    var connectionId = eventReceived.getConnectionId();
    var serverEvent = eventReceived.getEvent();
    if (serverEvent instanceof RuleSetChangedEvent) {
      updateStorage(connectionId, (RuleSetChangedEvent) serverEvent);
    }
  }

  private void updateStorage(String connectionId, RuleSetChangedEvent event) {
    event.getProjectKeys().forEach(projectKey -> storageService.connection(connectionId).project(projectKey).analyzerConfiguration().update(currentConfiguration -> {
      var newRuleSetByLanguageKey = incorporate(event, currentConfiguration.getRuleSetByLanguageKey());
      return new AnalyzerConfiguration(currentConfiguration.getSettings(), newRuleSetByLanguageKey, currentConfiguration.getSchemaVersion());
    }));
  }

  private static Map<String, RuleSet> incorporate(RuleSetChangedEvent event, Map<String, RuleSet> ruleSetByLanguageKey) {
    Map<String, RuleSet> resultingRuleSetsByLanguageKey = new HashMap<>(ruleSetByLanguageKey);
    event.getDeactivatedRules().forEach(deactivatedRule -> deactivate(deactivatedRule, resultingRuleSetsByLanguageKey));
    event.getActivatedRules().forEach(activatedRule -> activate(activatedRule, resultingRuleSetsByLanguageKey));
    return resultingRuleSetsByLanguageKey;
  }

  private static void activate(RuleSetChangedEvent.ActiveRule activatedRule, Map<String, RuleSet> ruleSetsByLanguageKey) {
    var ruleLanguageKey = activatedRule.getLanguageKey();
    var currentRuleSet = ruleSetsByLanguageKey.computeIfAbsent(ruleLanguageKey, k -> new RuleSet(Collections.emptyList(), ""));
    var languageRulesByKey = new HashMap<>(currentRuleSet.getRulesByKey());
    var ruleTemplateKey = activatedRule.getTemplateKey();
    languageRulesByKey.put(activatedRule.getKey(), new ServerActiveRule(
      activatedRule.getKey(),
      activatedRule.getSeverity(),
      activatedRule.getParameters(),
      ruleTemplateKey == null ? "" : ruleTemplateKey));
    ruleSetsByLanguageKey.put(ruleLanguageKey, new RuleSet(new ArrayList<>(languageRulesByKey.values()), currentRuleSet.getLastModified()));
  }

  private static void deactivate(String deactivatedRuleKey, Map<String, RuleSet> ruleSetsByLanguageKey) {
    var ruleSetsIterator = ruleSetsByLanguageKey.entrySet().iterator();
    while (ruleSetsIterator.hasNext()) {
      var ruleSetEntry = ruleSetsIterator.next();
      var ruleSet = ruleSetEntry.getValue();
      var newRules = new HashMap<>(ruleSet.getRulesByKey());
      newRules.remove(deactivatedRuleKey);
      if (newRules.isEmpty()) {
        ruleSetsIterator.remove();
      } else {
        ruleSetsByLanguageKey.put(ruleSetEntry.getKey(), new RuleSet(List.copyOf(newRules.values()), ruleSet.getLastModified()));
      }
    }
  }

  public GetRuleDetailsResponse getRuleDetailsForAnalysis(String configScopeId, String ruleKey) throws RuleNotFoundException {
    var effectiveBinding = configurationRepository.getEffectiveBinding(configScopeId);
    return effectiveBinding.isEmpty() ? getRuleDetailsForStandaloneAnalysis(ruleKey) : getRuleDetailsForConnectedAnalysis(effectiveBinding.get(), ruleKey);
  }

  private GetRuleDetailsResponse getRuleDetailsForStandaloneAnalysis(String ruleKey) throws RuleNotFoundException {
    var embeddedRule = rulesRepository.getEmbeddedRule(ruleKey);
    if (embeddedRule.isEmpty()) {
      throw new RuleNotFoundException(COULD_NOT_FIND_RULE + ruleKey + "' in embedded rules", ruleKey);
    }
    var ruleDefinition = embeddedRule.get();
    return new GetRuleDetailsResponse(
      RuleDetailsAdapter.adapt(ruleDefinition.getDefaultSeverity()),
      RuleDetailsAdapter.adapt(ruleDefinition.getType()),
      ruleDefinition.getCleanCodeAttribute().map(RuleDetailsAdapter::adapt).orElse(org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute.CONVENTIONAL),
      ruleDefinition.getDefaultImpacts().entrySet().stream().map(entry -> new ImpactDto(RuleDetailsAdapter.adapt(entry.getKey()), RuleDetailsAdapter.adapt(entry.getValue())))
        .collect(Collectors.toList()),
      ruleDefinition.getVulnerabilityProbability().map(RuleDetailsAdapter::adapt).orElse(null));
  }

  public GetRuleDetailsResponse getRuleDetailsForConnectedAnalysis(Binding binding, String ruleKey) throws RuleNotFoundException {
    var activeRuleOpt = findServerActiveRuleInStorage(binding, ruleKey);
    if (activeRuleOpt.isEmpty()) {
      throw new RuleNotFoundException(COULD_NOT_FIND_RULE + ruleKey + "' in active rules", ruleKey);
    }
    var activeRule = activeRuleOpt.get();
    var actualRuleKey = ruleKey;
    if (StringUtils.isNotBlank(activeRule.getTemplateKey())) {
      actualRuleKey = activeRule.getTemplateKey();
    }
    var ruleDefinitionOpt = rulesRepository.getRule(binding.getConnectionId(), actualRuleKey);
    if (ruleDefinitionOpt.isEmpty()) {
      throw new RuleNotFoundException(COULD_NOT_FIND_RULE + actualRuleKey + "' in embedded rules", actualRuleKey);
    }
    var ruleDefinition = ruleDefinitionOpt.get();
    return new GetRuleDetailsResponse(
      RuleDetailsAdapter.adapt(activeRule.getSeverity()),
      RuleDetailsAdapter.adapt(ruleDefinition.getType()),
      ruleDefinition.getCleanCodeAttribute().map(RuleDetailsAdapter::adapt).orElse(org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute.CONVENTIONAL),
      ruleDefinition.getDefaultImpacts().entrySet().stream().map(entry -> new ImpactDto(RuleDetailsAdapter.adapt(entry.getKey()), RuleDetailsAdapter.adapt(entry.getValue())))
        .collect(Collectors.toList()),
      ruleDefinition.getVulnerabilityProbability().map(RuleDetailsAdapter::adapt).orElse(null));
  }
}
