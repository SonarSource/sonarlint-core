/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.client.api.standalone;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.plugin.common.Language;
import testutils.TestClientInputFile;

import static java.nio.file.Files.createDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class StandaloneAnalysisConfigurationTests {

  @Test
  void testToString_and_getters(@TempDir Path temp) throws Exception {
    Map<String, String> props = new HashMap<>();
    props.put("sonar.java.libraries", "foo bar");

    final Path srcFile1 = createDirectory(temp.resolve("src1"));
    final Path srcFile2 = createDirectory(temp.resolve("src2"));
    final Path srcFile3 = createDirectory(temp.resolve("src3"));
    ClientInputFile inputFile = new TestClientInputFile(temp, srcFile1, false, StandardCharsets.UTF_8, null);
    ClientInputFile inputFileWithLanguage = new TestClientInputFile(temp, srcFile2, false, StandardCharsets.UTF_8, Language.JAVA);
    ClientInputFile testInputFile = new TestClientInputFile(temp, srcFile3, true, null, Language.PHP);
    Path baseDir = createDirectory(temp.resolve("baseDir"));
    Collection<String> excludedRules = Arrays.asList("squid:S1135", "squid:S1181");
    Collection<String> includedRules = Arrays.asList("javascript:S2424", "javascript:S1442");
    Map<String, String> squidS5Parameters = new HashMap<>();
    squidS5Parameters.put("s5param1", "s5value1");
    squidS5Parameters.put("s5param2", "s5value2");
    Map<String, String> squidS6Parameters = new HashMap<>();
    squidS6Parameters.put("s6param1", "s6value1");
    squidS6Parameters.put("s6param2", "s6value2");
    Map<String, Map<String, String>> ruleParameters = new HashMap<>();
    ruleParameters.put("squid:S6", squidS6Parameters);
    StandaloneAnalysisConfiguration config = StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .addInputFile(inputFile)
      .addInputFiles(inputFileWithLanguage)
      .addInputFiles(Arrays.asList(testInputFile))
      .putAllExtraProperties(props)
      .putExtraProperty("sonar.foo", "bar")
      .addExcludedRules(excludedRules)
      .addExcludedRule("squid:S1")
      .addExcludedRules("squid:S2", "squid:S3")
      .addIncludedRules(includedRules)
      .addIncludedRule("squid:I1")
      .addIncludedRules("squid:I2", "squid:I3")
      .addRuleParameter("squid:S4", "param1", "value1")
      .addRuleParameters("squid:S5", squidS5Parameters)
      .addRuleParameters(ruleParameters)
      .build();
    assertThat(config.toString()).isEqualTo("[\n" +
      "  baseDir: " + baseDir.toString() + "\n" +
      "  extraProperties: {sonar.java.libraries=foo bar, sonar.foo=bar}\n" +
      "  moduleKey: null\n" +
      "  excludedRules: [squid:S1135, squid:S1181, squid:S1, squid:S2, squid:S3]\n" +
      "  includedRules: [javascript:S2424, javascript:S1442, squid:I1, squid:I2, squid:I3]\n" +
      "  ruleParameters: {squid:S4={param1=value1}, squid:S5={s5param1=s5value1, s5param2=s5value2}, squid:S6={s6param1=s6value1, s6param2=s6value2}}\n" +
      "  inputFiles: [\n" +
      "    " + srcFile1.toUri().toString() + " (UTF-8)\n" +
      "    " + srcFile2.toUri().toString() + " (UTF-8) [java]\n" +
      "    " + srcFile3.toUri().toString() + " (default) [test] [php]\n" +
      "  ]\n" +
      "]\n");
    assertThat(config.baseDir()).isEqualTo(baseDir);
    assertThat(config.inputFiles()).containsExactly(inputFile, inputFileWithLanguage, testInputFile);
    assertThat(config.extraProperties()).containsExactly(entry("sonar.java.libraries", "foo bar"), entry("sonar.foo", "bar"));
    assertThat(config.excludedRules()).containsExactly("squid:S1135", "squid:S1181", "squid:S1", "squid:S2", "squid:S3");
    assertThat(config.includedRules()).containsExactly("javascript:S2424", "javascript:S1442", "squid:I1", "squid:I2", "squid:I3");
    assertThat(config.ruleParameters()).containsKeys("squid:S4", "squid:S5", "squid:S6");
  }
}
