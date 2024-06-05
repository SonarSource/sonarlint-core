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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import mediumtest.fixtures.SonarLintBackendFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;
import static testutils.AnalysisUtils.analyzeAndGetIssuesByFile;
import static testutils.AnalysisUtils.createFile;

class ConnectedIssueExclusionsMediumTests {
  private static final String FILE1_PATH = "Foo.java";
  private static final String FILE2_PATH = "Foo2.java";
  private static final String CONNECTION_ID = "local";
  private static final String JAVA_MODULE_KEY = "test-project-2";

  @TempDir
  private static Path baseDir;

  private static Path filePath1;
  private static Path filePath2;
  private static SonarLintBackendFixture.FakeSonarLintRpcClient client;
  private static SonarLintTestRpcServer backend;

  @BeforeAll
  static void prepare() {
    filePath1 = prepareJavaInputFile1(baseDir);
    filePath2 = prepareJavaInputFile2(baseDir);
    client = newFakeClient()
      .withInitialFs(JAVA_MODULE_KEY, baseDir,
        List.of(new ClientFileDto(filePath1.toUri(), baseDir.relativize(filePath1), JAVA_MODULE_KEY, false, null, filePath1, null, null),
          new ClientFileDto(filePath2.toUri(), baseDir.relativize(filePath2), JAVA_MODULE_KEY, false, null, filePath2, null, null)))
      .build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, storage -> storage
        .withPlugin(TestPlugin.JAVA)
        .withProject("test-project")
        .withProject(JAVA_MODULE_KEY, project -> project
          .withRuleSet("java", ruleSet -> ruleSet
            .withActiveRule("java:S106", "MAJOR")
            .withActiveRule("java:S1220", "MINOR")
            .withActiveRule("java:S1481", "BLOCKER"))))
      .withBoundConfigScope(JAVA_MODULE_KEY, CONNECTION_ID, JAVA_MODULE_KEY)
      .withEnabledLanguageInStandaloneMode(Language.JAVA).build(client);
  }

  @AfterAll
  static void stop() {
    if (backend != null) {
      backend.shutdown().join();
    }
  }

  @BeforeEach
  void restoreConfig() {
    updateIssueExclusionsSettings(Map.of());
  }

  @Test
  void issueExclusions() {
    assertThat(collectIssuesByFile(filePath1, filePath2))
      .containsOnly(
        entry(filePath1.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)),
            tuple("java:S106", tuple(5, 4, 5, 14)))),
        entry(filePath2.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)),
            tuple("java:S106", tuple(4, 4, 4, 14)))));

    updateIssueExclusionsSettings(Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssuesByFile(filePath1, filePath2)).isEmpty();

    updateIssueExclusionsSettings(Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*S1481"));
    assertThat(collectIssuesByFile(filePath1, filePath2))
      .containsOnly(
        entry(filePath1.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S106", tuple(5, 4, 5, 14)))),
        entry(filePath2.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S106", tuple(4, 4, 4, 14)))));

    updateIssueExclusionsSettings(Map.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssuesByFile(filePath1, filePath2))
      .containsOnly(
        entry(filePath1.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)),
            tuple("java:S106", tuple(5, 4, 5, 14)))));

    updateIssueExclusionsSettings(Map.of("sonar.issue.ignore.multicriteria", "1,2",
      "sonar.issue.ignore.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.ignore.multicriteria.1.ruleKey", "java:S1481",
      "sonar.issue.ignore.multicriteria.2.resourceKey", FILE1_PATH,
      "sonar.issue.ignore.multicriteria.2.ruleKey", "java:S106"));
    assertThat(collectIssuesByFile(filePath1, filePath2))
      .containsOnly(
        entry(filePath1.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)))),
        entry(filePath2.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S106", tuple(4, 4, 4, 14)))));
  }

  @Test
  void issueExclusionsByRegexp() {
    assertThat(collectIssuesByFile(filePath1, filePath2))
      .containsOnly(
        entry(filePath1.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)),
            tuple("java:S106", tuple(5, 4, 5, 14)))),
        entry(filePath2.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)),
            tuple("java:S106", tuple(4, 4, 4, 14)))));

    updateIssueExclusionsSettings(Map.of("sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", "NOSL1"));
    assertThat(collectIssuesByFile(filePath1, filePath2))
      .containsOnly(
        entry(filePath2.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)),
            tuple("java:S106", tuple(4, 4, 4, 14)))));

    updateIssueExclusionsSettings(Map.of("sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", "NOSL(1|2)"));
    assertThat(collectIssuesByFile(filePath1, filePath2)).isEmpty();
  }

  @Test
  void issueExclusionsByBlock() {
    assertThat(collectIssuesByFile(filePath1, filePath2))
      .containsOnly(
        entry(filePath1.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)),
            tuple("java:S106", tuple(5, 4, 5, 14)))),
        entry(filePath2.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)),
            tuple("java:S106", tuple(4, 4, 4, 14)))));

    updateIssueExclusionsSettings(Map.of("sonar.issue.ignore.block", "1",
      "sonar.issue.ignore.block.1.beginBlockRegexp", "SON.*-OFF",
      "sonar.issue.ignore.block.1.endBlockRegexp", "SON.*-ON"));
    assertThat(collectIssuesByFile(filePath1, filePath2))
      .containsOnly(
        entry(filePath1.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)))),
        entry(filePath2.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)),
            tuple("java:S106", tuple(4, 4, 4, 14)))));
  }

  @Test
  void issueInclusions() {
    updateIssueExclusionsSettings(Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", "Foo*.java",
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssuesByFile(filePath1, filePath2))
      .containsOnly(
        entry(filePath1.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)),
            tuple("java:S106", tuple(5, 4, 5, 14)))),
        entry(filePath2.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)),
            tuple("java:S106", tuple(4, 4, 4, 14)))));

    updateIssueExclusionsSettings(Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*S1481"));
    assertThat(collectIssuesByFile(filePath1, filePath2))
      .containsOnly(
        entry(filePath1.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S106", tuple(5, 4, 5, 14)))),
        entry(filePath2.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)),
            tuple("java:S106", tuple(4, 4, 4, 14)))));

    updateIssueExclusionsSettings(Map.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssuesByFile(filePath1, filePath2))
      .containsOnly(
        entry(filePath2.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)),
            tuple("java:S106", tuple(4, 4, 4, 14)))));

    updateIssueExclusionsSettings(Map.of("sonar.issue.enforce.multicriteria", "1,2",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "java:S1481",
      "sonar.issue.enforce.multicriteria.2.resourceKey", FILE1_PATH,
      "sonar.issue.enforce.multicriteria.2.ruleKey", "java:S106"));
    assertThat(collectIssuesByFile(filePath1, filePath2))
      .containsOnly(
        entry(filePath1.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S106", tuple(5, 4, 5, 14)))),
        entry(filePath2.toUri(),
          List.of(
            tuple("java:S1220", null),
            tuple("java:S1481", tuple(3, 8, 3, 9)))));
  }

  private Map<URI, List<Tuple>> collectIssuesByFile(Path... filePaths) {
    return analyzeAndGetIssuesByFile(backend, client, JAVA_MODULE_KEY, Arrays.stream(filePaths).map(Path::toUri).toArray(URI[]::new))
      .entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream().map(issue -> {
        var textRange = issue.getTextRange();
        return tuple(issue.getRuleKey(),
          textRange == null ? null : tuple(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset()));
      }).collect(Collectors.toList())));
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

  private static Path prepareJavaInputFile1(Path baseDir) {
    return createFile(baseDir, FILE1_PATH,
      "/*NOSL1*/ public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    // SONAR-OFF\n"
        + "    System.out.println(\"Foo\");\n"
        + "    // SONAR-ON\n"
        + "  }\n"
        + "}");
  }

  private static Path prepareJavaInputFile2(Path baseDir) {
    return createFile(baseDir, FILE2_PATH,
      "/*NOSL2*/ public class Foo2 {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}");
  }
}
