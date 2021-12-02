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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonarsource.sonarlint.core.plugin.common.Language;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractor;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

class RuleExtractorMediumTests {

  // commercial plugins might not be available
  // (if you pass -Dcommercial to maven, a profile will be activated that downloads the commercial plugins)
  private static final boolean COMMERCIAL_ENABLED = System.getProperty("commercial") != null;
  private static Set<Path> allJars;

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  @BeforeAll
  public static void prepare() throws IOException {
    Path dir = Paths.get("target/plugins/");
    allJars = Files.list(dir)
      .filter(x -> x.getFileName().toString().endsWith(".jar"))
      .collect(toSet());
  }

  @Test
  void extractAllRules() throws Exception {
    List<SonarLintRuleDefinition> allRules = new RulesDefinitionExtractor().extractRules(allJars, Set.of(Language.values()));
    if (COMMERCIAL_ENABLED) {
      assertThat(allJars).hasSize(19);
      assertThat(allRules).hasSize(2863);
      assertThat(logTester.logs(LoggerLevel.WARN)).containsExactlyInAnyOrder(
        "Plugin 'rpg' embeds dependencies. This will be deprecated soon. Plugin should be updated.",
        "Plugin 'cobol' embeds dependencies. This will be deprecated soon. Plugin should be updated.",
        "Plugin 'swift' embeds dependencies. This will be deprecated soon. Plugin should be updated.");
    } else {
      assertThat(allJars).hasSize(10);
      assertThat(allRules).hasSize(1199);
    }
  }

  @Test
  void onlyLoadRulesOfEnabledLanguages() throws Exception {
    Set<Language> enabledLanguages = EnumSet.of(
      Language.JAVA,
      Language.JS,
      Language.PHP,
      Language.PYTHON,
      Language.TS);

    if (COMMERCIAL_ENABLED) {
      enabledLanguages.add(Language.C);
    }
    List<SonarLintRuleDefinition> allRules = new RulesDefinitionExtractor().extractRules(allJars, enabledLanguages);

    assertThat(allRules.stream().map(SonarLintRuleDefinition::getLanguage)).hasSameElementsAs(enabledLanguages);
  }

}
