/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2019 SonarSource SA
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.client.api.TestClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class StandaloneAnalysisConfigurationTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testToString_and_getters() throws Exception {
    Map<String, String> props = new HashMap<>();
    props.put("sonar.java.libraries", "foo bar");

    final Path srcFile1 = temp.newFile().toPath();
    final Path srcFile2 = temp.newFile().toPath();
    final Path srcFile3 = temp.newFile().toPath();
    ClientInputFile inputFile = new TestClientInputFile(temp.getRoot().toPath(), srcFile1, false, StandardCharsets.UTF_8, null);
    ClientInputFile inputFileWithLanguage = new TestClientInputFile(temp.getRoot().toPath(), srcFile2, false, StandardCharsets.UTF_8, "java");
    ClientInputFile testInputFile = new TestClientInputFile(temp.getRoot().toPath(), srcFile3, true, null, "php");
    Path baseDir = temp.newFolder().toPath();
    Collection<RuleKey> excludedRules = Arrays.asList(new RuleKey("squid", "S1135"), new RuleKey("squid", "S1181"));
    Collection<RuleKey> includedRules = Arrays.asList(new RuleKey("javascript", "S2424"), new RuleKey("javascript", "S1442"));
    StandaloneAnalysisConfiguration config = StandaloneAnalysisConfiguration.builder()
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
      .build();
    assertThat(config.toString()).isEqualTo("[\n" +
      "  baseDir: " + baseDir.toString() + "\n" +
      "  extraProperties: {sonar.java.libraries=foo bar, sonar.foo=bar}\n" +
      "  excludedRules: [squid:S1135, squid:S1181, squid:S1, squid:S2, squid:S3]\n" +
      "  includedRules: [javascript:S2424, javascript:S1442, squid:I1, squid:I2, squid:I3]\n" +
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
  }
}
