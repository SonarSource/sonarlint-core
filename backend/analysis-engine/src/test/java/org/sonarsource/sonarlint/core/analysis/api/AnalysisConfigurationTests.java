/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import testutils.TestClientInputFile;

import static java.nio.file.Files.createDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class AnalysisConfigurationTests {

  @AfterEach
  void init_property() {
    System.clearProperty("sonarlint.debug.active.rules");
  }

  @Test
  void testToString_and_getters(@TempDir Path temp) throws Exception {
    Map<String, String> props = new HashMap<>();
    props.put("sonar.java.libraries", "foo bar");

    final var srcFile1 = createDirectory(temp.resolve("src1"));
    final var srcFile2 = createDirectory(temp.resolve("src2"));
    final var srcFile3 = createDirectory(temp.resolve("src3"));
    ClientInputFile inputFile = new TestClientInputFile(temp, srcFile1, false, StandardCharsets.UTF_8, null);
    ClientInputFile inputFileWithLanguage = new TestClientInputFile(temp, srcFile2, false, StandardCharsets.UTF_8, SonarLanguage.JAVA);
    ClientInputFile testInputFile = new TestClientInputFile(temp, srcFile3, true, null, SonarLanguage.PHP);
    var baseDir = createDirectory(temp.resolve("baseDir"));
    var activeRuleWithParams = newActiveRule("php:S123", Map.of("param1", "value1"));
    var config = AnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .addInputFile(inputFile)
      .addInputFiles(inputFileWithLanguage)
      .addInputFiles(List.of(testInputFile))
      .putAllExtraProperties(props)
      .putExtraProperty("sonar.foo", "bar")
      .addActiveRules(List.of(newActiveRule("java:S123"), newActiveRule("java:S456")))
      .addActiveRule(activeRuleWithParams)
      .addActiveRules(newActiveRule("python:S123"), newActiveRule("python:S456"))
      .build();
    assertThat(config).hasToString("[\n" +
      "  baseDir: " + baseDir + "\n" +
      "  extraProperties: {sonar.java.libraries=foo bar, sonar.foo=bar}\n" +
      "  activeRules: [2 python, 2 java, 1 php]\n" +
      "  inputFiles: [\n" +
      "    " + srcFile1.toUri() + " (UTF-8)\n" +
      "    " + srcFile2.toUri() + " (UTF-8) [java]\n" +
      "    " + srcFile3.toUri() + " (default) [test] [php]\n" +
      "  ]\n" +
      "]\n");
    assertThat(config.baseDir()).isEqualTo(baseDir);
    assertThat(config.inputFiles()).containsExactly(inputFile, inputFileWithLanguage, testInputFile);
    assertThat(config.extraProperties()).containsExactly(entry("sonar.java.libraries", "foo bar"), entry("sonar.foo", "bar"));
    assertThat(config.activeRules()).extracting(ActiveRule::ruleKey).map(RuleKey::toString).containsExactly("java:S123", "java:S456", "php:S123", "python:S123", "python:S456");
  }

  @Test
  void testToString_and_getters_when_empty() {
    var config = AnalysisConfiguration.builder().build();
    assertThat(config).hasToString("""
      [
        baseDir: null
        extraProperties: {}
        activeRules: []
        inputFiles: [
        ]
      ]
      """);
    assertThat(config.baseDir()).isNull();
    assertThat(config.inputFiles()).isEmpty();
    assertThat(config.activeRules()).isEmpty();
  }

  @Test
  void testToString_and_getters_when_active_rules_verbose() {
    System.setProperty("sonarlint.debug.active.rules", "true");

    var activeRuleWithParams = newActiveRule("php:S123", Map.of("param1", "value1"));
    var config = AnalysisConfiguration.builder()
      .addActiveRules(List.of(newActiveRule("java:S123"), newActiveRule("java:S456")))
      .addActiveRules(activeRuleWithParams)
      .addActiveRules(newActiveRule("python:S123"), newActiveRule("python:S456"))
      .build();
    assertThat(config).hasToString("""
      [
        baseDir: null
        extraProperties: {}
        activeRules: [java:S123, java:S456, php:S123{param1=value1}, python:S123, python:S456]
        inputFiles: [
        ]
      ]
      """);
    assertThat(config.baseDir()).isNull();
    assertThat(config.inputFiles()).isEmpty();
    assertThat(config.activeRules()).extracting(ActiveRule::ruleKey).map(RuleKey::toString).containsExactly("java:S123", "java:S456", "php:S123", "python:S123", "python:S456");
  }

  @Test
  void testToString_and_getters_when_active_rules_not_verbose() {
    var activeRuleWithParams = newActiveRule("php:S123", Map.of("param1", "value1"));
    var config = AnalysisConfiguration.builder()
      .addActiveRules(List.of(newActiveRule("java:S123"), newActiveRule("java:S456")))
      .addActiveRules(activeRuleWithParams)
      .addActiveRules(newActiveRule("python:S123"), newActiveRule("python:S456"))
      .build();
    assertThat(config).hasToString("""
      [
        baseDir: null
        extraProperties: {}
        activeRules: [2 python, 2 java, 1 php]
        inputFiles: [
        ]
      ]
      """);
    assertThat(config.baseDir()).isNull();
    assertThat(config.inputFiles()).isEmpty();
    assertThat(config.activeRules()).extracting(ActiveRule::ruleKey).map(RuleKey::toString).containsExactly("java:S123", "java:S456", "php:S123", "python:S123", "python:S456");
  }

  private static ActiveRule newActiveRule(String ruleKey) {
    return newActiveRule(ruleKey, Map.of());
  }

  private static ActiveRule newActiveRule(String ruleKey, Map<String, String> params) {
    return new ActiveRule() {

      @Override
      public RuleKey ruleKey() {
        return RuleKey.parse(ruleKey);
      }

      @Override
      public String severity() {
        return "";
      }

      @Override
      public String language() {
        return "";
      }

      @CheckForNull
      @Override
      public String param(String key) {
        return params().get(key);
      }

      @Override
      public Map<String, String> params() {
        return params;
      }

      @CheckForNull
      @Override
      public String internalKey() {
        return "";
      }

      @CheckForNull
      @Override
      public String templateRuleKey() {
        return "";
      }

      @Override
      public String qpKey() {
        return "";
      }
    };
  }

}
