/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import mediumtest.fixtures.SonarLintBackendFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;
import static testutils.AnalysisUtils.analyzeFilesAndGetIssuesAsMap;
import static testutils.AnalysisUtils.analyzeFilesAndVerifyNoIssues;
import static testutils.AnalysisUtils.createFile;

class ConnectedIssueExclusionsMediumTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String FILE1_PATH = "Foo.java";
  private static final String FILE2_PATH = "Foo2.java";
  private static Path inputFile1;
  private static Path inputFile2;
  private static final String CONNECTION_ID = "local";
  private static final String JAVA_MODULE_KEY = "test-project-2";
  private static SonarLintTestRpcServer backend;
  private static SonarLintBackendFixture.FakeSonarLintRpcClient client;

  @BeforeAll
  static void prepare(@TempDir Path baseDir) {
    inputFile1 = prepareJavaInputFile1(baseDir, FILE1_PATH);
    inputFile2 = prepareJavaInputFile2(baseDir, FILE2_PATH);

    client = newFakeClient()
      .withInitialFs(JAVA_MODULE_KEY, List.of(
        new ClientFileDto(inputFile1.toUri(), baseDir.relativize(inputFile1), JAVA_MODULE_KEY, false, null, inputFile1, null, null, true),
        new ClientFileDto(inputFile2.toUri(), baseDir.relativize(inputFile2), JAVA_MODULE_KEY, false, null, inputFile2, null, null, true)
      ))
      .build();
    var server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage
        .withPlugin(TestPlugin.JAVA)
        .withProject("test-project")
        .withProject(JAVA_MODULE_KEY, project -> project
          .withRuleSet("java", ruleSet -> ruleSet
            .withActiveRule("java:S106", "MAJOR")
            .withActiveRule("java:S1220", "MINOR")
            .withActiveRule("java:S1481", "BLOCKER"))))
      .withBoundConfigScope(JAVA_MODULE_KEY, CONNECTION_ID, JAVA_MODULE_KEY)
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .build(client);
  }

  @AfterAll
  static void stop() {
    if (backend != null) {
      backend.shutdown().join();
      backend = null;
    }
  }

  @BeforeEach
  void restoreConfig() {
    updateIssueExclusionsSettings(Map.of());
  }

  @Test
  void issueExclusions() {
    var issues = analyzeFilesAndGetIssuesAsMap(List.of(inputFile1.toUri(), inputFile2.toUri()), client, backend, JAVA_MODULE_KEY);
    var issuesFile1 = issues.get(inputFile1.toUri());
    var issuesFile2 = issues.get(inputFile2.toUri());
    assertThat(issuesFile1).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14)),
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));
    assertThat(issuesFile2).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14)),
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));
    client.cleanRaisedIssues();

    updateIssueExclusionsSettings(Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*"));
    analyzeFilesAndVerifyNoIssues(List.of(inputFile1.toUri(), inputFile2.toUri()), client, backend, JAVA_MODULE_KEY);

    updateIssueExclusionsSettings(Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*S1481"));
    issues = analyzeFilesAndGetIssuesAsMap(List.of(inputFile1.toUri(), inputFile2.toUri()), client, backend, JAVA_MODULE_KEY);
    issuesFile1 = issues.get(inputFile1.toUri());
    issuesFile2 = issues.get(inputFile2.toUri());
    assertThat(issuesFile1).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14)),
        tuple("java:S1220", null));
    assertThat(issuesFile2).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14)),
        tuple("java:S1220", null));
    client.cleanRaisedIssues();

    updateIssueExclusionsSettings(Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", FILE2_PATH.toString(),
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*"));
    issues = analyzeFilesAndGetIssuesAsMap(List.of(inputFile1.toUri(), inputFile2.toUri()), client, backend, JAVA_MODULE_KEY);
    issuesFile1 = issues.get(inputFile1.toUri());
    issuesFile2 = issues.get(inputFile2.toUri());
    assertThat(issuesFile1).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14)),
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));
    assertThat(issuesFile2).isNullOrEmpty();
    client.cleanRaisedIssues();

    updateIssueExclusionsSettings(Map.of("sonar.issue.ignore.multicriteria", "1,2",
      "sonar.issue.ignore.multicriteria.1.resourceKey", FILE2_PATH.toString(),
      "sonar.issue.ignore.multicriteria.1.ruleKey", "java:S1481",
      "sonar.issue.ignore.multicriteria.2.resourceKey", FILE1_PATH.toString(),
      "sonar.issue.ignore.multicriteria.2.ruleKey", "java:S106"));
    issues = analyzeFilesAndGetIssuesAsMap(List.of(inputFile1.toUri(), inputFile2.toUri()), client, backend, JAVA_MODULE_KEY);
    issuesFile1 = issues.get(inputFile1.toUri());
    issuesFile2 = issues.get(inputFile2.toUri());
    assertThat(issuesFile1).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));
    assertThat(issuesFile2).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14)),
        tuple("java:S1220", null));
  }

  @Test
  void issueExclusionsByRegexp() {
    var issues = analyzeFilesAndGetIssuesAsMap(List.of(inputFile1.toUri(), inputFile2.toUri()), client, backend, JAVA_MODULE_KEY);
    var issuesFile1 = issues.get(inputFile1.toUri());
    var issuesFile2 = issues.get(inputFile2.toUri());
    assertThat(issuesFile1).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14)),
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));
    assertThat(issuesFile2).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14)),
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));
    client.cleanRaisedIssues();

    updateIssueExclusionsSettings(Map.of("sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", "NOSL1"));
    issues = analyzeFilesAndGetIssuesAsMap(List.of(inputFile1.toUri(), inputFile2.toUri()), client, backend, JAVA_MODULE_KEY);
    issuesFile1 = issues.get(inputFile1.toUri());
    issuesFile2 = issues.get(inputFile2.toUri());
    assertThat(issuesFile2).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14)),
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));
    assertThat(issuesFile1).isNullOrEmpty();
    client.cleanRaisedIssues();

    updateIssueExclusionsSettings(Map.of("sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", "NOSL(1|2)"));
    analyzeFilesAndVerifyNoIssues(List.of(inputFile1.toUri(), inputFile2.toUri()), client, backend, JAVA_MODULE_KEY);
  }

  @Test
  void issueExclusionsByBlock() {
    var issues = analyzeFilesAndGetIssuesAsMap(List.of(inputFile1.toUri(), inputFile2.toUri()), client, backend, JAVA_MODULE_KEY);
    var issuesFile1 = issues.get(inputFile1.toUri());
    var issuesFile2 = issues.get(inputFile2.toUri());
    assertThat(issuesFile1).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14)),
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));
    assertThat(issuesFile2).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14)),
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));

    updateIssueExclusionsSettings(Map.of("sonar.issue.ignore.block", "1",
      "sonar.issue.ignore.block.1.beginBlockRegexp", "SON.*-OFF",
      "sonar.issue.ignore.block.1.endBlockRegexp", "SON.*-ON"));
    issues = analyzeFilesAndGetIssuesAsMap(List.of(inputFile1.toUri(), inputFile2.toUri()), client, backend, JAVA_MODULE_KEY);
    issuesFile1 = issues.get(inputFile1.toUri());
    issuesFile2 = issues.get(inputFile2.toUri());
    assertThat(issuesFile1).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));
    assertThat(issuesFile2).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14)),
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));
  }

  @Test
  void issueInclusions() {
    updateIssueExclusionsSettings(Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", "Foo*.java",
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*"));
    var issues = analyzeFilesAndGetIssuesAsMap(List.of(inputFile1.toUri(), inputFile2.toUri()), client, backend, JAVA_MODULE_KEY);
    var issuesFile1 = issues.get(inputFile1.toUri());
    var issuesFile2 = issues.get(inputFile2.toUri());
    assertThat(issuesFile1).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14)),
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));
    assertThat(issuesFile2).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14)),
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));

    updateIssueExclusionsSettings(Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH.toString(),
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*S1481"));
    issues = analyzeFilesAndGetIssuesAsMap(List.of(inputFile1.toUri(), inputFile2.toUri()), client, backend, JAVA_MODULE_KEY);
    issuesFile1 = issues.get(inputFile1.toUri());
    issuesFile2 = issues.get(inputFile2.toUri());
    assertThat(issuesFile1).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14)),
        tuple("java:S1220", null));
    assertThat(issuesFile2).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14)),
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));

    updateIssueExclusionsSettings(Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH.toString(),
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*"));
    issues = analyzeFilesAndGetIssuesAsMap(List.of(inputFile1.toUri(), inputFile2.toUri()), client, backend, JAVA_MODULE_KEY);
    issuesFile1 = issues.get(inputFile1.toUri());
    issuesFile2 = issues.get(inputFile2.toUri());
    assertThat(issuesFile1).isEmpty();
    assertThat(issuesFile2).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14)),
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));

    updateIssueExclusionsSettings(Map.of("sonar.issue.enforce.multicriteria", "1,2",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH.toString(),
      "sonar.issue.enforce.multicriteria.1.ruleKey", "java:S1481",
      "sonar.issue.enforce.multicriteria.2.resourceKey", FILE1_PATH.toString(),
      "sonar.issue.enforce.multicriteria.2.ruleKey", "java:S106"));
    issues = analyzeFilesAndGetIssuesAsMap(List.of(inputFile1.toUri(), inputFile2.toUri()), client, backend, JAVA_MODULE_KEY);
    issuesFile1 = issues.get(inputFile1.toUri());
    issuesFile2 = issues.get(inputFile2.toUri());
    assertThat(issuesFile1).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14)),
        tuple("java:S1220", null));
    assertThat(issuesFile2).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)));
  }

  private void updateIssueExclusionsSettings(Map<String, String> settings) {
    var analyzerConfigPath = backend.getStorageRoot().resolve(encodeForFs(CONNECTION_ID)).resolve("projects").resolve(encodeForFs(JAVA_MODULE_KEY)).resolve("analyzer_config.pb");
    Sonarlint.AnalyzerConfiguration.Builder analyzerConfigurationBuilder;
    if (Files.exists(analyzerConfigPath)) {
      analyzerConfigurationBuilder = Sonarlint.AnalyzerConfiguration.newBuilder(ProtobufFileUtil.readFile(analyzerConfigPath, Sonarlint.AnalyzerConfiguration.parser()));
      analyzerConfigurationBuilder.clearSettings();
    } else {
      analyzerConfigurationBuilder = Sonarlint.AnalyzerConfiguration.newBuilder();
    }
    analyzerConfigurationBuilder.putAllSettings(settings);
    ProtobufFileUtil.writeToFile(analyzerConfigurationBuilder.build(), analyzerConfigPath);
  }

  private static Path prepareJavaInputFile1(Path baseDir, String filePath) {
    return createFile(baseDir, filePath,
      "/*NOSL1*/ public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    // SONAR-OFF\n"
        + "    System.out.println(\"Foo\");\n"
        + "    // SONAR-ON\n"
        + "  }\n"
        + "}");
  }

  private static Path prepareJavaInputFile2(Path baseDir, String filePath) {
    return createFile(baseDir, filePath,
      "/*NOSL2*/ public class Foo2 {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}");
  }

}
