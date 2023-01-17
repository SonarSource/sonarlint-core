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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.plugin.PluginsRepository;
import org.sonarsource.sonarlint.core.plugin.PluginsServiceImpl;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.rules.RulesFixtures.aRule;

class RulesServiceImplTest {

  private RulesRepository rulesRepository;
  private PluginsServiceImpl pluginsService;

  @BeforeEach
  void prepare() {
    rulesRepository = new RulesRepository();
    PluginsRepository pluginsRepository = new PluginsRepository();
    pluginsService = new PluginsServiceImpl(pluginsRepository);
  }

  @Test
  void it_should_return_all_embedded_rules_from_the_repository() {
    rulesRepository.setEmbeddedRules(List.of(aRule()));
    var rulesService = new RulesServiceImpl(pluginsService, rulesRepository);

    var embeddedRules = rulesService.getEmbeddedRules();

    assertThat(embeddedRules)
      .extracting("key", "name")
      .containsExactly(tuple("repo:ruleKey", "ruleName"));
  }
}
