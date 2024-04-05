/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.pattern;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.analysis.sonarapi.MapSettings;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;

class IssueExclusionPatternInitializerTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void testNoConfiguration() {
    var patternsInitializer = new IssueExclusionPatternInitializer(new MapSettings(Map.of()).asConfig());
    assertThat(patternsInitializer.hasConfiguredPatterns()).isFalse();
    assertThat(patternsInitializer.getMulticriteriaPatterns()).isEmpty();
  }

  @Test
  void shouldLogInvalidResourceKey() {
    Map<String, String> settings = new HashMap<>();
    settings.put("sonar.issue.ignore" + ".multicriteria", "1");
    settings.put("sonar.issue.ignore" + ".multicriteria" + ".1." + "resourceKey", "");
    settings.put("sonar.issue.ignore" + ".multicriteria" + ".1." + "ruleKey", "*");
    new IssueExclusionPatternInitializer(new MapSettings(settings).asConfig());

    assertThat(logTester.logs()).containsExactly("Issue exclusions are misconfigured. File pattern is mandatory for each entry of 'sonar.issue.ignore.multicriteria'");
  }

  @Test
  void shouldLogInvalidRuleKey() {
    Map<String, String> settings = new HashMap<>();
    settings.put("sonar.issue.ignore" + ".multicriteria", "1");
    settings.put("sonar.issue.ignore" + ".multicriteria" + ".1." + "resourceKey", "*");
    settings.put("sonar.issue.ignore" + ".multicriteria" + ".1." + "ruleKey", "");
    new IssueExclusionPatternInitializer(new MapSettings(settings).asConfig());

    assertThat(logTester.logs()).containsExactly("Issue exclusions are misconfigured. Rule key pattern is mandatory for each entry of 'sonar.issue.ignore.multicriteria'");
  }

  @Test
  void shouldReturnBlockPattern() {
    Map<String, String> settings = new HashMap<>();
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY, "1,2,3");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".1." + IssueExclusionPatternInitializer.BEGIN_BLOCK_REGEXP, "// SONAR-OFF");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".1." + IssueExclusionPatternInitializer.END_BLOCK_REGEXP, "// SONAR-ON");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".2." + IssueExclusionPatternInitializer.BEGIN_BLOCK_REGEXP, "// FOO-OFF");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".2." + IssueExclusionPatternInitializer.END_BLOCK_REGEXP, "// FOO-ON");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".3." + IssueExclusionPatternInitializer.BEGIN_BLOCK_REGEXP, "// IGNORE-TO-EOF");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".3." + IssueExclusionPatternInitializer.END_BLOCK_REGEXP, "");
    var patternsInitializer = new IssueExclusionPatternInitializer(new MapSettings(settings).asConfig());

    assertThat(patternsInitializer.hasConfiguredPatterns()).isTrue();
    assertThat(patternsInitializer.hasFileContentPattern()).isTrue();
    assertThat(patternsInitializer.hasMulticriteriaPatterns()).isFalse();
    assertThat(patternsInitializer.getMulticriteriaPatterns()).isEmpty();
    assertThat(patternsInitializer.getBlockPatterns().size()).isEqualTo(3);
    assertThat(patternsInitializer.getAllFilePatterns()).isEmpty();
  }

  @Test
  void shouldLogInvalidStartBlockPattern() {
    Map<String, String> settings = new HashMap<>();
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY, "1");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".1." + IssueExclusionPatternInitializer.BEGIN_BLOCK_REGEXP, "");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".1." + IssueExclusionPatternInitializer.END_BLOCK_REGEXP, "// SONAR-ON");
    new IssueExclusionPatternInitializer(new MapSettings(settings).asConfig());

    assertThat(logTester.logs()).containsExactly("Issue exclusions are misconfigured. Start block regexp is mandatory for each entry of 'sonar.issue.ignore.block'");
  }

  @Test
  void shouldReturnAllFilePattern() {
    Map<String, String> settings = new HashMap<>();
    settings.put(IssueExclusionPatternInitializer.PATTERNS_ALLFILE_KEY, "1,2");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_ALLFILE_KEY + ".1." + IssueExclusionPatternInitializer.FILE_REGEXP, "@SONAR-IGNORE-ALL");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_ALLFILE_KEY + ".2." + IssueExclusionPatternInitializer.FILE_REGEXP, "//FOO-IGNORE-ALL");
    var patternsInitializer = new IssueExclusionPatternInitializer(new MapSettings(settings).asConfig());

    assertThat(patternsInitializer.hasConfiguredPatterns()).isTrue();
    assertThat(patternsInitializer.hasFileContentPattern()).isTrue();
    assertThat(patternsInitializer.hasMulticriteriaPatterns()).isFalse();
    assertThat(patternsInitializer.getMulticriteriaPatterns()).isEmpty();
    assertThat(patternsInitializer.getBlockPatterns()).isEmpty();
    assertThat(patternsInitializer.getAllFilePatterns().size()).isEqualTo(2);
  }

  @Test
  void shouldLogInvalidAllFilePattern() {
    Map<String, String> settings = new HashMap<>();
    settings.put(IssueExclusionPatternInitializer.PATTERNS_ALLFILE_KEY, "1");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_ALLFILE_KEY + ".1." + IssueExclusionPatternInitializer.FILE_REGEXP, "");
    new IssueExclusionPatternInitializer(new MapSettings(settings).asConfig());

    assertThat(logTester.logs()).containsExactly("Issue exclusions are misconfigured. Remove blank entries from 'sonar.issue.ignore.allfile'");
  }
}
