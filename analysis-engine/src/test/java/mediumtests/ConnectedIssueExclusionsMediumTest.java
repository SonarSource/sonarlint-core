/*
 * SonarLint Core - Analysis Engine
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.GlobalAnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.plugin.common.Language;
import testutils.PluginLocator;
import testutils.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class ConnectedIssueExclusionsMediumTest {

  private static final String FILE1_PATH = "Foo.java";
  private static final String FILE2_PATH = "Foo2.java";

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static AnalysisEngine sonarlint;
  private static File baseDir;

  @BeforeClass
  public static void prepare() throws Exception {
    Path slHome = temp.newFolder().toPath();

    GlobalAnalysisConfiguration.Builder configBuilder = GlobalAnalysisConfiguration.builder()
      .addPlugin(PluginLocator.getJavaPluginPath())
      .addEnabledLanguages(Language.JAVA)
      .setWorkDir(slHome);
    sonarlint = new AnalysisEngine(configBuilder.build());

    baseDir = temp.newFolder();
  }

  @AfterClass
  public static void stop() {
    if (sonarlint != null) {
      sonarlint.stop();
      sonarlint = null;
    }
  }

  @Test
  public void issueExclusions() throws Exception {
    ClientInputFile inputFile1 = prepareJavaInputFile1();
    ClientInputFile inputFile2 = prepareJavaInputFile2();

    assertThat(collectIssues(inputFile1, inputFile2, Map.of())).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath())
      .containsOnly(
        tuple("java:S106", 5, FILE1_PATH),
        tuple("java:S1220", null, FILE1_PATH),
        tuple("java:S1481", 3, FILE1_PATH),
        tuple("java:S106", 4, FILE2_PATH),
        tuple("java:S1220", null, FILE2_PATH),
        tuple("java:S1481", 3, FILE2_PATH));

    assertThat(collectIssues(inputFile1, inputFile2, Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*"))).isEmpty();

    assertThat(collectIssues(inputFile1, inputFile2, Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*S1481"))).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath())
        .containsOnly(
          tuple("java:S106", 5, FILE1_PATH),
          tuple("java:S1220", null, FILE1_PATH),
          tuple("java:S106", 4, FILE2_PATH),
          tuple("java:S1220", null, FILE2_PATH));

    assertThat(collectIssues(inputFile1, inputFile2, Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*"))).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath())
        .containsOnly(
          tuple("java:S106", 5, FILE1_PATH),
          tuple("java:S1220", null, FILE1_PATH),
          tuple("java:S1481", 3, FILE1_PATH));

    assertThat(collectIssues(inputFile1, inputFile2, Map.of("sonar.issue.ignore.multicriteria", "1,2",
      "sonar.issue.ignore.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.ignore.multicriteria.1.ruleKey", "java:S1481",
      "sonar.issue.ignore.multicriteria.2.resourceKey", FILE1_PATH,
      "sonar.issue.ignore.multicriteria.2.ruleKey", "java:S106"))).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath())
        .containsOnly(
          tuple("java:S1220", null, FILE1_PATH),
          tuple("java:S1481", 3, FILE1_PATH),
          tuple("java:S106", 4, FILE2_PATH),
          tuple("java:S1220", null, FILE2_PATH));
  }

  @Test
  public void issueExclusionsByRegexp() throws Exception {
    ClientInputFile inputFile1 = prepareJavaInputFile1();
    ClientInputFile inputFile2 = prepareJavaInputFile2();

    assertThat(collectIssues(inputFile1, inputFile2, Map.of())).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath())
      .containsOnly(
        tuple("java:S106", 5, FILE1_PATH),
        tuple("java:S1220", null, FILE1_PATH),
        tuple("java:S1481", 3, FILE1_PATH),
        tuple("java:S106", 4, FILE2_PATH),
        tuple("java:S1220", null, FILE2_PATH),
        tuple("java:S1481", 3, FILE2_PATH));

    assertThat(collectIssues(inputFile1, inputFile2, Map.of("sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", "NOSL1"))).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath())
        .containsOnly(
          tuple("java:S106", 4, FILE2_PATH),
          tuple("java:S1220", null, FILE2_PATH),
          tuple("java:S1481", 3, FILE2_PATH));

    assertThat(collectIssues(inputFile1, inputFile2, Map.of("sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", "NOSL(1|2)"))).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath())
        .isEmpty();
  }

  @Test
  public void issueExclusionsByBlock() throws Exception {
    ClientInputFile inputFile1 = prepareJavaInputFile1();
    ClientInputFile inputFile2 = prepareJavaInputFile2();

    assertThat(collectIssues(inputFile1, inputFile2, Map.of())).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath())
      .containsOnly(
        tuple("java:S106", 5, FILE1_PATH),
        tuple("java:S1220", null, FILE1_PATH),
        tuple("java:S1481", 3, FILE1_PATH),
        tuple("java:S106", 4, FILE2_PATH),
        tuple("java:S1220", null, FILE2_PATH),
        tuple("java:S1481", 3, FILE2_PATH));

    assertThat(collectIssues(inputFile1, inputFile2, Map.of("sonar.issue.ignore.block", "1",
      "sonar.issue.ignore.block.1.beginBlockRegexp", "SON.*-OFF",
      "sonar.issue.ignore.block.1.endBlockRegexp", "SON.*-ON"))).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath())
        .containsOnly(
          tuple("java:S1220", null, FILE1_PATH),
          tuple("java:S1481", 3, FILE1_PATH),
          tuple("java:S106", 4, FILE2_PATH),
          tuple("java:S1220", null, FILE2_PATH),
          tuple("java:S1481", 3, FILE2_PATH));
  }

  @Test
  public void issueInclusions() throws Exception {
    ClientInputFile inputFile1 = prepareJavaInputFile1();
    ClientInputFile inputFile2 = prepareJavaInputFile2();

    assertThat(collectIssues(inputFile1, inputFile2, Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", "Foo*.java",
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*"))).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath())
        .containsOnly(
          tuple("java:S106", 5, FILE1_PATH),
          tuple("java:S1220", null, FILE1_PATH),
          tuple("java:S1481", 3, FILE1_PATH),
          tuple("java:S106", 4, FILE2_PATH),
          tuple("java:S1220", null, FILE2_PATH),
          tuple("java:S1481", 3, FILE2_PATH));

    assertThat(collectIssues(inputFile1, inputFile2, Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*S1481"))).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath())
        .containsOnly(
          tuple("java:S106", 5, FILE1_PATH),
          tuple("java:S1220", null, FILE1_PATH),
          tuple("java:S106", 4, FILE2_PATH),
          tuple("java:S1220", null, FILE2_PATH),
          tuple("java:S1481", 3, FILE2_PATH));

    assertThat(collectIssues(inputFile1, inputFile2, Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*"))).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath())
        .containsOnly(
          tuple("java:S106", 4, FILE2_PATH),
          tuple("java:S1220", null, FILE2_PATH),
          tuple("java:S1481", 3, FILE2_PATH));

    assertThat(collectIssues(inputFile1, inputFile2, Map.of("sonar.issue.enforce.multicriteria", "1,2",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "java:S1481",
      "sonar.issue.enforce.multicriteria.2.resourceKey", FILE1_PATH,
      "sonar.issue.enforce.multicriteria.2.ruleKey", "java:S106"))).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath())
        .containsOnly(
          tuple("java:S106", 5, FILE1_PATH),
          tuple("java:S1220", null, FILE1_PATH),
          tuple("java:S1220", null, FILE2_PATH),
          tuple("java:S1481", 3, FILE2_PATH));
  }

  private List<Issue> collectIssues(ClientInputFile inputFile1, ClientInputFile inputFile2, Map<String, String> config) throws IOException {
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFiles(inputFile1, inputFile2)
        .putAllExtraProperties(config)
        .addActiveRules(createActiveRule("java:S106"), createActiveRule("java:S1220"), createActiveRule("java:S1481"))
        .build(),
      new StoreIssueListener(issues), null, null);
    return issues;
  }

  private ClientInputFile prepareJavaInputFile1() throws IOException {
    return prepareInputFile(FILE1_PATH,
      "/*NOSL1*/ public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    // SONAR-OFF\n"
        + "    System.out.println(\"Foo\");\n"
        + "    // SONAR-ON\n"
        + "  }\n"
        + "}",
      false);
  }

  private ClientInputFile prepareJavaInputFile2() throws IOException {
    return prepareInputFile(FILE2_PATH,
      "/*NOSL2*/ public class Foo2 {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}",
      false);
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return TestUtils.createInputFile(file.toPath(), relativePath, isTest);
  }

  static class StoreIssueListener implements Consumer<Issue> {
    private final List<Issue> issues;

    StoreIssueListener(List<Issue> issues) {
      this.issues = issues;
    }

    @Override
    public void accept(Issue t) {
      issues.add(t);
    }
  }

  private static ActiveRule createActiveRule(String ruleKey) {
    return new ActiveRule(ruleKey, null);
  }

}
