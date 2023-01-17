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
package mediumtest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mediumtest.fixtures.ProjectStorageFixture;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.commons.Language;
import testutils.TestUtils;

import static mediumtest.fixtures.StorageFixture.newStorage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static testutils.TestUtils.createNoOpLogOutput;

class ConnectedIssueExclusionsMediumTests {

  private static final String FILE1_PATH = "Foo.java";
  private static final String FILE2_PATH = "Foo2.java";
  private static final String SERVER_ID = "local";
  private static final String JAVA_MODULE_KEY = "test-project-2";
  private static ConnectedSonarLintEngineImpl sonarlint;

  @TempDir
  private static File baseDir;

  private static ProjectStorageFixture.ProjectStorage projectStorage;

  @BeforeAll
  static void prepare(@TempDir Path slHome) throws Exception {
    var storage = newStorage(SERVER_ID)
      .withJavaPlugin()
      .withProject("test-project")
      .withProject(JAVA_MODULE_KEY, project -> project
        .withRuleSet("java", ruleSet -> ruleSet
          .withActiveRule("java:S106", "MAJOR")
          .withActiveRule("java:S1220", "MINOR")
          .withActiveRule("java:S1481", "BLOCKER")))
      .create(slHome);
    projectStorage = storage.getProjectStorages().get(1);

    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId(SERVER_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(storage.getPath())
      .setLogOutput(createNoOpLogOutput())
      .addEnabledLanguage(Language.JAVA)
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);
  }

  @AfterAll
  static void stop() {
    if (sonarlint != null) {
      sonarlint.stop(true);
      sonarlint = null;
    }
  }

  @BeforeEach
  void restoreConfig() {
    storeProjectSettings(Map.of());
  }

  @Test
  void issueExclusions() throws Exception {
    var inputFile1 = prepareJavaInputFile1();
    var inputFile2 = prepareJavaInputFile2();

    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S106", 5, FILE1_PATH),
      tuple("java:S1220", null, FILE1_PATH),
      tuple("java:S1481", 3, FILE1_PATH),
      tuple("java:S106", 4, FILE2_PATH),
      tuple("java:S1220", null, FILE2_PATH),
      tuple("java:S1481", 3, FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).isEmpty();

    storeProjectSettings(Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*S1481"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S106", 5, FILE1_PATH),
      tuple("java:S1220", null, FILE1_PATH),
      tuple("java:S106", 4, FILE2_PATH),
      tuple("java:S1220", null, FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S106", 5, FILE1_PATH),
      tuple("java:S1220", null, FILE1_PATH),
      tuple("java:S1481", 3, FILE1_PATH));

    storeProjectSettings(Map.of("sonar.issue.ignore.multicriteria", "1,2",
      "sonar.issue.ignore.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.ignore.multicriteria.1.ruleKey", "java:S1481",
      "sonar.issue.ignore.multicriteria.2.resourceKey", FILE1_PATH,
      "sonar.issue.ignore.multicriteria.2.ruleKey", "java:S106"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S1220", null, FILE1_PATH),
      tuple("java:S1481", 3, FILE1_PATH),
      tuple("java:S106", 4, FILE2_PATH),
      tuple("java:S1220", null, FILE2_PATH));
  }

  @Test
  void issueExclusionsByRegexp() throws Exception {
    var inputFile1 = prepareJavaInputFile1();
    var inputFile2 = prepareJavaInputFile2();

    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S106", 5, FILE1_PATH),
      tuple("java:S1220", null, FILE1_PATH),
      tuple("java:S1481", 3, FILE1_PATH),
      tuple("java:S106", 4, FILE2_PATH),
      tuple("java:S1220", null, FILE2_PATH),
      tuple("java:S1481", 3, FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", "NOSL1"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S106", 4, FILE2_PATH),
      tuple("java:S1220", null, FILE2_PATH),
      tuple("java:S1481", 3, FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", "NOSL(1|2)"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).isEmpty();
  }

  @Test
  void issueExclusionsByBlock() throws Exception {
    var inputFile1 = prepareJavaInputFile1();
    var inputFile2 = prepareJavaInputFile2();

    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S106", 5, FILE1_PATH),
      tuple("java:S1220", null, FILE1_PATH),
      tuple("java:S1481", 3, FILE1_PATH),
      tuple("java:S106", 4, FILE2_PATH),
      tuple("java:S1220", null, FILE2_PATH),
      tuple("java:S1481", 3, FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.ignore.block", "1",
      "sonar.issue.ignore.block.1.beginBlockRegexp", "SON.*-OFF",
      "sonar.issue.ignore.block.1.endBlockRegexp", "SON.*-ON"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S1220", null, FILE1_PATH),
      tuple("java:S1481", 3, FILE1_PATH),
      tuple("java:S106", 4, FILE2_PATH),
      tuple("java:S1220", null, FILE2_PATH),
      tuple("java:S1481", 3, FILE2_PATH));
  }

  @Test
  void issueInclusions() throws Exception {
    var inputFile1 = prepareJavaInputFile1();
    var inputFile2 = prepareJavaInputFile2();

    storeProjectSettings(Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", "Foo*.java",
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S106", 5, FILE1_PATH),
      tuple("java:S1220", null, FILE1_PATH),
      tuple("java:S1481", 3, FILE1_PATH),
      tuple("java:S106", 4, FILE2_PATH),
      tuple("java:S1220", null, FILE2_PATH),
      tuple("java:S1481", 3, FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*S1481"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S106", 5, FILE1_PATH),
      tuple("java:S1220", null, FILE1_PATH),
      tuple("java:S106", 4, FILE2_PATH),
      tuple("java:S1220", null, FILE2_PATH),
      tuple("java:S1481", 3, FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S106", 4, FILE2_PATH),
      tuple("java:S1220", null, FILE2_PATH),
      tuple("java:S1481", 3, FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.enforce.multicriteria", "1,2",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "java:S1481",
      "sonar.issue.enforce.multicriteria.2.resourceKey", FILE1_PATH,
      "sonar.issue.enforce.multicriteria.2.ruleKey", "java:S106"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S106", 5, FILE1_PATH),
      tuple("java:S1220", null, FILE1_PATH),
      tuple("java:S1220", null, FILE2_PATH),
      tuple("java:S1481", 3, FILE2_PATH));
  }

  private List<Issue> collectIssues(ClientInputFile inputFile1, ClientInputFile inputFile2) {
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      ConnectedAnalysisConfiguration.builder()
        .setProjectKey(JAVA_MODULE_KEY)
        .setBaseDir(baseDir.toPath())
        .addInputFiles(inputFile1, inputFile2)
        .build(),
      new StoreIssueListener(issues), null, null);
    return issues;
  }

  private void storeProjectSettings(Map<String, String> settings) {
    projectStorage.setSettings(settings);
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
    final var file = new File(baseDir, relativePath);
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return TestUtils.createInputFile(file.toPath(), relativePath, isTest);
  }

  static class StoreIssueListener implements IssueListener {
    private final List<Issue> issues;

    StoreIssueListener(List<Issue> issues) {
      this.issues = issues;
    }

    @Override
    public void handle(Issue issue) {
      issues.add(issue);
    }
  }

}
