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
package org.sonarsource.sonarlint.core.rules;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleParamDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleParamType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleParamDefinition;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleParamType;

import static org.sonarsource.sonarlint.core.rules.RuleDetailsAdapter.adapt;
import static org.sonarsource.sonarlint.core.rules.RuleDetailsAdapter.toDto;

@Named
@Singleton
public class RulesService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final String IN_EMBEDDED_RULES = "' in embedded rules";
  private final RulesRepository rulesRepository;
  public static final String COULD_NOT_FIND_RULE = "Could not find rule '";

  public RulesService(RulesRepository rulesRepository) {
    this.rulesRepository = rulesRepository;
  }

  public Map<String, RuleDefinitionDto> listAllStandaloneRulesDefinitions() {
    return rulesRepository.getEmbeddedRules()
      .stream()
      .map(RulesService::convert)
      .collect(Collectors.toMap(RuleDefinitionDto::getKey, r -> r));
  }

  @NotNull
  public static RuleDefinitionDto convert(SonarLintRuleDefinition r) {
    var cleanCodeAttribute = r.getCleanCodeAttribute().map(RuleDetailsAdapter::adapt).orElse(CleanCodeAttribute.CONVENTIONAL);
    return new RuleDefinitionDto(r.getKey(), r.getName(), cleanCodeAttribute, toDto(r.getDefaultImpacts()),
      convert(r.getParams()), r.isActiveByDefault(), adapt(r.getLanguage()));
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

}
