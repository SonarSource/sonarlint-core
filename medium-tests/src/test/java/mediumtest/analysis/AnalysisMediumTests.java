/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource SA
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
package mediumtest.analysis;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import mediumtest.analysis.sensor.ThrowingSensorConstructor;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonar.api.utils.System2;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.commons.LogTestStartAndEnd;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.testutils.GitUtils;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangeAnalysisPropertiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangePathToCompileCommandsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetAnalysisConfigParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.ShouldUseEnterpriseCSharpAnalyzerParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidOpenFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidSkipLoadingPluginParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ProgressEndNotification;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.OnDiskTestClientInputFile;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.sonarsource.sonarlint.core.test.utils.plugins.SonarPluginBuilder.newSonarPlugin;
import static utils.AnalysisUtils.waitForRaisedIssues;

@ExtendWith(LogTestStartAndEnd.class)
class AnalysisMediumTests {

  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";
  private String javaVersion;
  private static final boolean COMMERCIAL_ENABLED = System.getProperty("commercial") != null;

  @BeforeEach
  public void setUp() {
    javaVersion = System2.INSTANCE.property("java.specification.version");
  }

  @AfterEach
  void stop() {
    System2.INSTANCE.setProperty("java.specification.version", javaVersion);
  }

  @SonarLintTest
  void it_should_skip_analysis_if_no_file_provided(SonarLintTestHarness harness, @TempDir Path tempDir) {
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(tempDir.resolve("File.java").toUri()), Map.of(), false, System.currentTimeMillis()))
      .join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    await().during(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID)).isEmpty());
  }

  @SonarLintTest
  void should_not_raise_issues_for_previously_analysed_files_if_they_were_not_submitted_for_analysis(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var fileFooPath = createFile(baseDir, "Foo.java",
      """
        public class Foo {
          public void foo() {
            int x;
            System.out.println("Foo");
          }
        }""");
    var fileBarPath = createFile(baseDir, "Bar.java",
      """
        public class Bar {
          public void foo() {
            int x;
            System.out.println("Foo");
          }
        }""");
    var fileFooUri = fileFooPath.toUri();
    var fileBarUri = fileBarPath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileFooUri, baseDir.relativize(fileFooPath), CONFIG_SCOPE_ID, false, null, fileFooPath, null, null, true),
        new ClientFileDto(fileBarUri, baseDir.relativize(fileBarPath), CONFIG_SCOPE_ID, false, null, fileBarPath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withDisabledPluginsForAnalysis(SonarLanguage.JAVA.getPluginKey())
      .start(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileFooUri), Map.of(), true, System.currentTimeMillis())).join();
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isNotEmpty());
    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    var issues = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID);
    assertThat(issues).containsOnlyKeys(fileFooUri);

    client.cleanRaisedIssues();

    result = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileBarUri), Map.of(), true, System.currentTimeMillis())).join();
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isNotEmpty());

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    issues = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID);
    assertThat(issues).containsOnlyKeys(fileBarUri);
  }

  @SonarLintTest
  void it_should_analyze_xml_file_in_standalone_mode(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.foo</groupId>
          <artifactId>bar</artifactId>
          <version>${pom.version}</version>
        </project>""");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .start(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    var raisedIssueDto = awaitRaisedIssuesNotification(client, CONFIG_SCOPE_ID).get(0);
    assertThat(raisedIssueDto.getSeverityMode().isRight()).isTrue();
    assertThat(raisedIssueDto.getSeverityMode().getRight().getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.CONVENTIONAL);
    assertThat(raisedIssueDto.getSeverityMode().getRight().getImpacts())
      .extracting(ImpactDto::getSoftwareQuality, ImpactDto::getImpactSeverity)
      .containsExactly(tuple(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.LOW));
    assertThat(raisedIssueDto.getRuleKey()).isEqualTo("xml:S3421");
    assertThat(raisedIssueDto.getPrimaryMessage()).isEqualTo("Replace \"pom.version\" with \"project.version\".");
    assertThat(raisedIssueDto.getFlows()).isEmpty();
    assertThat(raisedIssueDto.getQuickFixes()).isEmpty();
    assertThat(raisedIssueDto.getTextRange()).usingRecursiveComparison().isEqualTo(new TextRangeDto(6, 11, 6, 25));
    assertThat(raisedIssueDto.getRuleDescriptionContextKey()).isNull();
  }

  @SonarLintTest
  void it_should_analyze_xml_file_in_connected_mode(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.foo</groupId>
          <artifactId>bar</artifactId>
          <version>${pom.version}</version>
        </project>""");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withPlugin(TestPlugin.XML).withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "BLOCKER"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .start(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    var raisedIssueDto = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID).get(fileUri).get(0);
    assertThat(raisedIssueDto.getSeverityMode().isRight()).isTrue();
    assertThat(raisedIssueDto.getSeverityMode().getRight().getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.CONVENTIONAL);
    assertThat(raisedIssueDto.getSeverityMode().getRight().getImpacts())
      .extracting(ImpactDto::getSoftwareQuality, ImpactDto::getImpactSeverity)
      .containsExactly(tuple(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.LOW));
    assertThat(raisedIssueDto.getRuleKey()).isEqualTo("xml:S3421");
  }

  @SonarLintTest
  void it_should_notify_client_on_plugin_skip(SonarLintTestHarness harness, @TempDir Path baseDir) {
    System2.INSTANCE.setProperty("java.specification.version", "10");
    var filePath = createFile(baseDir, "Main.java",
      "public class Main {}");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .start(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    verify(client, timeout(200)).didSkipLoadingPlugin(CONFIG_SCOPE_ID, Language.JAVA, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_JRE, "17", "10");
  }

  @SonarLintTest
  void it_should_notify_client_on_secret_detection(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "secret.py",
      "KEY = \"AKIAIGKECZXA7AEIJLMQ\"");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.TEXT)
      .start(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    verify(client, timeout(1000)).didDetectSecret(CONFIG_SCOPE_ID);
  }

  @SonarLintTest
  void it_should_notify_client_on_analysis_progress(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "secret.py",
      "KEY = \"AKIAIGKECZXA7AEIJLMQ\"");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.TEXT)
      .start(client);
    var analysisId = UUID.randomUUID();

    backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), false, System.currentTimeMillis())).join();

    verify(client).startProgress(refEq(new StartProgressParams(analysisId.toString(), CONFIG_SCOPE_ID, "Analyzing 1 file", null, true, false)));
    var reportProgressCaptor = ArgumentCaptor.forClass(ReportProgressParams.class);
    verify(client, timeout(500)).reportProgress(reportProgressCaptor.capture());
    assertThat(reportProgressCaptor.getValue())
      .usingRecursiveComparison()
      .isEqualTo(new ReportProgressParams(analysisId.toString(), new ProgressEndNotification()));
  }

  @SonarLintTest
  void it_should_print_a_log_when_the_analysis_fails(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "secret.py",
      "KEY = \"AKIAIGKECZXA7AEIJLMQ\"");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var throwingPluginPath = newSonarPlugin("python")
      .withSensor(ThrowingSensorConstructor.class)
      .generate(baseDir);
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPlugin(throwingPluginPath)
      .withEnabledLanguageInStandaloneMode(Language.PYTHON)
      .start(client);
    var analysisId = UUID.randomUUID();

    var future = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), false, System.currentTimeMillis()));

    assertThat(future)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(ResponseErrorException.class);

    assertThat(client.getLogs()).extracting(LogParams::getMessage).contains("Error during analysis");
  }

  @SonarLintTest
  void analysis_response_should_contain_raw_issues(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "secret.py",
      "KEY = \"AKIAIGKECZXA7AEIJLMQ\"");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.TEXT)
      .start(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    assertThat(result.getRawIssues()).hasSize(1);
    assertThat(result.getRawIssues().get(0).getRuleKey()).isEqualTo("secrets:S6290");
  }

  @SonarLintTest
  void it_should_report_issues_for_multi_file_analysis_taking_data_from_module_filesystem(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var fileIssue = createFile(baseDir, "fileIssue.py",
      """
        from fileFuncDef import foo
        foo(1,2,3)
        """);
    var fileFuncDef = createFile(baseDir, "fileFuncDef.py",
      """
        def foo(a):
            print(a)
        """);
    var fileIssueUri = fileIssue.toUri();
    var fileFuncDefUri = fileFuncDef.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileIssueUri, baseDir.relativize(fileIssue), CONFIG_SCOPE_ID, false, null, fileIssue, null, null, true),
        new ClientFileDto(fileFuncDefUri, baseDir.relativize(fileFuncDef), CONFIG_SCOPE_ID, false, null, fileFuncDef, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId,
        List.of(fileIssueUri), Map.of(), false, System.currentTimeMillis()))
      .join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    var raisedIssueDto = awaitRaisedIssuesNotification(client, CONFIG_SCOPE_ID).get(0);
    assertThat(raisedIssueDto.getRuleKey()).isEqualTo("python:S930");
  }

  @SonarLintTest
  void it_should_report_multi_file_issues_for_files_added_after_initialization(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var fileIssue = createFile(baseDir, "fileIssue.py",
      """
        from fileFuncDef import foo
        foo(1,2,3)
        """);
    var fileFuncDef = createFile(baseDir, "fileFuncDef.py",
      """
        def foo(a):
            print(a)
        """);
    var fileIssueUri = fileIssue.toUri();
    var fileFuncDefUri = fileFuncDef.toUri();
    var client = harness.newFakeClient()
      .build();
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    await().during(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID)).isEmpty());

    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
      new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, false, CONFIG_SCOPE_ID, null))));
    backend.getFileService().didUpdateFileSystem(
      new DidUpdateFileSystemParams(
        List.of(new ClientFileDto(fileIssueUri, baseDir.relativize(fileIssue), CONFIG_SCOPE_ID, false, null, fileIssue, null, null, true),
          new ClientFileDto(fileFuncDefUri, baseDir.relativize(fileFuncDef), CONFIG_SCOPE_ID, false, null, fileFuncDef, null, null, true)),
        List.of(),
        List.of()));

    result = backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    var raisedIssueDto = awaitRaisedIssuesNotification(client, CONFIG_SCOPE_ID).get(0);
    assertThat(raisedIssueDto.getRuleKey()).isEqualTo("python:S930");
  }

  @SonarLintTest
  void it_should_report_issues_for_multi_file_analysis_only_for_leaf_config_scopes(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var file1Issue = createFile(baseDir, "file1Issue.py",
      """
        from file1FuncDef import foo
        foo(1,2,3)
        """);
    var file1FuncDef = createFile(baseDir, "file1FuncDef.py",
      """
        def foo(a):
            print(a)
        """);
    var file2Issue = createFile(baseDir, "file2Issue.py",
      """
        from file2FuncDef import foo
        foo(1,2,3)
        """);
    var file2FuncDef = createFile(baseDir, "file2FuncDef.py",
      """
        def foo(a):
            print(a)
        """);
    var file1IssueUri = file1Issue.toUri();
    var file1FuncDefUri = file1FuncDef.toUri();
    var file2IssueUri = file2Issue.toUri();
    var file2FuncDefUri = file2FuncDef.toUri();
    var parentConfigScope = "parentConfigScope";
    var leafConfigScope = "leafConfigScope";
    var client = harness.newFakeClient()
      .withInitialFs(parentConfigScope, baseDir,
        List.of(new ClientFileDto(file1IssueUri, baseDir.relativize(file1Issue), parentConfigScope, false, null, file1Issue, null, null, true),
          new ClientFileDto(file1FuncDefUri, baseDir.relativize(file1FuncDef), parentConfigScope, false, null, file1FuncDef, null, null, true)))
      .withInitialFs(leafConfigScope, baseDir, List.of(new ClientFileDto(file2IssueUri, baseDir.relativize(file2Issue), leafConfigScope, false, null, file2Issue, null, null, true),
        new ClientFileDto(file2FuncDefUri, baseDir.relativize(file2FuncDef), leafConfigScope, false, null, file2FuncDef, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(parentConfigScope)
      .withUnboundConfigScope(leafConfigScope, leafConfigScope, parentConfigScope)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start(client);
    var analysisId = UUID.randomUUID();

    var leafConfigScopeResult = backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(leafConfigScope, analysisId,
      List.of(file2IssueUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(leafConfigScopeResult.getFailedAnalysisFiles()).isEmpty();
    var raisedIssueDto = awaitRaisedIssuesNotification(client, leafConfigScope).get(0);
    assertThat(raisedIssueDto.getRuleKey()).isEqualTo("python:S930");

    var parentConfigScopeResult = backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(parentConfigScope,
      analysisId, List.of(file1IssueUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(parentConfigScopeResult.getFailedAnalysisFiles()).isEmpty();
    await().during(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(parentConfigScope)).isEmpty());
  }

  @SonarLintTest
  void it_should_update_module_file_system_on_file_events_creating_file(SonarLintTestHarness harness, @TempDir Path tempDir) throws IOException {
    var baseDir = Files.createDirectory(tempDir.resolve("it_should_update_module_file_system_on_file_events_creating_file"));
    var fileIssue = createFile(baseDir, "fileIssue.py",
      """
        from fileFuncDef import foo
        foo(1,2,3)
        """);
    var fileIssueUri = fileIssue.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileIssueUri, baseDir.relativize(fileIssue),
        CONFIG_SCOPE_ID, false, null, fileIssue, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start(client);
    var analysisId = UUID.randomUUID();

    var parentConfigScopeResult = backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(parentConfigScopeResult.getFailedAnalysisFiles()).isEmpty();
    await().during(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID)).isEmpty());

    var fileFuncDef = createFile(baseDir, "fileFuncDef.py",
      """
        def foo(a):
            print(a)
        """);
    var fileFuncDefUri = fileFuncDef.toUri();
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(
      List.of(new ClientFileDto(fileFuncDefUri, baseDir.relativize(fileFuncDef), CONFIG_SCOPE_ID, false, null, fileFuncDef, null, null,
        true)),
      List.of(),
      List.of()));

    parentConfigScopeResult = backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID,
      analysisId, List.of(fileIssueUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(parentConfigScopeResult.getFailedAnalysisFiles()).isEmpty();
    var raisedIssueDto = awaitRaisedIssuesNotification(client, CONFIG_SCOPE_ID).get(0);
    assertThat(raisedIssueDto.getRuleKey()).isEqualTo("python:S930");
  }

  @Disabled("SLCORE-1113")
  @SonarLintTest
  void it_should_update_module_file_system_on_file_events_deleting_file(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var fileIssue = createFile(baseDir, "fileIssue.py",
      """
        from fileFuncDef import foo
        foo(1,2,3)
        """);
    var fileFuncDef = createFile(baseDir, "fileFuncDef.py",
      """
        def foo(a):
            print(a)
        """);
    var fileIssueUri = fileIssue.toUri();
    var fileFuncDefUri = fileFuncDef.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileIssueUri, baseDir.relativize(fileIssue),
        CONFIG_SCOPE_ID, false, null, fileIssue, null, null, true),
        new ClientFileDto(fileFuncDefUri, baseDir.relativize(fileFuncDef),
          CONFIG_SCOPE_ID, false, null, fileFuncDef, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    var raisedIssueDto = awaitRaisedIssuesNotification(client, CONFIG_SCOPE_ID).get(0);
    assertThat(raisedIssueDto.getSeverityMode().isLeft()).isTrue();
    assertThat(raisedIssueDto.getSeverityMode().getLeft().getSeverity()).isEqualTo(IssueSeverity.BLOCKER);
    assertThat(raisedIssueDto.getRuleKey()).isEqualTo("python:S930");

    removeFile(baseDir, "fileFuncDef.py");
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(), List.of(), List.of(fileFuncDefUri)));

    result = backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    assertThat(client.getRaisedHotspotsForScopeIdAsList(CONFIG_SCOPE_ID)).isEmpty();
  }

  @Disabled("SLCORE-1113")
  @SonarLintTest
  void it_should_update_module_file_system_on_file_events_editing_file(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var fileIssue = createFile(baseDir, "fileIssue.py",
      """
        from fileFuncDef import foo
        foo(1,2,3)
        """);
    var fileFuncDef = createFile(baseDir, "fileFuncDef.py",
      """
        def foo(a):
            print(a)
        """);
    var fileIssueUri = fileIssue.toUri();
    var fileFuncDefUri = fileFuncDef.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileIssueUri, baseDir.relativize(fileIssue),
        CONFIG_SCOPE_ID, false, null, fileIssue, null, null, true),
        new ClientFileDto(fileFuncDefUri, baseDir.relativize(fileFuncDef),
          CONFIG_SCOPE_ID, false, null, fileFuncDef, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .start(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    var raisedIssueDto = awaitRaisedIssuesNotification(client, CONFIG_SCOPE_ID).get(0);
    assertThat(raisedIssueDto.getSeverityMode().isLeft()).isTrue();
    assertThat(raisedIssueDto.getSeverityMode().getLeft().getSeverity()).isEqualTo(IssueSeverity.BLOCKER);
    assertThat(raisedIssueDto.getRuleKey()).isEqualTo("python:S930");

    editFile(baseDir, "fileFuncDef.py", "");
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(
      List.of(new ClientFileDto(fileFuncDefUri, baseDir.relativize(fileFuncDef),
        CONFIG_SCOPE_ID, false, null, fileFuncDef, "", null, true)),
      List.of(),
      List.of()));

    result = backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    assertThat(client.getRaisedHotspotsForScopeIdAsList(CONFIG_SCOPE_ID)).isEmpty();
  }

  @SonarLintTest
  void should_save_and_return_client_analysis_settings(SonarLintTestHarness harness) {
    var backend = harness.newBackend().start();
    backend.getAnalysisService().didSetUserAnalysisProperties(
      new DidChangeAnalysisPropertiesParams(CONFIG_SCOPE_ID, Map.of("key1", "user-value1", "key2", "user-value2")));

    var analysisProperties = backend.getAnalysisService().getAnalysisConfig(new GetAnalysisConfigParams(CONFIG_SCOPE_ID)).join().getAnalysisProperties();

    assertThat(analysisProperties).containsEntry("key1", "user-value1").containsEntry("key2", "user-value2");
  }

  @SonarLintTest
  void should_set_js_internal_bundlePath_if_provided(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var backend = harness.newBackend()
      .withEslintBridgeServerBundlePath(baseDir.resolve("eslint-bridge"))
      .start();

    var analysisProperties = backend.getAnalysisService().getAnalysisConfig(new GetAnalysisConfigParams(CONFIG_SCOPE_ID)).join().getAnalysisProperties();

    assertThat(analysisProperties).containsEntry("sonar.js.internal.bundlePath", baseDir.resolve("eslint-bridge").toString());
  }

  @SonarLintTest
  void should_not_set_js_internal_bundlePath_when_not_provided(SonarLintTestHarness harness) {
    var backend = harness.newBackend().start();

    var analysisProperties = backend.getAnalysisService().getAnalysisConfig(new GetAnalysisConfigParams(CONFIG_SCOPE_ID)).join().getAnalysisProperties();

    assertThat(analysisProperties).doesNotContainKey("sonar.js.internal.bundlePath");
  }

  @SonarLintTest
  void should_not_set_js_internal_bundlePath_when_no_language_specific_requirements(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withNoLanguageSpecificRequirements()
      .start();

    var analysisProperties = backend.getAnalysisService().getAnalysisConfig(new GetAnalysisConfigParams(CONFIG_SCOPE_ID)).join().getAnalysisProperties();

    assertThat(analysisProperties).doesNotContainKey("sonar.js.internal.bundlePath");
  }

  @SonarLintTest
  void it_should_skip_analysis_and_keep_rules_if_disabled_language_for_analysis(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.foo</groupId>
          <artifactId>bar</artifactId>
          <version>${pom.version}</version>
        </project>""");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withDisabledPluginsForAnalysis(SonarLanguage.XML.getPluginKey())
      .start(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    assertThat(result.getRawIssues()).isEmpty();
    await().during(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID)).isEmpty());

    var allRules = backend.getRulesService().listAllStandaloneRulesDefinitions().join();
    assertThat(allRules.getRulesByKey().keySet())
      .isNotEmpty()
      .allMatch(key -> key.startsWith("xml:"));
    var ruleDetails = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(CONFIG_SCOPE_ID, "xml:S103", null)).join();
    assertThat(ruleDetails.details().getName()).isEqualTo("Lines should not be too long");
  }

  @SonarLintTest
  void it_should_skip_analysis_only_for_disabled_language(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var xmlFilePath = createFile(baseDir, "pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.foo</groupId>
          <artifactId>bar</artifactId>
          <version>${pom.version}</version>
        </project>""");
    var xmlFileUri = xmlFilePath.toUri();
    var javaFilePath = createFile(baseDir, "Main.java",
      "public class Main {}");
    var javaFileUri = javaFilePath.toUri();

    var xmlClientFile = new ClientFileDto(xmlFileUri, baseDir.relativize(xmlFilePath), CONFIG_SCOPE_ID, false, null, xmlFilePath, null, null, true);
    var javaClientFile = new ClientFileDto(javaFileUri, baseDir.relativize(javaFilePath), CONFIG_SCOPE_ID, false, null, javaFilePath, null, null, true);
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(xmlClientFile, javaClientFile))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withDisabledPluginsForAnalysis(SonarLanguage.XML.getPluginKey())
      .start(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(xmlFileUri, javaFileUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    assertThat(result.getRawIssues())
      .hasSize(2)
      .allMatch(rawIssueDto -> rawIssueDto.getRuleKey().startsWith("java:"));
  }

  @SonarLintTest
  void should_trigger_analysis_on_path_to_compile_command_change(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    assumeTrue(COMMERCIAL_ENABLED);
    var cFile = prepareInputFile("foo.c", "#import \"foo.h\"\n", false, StandardCharsets.UTF_8, SonarLanguage.C, baseDir);
    var cFilePath = baseDir.resolve("foo.c");
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
    var cFileUri = cFile.uri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(cFileUri, baseDir.relativize(cFilePath), CONFIG_SCOPE_ID, false, null, cFilePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.CFAMILY)
      .start(client);
    backend.getAnalysisService()
      .didSetUserAnalysisProperties(new DidChangeAnalysisPropertiesParams(CONFIG_SCOPE_ID, Map.of("sonar.cfamily.build-wrapper-content", buildWrapperContent)));
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, cFileUri));
    await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID).get(cFileUri)).isNotEmpty());
    client.cleanRaisedIssues();

    backend.getAnalysisService().didChangePathToCompileCommands(new DidChangePathToCompileCommandsParams(CONFIG_SCOPE_ID, "/path"));

    var analysisConfigResponse = backend.getAnalysisService().getAnalysisConfig(new GetAnalysisConfigParams(CONFIG_SCOPE_ID)).join();
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(analysisConfigResponse.getAnalysisProperties()).containsEntry("sonar.cfamily.compile-commands", "/path"));
    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isNotEmpty());
    assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).containsOnlyKeys(cFileUri);
    assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID).get(cFileUri)).hasSize(1);
  }

  @SonarLintTest
  void should_allow_removing_compile_commands_path(SonarLintTestHarness harness) {
    assumeTrue(COMMERCIAL_ENABLED);
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.CFAMILY)
      .start();

    backend.getAnalysisService().didChangePathToCompileCommands(new DidChangePathToCompileCommandsParams(CONFIG_SCOPE_ID, null));

    var analysisConfigResponse = backend.getAnalysisService().getAnalysisConfig(new GetAnalysisConfigParams(CONFIG_SCOPE_ID)).join();
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(analysisConfigResponse.getAnalysisProperties()).containsEntry("sonar.cfamily.compile-commands", ""));
  }

  @SonarLintTest
  void it_should_unload_rules_cache_on_config_scope_closed(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.foo</groupId>
          <artifactId>bar</artifactId>
          <version>${pom.version}</version>
        </project>""");
    var filePath2 = createFile(baseDir, "pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.foo</groupId>
          <artifactId>bar</artifactId>
          <version>${pom.version}</version>
        </project>""");
    var fileUri = filePath.toUri();
    var fileUri2 = filePath2.toUri();
    var configScope2 = "configScope2";
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .withInitialFs(configScope2, baseDir, List.of(new ClientFileDto(fileUri2, baseDir.relativize(filePath2), configScope2, false, null, filePath2, null, null, true)))
      .build();
    var projectKey = "projectKey";
    var connectionId = "connectionId";
    var projectKey2 = "projectKey-2";
    var connectionId2 = "connectionId-2";
    var server = harness.newFakeSonarQubeServer().withSmartNotificationsSupported(false).start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(connectionId, server,
        storage -> storage.withPlugin(TestPlugin.XML).withProject(projectKey,
          project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "BLOCKER"))))
      .withSonarQubeConnection(connectionId2, server,
        storage -> storage.withPlugin(TestPlugin.XML).withProject(projectKey2,
          project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "BLOCKER"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, connectionId, projectKey)
      .withBoundConfigScope(configScope2, connectionId2, projectKey2)
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .withFullSynchronization()
      .start(client);
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
      new ConfigurationScopeDto(configScope2, null, true, configScope2,
        new BindingConfigurationDto(connectionId2, projectKey2, true)))));

    // analyse files to warmup caches
    var analysisId1 = UUID.randomUUID();
    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId1, List.of(fileUri), Map.of(), true, System.currentTimeMillis()))
      .join();
    var analysisId2 = UUID.randomUUID();
    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(configScope2, analysisId2, List.of(fileUri2), Map.of(), true, System.currentTimeMillis()))
      .join();

    // unload one of the projects
    backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams(configScope2));

    // expect corresponding cache to be evicted
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getLogMessages()).contains("Evict cached rules definitions for connection 'connectionId-2'"));
  }

  @SonarLintTest
  void it_should_not_unload_rules_cache_on_config_scope_closed_if_another_config_scope_still_opened(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.foo</groupId>
          <artifactId>bar</artifactId>
          <version>${pom.version}</version>
        </project>""");
    var filePath2 = createFile(baseDir, "pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.foo</groupId>
          <artifactId>bar</artifactId>
          <version>${pom.version}</version>
        </project>""");
    var filePath3 = createFile(baseDir, "pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.foo</groupId>
          <artifactId>bar</artifactId>
          <version>${pom.version}</version>
        </project>""");
    var fileUri = filePath.toUri();
    var fileUri2 = filePath2.toUri();
    var fileUri3 = filePath3.toUri();
    var configScope2 = "configScope2";
    var configScope3 = "configScope3";
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .withInitialFs(configScope2, baseDir, List.of(new ClientFileDto(fileUri2, baseDir.relativize(filePath2), configScope2, false, null, filePath2, null, null, true)))
      .withInitialFs(configScope3, baseDir, List.of(new ClientFileDto(fileUri3, baseDir.relativize(filePath3), configScope3, false, null, filePath3, null, null, true)))
      .build();
    var projectKey = "projectKey";
    var connectionId = "connectionId";
    var projectKey2 = "projectKey-2";
    var connectionId2 = "connectionId-2";
    var server = harness.newFakeSonarQubeServer().withSmartNotificationsSupported(false).start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(connectionId, server,
        storage -> storage.withPlugin(TestPlugin.XML).withProject(projectKey,
          project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "BLOCKER"))))
      .withSonarQubeConnection(connectionId2, server,
        storage -> storage.withPlugin(TestPlugin.XML).withProject(projectKey2,
          project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "BLOCKER"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, connectionId, projectKey)
      .withBoundConfigScope(configScope2, connectionId2, projectKey2)
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .withFullSynchronization()
      .start(client);
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
      new ConfigurationScopeDto(configScope2, null, true, configScope2,
        new BindingConfigurationDto(connectionId2, projectKey2, true)))));
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
      new ConfigurationScopeDto(configScope3, null, true, configScope3,
        new BindingConfigurationDto(connectionId2, projectKey2, true)))));

    // analyse files to warmup caches
    var analysisId1 = UUID.randomUUID();
    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId1, List.of(fileUri), Map.of(), true, System.currentTimeMillis()))
      .join();
    var analysisId2 = UUID.randomUUID();
    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(configScope2, analysisId2, List.of(fileUri2), Map.of(), true, System.currentTimeMillis()))
      .join();

    // unload one of the projects
    backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams(configScope2));

    // expect corresponding cache not to be evicted
    await().during(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getLogMessages())
      .doesNotContain("Evict cached rules definitions for connection 'connectionId-2'"));
  }

  @SonarLintTest
  void it_should_analyse_file_with_non_file_uri_schema(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath1 = createFile(baseDir, "Foo.java", "public interface Foo {}");
    var fileUri1 = URI.create("temp:///file/path/Foo.java");
    var filePath2 = createFile(baseDir, "Bar.java", "public interface Bar {}");
    var fileUri2 = URI.create("http:///localhost:12345/file/path/Bar.java");
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileUri1, baseDir.relativize(filePath1), CONFIG_SCOPE_ID, false, null, filePath1, null, null, true),
        new ClientFileDto(fileUri2, baseDir.relativize(filePath2), CONFIG_SCOPE_ID, false, null, filePath2, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .start(client);

    backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(fileUri1), Map.of(), false, System.currentTimeMillis())).join();

    var raisedIssues = awaitRaisedIssuesNotification(client, CONFIG_SCOPE_ID);
    assertThat(raisedIssues).hasSize(1);
    client.cleanRaisedIssues();

    backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(fileUri2), Map.of(), false, System.currentTimeMillis())).join();
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).hasSize(1));

    raisedIssues = awaitRaisedIssuesNotification(client, CONFIG_SCOPE_ID);
    assertThat(raisedIssues).hasSize(1);
  }

  @SonarLintTest
  void it_should_respect_gitignore_exclusions(SonarLintTestHarness harness, @TempDir Path baseDir) throws GitAPIException, IOException {
    var filePath = createFile(baseDir, "Foo.java", "public interface Foo {}");
    GitUtils.createRepository(baseDir);
    var gitignorePath = createFile(baseDir, ".gitignore", "*.java");
    var fileUri = filePath.toUri();
    var gitignoreUri = gitignorePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true),
        new ClientFileDto(gitignoreUri, baseDir.relativize(gitignorePath), CONFIG_SCOPE_ID, false, null, gitignorePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .start(client);

    backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(fileUri), Map.of(), false, System.currentTimeMillis())).join();
    await().during(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isEmpty());
  }

  @SonarLintTest
  void it_should_not_use_enterprise_csharp_analyzer_in_standalone(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start();

    var response = backend.getAnalysisService().shouldUseEnterpriseCSharpAnalyzer(new ShouldUseEnterpriseCSharpAnalyzerParams(CONFIG_SCOPE_ID)).join();

    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(response.shouldUseEnterpriseAnalyzer()).isFalse());
  }

  @SonarLintTest
  void it_should_not_use_enterprise_csharp_analyzer_when_connected_to_community(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.8").start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId",
        server,
        storage -> storage.withPlugin(TestPlugin.XML).withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "BLOCKER"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .withFullSynchronization()
      .start();

    var result = backend.getAnalysisService().shouldUseEnterpriseCSharpAnalyzer(new ShouldUseEnterpriseCSharpAnalyzerParams(CONFIG_SCOPE_ID)).join();

    assertThat(result.shouldUseEnterpriseAnalyzer()).isFalse();
  }

  @SonarLintTest
  void it_should_use_enterprise_csharp_analyzer_when_connected_to_community_non_repackaged(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.7").start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId",
        server,
        storage -> storage
          .withPlugin(TestPlugin.XML)
          .withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "BLOCKER")))
          .withServerVersion("10.7"))
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .withFullSynchronization()
      .start();

    var result = backend.getAnalysisService().shouldUseEnterpriseCSharpAnalyzer(new ShouldUseEnterpriseCSharpAnalyzerParams(CONFIG_SCOPE_ID)).join();

    assertThat(result.shouldUseEnterpriseAnalyzer()).isTrue();
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest, Charset encoding,
    @Nullable SonarLanguage language, Path baseDir) throws IOException {
    final var file = new File(baseDir.toFile(), relativePath);
    FileUtils.write(file, content, encoding);
    return new OnDiskTestClientInputFile(file.toPath(), relativePath, isTest, encoding, language);
  }

  private static Path createFile(Path folderPath, String fileName, String content) {
    var filePath = folderPath.resolve(fileName);
    try {
      Files.writeString(filePath, content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return filePath;
  }

  private static void editFile(Path folderPath, String fileName, String content) {
    var filePath = folderPath.resolve(fileName);
    try {
      Files.writeString(filePath, content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void removeFile(Path folderPath, String fileName) {
    var filePath = folderPath.resolve(fileName);
    try {
      Files.deleteIfExists(filePath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static List<RaisedIssueDto> awaitRaisedIssuesNotification(SonarLintBackendFixture.FakeSonarLintRpcClient client, String configScopeId) {
    waitForRaisedIssues(client, configScopeId);
    return client.getRaisedIssuesForScopeIdAsList(configScopeId);
  }
}
