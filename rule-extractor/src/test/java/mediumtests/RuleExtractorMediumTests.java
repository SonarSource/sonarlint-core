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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.plugin.common.Language;
import org.sonarsource.sonarlint.core.rule.extractor.RulesDefinitionExtractor;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

class RuleExtractorMediumTests {

  // commercial plugins might not be available
  // (if you pass -Dcommercial to maven, a profile will be activated that downloads the commercial plugins)
  private static final boolean COMMERCIAL_ENABLED = System.getProperty("commercial") != null;

  @Test
  void extractAllRules() throws Exception {
    Path dir = Paths.get("target/plugins/");
    Set<Path> allJars = Files.list(dir)
      .filter(x -> x.getFileName().toString().endsWith(".jar"))
      .collect(toSet());

    if (COMMERCIAL_ENABLED) {
      assertThat(allJars).hasSize(19);
    } else {
      assertThat(allJars).hasSize(10);
    }

    List<SonarLintRuleDefinition> allRules = new RulesDefinitionExtractor().extractRules(allJars, Set.of(Language.values()));

    assertThat(allRules).hasSize(707);
  }

}
