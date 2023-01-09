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
package org.sonarsource.sonarlint.core.rules;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleContextualSectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleDescriptionTabDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleDetailsDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleMonolithicDescriptionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleNonContextualSectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleParamDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleSplitDescriptionDto;
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
  public static final String INTRODUCTION_SECTION_KEY = "introduction";
  public static final String RESOURCES_SECTION_KEY = "resources";
  private static final Map<String, String> SECTION_KEYS_TO_TAB_TITLE_ORDERED = new LinkedHashMap<>();

  static {
    SECTION_KEYS_TO_TAB_TITLE_ORDERED.put("root_cause", "Why is this an issue?");
    SECTION_KEYS_TO_TAB_TITLE_ORDERED.put("assess_the_problem", "Assess the risk");
    SECTION_KEYS_TO_TAB_TITLE_ORDERED.put("how_to_fix", "How can I fix it?");
    SECTION_KEYS_TO_TAB_TITLE_ORDERED.put(RESOURCES_SECTION_KEY, "More Info");
  }

  private final ServerApiProvider serverApiProvider;
  private final ConfigurationRepository configurationRepository;
  private final RulesServiceImpl rulesService;
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
      .thenApply(ActiveRulesServiceImpl::response);
  }

  private CompletableFuture<ActiveRuleDetails> getActiveEmbeddedRule(String ruleKey) {
    return rulesService.getEmbeddedRule(ruleKey)
      .map(ActiveRuleDetails::from)
      .map(CompletableFuture::completedFuture)
      .orElseGet(() -> CompletableFuture.failedFuture(new IllegalArgumentException("Could not find rule '" + ruleKey + "' in embedded rules")));
  }

  private CompletableFuture<ActiveRuleDetails> getActiveRuleForBinding(String ruleKey, Binding binding) {
    var connectionId = binding.getConnectionId();

    return findServerActiveRuleInStorage(binding, ruleKey)
      .map(storageRule -> hydrateDetailsWithServer(connectionId, storageRule))
      // try from loaded rules, for e.g. extra analyzers
      .orElseGet(() -> rulesService.getRule(connectionId, ruleKey)
        .map(ActiveRuleDetails::from)
        .map(CompletableFuture::completedFuture)
        .orElseGet(() -> CompletableFuture.failedFuture(new IllegalArgumentException("Could not find rule '" + ruleKey + "' in plugins loaded from '" + connectionId + "'"))));
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
          throw new IllegalStateException("Could not find rule '" + ruleKey + "' on '" + connectionId + "'");
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

  private static GetActiveRuleDetailsResponse response(ActiveRuleDetails activeRuleDetails) {
    return new GetActiveRuleDetailsResponse(transform(activeRuleDetails));
  }

  private static ActiveRuleDetailsDto transform(ActiveRuleDetails ruleDetails) {
    return new ActiveRuleDetailsDto(
      ruleDetails.getKey(),
      ruleDetails.getName(),
      ruleDetails.getDefaultSeverity(),
      ruleDetails.getType(),
      transformDescriptions(ruleDetails),
      transform(ruleDetails.getParams()),
      ruleDetails.getLanguage());
  }

  private static Either<ActiveRuleMonolithicDescriptionDto, ActiveRuleSplitDescriptionDto> transformDescriptions(ActiveRuleDetails ruleDetails) {
    if (ruleDetails.hasMonolithicDescription()) {
      return Either.forLeft(transformMonolithicDescription(ruleDetails));
    }
    return Either.forRight(transformSplitDescription(ruleDetails));
  }

  private static ActiveRuleMonolithicDescriptionDto transformMonolithicDescription(ActiveRuleDetails ruleDetails) {
    return new ActiveRuleMonolithicDescriptionDto(concat(ruleDetails.getHtmlDescription(), ruleDetails.getExtendedDescription()));
  }

  private static ActiveRuleSplitDescriptionDto transformSplitDescription(ActiveRuleDetails ruleDetails) {
    var sectionsByKey = new HashMap<>(ruleDetails.getDescriptionSectionsByKey());

    var tabbedSections = new ArrayList<ActiveRuleDescriptionTabDto>();
    SECTION_KEYS_TO_TAB_TITLE_ORDERED.keySet().forEach(sectionKey -> {
      if (sectionsByKey.containsKey(sectionKey)) {
        var sections = sectionsByKey.get(sectionKey);
        var title = SECTION_KEYS_TO_TAB_TITLE_ORDERED.get(sectionKey);
        Either<ActiveRuleNonContextualSectionDto, Collection<ActiveRuleContextualSectionDto>> content;
        if (sections.size() == 1 && sections.get(0).getContext().isEmpty()) {
          content = Either.forLeft(new ActiveRuleNonContextualSectionDto(getTabContent(sections.get(0), ruleDetails.getExtendedDescription())));
        } else {
          // if there is more than one section, they should all have a context (verified in sonar-plugin-api)
          content = Either.forRight(sections.stream().map(s -> {
            var context = s.getContext().get();
            return new ActiveRuleContextualSectionDto(getTabContent(s, ruleDetails.getExtendedDescription()), context.getKey(), context.getDisplayName());
          }).collect(Collectors.toList()));
        }
        tabbedSections.add(new ActiveRuleDescriptionTabDto(title, content));
      }
    });
    var introductionSections = sectionsByKey.get(INTRODUCTION_SECTION_KEY);
    String introductionHtmlContent = null;
    if (introductionSections != null && !introductionSections.isEmpty()) {
      // assume there is only one introduction section
      introductionHtmlContent = introductionSections.get(0).getHtmlContent();
    }
    return new ActiveRuleSplitDescriptionDto(introductionHtmlContent, tabbedSections);
  }

  private static String concat(String htmlDescription, @Nullable String extendedDescription) {
    var result = htmlDescription;
    if (StringUtils.isNotBlank(extendedDescription)) {
      result += "<br/><br/>" + extendedDescription;
    }
    return result;
  }

  private static String getTabContent(ActiveRuleDetails.DescriptionSection section, @Nullable String extendedDescription) {
    var result = section.getHtmlContent();
    if (RESOURCES_SECTION_KEY.equals(section.getKey()) && StringUtils.isNotBlank(extendedDescription)) {
      result += "<br/><br/>" + extendedDescription;
    }
    return result;
  }

  private static Collection<ActiveRuleParamDto> transform(Collection<ActiveRuleDetails.ActiveRuleParam> params) {
    var builder = new ArrayList<ActiveRuleParamDto>();
    for (var param : params) {
      builder.add(new ActiveRuleParamDto(
        param.getName(),
        param.getDescription(),
        param.getDefaultValue()));
    }
    return builder;
  }
}
