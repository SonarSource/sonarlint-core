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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mediumtest.fixtures.SonarLintBackendFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.GetEffectiveIssueDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.UpdateStandaloneRulesConfigurationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.FileEditDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.QuickFixDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.TextEditDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

import static java.util.Collections.emptyMap;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.C;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JS;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.KOTLIN;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.PHP;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.PYTHON;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.TS;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.XML;
import static testutils.AnalysisUtils.analyzeFileAndGetIssues;
import static testutils.AnalysisUtils.analyzeFilesAndVerifyNoIssues;
import static testutils.AnalysisUtils.createFile;

class StandaloneIssueMediumTests {
  private static SonarLintBackendFixture.FakeSonarLintRpcClient client;

  private static final String A_JAVA_FILE_PATH = "Foo.java";
  private static final String CONFIGURATION_SCOPE_ID = "configScopeId";
  private static final List<String> logs = new CopyOnWriteArrayList<>();
  private static SonarLintTestRpcServer backend;
  // commercial plugins might not be available
  // (if you pass -Dcommercial to maven, a profile will be activated that downloads the commercial plugins)
  private static final boolean COMMERCIAL_ENABLED = System.getProperty("commercial") != null;

  @AfterAll
  static void stop() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @AfterEach
  void cleanup() {
    client.cleanRaisedIssues();
  }

  @Test
  void simpleJavaScript(@TempDir Path baseDir) throws Exception {
    var content = "function foo() {\n"
      + "  let x;\n"
      + "  let y; //NOSONAR\n"
      + "}";
    var inputFile = createFile(baseDir, "foo.js", content);

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();

    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVASCRIPT)
      .build(client);

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues)
      .extracting(RaisedIssueDto::getRuleKey, i -> i.getTextRange().getStartLine(), RaisedIssueDto::getRuleDescriptionContextKey,
        RaisedIssueDto::getCleanCodeAttribute,
        i -> i.getImpacts().get(0).getSoftwareQuality(), i -> i.getImpacts().get(0).getImpactSeverity())
      .containsOnly(tuple("javascript:S1481", 2, null, CleanCodeAttribute.CONVENTIONAL, SoftwareQuality.MAINTAINABILITY, ImpactSeverity.LOW));
    client.cleanRaisedIssues();

    // SLCORE-160
    var nodeModulesDir = Files.createDirectory(baseDir.resolve("node_modules"));

    inputFile = createFile(nodeModulesDir, "foo.js", content);

    analyzeFilesAndVerifyNoIssues(List.of(inputFile.toUri()), client, backend, CONFIGURATION_SCOPE_ID);
  }

  // looks like we don't pass global settings to init params, only exclusion is omnisharp params
  // to be checked if we need this functionality back, it will require to modify init params
  @Test
  void sonarjs_should_honor_global_and_analysis_level_properties(@TempDir Path baseDir) {
    var content = "function foo() {\n"
      + "  console.log(LOCAL1); // Noncompliant\n"
      + "  console.log(GLOBAL1); // GLOBAL1 defined as global variable in global settings\n"
      + "}";
    var inputFile = createFile(baseDir, "foo.js", content);

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVASCRIPT)

      .build(client);
    backend.getRulesService()
      .updateStandaloneRulesConfiguration(new UpdateStandaloneRulesConfigurationParams(Map.of("javascript:S3827", new StandaloneRuleConfigDto(true, emptyMap()))));

    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(CONFIGURATION_SCOPE_ID, analysisId, List.of(inputFile.toUri()), Map.of("sonar.javascript.globals", "LOCAL1"), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIGURATION_SCOPE_ID)).isNotEmpty());
    var issues = client.getRaisedIssuesForScopeIdAsList(CONFIGURATION_SCOPE_ID);
    assertThat(issues).hasSize(1);
    var issue = issues.get(0);
    assertThat(issue.getTextRange().getStartLine()).isEqualTo(3);
  }

  @Test
  void simpleTypeScript(@TempDir Path baseDir) throws Exception {
    final var tsConfig = new File(baseDir.toFile(), "tsconfig.json");
    FileUtils.write(tsConfig, "{}", StandardCharsets.UTF_8);
    var tsConfigPath = tsConfig.toPath();
    var content = "function foo() {\n"
      + "  if(bar() && bar()) { return 42; }\n"
      + "}";
    var inputFile = createFile(baseDir, "foo.ts", content);

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true),
        new ClientFileDto(tsConfigPath.toUri(), baseDir.relativize(tsConfigPath), CONFIGURATION_SCOPE_ID, false, null, tsConfigPath, null, null, true)
      ))
      .build();

    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVASCRIPT)
      .build(client);

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, i -> i.getTextRange().getStartLine()).containsOnly(
      tuple("typescript:S1764", 2));
  }

  @Disabled("https://sonarsource.atlassian.net/browse/SLCORE-873 - plug test YAML plugin")
  @Test
  void simpleJavaScriptInYamlFile(@TempDir Path baseDir) {
    String content = "Resources:\n" +
      "  LambdaFunction:\n" +
      "    Type: 'AWS::Lambda::Function'\n" +
      "    Properties:\n" +
      "      Code:\n" +
      "        ZipFile: >\n" +
      "          exports.handler = function(event, context) {\n" +
      "            let x;\n" +
      "          };\n" +
      "      Runtime: nodejs8.10";

    var inputFile = createFile(baseDir, "foo.yaml", content);

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();

    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVASCRIPT)
      .build(client);

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);
    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, i -> i.getTextRange().getStartLine()).containsOnly(
      tuple("javascript:S1481", 8));
  }

  @Test
  void simpleC(@TempDir Path baseDir) {
    assumeTrue(COMMERCIAL_ENABLED);
    var inputFile = createFile(baseDir, "foo.c", "#import \"foo.h\"\n"
      + "#import \"foo2.h\" //NOSONAR\n");
    var buildWrapperContent = "{\"version\":0,\"captures\":[" +
      "{" +
      "\"compiler\": \"clang\"," +
      "\"executable\": \"compiler\"," +
      "\"stdout\": \"#define __STDC_VERSION__ 201112L\n\"," +
      "\"stderr\": \"\"" +
      "}," +
      "{" +
      "\"compiler\": \"clang\"," +
      "\"executable\": \"compiler\"," +
      "\"stdout\": \"#define __cplusplus 201703L\n\"," +
      "\"stderr\": \"\"" +
      "}," +
      "{\"compiler\":\"clang\",\"cwd\":\"" +
      baseDir.toString().replace("\\", "\\\\") +
      "\",\"executable\":\"compiler\",\"cmd\":[\"cc\",\"foo.c\"]}]}";
    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.CFAMILY)
      .build(client);

    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(CONFIGURATION_SCOPE_ID, analysisId, List.of(inputFile.toUri()), Map.of("sonar.cfamily.build-wrapper-content", buildWrapperContent), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIGURATION_SCOPE_ID)).isNotEmpty());
    var issues = client.getRaisedIssuesForScopeIdAsList(CONFIGURATION_SCOPE_ID);
    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, i -> i.getTextRange().getStartLine(), i -> i.getTextRange().getStartLineOffset())
      .containsOnly(
        tuple("c:S3805", 1, 0),
        // FIXME no sonar is not supported by the CFamily analyzer
        tuple("c:S3805", 2, 0));
  }

  @Test
  void simplePhp(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, "foo.php", "<?php\n"
      + "function writeMsg($fname) {\n"
      + "    $i = 0; // NOSONAR\n"
      + "    echo \"Hello world!\";\n"
      + "}\n"
      + "?>\n");

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();

    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PHP)
      .build(client);

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, i -> i.getTextRange().getStartLine()).contains(tuple("php:S1172", 2));
  }

  @Test
  void fileEncoding(@TempDir Path baseDir) throws IOException {
    var content = "<?php\n"
      + "function writeMsg($fname) {\n"
      + "    $i = 0; // NOSONAR\n"
      + "    echo \"Hello world!\";\n"
      + "}\n"
      + "?>\n";
    var inputFile = baseDir.resolve("foo.php");
    try {
      Files.writeString(inputFile, content, StandardCharsets.UTF_16);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, StandardCharsets.UTF_16.name(), inputFile, null, null, true)
      ))
      .build();

    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PHP)
      .build(client);

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, i -> i.getTextRange().getStartLine()).contains(tuple("php:S1172", 2));
  }

  @Test
  void analysisErrors(@TempDir Path baseDir) {
    var content = "<?php\n"
      + "function writeMsg($fname) {\n"
      + "    echo \"Hello world!;\n"
      + "}\n"
      + "?>";
    var inputFile = createFile(baseDir, "foo.php", content);

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();

    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PHP)
      .build(client);

    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(CONFIGURATION_SCOPE_ID, analysisId, List.of(inputFile.toUri()), Map.of(), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).containsExactly(inputFile.toUri());
    await().during(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIGURATION_SCOPE_ID)).isEmpty());
  }

  @Test
  void simplePython(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, "foo.py", "def my_function(name):\n"
      + "    print \"Hello\"\n"
      + "    print \"world!\" # NOSONAR\n"
      + "\n");
    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build(client);

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, i -> i.getTextRange().getStartLine()).containsOnly(
      tuple("python:S1172", 1),
      tuple("python:PrintStatementUsage", 2));
  }

  @Test
  void simpleKotlinKts(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, "settings.gradle.kts", "description = \"SonarLint for IntelliJ IDEA\"");
    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.KOTLIN)
      .build(client);

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange).containsOnly(
      tuple("kotlin:S6625", null));
  }

  // SLCORE-162
  @Test
  void useRelativePathToEvaluatePathPatterns(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, "foo.tmp", "def my_function(name):\n"
      + "    print \"Hello\"\n"
      + "    print \"world!\" # NOSONAR\n"
      + "\n");
    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), Path.of("foo.py"), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build(client);

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, i -> i.getTextRange().getStartLine()).containsOnly(
      tuple("python:S1172", 1),
      tuple("python:PrintStatementUsage", 2));
  }

  @Test
  void simpleJava(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    // TODO full line issue\n"
        + "  }\n"
        + "}");

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange, RaisedIssueDto::getSeverity)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S1220", null, IssueSeverity.MINOR),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), IssueSeverity.MINOR),
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14), IssueSeverity.MAJOR),
        tuple("java:S1135", new TextRangeDto(5, 0, 5, 27), IssueSeverity.INFO));
  }

  @Test
  void simpleJavaWithQuickFix(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "     \n"
        + "  }\n"
        + "}");
    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange, RaisedIssueDto::getSeverity)
      .usingRecursiveFieldByFieldElementComparator()
      .contains(
        tuple("java:S1186", new TextRangeDto(2, 14, 2, 17), IssueSeverity.CRITICAL));

    assertThat(issues)
      .flatExtracting(RaisedIssueDto::getQuickFixes)
      .extracting(QuickFixDto::message)
      .containsOnly("Insert placeholder comment");
    assertThat(issues)
      .flatExtracting(RaisedIssueDto::getQuickFixes)
      .flatExtracting(QuickFixDto::fileEdits)
      .extracting(FileEditDto::target)
      .containsOnly(inputFile.toUri());
    assertThat(issues)
      .usingRecursiveFieldByFieldElementComparator()
      .flatExtracting(RaisedIssueDto::getQuickFixes)
      .flatExtracting(QuickFixDto::fileEdits)
      .flatExtracting(FileEditDto::textEdits)
      .extracting(TextEditDto::range, TextEditDto::newText)
      .containsOnly(
        tuple(new TextRangeDto(2, 21, 4, 2), "\n    // TODO document why this method is empty\n  "));
  }

  @Test
  void simpleJavaWithCommaInClasspath(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "  }\n"
        + "}");
    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(CONFIGURATION_SCOPE_ID, analysisId, List.of(inputFile.toUri()), Map.of("sonar.java.libraries", "\"" + Paths.get("target/lib/guava,with,comma.jar").toAbsolutePath().toString() + "\""), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIGURATION_SCOPE_ID)).isNotEmpty());
    var issues = client.getRaisedIssuesForScopeIdAsList(CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange, RaisedIssueDto::getSeverity)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S1220", null, IssueSeverity.MINOR),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), IssueSeverity.MINOR));
  }

  @Test
  void it_should_get_issue_details_for_standalone_issue(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "secret.py",
      "KEY = \"AKIAIGKECZXA7AEIJLMQ\"");
    var fileUri = filePath.toUri();
    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIGURATION_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.TEXT)
      .build(client);
    var analysisId = UUID.randomUUID();

    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIGURATION_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), false, System.currentTimeMillis())).join();
    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIGURATION_SCOPE_ID).get(fileUri)).isNotEmpty());

    var issueId = client.getRaisedIssuesForScopeId(CONFIGURATION_SCOPE_ID).get(fileUri).get(0).getId();
    var result = backend.getIssueService().getEffectiveIssueDetails(new GetEffectiveIssueDetailsParams(CONFIGURATION_SCOPE_ID, issueId)).join();

    assertThat(result.getDetails()).isNotNull();
    // standalone mode should have Clean Code attribute
    assertThat(result.getDetails().getSeverityDetails().isRight()).isTrue();
    assertThat(result.getDetails().getSeverityDetails().getRight().getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.CONVENTIONAL);
    assertThat(result.getDetails().getRuleKey()).isEqualTo("secrets:S6290");
    assertThat(result.getDetails().getName()).isEqualTo("Amazon Web Services credentials should not be disclosed");
    assertThat(result.getDetails().getRuleDescriptionContextKey()).isNull();
    assertThat(result.getDetails().getVulnerabilityProbability()).isNull();
    assertThat(result.getDetails().getDescription().isLeft()).isTrue();
    assertThat(result.getDetails().getLanguage().name()).isEqualTo("SECRETS");
  }

  @Test
  void it_should_not_get_issue_details_for_non_existent_issue(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "secret.py",
      "KEY = \"AKIAIGKECZXA7AEIJLMQ\"");
    var fileUri = filePath.toUri();
    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIGURATION_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.TEXT)
      .build(client);

    var issueId = UUID.randomUUID();
    var params = new GetEffectiveIssueDetailsParams(CONFIGURATION_SCOPE_ID, issueId);
    var issueService = backend.getIssueService();
    var detailsFuture = issueService.getEffectiveIssueDetails(params);
    assertThrows(CompletionException.class, detailsFuture::join);
  }

  // SLCORE-251
  @Test
  void noRuleTemplates() throws ExecutionException, InterruptedException {
    client = newFakeClient().build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build(client);

    var response = backend.getRulesService().listAllStandaloneRulesDefinitions().get();
    assertThat(response.getRulesByKey()).doesNotContainKey("python:XPath");
  }

  @Test
  void onlyLoadRulesOfEnabledLanguages() throws ExecutionException, InterruptedException {
    client = newFakeClient().build();
    var backendBuilder = newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVASCRIPT)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PHP)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.KOTLIN);

    if (COMMERCIAL_ENABLED) {
      backendBuilder = backendBuilder.withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.CFAMILY);
    }
    backend = backendBuilder.build(client);

    var enabledLanguages = EnumSet.of(JAVA, JS, PHP, PYTHON, TS, XML, KOTLIN);

    if (COMMERCIAL_ENABLED) {
      enabledLanguages.add(C);
    }
    var response = backend.getRulesService().listAllStandaloneRulesDefinitions().get();
    assertThat(response.getRulesByKey().values())
      .flatExtracting(RuleDefinitionDto::getLanguage)
      .containsAll(enabledLanguages);
  }

  @Test
  void simpleJavaNoHotspots(@TempDir Path baseDir) throws Exception {
    var fooDir = Files.createDirectory(baseDir.resolve("foo"));
    var inputFile = createFile(fooDir, "Foo.java",
      "package foo;\n"
        + "public class Foo {\n"
        + "  String ip = \"192.168.12.42\"; // Hotspots should not be reported in SonarLint\n"
        + "}");

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);
    backend.getRulesService()
      .updateStandaloneRulesConfiguration(new UpdateStandaloneRulesConfigurationParams(Map.of("java:S1313", new StandaloneRuleConfigDto(true, emptyMap()))));

    analyzeFilesAndVerifyNoIssues(List.of(inputFile.toUri()), client, backend, CONFIGURATION_SCOPE_ID);
  }

  @Test
  void simpleJavaPomXml(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, "pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<project>\n"
        + "  <modelVersion>4.0.0</modelVersion>\n"
        + "  <groupId>com.foo</groupId>\n"
        + "  <artifactId>bar</artifactId>\n"
        + "  <version>${pom.version}</version>\n"
        + "</project>");
    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, i -> i.getTextRange().getStartLine(), RaisedIssueDto::getSeverity).containsOnly(
      tuple("xml:S3421", 6, IssueSeverity.MINOR));
  }

  @Test
  void supportJavaSuppressWarning(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  @SuppressWarnings(\"java:S106\")\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}");
    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange, RaisedIssueDto::getSeverity)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S1220", null, IssueSeverity.MINOR),
        tuple("java:S1481", new TextRangeDto(4, 8, 4, 9), IssueSeverity.MINOR));
  }

  @Test
  void simpleJavaWithBytecode() {
    var projectWithByteCode = new File("src/test/projects/java-with-bytecode").getAbsoluteFile().toPath();
    var inputFile = projectWithByteCode.resolve("src/Foo.java");
    var binFile = projectWithByteCode.resolve("bin/Foo.class");

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), projectWithByteCode.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true),
        new ClientFileDto(binFile.toUri(), projectWithByteCode.relativize(binFile), CONFIGURATION_SCOPE_ID, false, null, binFile, null, null, false)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(CONFIGURATION_SCOPE_ID, analysisId, List.of(inputFile.toUri()), Map.of("sonar.java.binaries", projectWithByteCode.resolve("bin").toString()), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIGURATION_SCOPE_ID)).isNotEmpty());
    var issues = client.getRaisedIssuesForScopeIdAsList(CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(5, 2, 5, 12)),
        tuple("java:S1220", null),
        tuple("java:S1144", new TextRangeDto(8, 14, 8, 17)),
        tuple("java:S1186", new TextRangeDto(8, 14, 8, 17)));
  }

  @Test
  void simpleJavaWithExcludedRules(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}");

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);
    backend.getRulesService()
      .updateStandaloneRulesConfiguration(new UpdateStandaloneRulesConfigurationParams(Map.of("java:S106", new StandaloneRuleConfigDto(false, emptyMap()))));

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange, RaisedIssueDto::getSeverity)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S1220", null, IssueSeverity.MINOR),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), IssueSeverity.MINOR));
  }

  @Test
  void simpleJavaWithExcludedRulesUsingDeprecatedKey(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}");

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);
    backend.getRulesService()
      .updateStandaloneRulesConfiguration(new UpdateStandaloneRulesConfigurationParams(Map.of("squid:S106", new StandaloneRuleConfigDto(false, emptyMap()))));

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange, RaisedIssueDto::getSeverity)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S1220", null, IssueSeverity.MINOR),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), IssueSeverity.MINOR));

    assertThat(client.getLogMessages()).contains("Rule 'java:S106' was excluded using its deprecated key 'squid:S106'. Please fix your configuration.");
  }

  @Test
  void simpleJavaWithIncludedRules(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, A_JAVA_FILE_PATH,
      "import java.util.Optional;\n"
        + "public class Foo {\n"
        + "  public void foo(Optional<String> name) {  // for squid:3553, not in Sonar Way\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\" + name.isPresent());\n"
        + "  }\n"
        + "}");
    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);
    backend.getRulesService()
      .updateStandaloneRulesConfiguration(new UpdateStandaloneRulesConfigurationParams(Map.of("java:S3553", new StandaloneRuleConfigDto(true, emptyMap()))));

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange, RaisedIssueDto::getSeverity)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S3553", new TextRangeDto(3, 18, 3, 34), IssueSeverity.MAJOR),
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14), IssueSeverity.MAJOR),
        tuple("java:S1220", null, IssueSeverity.MINOR),
        tuple("java:S1481", new TextRangeDto(4, 8, 4, 9), IssueSeverity.MINOR));
  }

  @Test
  void simpleJavaWithIncludedRulesUsingDeprecatedKey(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, A_JAVA_FILE_PATH,
      "import java.util.Optional;\n"
        + "public class Foo {\n"
        + "  public void foo(Optional<String> name) {  // for squid:3553, not in Sonar Way\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\" + name.isPresent());\n"
        + "  }\n"
        + "}");
    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);
    backend.getRulesService()
      .updateStandaloneRulesConfiguration(new UpdateStandaloneRulesConfigurationParams(Map.of("squid:S3553", new StandaloneRuleConfigDto(true, emptyMap()))));

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange, RaisedIssueDto::getSeverity)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S3553", new TextRangeDto(3, 18, 3, 34), IssueSeverity.MAJOR),
        tuple("java:S106", new TextRangeDto(5, 4, 5, 14), IssueSeverity.MAJOR),
        tuple("java:S1220", null, IssueSeverity.MINOR),
        tuple("java:S1481", new TextRangeDto(4, 8, 4, 9), IssueSeverity.MINOR));

    assertThat(client.getLogMessages()).contains("Rule 'java:S3553' was included using its deprecated key 'squid:S3553'. Please fix your configuration.");
  }

  @Disabled("Rule java:S1228 is not reported: Add a 'package-info.java' file to document the 'foo' package")
  @Test
  void simpleJavaWithIssueOnDir(@TempDir Path baseDir) throws Exception {
    var fooDir = Files.createDirectory(baseDir.resolve("foo"));
    var inputFile = createFile(fooDir, "Foo.java",
      "package foo;\n"
        + "public class Foo {\n"
        + "}");
    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);
    backend.getRulesService()
      .updateStandaloneRulesConfiguration(new UpdateStandaloneRulesConfigurationParams(Map.of("java:S1228", new StandaloneRuleConfigDto(true, emptyMap()))));

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues)
      .extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange, RaisedIssueDto::getSeverity)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S2094", new TextRangeDto(2, 13, 2, 16), IssueSeverity.MINOR),
        tuple("java:S1228", null, IssueSeverity.MINOR));
  }

  @Test
  void simpleJavaWithSecondaryLocations(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, "Foo.java",
      "package foo;\n"
        + "public class Foo {\n"
        + "  public void method() {\n"
        + "    String S1 = \"duplicated\";\n"
        + "    String S2 = \"duplicated\";\n"
        + "    String S3 = \"duplicated\";\n"
        + "  }"
        + "}");

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues)
      .extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .contains(tuple("java:S1192", new TextRangeDto(4, 16, 4, 28)));
    assertThat(issues)
      .filteredOn(issue -> issue.getRuleKey().equals("java:S1192"))
      .flatExtracting(RaisedIssueDto::getFlows)
      .hasSize(3);
  }

  @Test
  void testJavaSurefireDontCrashAnalysis(@TempDir Path baseDir) throws Exception {
    var surefireReport = new File(baseDir.toFile(), "reports/TEST-FooTest.xml");
    FileUtils.write(surefireReport, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<testsuite name=\"FooTest\" time=\"0.121\" tests=\"1\" errors=\"0\" skipped=\"0\" failures=\"0\">\n" +
      "<testcase name=\"errorAnalysis\" classname=\"FooTest\" time=\"0.031\"/>\n" +
      "</testsuite>", StandardCharsets.UTF_8);

    var inputFile = createFile(baseDir, A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}");

    var inputFileTest = createFile(baseDir, "FooTest.java",
      "public class FooTest {\n"
        + "  public void testFoo() {\n"
        + "  }\n"
        + "}");

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true),
        new ClientFileDto(inputFileTest.toUri(), baseDir.relativize(inputFileTest), CONFIGURATION_SCOPE_ID, true, null, inputFileTest, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(CONFIGURATION_SCOPE_ID, analysisId, List.of(inputFile.toUri(), inputFileTest.toUri()), Map.of("sonar.junit.reportsPath", "reports/"), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIGURATION_SCOPE_ID)).isNotEmpty());

    var issues = client.getRaisedIssuesForScopeIdAsList(CONFIGURATION_SCOPE_ID);
    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::getTextRange)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14)),
        tuple("java:S1220", null),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9)),
        tuple("java:S2187", new TextRangeDto(1, 13, 1, 20)));
  }

  @Test
  void lazy_init_file_metadata(@TempDir Path baseDir) {
    final var inputFile = createFile(baseDir, A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}");
    var unexistingFile = new File(baseDir.toFile(), "missing.bin");
    var unexistingFilePath = unexistingFile.toPath();
    assertThat(unexistingFile).doesNotExist();

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true),
        new ClientFileDto(unexistingFilePath.toUri(), baseDir.relativize(unexistingFilePath), CONFIGURATION_SCOPE_ID, false, null, unexistingFilePath, null, null, true)
      ))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(CONFIGURATION_SCOPE_ID, analysisId, List.of(inputFile.toUri(), unexistingFilePath.toUri()), Map.of("sonar.junit.reportsPath", "reports/"), true, System.currentTimeMillis()))
      .join();

    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    assertThat(client.getLogMessages())
      .contains("Initializing metadata of file " + inputFile.toUri())
      .doesNotContain("Initializing metadata of file " + unexistingFilePath.toFile());
  }
}
