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

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRulesService;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetActiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetActiveRuleDetailsResponse;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.repository.config.Binding;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerRule;
import org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfiguration;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

public class ActiveRulesServiceImpl implements ActiveRulesService {
  private final ServerApiProvider serverApiProvider;
  private final ConfigurationRepository configurationRepository;
  private final RulesServiceImpl rulesService;
  private static final String COULD_NOT_FIND_RULE = "Could not find rule '";
  private Path storageRoot;

  public ActiveRulesServiceImpl(ServerApiProvider serverApiProvider, RulesServiceImpl rulesService, ConfigurationRepository configurationRepository) {
    this.serverApiProvider = serverApiProvider;
    this.rulesService = rulesService;
    this.configurationRepository = configurationRepository;
  }

  public void initialize(Path storageRoot) {
    this.storageRoot = storageRoot;
  }

  @Override
  public CompletableFuture<GetActiveRuleDetailsResponse> getActiveRuleDetails(GetActiveRuleDetailsParams params) {
    return configurationRepository.getEffectiveBinding(params.getConfigurationScopeId())
      .map(binding -> getActiveRuleForBinding(params.getRuleKey(), binding))
      .orElseGet(() -> getActiveEmbeddedRule(params.getRuleKey()))
      .thenApply(activeRuleDetails -> buildResponse(activeRuleDetails, params.getContextKey()));
  }

  private CompletableFuture<ActiveRuleDetails> getActiveEmbeddedRule(String ruleKey) {
    return rulesService.getEmbeddedRule(ruleKey)
      .map(ActiveRuleDetails::from)
      .map(CompletableFuture::completedFuture)
      .orElseGet(() -> CompletableFuture.failedFuture(new IllegalArgumentException(COULD_NOT_FIND_RULE + ruleKey + "' in embedded rules")));
  }

  private CompletableFuture<ActiveRuleDetails> getActiveRuleForBinding(String ruleKey, Binding binding) {
    var connectionId = binding.getConnectionId();

    return findServerActiveRuleInStorage(binding, ruleKey)
      .map(storageRule -> hydrateDetailsWithServer(connectionId, storageRule))
      // try from loaded rules, for e.g. extra analyzers
      .orElseGet(() -> rulesService.getRule(connectionId, ruleKey)
        .map(ActiveRuleDetails::from)
        .map(CompletableFuture::completedFuture)
        .orElseGet(() -> CompletableFuture.failedFuture(new IllegalArgumentException(COULD_NOT_FIND_RULE + ruleKey + "' in plugins loaded from '" + connectionId + "'"))));
  }

  private Optional<ServerActiveRule> findServerActiveRuleInStorage(Binding binding, String ruleKey) {
    var projectStorage = new ProjectStorage(storageRoot.resolve(encodeForFs(binding.getConnectionId())).resolve("projects"));
    AnalyzerConfiguration analyzerConfiguration;
    try {
      analyzerConfiguration = projectStorage
        .getAnalyzerConfiguration(binding.getSonarProjectKey());
    } catch (StorageException e) {
      // XXX we should make sure this situation can not happen (sync should be enforced at least once)
      return Optional.empty();
    }
    return analyzerConfiguration.getRuleSetByLanguageKey().values().stream()
      .flatMap(s -> s.getRules().stream())
      // XXX is it important to migrate the rule repos in tryConvertDeprecatedKeys?
      .filter(r -> tryConvertDeprecatedKeys(r, binding.getConnectionId()).getRuleKey().equals(ruleKey)).findFirst();
  }

  private CompletableFuture<ActiveRuleDetails> hydrateDetailsWithServer(String connectionId, ServerActiveRule activeRuleFromStorage) {
    var ruleKey = activeRuleFromStorage.getRuleKey();
    var templateKey = activeRuleFromStorage.getTemplateKey();
    if (StringUtils.isNotBlank(templateKey)) {
      return rulesService.getRule(connectionId, templateKey)
        .map(templateRule -> serverApiProvider.getServerApi(connectionId)
          .map(serverApi -> fetchRuleFromServer(connectionId, ruleKey, serverApi)
            .thenApply(serverRule -> ActiveRuleDetails.merging(activeRuleFromStorage, serverRule, templateRule)))
          .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("Unknown connection '" + connectionId + "'"))))
        .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("Unable to find rule definition for rule template " + templateKey)));
    } else {
      return serverApiProvider.getServerApi(connectionId).map(serverApi -> fetchRuleFromServer(connectionId, ruleKey, serverApi)
          .thenApply(serverRule -> rulesService.getRule(connectionId, ruleKey)
            .map(ruleDefFromPlugin -> ActiveRuleDetails.merging(serverRule, ruleDefFromPlugin))
            .orElseGet(() -> ActiveRuleDetails.merging(activeRuleFromStorage, serverRule))))
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
      ruleOrTemplateDefinition = rulesService.getRule(connectionId, possiblyDeprecatedActiveRuleFromStorage.getTemplateKey());
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
      ruleOrTemplateDefinition = rulesService.getRule(connectionId, possiblyDeprecatedActiveRuleFromStorage.getRuleKey());
      if (ruleOrTemplateDefinition.isEmpty()) {
        // The rule is not known among our loaded analyzers, so return it untouched, to let calling code take appropriate decision
        return possiblyDeprecatedActiveRuleFromStorage;
      }
      return new ServerActiveRule(ruleOrTemplateDefinition.get().getKey(), possiblyDeprecatedActiveRuleFromStorage.getSeverity(),
        possiblyDeprecatedActiveRuleFromStorage.getParams(),
        null);
    }
  }

  private static GetActiveRuleDetailsResponse buildResponse(ActiveRuleDetails activeRuleDetails, @Nullable String contextKey) {
    return new GetActiveRuleDetailsResponse(ActiveRuleDetailsAdapter.transform(activeRuleDetails, contextKey));
  }

}