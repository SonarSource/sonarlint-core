/*
 * SonarLint Core - Rule Extractor
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
package mediumtests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractor;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleParamType;

import static java.util.Optional.empty;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class RuleExtractorMediumTests {

  private static final int COMMERCIAL_RULE_TEMPLATES_COUNT = 11;
  private static final int NON_COMMERCIAL_RULE_TEMPLATES_COUNT = 16;
  private static final int COMMERCIAL_SECURITY_HOTSPOTS_COUNT = 9;
  private static final int NON_COMMERCIAL_SECURITY_HOTSPOTS_COUNT = 79;
  private static final int ALL_RULES_COUNT_WITHOUT_COMMERCIAL = 1199;
  private static final int ALL_RULES_COUNT_WITH_COMMERCIAL = 2863;
  // commercial plugins might not be available
  // (if you pass -Dcommercial to maven, a profile will be activated that downloads the commercial plugins)
  private static final boolean COMMERCIAL_ENABLED = System.getProperty("commercial") != null;
  private static Set<Path> allJars;

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @BeforeAll
  static void prepare() throws IOException {
    var dir = Paths.get("target/plugins/");
    allJars = Files.list(dir)
      .filter(x -> x.getFileName().toString().endsWith(".jar"))
      .collect(toSet());
  }

  @Test
  void extractAllRules() {
    var enabledLanguages = Set.of(Language.values());
    var config = new PluginsLoader.Configuration(allJars, enabledLanguages, empty());
    var result = new PluginsLoader().load(config);

    var allRules = new RulesDefinitionExtractor().extractRules(result.getLoadedPlugins().getPluginInstancesByKeys(), enabledLanguages, false, false);
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

    var pythonRule = allRules.stream().filter(r -> r.getKey().equals("python:S139")).findFirst();
    assertThat(pythonRule).hasValueSatisfying(rule -> {
      assertThat(rule.getKey()).isEqualTo("python:S139");
      assertThat(rule.getType()).isEqualTo(RuleType.CODE_SMELL);
      assertThat(rule.getDefaultSeverity()).isEqualTo(IssueSeverity.MINOR);
      assertThat(rule.getLanguage()).isEqualTo(Language.PYTHON);
      assertThat(rule.getName()).isEqualTo("Comments should not be located at the end of lines of code");
      assertThat(rule.isActiveByDefault()).isFalse();
      assertThat(rule.getParams())
        .hasSize(1)
        .hasEntrySatisfying("legalTrailingCommentPattern", param -> {
          assertThat(param.defaultValue()).isEqualTo("^#\\s*+[^\\s]++$");
          assertThat(param.description()).isNull();
          assertThat(param.key()).isEqualTo("legalTrailingCommentPattern");
          assertThat(param.multiple()).isFalse();
          assertThat(param.name()).isEqualTo("legalTrailingCommentPattern");
          assertThat(param.possibleValues()).isEmpty();
          assertThat(param.type()).isEqualTo(SonarLintRuleParamType.STRING);
        });
      assertThat(rule.getDefaultParams()).containsOnly(entry("legalTrailingCommentPattern", "^#\\s*+[^\\s]++$"));
      assertThat(rule.getDeprecatedKeys()).isEmpty();
      assertThat(rule.getHtmlDescription()).contains("<p>This rule verifies that single-line comments are not located");
      assertThat(rule.getTags()).containsOnly("convention");
      assertThat(rule.getInternalKey()).isEmpty();
    });

    var ruleWithInternalKey = allRules.stream().filter(r -> r.getKey().equals("squid:ModifiersOrderCheck")).findFirst();
    assertThat(ruleWithInternalKey).isNotEmpty();
    assertThat(ruleWithInternalKey.get().getInternalKey()).contains("S1124");
  }

  @Test
  void extractAllRules_include_rule_templates() throws Exception {
    var enabledLanguages = Set.of(Language.values());
    var config = new PluginsLoader.Configuration(allJars, enabledLanguages, empty());
    var result = new PluginsLoader().load(config);

    var allRules = new RulesDefinitionExtractor().extractRules(result.getLoadedPlugins().getPluginInstancesByKeys(), enabledLanguages, true, false);
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

  @Test
  void extractAllRules_include_security_hotspots() throws Exception {
    var enabledLanguages = Set.of(Language.values());
    var config = new PluginsLoader.Configuration(allJars, enabledLanguages, empty());
    var result = new PluginsLoader().load(config);

    var allRules = new RulesDefinitionExtractor().extractRules(result.getLoadedPlugins().getPluginInstancesByKeys(), enabledLanguages, false, true);
    if (COMMERCIAL_ENABLED) {
      assertThat(allJars).hasSize(19);
      assertThat(allRules).hasSize(ALL_RULES_COUNT_WITH_COMMERCIAL + NON_COMMERCIAL_SECURITY_HOTSPOTS_COUNT + COMMERCIAL_SECURITY_HOTSPOTS_COUNT);
      assertThat(logTester.logs(ClientLogOutput.Level.WARN)).containsExactlyInAnyOrder(
        "Plugin 'rpg' embeds dependencies. This will be deprecated soon. Plugin should be updated.",
        "Plugin 'cobol' embeds dependencies. This will be deprecated soon. Plugin should be updated.",
        "Plugin 'swift' embeds dependencies. This will be deprecated soon. Plugin should be updated.");
    } else {
      assertThat(allJars).hasSize(10);
      assertThat(allRules).hasSize(ALL_RULES_COUNT_WITHOUT_COMMERCIAL + NON_COMMERCIAL_SECURITY_HOTSPOTS_COUNT);
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
    var config = new PluginsLoader.Configuration(allJars, enabledLanguages, empty());
    var result = new PluginsLoader().load(config);

    var allRules = new RulesDefinitionExtractor().extractRules(result.getLoadedPlugins().getPluginInstancesByKeys(), enabledLanguages, false, false);

    assertThat(allRules.stream().map(SonarLintRuleDefinition::getLanguage)).hasSameElementsAs(enabledLanguages);

  }

  @Test
  void loadNoRuleIfThereIsNoPlugin() {
    var enabledLanguages = Set.of(Language.values());
    var config = new PluginsLoader.Configuration(Set.of(), enabledLanguages, empty());
    var result = new PluginsLoader().load(config);
    var allRules = new RulesDefinitionExtractor().extractRules(result.getLoadedPlugins().getPluginInstancesByKeys(), enabledLanguages, false, false);

    assertThat(allRules).isEmpty();
  }

}
