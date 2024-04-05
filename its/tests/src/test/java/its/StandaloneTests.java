/*
 * SonarLint Core - ITs - Tests
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
package its;

import its.utils.TestClientInputFile;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParamType;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class StandaloneTests {

  private static StandaloneSonarLintEngineImpl sonarlint;
  private static List<String> logs;
  @TempDir
  private File baseDir;

  @BeforeAll
  static void prepare(@TempDir Path sonarlintUserHome) throws Exception {
    logs = new ArrayList<>();
    Map<String, String> globalProps = new HashMap<>();
    globalProps.put("sonar.global.label", "It works");
    var config = StandaloneGlobalConfiguration.builder()
      .addPlugin(Paths.get("../plugins/global-extension-plugin/target/global-extension-plugin.jar"))
      // The global-extension-plugin reuses the cobol plugin key to be whitelisted
      .addEnabledLanguage(Language.COBOL)
      .setSonarLintUserHome(sonarlintUserHome)
      .setLogOutput((msg, level) -> logs.add(msg))
      .setExtraProperties(globalProps).build();
    sonarlint = new StandaloneSonarLintEngineImpl(config);

    assertThat(logs).containsOnlyOnce("Start Global Extension It works");
  }

  @AfterAll
  static void stop() {
    sonarlint.stop();
    assertThat(logs).containsOnlyOnce("Stop Global Extension");
  }

  @Test
  void checkRuleParameterDeclarations() {
    var ruleDetails = sonarlint.getAllRuleDetails();
    assertThat(ruleDetails).hasSize(1);
    var incRule = ruleDetails.iterator().next();
    assertThat(incRule.paramDetails()).hasSize(8);
    assertRuleHasParam(incRule, "stringParam", StandaloneRuleParamType.STRING);
    assertRuleHasParam(incRule, "textParam", StandaloneRuleParamType.TEXT);
    assertRuleHasParam(incRule, "boolParam", StandaloneRuleParamType.BOOLEAN);
    assertRuleHasParam(incRule, "intParam", StandaloneRuleParamType.INTEGER);
    assertRuleHasParam(incRule, "floatParam", StandaloneRuleParamType.FLOAT);
    assertRuleHasParam(incRule, "enumParam", StandaloneRuleParamType.STRING, "enum1", "enum2", "enum3");
    assertRuleHasParam(incRule, "enumListParam", StandaloneRuleParamType.STRING, "list1", "list2", "list3");
    assertRuleHasParam(incRule, "multipleIntegersParam", StandaloneRuleParamType.INTEGER, "80", "120", "160");
  }

  private static void assertRuleHasParam(StandaloneRuleDetails rule, String paramKey, StandaloneRuleParamType expectedType,
    String... possibleValues) {
    var param = rule.paramDetails().stream().filter(p -> p.key().equals(paramKey)).findFirst();
    assertThat(param).isNotEmpty();
    assertThat(param.get())
      .extracting(StandaloneRuleParam::type, StandaloneRuleParam::possibleValues)
      .containsExactly(expectedType, Arrays.asList(possibleValues));
  }

  @Test
  void globalExtension() throws Exception {
    var inputFile = prepareInputFile("foo.glob", "foo", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .putExtraProperty("sonar.cobol.file.suffixes", "glob")
        .build(),
      issues::add, null, null);
    assertThat(issues).extracting("ruleKey", "inputFile.path", "message").containsOnly(
      tuple("global:inc", inputFile.getPath(), "Issue number 0"));

    // Default parameter values
    assertThat(logs).containsSubsequence(
      "Param stringParam has value null",
      "Param textParam has value text\nparameter",
      "Param intParam has value 42",
      "Param boolParam has value true",
      "Param floatParam has value 3.14159265358",
      "Param enumParam has value enum1",
      "Param enumListParam has value list1,list2",
      "Param multipleIntegersParam has value null");

    issues.clear();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .putExtraProperty("sonar.cobol.file.suffixes", "glob")
        .addRuleParameter(RuleKey.parse("global:inc"), "stringParam", "polop")
        .addRuleParameter(RuleKey.parse("global:inc"), "textParam", "")
        .addRuleParameter(RuleKey.parse("global:inc"), "multipleIntegersParam", "80,160")
        .addRuleParameter(RuleKey.parse("unknown:rule"), "unknown", "parameter")
        .build(),
      issues::add, null, null);
    assertThat(issues).extracting("ruleKey", "inputFile.path", "message").containsOnly(
      tuple("global:inc", inputFile.getPath(), "Issue number 1"));

    // Overridden parameter values
    assertThat(logs).contains(
      "Param stringParam has value polop",
      "Param textParam has value ",
      "Param intParam has value 42",
      "Param boolParam has value true",
      "Param floatParam has value 3.14159265358",
      "Param enumParam has value enum1",
      "Param enumListParam has value list1,list2",
      "Param multipleIntegersParam has value 80,160");
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest, Charset encoding) throws IOException {
    final var file = new File(baseDir, relativePath);
    FileUtils.write(file, content, encoding);
    return new TestClientInputFile(baseDir.toPath(), file.toPath(), isTest, encoding);
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    return prepareInputFile(relativePath, content, isTest, StandardCharsets.UTF_8);
  }

}
