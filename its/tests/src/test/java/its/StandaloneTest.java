/*
 * SonarLint Core - ITs - Tests
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
package its;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParamType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class StandaloneTest {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static StandaloneSonarLintEngine sonarlint;
  private static List<String> logs;
  private File baseDir;

  @BeforeClass
  public static void prepare() throws Exception {
    Path sonarlintUserHome = temp.newFolder().toPath();
    logs = new ArrayList<>();
    Map<String, String> globalProps = new HashMap<>();
    globalProps.put("sonar.global.label", "It works");
    StandaloneGlobalConfiguration config = StandaloneGlobalConfiguration.builder()
      .addPlugin(new File("../plugins/global-extension-plugin/target/global-extension-plugin.jar").toURI().toURL())
      .addEnabledLanguage(Language.XOO)
      .setSonarLintUserHome(sonarlintUserHome)
      .setLogOutput((msg, level) -> logs.add(msg))
      .setExtraProperties(globalProps).build();
    sonarlint = new StandaloneSonarLintEngine(config);

    assertThat(logs).containsOnlyOnce("Start Global Extension It works");
  }

  @AfterClass
  public static void stop() {
    sonarlint.stop();
    assertThat(logs).containsOnlyOnce("Stop Global Extension");
  }

  @Before
  public void prepareBasedir() throws Exception {
    baseDir = temp.newFolder();
  }

  @Test
  public void checkRuleParameterDeclarations() {
    Collection<StandaloneRuleDetails> ruleDetails = sonarlint.getAllRuleDetails();
    assertThat(ruleDetails).hasSize(1);
    StandaloneRuleDetails incRule = ruleDetails.iterator().next();
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
    Optional<StandaloneRuleParam> param = rule.paramDetails().stream().filter(p -> p.key().equals(paramKey)).findFirst();
    assertThat(param).isNotEmpty();
    assertThat(param.get())
      .extracting(StandaloneRuleParam::type, StandaloneRuleParam::possibleValues)
      .containsExactly(expectedType, Arrays.asList(possibleValues));
  }

  @Test
  public void globalExtension() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo.glob", "foo", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .putExtraProperty("sonar.xoo.file.suffixes", "glob")
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
        .putExtraProperty("sonar.xoo.file.suffixes", "glob")
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

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest, Charset encoding, @Nullable String language) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content, encoding);
    ClientInputFile inputFile = new TestClientInputFile(baseDir.toPath(), file.toPath(), isTest, encoding);
    return inputFile;
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    return prepareInputFile(relativePath, content, isTest, StandardCharsets.UTF_8, null);
  }

}
