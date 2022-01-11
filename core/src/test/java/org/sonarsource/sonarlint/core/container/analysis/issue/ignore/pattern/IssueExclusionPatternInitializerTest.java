/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.log.LogTester;
import org.sonarsource.sonarlint.core.container.analysis.ServerConfigurationProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class IssueExclusionPatternInitializerTest {

  @Rule
  public LogTester logTester = new LogTester();

  @Test
  public void testNoConfiguration() {
    IssueExclusionPatternInitializer patternsInitializer = new IssueExclusionPatternInitializer(new ServerConfigurationProvider(Collections.emptyMap()));
    assertThat(patternsInitializer.hasConfiguredPatterns()).isFalse();
    assertThat(patternsInitializer.getMulticriteriaPatterns().size()).isEqualTo(0);
  }

  @Test
  public void shouldLogInvalidResourceKey() {
    Map<String, String> settings = new HashMap<>();
    settings.put("sonar.issue.ignore" + ".multicriteria", "1");
    settings.put("sonar.issue.ignore" + ".multicriteria" + ".1." + "resourceKey", "");
    settings.put("sonar.issue.ignore" + ".multicriteria" + ".1." + "ruleKey", "*");
    new IssueExclusionPatternInitializer(new ServerConfigurationProvider(settings));

    assertThat(logTester.logs()).containsExactly("Issue exclusions are misconfigured. File pattern is mandatory for each entry of 'sonar.issue.ignore.multicriteria'");
  }

  @Test
  public void shouldLogInvalidRuleKey() {
    Map<String, String> settings = new HashMap<>();
    settings.put("sonar.issue.ignore" + ".multicriteria", "1");
    settings.put("sonar.issue.ignore" + ".multicriteria" + ".1." + "resourceKey", "*");
    settings.put("sonar.issue.ignore" + ".multicriteria" + ".1." + "ruleKey", "");
    new IssueExclusionPatternInitializer(new ServerConfigurationProvider(settings));

    assertThat(logTester.logs()).containsExactly("Issue exclusions are misconfigured. Rule key pattern is mandatory for each entry of 'sonar.issue.ignore.multicriteria'");
  }

  @Test
  public void shouldReturnBlockPattern() {
    Map<String, String> settings = new HashMap<>();
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY, "1,2,3");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".1." + IssueExclusionPatternInitializer.BEGIN_BLOCK_REGEXP, "// SONAR-OFF");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".1." + IssueExclusionPatternInitializer.END_BLOCK_REGEXP, "// SONAR-ON");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".2." + IssueExclusionPatternInitializer.BEGIN_BLOCK_REGEXP, "// FOO-OFF");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".2." + IssueExclusionPatternInitializer.END_BLOCK_REGEXP, "// FOO-ON");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".3." + IssueExclusionPatternInitializer.BEGIN_BLOCK_REGEXP, "// IGNORE-TO-EOF");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".3." + IssueExclusionPatternInitializer.END_BLOCK_REGEXP, "");
    IssueExclusionPatternInitializer patternsInitializer = new IssueExclusionPatternInitializer(new ServerConfigurationProvider(settings));

    assertThat(patternsInitializer.hasConfiguredPatterns()).isTrue();
    assertThat(patternsInitializer.hasFileContentPattern()).isTrue();
    assertThat(patternsInitializer.hasMulticriteriaPatterns()).isFalse();
    assertThat(patternsInitializer.getMulticriteriaPatterns().size()).isEqualTo(0);
    assertThat(patternsInitializer.getBlockPatterns().size()).isEqualTo(3);
    assertThat(patternsInitializer.getAllFilePatterns().size()).isEqualTo(0);
  }

  @Test
  public void shouldLogInvalidStartBlockPattern() {
    Map<String, String> settings = new HashMap<>();
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY, "1");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".1." + IssueExclusionPatternInitializer.BEGIN_BLOCK_REGEXP, "");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_BLOCK_KEY + ".1." + IssueExclusionPatternInitializer.END_BLOCK_REGEXP, "// SONAR-ON");
    new IssueExclusionPatternInitializer(new ServerConfigurationProvider(settings));

    assertThat(logTester.logs()).containsExactly("Issue exclusions are misconfigured. Start block regexp is mandatory for each entry of 'sonar.issue.ignore.block'");
  }

  @Test
  public void shouldReturnAllFilePattern() {
    Map<String, String> settings = new HashMap<>();
    settings.put(IssueExclusionPatternInitializer.PATTERNS_ALLFILE_KEY, "1,2");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_ALLFILE_KEY + ".1." + IssueExclusionPatternInitializer.FILE_REGEXP, "@SONAR-IGNORE-ALL");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_ALLFILE_KEY + ".2." + IssueExclusionPatternInitializer.FILE_REGEXP, "//FOO-IGNORE-ALL");
    IssueExclusionPatternInitializer patternsInitializer = new IssueExclusionPatternInitializer(new ServerConfigurationProvider(settings));

    assertThat(patternsInitializer.hasConfiguredPatterns()).isTrue();
    assertThat(patternsInitializer.hasFileContentPattern()).isTrue();
    assertThat(patternsInitializer.hasMulticriteriaPatterns()).isFalse();
    assertThat(patternsInitializer.getMulticriteriaPatterns().size()).isEqualTo(0);
    assertThat(patternsInitializer.getBlockPatterns().size()).isEqualTo(0);
    assertThat(patternsInitializer.getAllFilePatterns().size()).isEqualTo(2);
  }

  @Test
  public void shouldLogInvalidAllFilePattern() {
    Map<String, String> settings = new HashMap<>();
    settings.put(IssueExclusionPatternInitializer.PATTERNS_ALLFILE_KEY, "1");
    settings.put(IssueExclusionPatternInitializer.PATTERNS_ALLFILE_KEY + ".1." + IssueExclusionPatternInitializer.FILE_REGEXP, "");
    new IssueExclusionPatternInitializer(new ServerConfigurationProvider(settings));

    assertThat(logTester.logs()).containsExactly("Issue exclusions are misconfigured. Remove blank entries from 'sonar.issue.ignore.allfile'");
  }
}
