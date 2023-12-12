/*
 * SonarLint Core - Medium Tests
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.legacy.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.EngineConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssue;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssueListener;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import testutils.TestUtils;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;
import static testutils.TestUtils.createNoOpLogOutput;

class ConnectedIssueExclusionsMediumTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String FILE1_PATH = "Foo.java";
  private static final String FILE2_PATH = "Foo2.java";
  private static final String CONNECTION_ID = "local";
  private static final String JAVA_MODULE_KEY = "test-project-2";
  private static final SonarLintTestRpcServer backend = newBackend()
    .withSonarQubeConnection(CONNECTION_ID, storage -> storage
      .withPlugin(TestPlugin.JAVA)
      .withProject("test-project")
      .withProject(JAVA_MODULE_KEY, project -> project
        .withRuleSet("java", ruleSet -> ruleSet
          .withActiveRule("java:S106", "MAJOR")
          .withActiveRule("java:S1220", "MINOR")
          .withActiveRule("java:S1481", "BLOCKER"))))
    .withBoundConfigScope(JAVA_MODULE_KEY, CONNECTION_ID, JAVA_MODULE_KEY)
    .withEnabledLanguageInStandaloneMode(Language.JAVA).build();
  private static SonarLintAnalysisEngine engine;

  @TempDir
  private static File baseDir;

  @BeforeAll
  static void prepare(@TempDir Path slHome) {
    var config = EngineConfiguration.builder()
      .setSonarLintUserHome(slHome)
      .setLogOutput(createNoOpLogOutput())
      .build();
    engine = new SonarLintAnalysisEngine(config, backend, CONNECTION_ID);
  }

  @AfterAll
  static void stop() {
    if (engine != null) {
      engine.stop();
      engine = null;
    }
  }

  @BeforeEach
  void restoreConfig() {
    storeProjectSettings(Map.of());
  }

  @Test
  @Disabled("Reaction to exclusion changes is not fully implemented in the new backend, see SLCORE-650")
  void issueExclusions() throws Exception {
    var inputFile1 = prepareJavaInputFile1();
    var inputFile2 = prepareJavaInputFile2();

    var issues = collectIssues(inputFile1, inputFile2);
    assertThat(issues).extracting(RawIssue::getRuleKey, RawIssue::getTextRange, i -> i.getInputFile().relativePath())
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14), FILE1_PATH),
        tuple("java:S1220", null, FILE1_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE1_PATH),
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14), FILE2_PATH),
        tuple("java:S1220", null, FILE2_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).isEmpty();

    storeProjectSettings(Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*S1481"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(RawIssue::getRuleKey, RawIssue::getTextRange, i -> i.getInputFile().relativePath())
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14), FILE1_PATH),
        tuple("java:S1220", null, FILE1_PATH),
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14), FILE2_PATH),
        tuple("java:S1220", null, FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(RawIssue::getRuleKey, RawIssue::getTextRange, i -> i.getInputFile().relativePath())
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14), FILE1_PATH),
        tuple("java:S1220", null, FILE1_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE1_PATH));

    storeProjectSettings(Map.of("sonar.issue.ignore.multicriteria", "1,2",
      "sonar.issue.ignore.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.ignore.multicriteria.1.ruleKey", "java:S1481",
      "sonar.issue.ignore.multicriteria.2.resourceKey", FILE1_PATH,
      "sonar.issue.ignore.multicriteria.2.ruleKey", "java:S106"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(RawIssue::getRuleKey, RawIssue::getTextRange, i -> i.getInputFile().relativePath())
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S1220", null, FILE1_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE1_PATH),
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14), FILE2_PATH),
        tuple("java:S1220", null, FILE2_PATH));
  }

  @Test
  @Disabled("Reaction to exclusion changes is not fully implemented in the new backend, see SLCORE-650")
  void issueExclusionsByRegexp() throws Exception {
    var inputFile1 = prepareJavaInputFile1();
    var inputFile2 = prepareJavaInputFile2();

    assertThat(collectIssues(inputFile1, inputFile2)).extracting(RawIssue::getRuleKey, RawIssue::getTextRange, i -> i.getInputFile().relativePath())
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14), FILE1_PATH),
        tuple("java:S1220", null, FILE1_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE1_PATH),
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14), FILE2_PATH),
        tuple("java:S1220", null, FILE2_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", "NOSL1"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(RawIssue::getRuleKey, RawIssue::getTextRange, i -> i.getInputFile().relativePath())
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14), FILE2_PATH),
        tuple("java:S1220", null, FILE2_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", "NOSL(1|2)"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(RawIssue::getRuleKey, RawIssue::getTextRange, i -> i.getInputFile().relativePath()).isEmpty();
  }

  @Test
  @Disabled("Reaction to exclusion changes is not fully implemented in the new backend, see SLCORE-650")
  void issueExclusionsByBlock() throws Exception {
    var inputFile1 = prepareJavaInputFile1();
    var inputFile2 = prepareJavaInputFile2();

    assertThat(collectIssues(inputFile1, inputFile2)).extracting(RawIssue::getRuleKey, RawIssue::getTextRange, i -> i.getInputFile().relativePath())
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14), FILE1_PATH),
        tuple("java:S1220", null, FILE1_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE1_PATH),
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14), FILE2_PATH),
        tuple("java:S1220", null, FILE2_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.ignore.block", "1",
      "sonar.issue.ignore.block.1.beginBlockRegexp", "SON.*-OFF",
      "sonar.issue.ignore.block.1.endBlockRegexp", "SON.*-ON"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(RawIssue::getRuleKey, RawIssue::getTextRange, i -> i.getInputFile().relativePath())
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S1220", null, FILE1_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE1_PATH),
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14), FILE2_PATH),
        tuple("java:S1220", null, FILE2_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE2_PATH));
  }

  @Test
  void issueInclusions() throws Exception {
    var inputFile1 = prepareJavaInputFile1();
    var inputFile2 = prepareJavaInputFile2();

    storeProjectSettings(Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", "Foo*.java",
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(RawIssue::getRuleKey, RawIssue::getTextRange, i -> i.getInputFile().relativePath())
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14), FILE1_PATH),
        tuple("java:S1220", null, FILE1_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE1_PATH),
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14), FILE2_PATH),
        tuple("java:S1220", null, FILE2_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*S1481"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(RawIssue::getRuleKey, RawIssue::getTextRange, i -> i.getInputFile().relativePath())
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14), FILE1_PATH),
        tuple("java:S1220", null, FILE1_PATH),
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14), FILE2_PATH),
        tuple("java:S1220", null, FILE2_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(RawIssue::getRuleKey, RawIssue::getTextRange, i -> i.getInputFile().relativePath())
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14), FILE2_PATH),
        tuple("java:S1220", null, FILE2_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE2_PATH));

    storeProjectSettings(Map.of("sonar.issue.enforce.multicriteria", "1,2",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "java:S1481",
      "sonar.issue.enforce.multicriteria.2.resourceKey", FILE1_PATH,
      "sonar.issue.enforce.multicriteria.2.ruleKey", "java:S106"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(RawIssue::getRuleKey, RawIssue::getTextRange, i -> i.getInputFile().relativePath())
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14), FILE1_PATH),
        tuple("java:S1220", null, FILE1_PATH),
        tuple("java:S1220", null, FILE2_PATH),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), FILE2_PATH));
  }

  private List<RawIssue> collectIssues(ClientInputFile inputFile1, ClientInputFile inputFile2) {
    final List<RawIssue> issues = new ArrayList<>();
    engine.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFiles(inputFile1, inputFile2)
        .build(),
      new StoreIssueListener(issues), null, null, JAVA_MODULE_KEY);
    return issues;
  }

  private void storeProjectSettings(Map<String, String> settings) {
    // XXX find a better way to change the settings that triggers a recomputation of the exclusions
    var analyzerConfigPath = backend.getStorageRoot().resolve(encodeForFs(CONNECTION_ID)).resolve("projects").resolve(encodeForFs(JAVA_MODULE_KEY)).resolve("analyzer_config.pb");
    Sonarlint.AnalyzerConfiguration.Builder analyzerConfigurationBuilder;
    if (Files.exists(analyzerConfigPath)) {
      analyzerConfigurationBuilder = Sonarlint.AnalyzerConfiguration.newBuilder(ProtobufFileUtil.readFile(analyzerConfigPath, Sonarlint.AnalyzerConfiguration.parser()));
    } else {
      analyzerConfigurationBuilder = Sonarlint.AnalyzerConfiguration.newBuilder();
    }
    analyzerConfigurationBuilder.putAllSettings(settings);
    ProtobufFileUtil.writeToFile(analyzerConfigurationBuilder.build(), analyzerConfigPath);
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

  static class StoreIssueListener implements RawIssueListener {
    private final List<RawIssue> issues;

    StoreIssueListener(List<RawIssue> issues) {
      this.issues = issues;
    }

    @Override
    public void handle(RawIssue rawIssue) {
      issues.add(rawIssue);
    }
  }

}
