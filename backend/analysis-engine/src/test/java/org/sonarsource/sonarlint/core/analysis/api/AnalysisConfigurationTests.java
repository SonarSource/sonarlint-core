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
package org.sonarsource.sonarlint.core.analysis.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.Language;
import testutils.TestClientInputFile;

import static java.nio.file.Files.createDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class AnalysisConfigurationTests {

  @Test
  void testToString_and_getters(@TempDir Path temp) throws Exception {
    Map<String, String> props = new HashMap<>();
    props.put("sonar.java.libraries", "foo bar");

    final var srcFile1 = createDirectory(temp.resolve("src1"));
    final var srcFile2 = createDirectory(temp.resolve("src2"));
    final var srcFile3 = createDirectory(temp.resolve("src3"));
    ClientInputFile inputFile = new TestClientInputFile(temp, srcFile1, false, StandardCharsets.UTF_8, null);
    ClientInputFile inputFileWithLanguage = new TestClientInputFile(temp, srcFile2, false, StandardCharsets.UTF_8, Language.JAVA);
    ClientInputFile testInputFile = new TestClientInputFile(temp, srcFile3, true, null, Language.PHP);
    var baseDir = createDirectory(temp.resolve("baseDir"));
    var activeRuleWithParams = new ActiveRule("php:S123", null);
    activeRuleWithParams.setParams(Map.of("param1", "value1"));
    var config = AnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .addInputFile(inputFile)
      .addInputFiles(inputFileWithLanguage)
      .addInputFiles(List.of(testInputFile))
      .putAllExtraProperties(props)
      .putExtraProperty("sonar.foo", "bar")
      .addActiveRules(List.of(new ActiveRule("java:S123", null), new ActiveRule("java:S456", null)))
      .addActiveRule(activeRuleWithParams)
      .addActiveRules(new ActiveRule("python:S123", null), new ActiveRule("python:S456", null))
      .build();
    assertThat(config).hasToString("[\n" +
      "  baseDir: " + baseDir + "\n" +
      "  extraProperties: {sonar.java.libraries=foo bar, sonar.foo=bar}\n" +
      "  activeRules: [java:S123, java:S456, php:S123{param1=value1}, python:S123, python:S456]\n" +
      "  inputFiles: [\n" +
      "    " + srcFile1.toUri() + " (UTF-8)\n" +
      "    " + srcFile2.toUri() + " (UTF-8) [java]\n" +
      "    " + srcFile3.toUri() + " (default) [test] [php]\n" +
      "  ]\n" +
      "]\n");
    assertThat(config.baseDir()).isEqualTo(baseDir);
    assertThat(config.inputFiles()).containsExactly(inputFile, inputFileWithLanguage, testInputFile);
    assertThat(config.extraProperties()).containsExactly(entry("sonar.java.libraries", "foo bar"), entry("sonar.foo", "bar"));
    assertThat(config.activeRules()).extracting(ActiveRule::getRuleKey).containsExactly("java:S123", "java:S456", "php:S123", "python:S123", "python:S456");
  }
}
