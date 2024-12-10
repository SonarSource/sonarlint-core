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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.common.ImpactPayload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.rules.RulesFixtures.aRule;

class RulesServiceTests {

  private RulesRepository rulesRepository;
  private RulesExtractionHelper extractionHelper;
  private ConfigurationRepository configurationRepository;

  @BeforeEach
  void prepare() {
    extractionHelper = mock(RulesExtractionHelper.class);
    configurationRepository = mock(ConfigurationRepository.class);
    rulesRepository = new RulesRepository(extractionHelper, configurationRepository);
  }

  @Test
  void it_should_return_all_embedded_rules_from_the_repository() {
    when(extractionHelper.extractEmbeddedRules()).thenReturn(List.of(aRule()));
    var rulesService = new RulesService(null, null, rulesRepository, null, null, Map.of(), null);

    var embeddedRules = rulesService.listAllStandaloneRulesDefinitions().values();

    assertThat(embeddedRules)
      .extracting(RuleDefinitionDto::getKey, RuleDefinitionDto::getName)
      .containsExactly(tuple("repo:ruleKey", "ruleName"));
  }

  @Test
  void it_should_only_override_overridden_impact_quality() {
    Map<SoftwareQuality, ImpactSeverity> defaultImpacts = Map.of(
      SoftwareQuality.MAINTAINABILITY, ImpactSeverity.LOW,
      SoftwareQuality.RELIABILITY, ImpactSeverity.MEDIUM
    );

    List<ImpactPayload> overriddenImpacts = List.of(
      new ImpactPayload("MAINTAINABILITY", "HIGH")
    );

    Map<SoftwareQuality, ImpactSeverity> result = RuleDetails.mergeImpacts(defaultImpacts, overriddenImpacts);
    assertThat(result)
      .containsEntry(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.HIGH)
      .containsEntry(SoftwareQuality.RELIABILITY, ImpactSeverity.MEDIUM);
  }

  @Test
  void it_should_work_when_no_overridden_impacts() {
    Map<SoftwareQuality, ImpactSeverity> defaultImpacts = Map.of(
      SoftwareQuality.MAINTAINABILITY, ImpactSeverity.LOW,
      SoftwareQuality.RELIABILITY, ImpactSeverity.MEDIUM
    );

    Map<SoftwareQuality, ImpactSeverity> result = RuleDetails.mergeImpacts(defaultImpacts, List.of());

    assertThat(result).isEqualTo(defaultImpacts);
  }

}
