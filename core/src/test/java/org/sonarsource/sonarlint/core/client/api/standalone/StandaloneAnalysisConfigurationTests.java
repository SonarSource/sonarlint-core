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
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import testutils.TestClientInputFile;

import static java.nio.file.Files.createDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class StandaloneAnalysisConfigurationTests {

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
    Collection<RuleKey> excludedRules = Arrays.asList(new RuleKey("squid", "S1135"), new RuleKey("squid", "S1181"));
    Collection<RuleKey> includedRules = Arrays.asList(new RuleKey("javascript", "S2424"), new RuleKey("javascript", "S1442"));
    Map<String, String> squidS5Parameters = new HashMap<>();
    squidS5Parameters.put("s5param1", "s5value1");
    squidS5Parameters.put("s5param2", "s5value2");
    Map<String, String> squidS6Parameters = new HashMap<>();
    squidS6Parameters.put("s6param1", "s6value1");
    squidS6Parameters.put("s6param2", "s6value2");
    Map<RuleKey, Map<String, String>> ruleParameters = new HashMap<>();
    ruleParameters.put(RuleKey.parse("squid:S6"), squidS6Parameters);
    var config = StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .addInputFile(inputFile)
      .addInputFiles(inputFileWithLanguage)
      .addInputFiles(Arrays.asList(testInputFile))
      .putAllExtraProperties(props)
      .putExtraProperty("sonar.foo", "bar")
      .addExcludedRules(excludedRules)
      .addExcludedRule(RuleKey.parse("squid:S1"))
      .addExcludedRules(RuleKey.parse("squid:S2"), RuleKey.parse("squid:S3"))
      .addIncludedRules(includedRules)
      .addIncludedRule(RuleKey.parse("squid:I1"))
      .addIncludedRules(RuleKey.parse("squid:I2"), RuleKey.parse("squid:I3"))
      .addRuleParameter(RuleKey.parse("squid:S4"), "param1", "value1")
      .addRuleParameters(RuleKey.parse("squid:S5"), squidS5Parameters)
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
    assertThat(config.excludedRules()).extracting(RuleKey::toString).containsExactly("squid:S1135", "squid:S1181", "squid:S1", "squid:S2", "squid:S3");
    assertThat(config.includedRules()).extracting(RuleKey::toString).containsExactly("javascript:S2424", "javascript:S1442", "squid:I1", "squid:I2", "squid:I3");
    assertThat(config.ruleParameters()).containsKeys(RuleKey.parse("squid:S4"), RuleKey.parse("squid:S5"), RuleKey.parse("squid:S6"));
  }
}
