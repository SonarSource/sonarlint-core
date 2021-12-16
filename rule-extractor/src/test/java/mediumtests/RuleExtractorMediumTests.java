/*
 * SonarLint Core - Rule Extractor
 * Copyright (C) 2016-2021 SonarSource SA
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
package mediumtests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginLocation;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractor;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

import static java.util.Optional.empty;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

class RuleExtractorMediumTests {

  private static final int COMMERCIAL_RULE_TEMPLATES_COUNT = 11;
  private static final int NON_COMMERCIAL_RULE_TEMPLATES_COUNT = 16;
  private static final int ALL_RULES_COUNT_WITHOUT_COMMERCIAL = 1199;
  private static final int ALL_RULES_COUNT_WITH_COMMERCIAL = 2863;
  // commercial plugins might not be available
  // (if you pass -Dcommercial to maven, a profile will be activated that downloads the commercial plugins)
  private static final boolean COMMERCIAL_ENABLED = System.getProperty("commercial") != null;
  private static Set<Path> allJars;

  @RegisterExtension
  public SonarLintLogTester logTester = new SonarLintLogTester();
  private static List<PluginLocation> pluginJarLocations;

  @BeforeAll
  public static void prepare() throws IOException {
    Path dir = Paths.get("target/plugins/");
    allJars = Files.list(dir)
      .filter(x -> x.getFileName().toString().endsWith(".jar"))
      .collect(toSet());
    pluginJarLocations = allJars.stream().map(p -> new PluginLocation(p)).collect(Collectors.toList());
  }

  @Test
  void extractAllRules() throws Exception {
    Set<Language> enabledLanguages = Set.of(Language.values());
    PluginInstancesRepository.Configuration config = new PluginInstancesRepository.Configuration(pluginJarLocations, enabledLanguages, empty());
    try (PluginInstancesRepository pluginInstancesRepository = new PluginInstancesRepository(config)) {

      List<SonarLintRuleDefinition> allRules = new RulesDefinitionExtractor().extractRules(pluginInstancesRepository, enabledLanguages, false);
      if (COMMERCIAL_ENABLED) {
        assertThat(allJars).hasSize(19);
        assertThat(allRules).hasSize(ALL_RULES_COUNT_WITH_COMMERCIAL);
        assertThat(logTester.logs(ClientLogOutput.Level.WARN)).containsExactlyInAnyOrder(
          "Plugin 'rpg' embeds dependencies. This will be deprecated soon. Plugin should be updated.",
          "Plugin 'cobol' embeds dependencies. This will be deprecated soon. Plugin should be updated.",
          "Plugin 'swift' embeds dependencies. This will be deprecated soon. Plugin should be updated.");
      } else {
        assertThat(allJars).hasSize(10);
        assertThat(allRules).hasSize(ALL_RULES_COUNT_WITHOUT_COMMERCIAL);
      }
    }
  }

  @Test
  void extractAllRules_include_rule_templates() throws Exception {
    Set<Language> enabledLanguages = Set.of(Language.values());
    PluginInstancesRepository.Configuration config = new PluginInstancesRepository.Configuration(pluginJarLocations, enabledLanguages, empty());
    try (PluginInstancesRepository pluginInstancesRepository = new PluginInstancesRepository(config)) {

      List<SonarLintRuleDefinition> allRules = new RulesDefinitionExtractor().extractRules(pluginInstancesRepository, enabledLanguages, true);
      if (COMMERCIAL_ENABLED) {
        assertThat(allJars).hasSize(19);
        assertThat(allRules).hasSize(ALL_RULES_COUNT_WITH_COMMERCIAL + NON_COMMERCIAL_RULE_TEMPLATES_COUNT + COMMERCIAL_RULE_TEMPLATES_COUNT);
        assertThat(logTester.logs(ClientLogOutput.Level.WARN)).containsExactlyInAnyOrder(
          "Plugin 'rpg' embeds dependencies. This will be deprecated soon. Plugin should be updated.",
          "Plugin 'cobol' embeds dependencies. This will be deprecated soon. Plugin should be updated.",
          "Plugin 'swift' embeds dependencies. This will be deprecated soon. Plugin should be updated.");
      } else {
        assertThat(allJars).hasSize(10);
        assertThat(allRules).hasSize(ALL_RULES_COUNT_WITHOUT_COMMERCIAL + NON_COMMERCIAL_RULE_TEMPLATES_COUNT);
      }
    }
  }

  @Test
  void onlyLoadRulesOfEnabledLanguages() throws Exception {
    Set<Language> enabledLanguages = EnumSet.of(
      Language.JAVA,
      // Enable JS but not TS
      Language.JS,
      Language.PHP,
      Language.PYTHON);

    if (COMMERCIAL_ENABLED) {
      // Enable C but not C++
      enabledLanguages.add(Language.C);
    }
    PluginInstancesRepository.Configuration config = new PluginInstancesRepository.Configuration(pluginJarLocations, enabledLanguages, empty());
    try (PluginInstancesRepository pluginInstancesRepository = new PluginInstancesRepository(config)) {

      List<SonarLintRuleDefinition> allRules = new RulesDefinitionExtractor().extractRules(pluginInstancesRepository, enabledLanguages, false);

      assertThat(allRules.stream().map(SonarLintRuleDefinition::getLanguage)).hasSameElementsAs(enabledLanguages);
    }
  }

}
