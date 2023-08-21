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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.rules.RulesFixtures.aRule;

class RulesServiceImplTest {

  private RulesRepository rulesRepository;
  private RulesExtractionHelper extractionHelper;

  @BeforeEach
  void prepare() {
    extractionHelper = mock(RulesExtractionHelper.class);
    rulesRepository = new RulesRepository(extractionHelper);
  }

  @Test
  void it_should_return_all_embedded_rules_from_the_repository() throws Exception {
    when(extractionHelper.extractEmbeddedRules()).thenReturn(List.of(aRule()));
    var rulesService = new RulesServiceImpl(null, null, rulesRepository, null, null, Map.of());

    var embeddedRules = rulesService.listAllStandaloneRulesDefinitions().get(1, TimeUnit.MINUTES).getRulesByKey().values();

    assertThat(embeddedRules)
      .extracting(RuleDefinitionDto::getKey, RuleDefinitionDto::getName)
      .containsExactly(tuple("repo:ruleKey", "ruleName"));
  }
}
