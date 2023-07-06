/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetStandaloneRuleDescriptionParams;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetStandaloneRuleDescriptionResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ListAllStandaloneRulesDefinitionsResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleParamDefinitionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleParamType;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RulesService;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.UpdateStandaloneRulesConfigurationParams;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleParamDefinition;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleParamType;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerRule;
import org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfiguration;
import org.sonarsource.sonarlint.core.serverconnection.StorageService;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;

@Named
@Singleton
public class RulesServiceImpl implements RulesService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ServerApiProvider serverApiProvider;
  private final ConfigurationRepository configurationRepository;
  private final RulesRepository rulesRepository;
  private final StorageService storageService;
  private static final String COULD_NOT_FIND_RULE = "Could not find rule '";
  private final Map<String, StandaloneRuleConfigDto> standaloneRuleConfig = new ConcurrentHashMap<>();

  @Inject
  public RulesServiceImpl(ServerApiProvider serverApiProvider, ConfigurationRepository configurationRepository, RulesRepository rulesRepository, StorageService storageService,
    InitializeParams params) {
    this(serverApiProvider, configurationRepository, rulesRepository, storageService, params.getStandaloneRuleConfigByKey());
  }

  RulesServiceImpl(ServerApiProvider serverApiProvider, ConfigurationRepository configurationRepository, RulesRepository rulesRepository, StorageService storageService,
    Map<String, StandaloneRuleConfigDto> standaloneRuleConfigByKey) {
    this.serverApiProvider = serverApiProvider;
    this.configurationRepository = configurationRepository;
    this.rulesRepository = rulesRepository;
    this.storageService = storageService;
    this.standaloneRuleConfig.putAll(standaloneRuleConfigByKey);
  }

  @Override
  public CompletableFuture<GetEffectiveRuleDetailsResponse> getEffectiveRuleDetails(GetEffectiveRuleDetailsParams params) {
    var ruleKey = params.getRuleKey();
    var effectiveBinding = configurationRepository.getEffectiveBinding(params.getConfigurationScopeId());
    if (effectiveBinding.isEmpty()) {
      var embeddedRule = rulesRepository.getEmbeddedRule(ruleKey);
      if (embeddedRule.isEmpty()) {
        return CompletableFuture.failedFuture(new IllegalArgumentException(COULD_NOT_FIND_RULE + ruleKey + "' in embedded rules"));
      }
      var ruleDetails = RuleDetails.from(embeddedRule.get(), standaloneRuleConfig.get(ruleKey));
      return CompletableFuture.completedFuture(buildResponse(ruleDetails, params.getContextKey()));
    }
    return getActiveRuleForBinding(ruleKey, effectiveBinding.get())
      .thenApply(ruleDetails -> buildResponse(ruleDetails, params.getContextKey()));
  }

  private CompletableFuture<RuleDetails> getActiveRuleForBinding(String ruleKey, Binding binding) {
    var connectionId = binding.getConnectionId();

    return findServerActiveRuleInStorage(binding, ruleKey)
      .map(storageRule -> hydrateDetailsWithServer(connectionId, storageRule))
      // try from loaded rules, for e.g. extra analyzers
      .orElseGet(() -> rulesRepository.getRule(connectionId, ruleKey)
        .map(r -> RuleDetails.from(r, standaloneRuleConfig.get(ruleKey)))
        .map(CompletableFuture::completedFuture)
        .orElseGet(() -> CompletableFuture.failedFuture(new IllegalArgumentException(COULD_NOT_FIND_RULE + ruleKey + "' in plugins loaded from '" + connectionId + "'"))));
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

  private CompletableFuture<RuleDetails> hydrateDetailsWithServer(String connectionId, ServerActiveRule activeRuleFromStorage) {
    var ruleKey = activeRuleFromStorage.getRuleKey();
    var templateKey = activeRuleFromStorage.getTemplateKey();
    if (StringUtils.isNotBlank(templateKey)) {
      return rulesRepository.getRule(connectionId, templateKey)
        .map(templateRule -> serverApiProvider.getServerApi(connectionId)
          .map(serverApi -> fetchRuleFromServer(connectionId, ruleKey, serverApi)
            .thenApply(serverRule -> RuleDetails.merging(activeRuleFromStorage, serverRule, templateRule)))
          .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("Unknown connection '" + connectionId + "'"))))
        .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("Unable to find rule definition for rule template " + templateKey)));
    } else {
      return serverApiProvider.getServerApi(connectionId).map(serverApi -> fetchRuleFromServer(connectionId, ruleKey, serverApi)
        .thenApply(serverRule -> rulesRepository.getRule(connectionId, ruleKey)
          .map(ruleDefFromPlugin -> RuleDetails.merging(serverRule, ruleDefFromPlugin))
          .orElseGet(() -> RuleDetails.merging(activeRuleFromStorage, serverRule))))
        .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("Unknown connection '" + connectionId + "'")));
    }
  }

  private static CompletableFuture<ServerRule> fetchRuleFromServer(String connectionId, String ruleKey, ServerApi serverApi) {
    return serverApi.rules().getRule(ruleKey)
      .handle((r, e) -> {
        if (e != null) {
          throw new IllegalStateException(COULD_NOT_FIND_RULE + ruleKey + "' on '" + connectionId + "'", e);
        }
        return r;
      });
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

  private static GetEffectiveRuleDetailsResponse buildResponse(RuleDetails ruleDetails, @Nullable String contextKey) {
    return new GetEffectiveRuleDetailsResponse(RuleDetailsAdapter.transform(ruleDetails, contextKey));
  }

  @Override
  public CompletableFuture<ListAllStandaloneRulesDefinitionsResponse> listAllStandaloneRulesDefinitions() {
    return CompletableFuture.supplyAsync(() -> new ListAllStandaloneRulesDefinitionsResponse(
      rulesRepository.getEmbeddedRules()
        .stream()
        .map(RulesServiceImpl::convert)
        .collect(Collectors.toMap(RuleDefinitionDto::getKey, r -> r))));
  }

  @NotNull
  private static RuleDefinitionDto convert(SonarLintRuleDefinition r) {
    return new RuleDefinitionDto(r.getKey(), r.getName(), r.getDefaultSeverity(), r.getType(), convert(r.getParams()), r.isActiveByDefault(), r.getLanguage());
  }

  private static Map<String, RuleParamDefinitionDto> convert(Map<String, SonarLintRuleParamDefinition> params) {
    return params.values().stream().map(RulesServiceImpl::convert).collect(Collectors.toMap(RuleParamDefinitionDto::getKey, r -> r));
  }

  private static RuleParamDefinitionDto convert(SonarLintRuleParamDefinition paramDef) {
    return new RuleParamDefinitionDto(paramDef.key(), paramDef.name(), paramDef.description(), paramDef.defaultValue(), convert(paramDef.type()), paramDef.multiple(),
      paramDef.possibleValues());
  }

  private static RuleParamType convert(SonarLintRuleParamType type) {
    try {
      return RuleParamType.valueOf(type.name());
    } catch (IllegalArgumentException unknownType) {
      LOG.warn("Unknown parameter type: " + type.name());
      return RuleParamType.STRING;
    }
  }

  @Override
  public CompletableFuture<GetStandaloneRuleDescriptionResponse> getStandaloneRuleDetails(GetStandaloneRuleDescriptionParams params) {
    var ruleKey = params.getRuleKey();
    var embeddedRule = rulesRepository.getEmbeddedRule(ruleKey);
    if (embeddedRule.isEmpty()) {
      return CompletableFuture.failedFuture(new IllegalArgumentException(COULD_NOT_FIND_RULE + ruleKey + "' in embedded rules"));
    }
    var ruleDefinition = embeddedRule.get();
    var ruleDetails = RuleDetails.from(ruleDefinition, standaloneRuleConfig.get(ruleKey));

    return CompletableFuture.completedFuture(new GetStandaloneRuleDescriptionResponse(convert(ruleDefinition), RuleDetailsAdapter.transformDescriptions(ruleDetails, null)));
  }

  @Override
  public void updateStandaloneRulesConfiguration(UpdateStandaloneRulesConfigurationParams params) {
    setStandaloneRuleConfig(params.getRuleConfigByKey());
  }

  private synchronized void setStandaloneRuleConfig(Map<String, StandaloneRuleConfigDto> standaloneRuleConfig) {
    this.standaloneRuleConfig.clear();
    this.standaloneRuleConfig.putAll(standaloneRuleConfig);
  }

}
