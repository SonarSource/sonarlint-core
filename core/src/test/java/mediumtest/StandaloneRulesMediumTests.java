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
package mediumtest;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mediumtest.fixtures.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetStandaloneRuleDescriptionParams;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ListAllStandaloneRulesDefinitionsResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.commons.Language;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;

class StandaloneRulesMediumTests {

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void it_should_return_only_embedded_rules_of_enabled_languages() {
    backend = newBackend()
      .withStorageRoot(storageDir)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .withStandaloneEmbeddedPlugin(TestPlugin.PHP)
      .build();

    var allRules = listAllStandaloneRulesDefinitions().getRulesByKey().values();

    assertThat(allRules).extracting(RuleDefinitionDto::getLanguage).containsOnly(Language.PYTHON);
  }

  @Test
  void it_should_not_contain_rule_templates() {
    backend = newBackend()
      .withStorageRoot(storageDir)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build();

    var allRules = listAllStandaloneRulesDefinitions().getRulesByKey().values();

    assertThat(allRules).extracting(RuleDefinitionDto::getKey).doesNotContain("python:XPath");
    assertThat(backend.getRulesService().getStandaloneRuleDescription(new GetStandaloneRuleDescriptionParams("python:XPath"))).failsWithin(1, TimeUnit.MINUTES);
  }

  @Test
  void it_should_not_contain_hotspots_by_default() {
    backend = newBackend()
      .withStorageRoot(storageDir)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build();

    var allRules = listAllStandaloneRulesDefinitions().getRulesByKey().values();

    assertThat(allRules).extracting(RuleDefinitionDto::getKey).doesNotContain("java:S1313");
    assertThat(backend.getRulesService().getStandaloneRuleDescription(new GetStandaloneRuleDescriptionParams("java:S1313"))).failsWithin(1, TimeUnit.MINUTES);
  }

  private ListAllStandaloneRulesDefinitionsResponse listAllStandaloneRulesDefinitions() {
    try {
      return this.backend.getRulesService().listAllStandaloneRulesDefinitions().get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @TempDir
  Path storageDir;
  private SonarLintBackendImpl backend;

}
