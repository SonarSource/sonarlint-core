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
package mediumtest.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonar.api.utils.System2;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidSkipLoadingPluginParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AnalysisMediumTests {

  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";
  private SonarLintTestRpcServer backend;
  private String javaVersion;

  @BeforeEach
  public void setUp() {
    javaVersion = System2.INSTANCE.property("java.specification.version");
  }

  @AfterEach
  void stop() {
    System2.INSTANCE.setProperty("java.specification.version", javaVersion);
    if (backend != null) {
      backend.shutdown();
    }
  }

  @Test
  void it_should_skip_analysis_if_no_file_provided(@TempDir Path tempDir) {
    var client = newFakeClient().build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .build(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService()
      .analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, analysisId, List.of(tempDir.resolve("File.java").toUri()), Map.of(), System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    verify(client, never()).didRaiseIssue(eq(CONFIG_SCOPE_ID), eq(analysisId), any());
  }

  @Test
  void it_should_analyze_xml_file_in_standalone_mode(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<project>\n"
        + "  <modelVersion>4.0.0</modelVersion>\n"
        + "  <groupId>com.foo</groupId>\n"
        + "  <artifactId>bar</artifactId>\n"
        + "  <version>${pom.version}</version>\n"
        + "</project>");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    var rawIssueCaptor = ArgumentCaptor.forClass(RawIssueDto.class);
    verify(client).didRaiseIssue(eq(CONFIG_SCOPE_ID), eq(analysisId), rawIssueCaptor.capture());
    var rawIssue = rawIssueCaptor.getValue();
    assertThat(rawIssue.getSeverity()).isEqualTo(IssueSeverity.MINOR);
    assertThat(rawIssue.getType()).isEqualTo(RuleType.CODE_SMELL);
    assertThat(rawIssue.getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.CONVENTIONAL);
    assertThat(rawIssue.getImpacts()).isEqualTo(Map.of(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.LOW));
    assertThat(rawIssue.getRuleKey()).isEqualTo("xml:S3421");
    assertThat(rawIssue.getPrimaryMessage()).isEqualTo("Replace \"pom.version\" with \"project.version\".");
    assertThat(rawIssue.getFileUri()).isEqualTo(fileUri);
    assertThat(rawIssue.getFlows()).isEmpty();
    assertThat(rawIssue.getQuickFixes()).isEmpty();
    assertThat(rawIssue.getTextRange()).usingRecursiveComparison().isEqualTo(new TextRangeDto(6, 11, 6, 25));
    assertThat(rawIssue.getRuleDescriptionContextKey()).isNull();
    assertThat(rawIssue.getVulnerabilityProbability()).isNull();
  }

  @Test
  void it_should_analyze_xml_file_in_connected_mode(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<project>\n"
        + "  <modelVersion>4.0.0</modelVersion>\n"
        + "  <groupId>com.foo</groupId>\n"
        + "  <artifactId>bar</artifactId>\n"
        + "  <version>${pom.version}</version>\n"
        + "</project>");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withPlugin(TestPlugin.XML).withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "BLOCKER"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .build(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    var rawIssueCaptor = ArgumentCaptor.forClass(RawIssueDto.class);
    verify(client).didRaiseIssue(eq(CONFIG_SCOPE_ID), eq(analysisId), rawIssueCaptor.capture());
    var rawIssue = rawIssueCaptor.getValue();
    assertThat(rawIssue.getSeverity()).isEqualTo(IssueSeverity.BLOCKER);
    assertThat(rawIssue.getRuleKey()).isEqualTo("xml:S3421");
  }

  @Test
  void it_should_notify_client_on_plugin_skip(@TempDir Path baseDir) {
    System2.INSTANCE.setProperty("java.specification.version", "10");
    var filePath = createFile(baseDir, "Main.java",
      "public class Main {}");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    verify(client, timeout(200)).didSkipLoadingPlugin(CONFIG_SCOPE_ID, Language.JAVA, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_JRE, "11", "10");
  }

  @Test
  void it_should_notify_client_on_secret_detection(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "secret.py",
      "KEY = \"AKIAIGKECZXA7AEIJLMQ\"");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.TEXT)
      .build(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    verify(client).didDetectSecret();
  }

  @Test
  void it_should_report_issues_for_multi_file_analysis_taking_data_from_module_filesystem(@TempDir Path baseDir) {
    var fileIssue = createFile(baseDir, "fileIssue.py",
      "from fileFuncDef import foo\n" +
        "foo(1,2,3)\n");
    var fileFuncDef = createFile(baseDir, "fileFuncDef.py",
      "def foo(a):\n" +
        "    print(a)\n");
    var fileIssueUri = fileIssue.toUri();
    var fileFuncDefUri = fileFuncDef.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileIssueUri, baseDir.relativize(fileIssue), CONFIG_SCOPE_ID, false, null, fileIssue, null, null),
        new ClientFileDto(fileFuncDefUri, baseDir.relativize(fileFuncDef), CONFIG_SCOPE_ID, false, null, fileFuncDef, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    var rawIssueCaptor = ArgumentCaptor.forClass(RawIssueDto.class);
    verify(client, timeout(2000)).didRaiseIssue(eq(CONFIG_SCOPE_ID), eq(analysisId), rawIssueCaptor.capture());
    var rawIssue = rawIssueCaptor.getValue();
    assertThat(rawIssue.getSeverity()).isEqualTo(IssueSeverity.BLOCKER);
    assertThat(rawIssue.getRuleKey()).isEqualTo("python:S930");
  }

  @Test
  void it_should_report_multi_file_issues_for_files_added_after_initialization(@TempDir Path baseDir) {
    var fileIssue = createFile(baseDir, "fileIssue.py",
      "from fileFuncDef import foo\n" +
        "foo(1,2,3)\n");
    var fileFuncDef = createFile(baseDir, "fileFuncDef.py",
      "def foo(a):\n" +
        "    print(a)\n");
    var fileIssueUri = fileIssue.toUri();
    var fileFuncDefUri = fileFuncDef.toUri();
    var client = newFakeClient()
      .build();
    backend = newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    var rawIssueCaptor = ArgumentCaptor.forClass(RawIssueDto.class);
    verify(client, times(0)).didRaiseIssue(eq(CONFIG_SCOPE_ID), eq(analysisId), rawIssueCaptor.capture());

    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
      new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, false, CONFIG_SCOPE_ID, null))));
    backend.getFileService().didUpdateFileSystem(
      new DidUpdateFileSystemParams(List.of(), List.of(new ClientFileDto(fileIssueUri, baseDir.relativize(fileIssue), CONFIG_SCOPE_ID, false, null, fileIssue, null, null),
        new ClientFileDto(fileFuncDefUri, baseDir.relativize(fileFuncDef), CONFIG_SCOPE_ID, false, null, fileFuncDef, null, null))));

    result = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    rawIssueCaptor = ArgumentCaptor.forClass(RawIssueDto.class);
    verify(client, timeout(2000)).didRaiseIssue(eq(CONFIG_SCOPE_ID), eq(analysisId), rawIssueCaptor.capture());
    var rawIssue = rawIssueCaptor.getValue();
    assertThat(rawIssue.getSeverity()).isEqualTo(IssueSeverity.BLOCKER);
    assertThat(rawIssue.getRuleKey()).isEqualTo("python:S930");
  }

  @Test
  void it_should_report_issues_for_multi_file_analysis_only_for_leaf_config_scopes(@TempDir Path baseDir) {
    var file1Issue = createFile(baseDir, "file1Issue.py",
      "from file1FuncDef import foo\n" +
        "foo(1,2,3)\n");
    var file1FuncDef = createFile(baseDir, "file1FuncDef.py",
      "def foo(a):\n" +
        "    print(a)\n");
    var file2Issue = createFile(baseDir, "file2Issue.py",
      "from file2FuncDef import foo\n" +
        "foo(1,2,3)\n");
    var file2FuncDef = createFile(baseDir, "file2FuncDef.py",
      "def foo(a):\n" +
        "    print(a)\n");
    var file1IssueUri = file1Issue.toUri();
    var file1FuncDefUri = file1FuncDef.toUri();
    var file2IssueUri = file2Issue.toUri();
    var file2FuncDefUri = file2FuncDef.toUri();
    var parentConfigScope = "parentConfigScope";
    var leafConfigScope = "leafConfigScope";
    var client = newFakeClient()
      .withInitialFs(parentConfigScope, baseDir, List.of(new ClientFileDto(file1IssueUri, baseDir.relativize(file1Issue), parentConfigScope, false, null, file1Issue, null, null),
        new ClientFileDto(file1FuncDefUri, baseDir.relativize(file1FuncDef), parentConfigScope, false, null, file1FuncDef, null, null)))
      .withInitialFs(leafConfigScope, baseDir, List.of(new ClientFileDto(file2IssueUri, baseDir.relativize(file2Issue), leafConfigScope, false, null, file2Issue, null, null),
        new ClientFileDto(file2FuncDefUri, baseDir.relativize(file2FuncDef), leafConfigScope, false, null, file2FuncDef, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(parentConfigScope)
      .withUnboundConfigScope(leafConfigScope, leafConfigScope, parentConfigScope)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build(client);
    var analysisId = UUID.randomUUID();

    var leafConfigScopeResult = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(leafConfigScope, analysisId,
      List.of(file2IssueUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(leafConfigScopeResult.getFailedAnalysisFiles()).isEmpty();
    var rawIssueCaptor = ArgumentCaptor.forClass(RawIssueDto.class);
    verify(client).didRaiseIssue(eq(leafConfigScope), eq(analysisId), rawIssueCaptor.capture());
    var rawIssue = rawIssueCaptor.getValue();
    assertThat(rawIssue.getSeverity()).isEqualTo(IssueSeverity.BLOCKER);
    assertThat(rawIssue.getRuleKey()).isEqualTo("python:S930");

    var parentConfigScopeResult = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(parentConfigScope,
      analysisId, List.of(file1IssueUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(parentConfigScopeResult.getFailedAnalysisFiles()).isEmpty();
    verify(client, times(0)).didRaiseIssue(eq(parentConfigScope), eq(analysisId), rawIssueCaptor.capture());
  }

  @Test
  void it_should_update_module_file_system_on_file_events_creating_file(@TempDir Path baseDir) {
    var fileIssue = createFile(baseDir, "fileIssue.py",
      "from fileFuncDef import foo\n" +
        "foo(1,2,3)\n");
    var fileIssueUri = fileIssue.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileIssueUri, baseDir.relativize(fileIssue),
        CONFIG_SCOPE_ID, false, null, fileIssue, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build(client);
    var analysisId = UUID.randomUUID();

    var parentConfigScopeResult = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(parentConfigScopeResult.getFailedAnalysisFiles()).isEmpty();
    var rawIssueCaptor = ArgumentCaptor.forClass(RawIssueDto.class);
    verify(client, times(0)).didRaiseIssue(eq(CONFIG_SCOPE_ID), eq(analysisId), rawIssueCaptor.capture());

    var fileFuncDef = createFile(baseDir, "fileFuncDef.py",
      "def foo(a):\n" +
        "    print(a)\n");
    var fileFuncDefUri = fileFuncDef.toUri();
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(),
      List.of(new ClientFileDto(fileFuncDefUri, baseDir.relativize(fileFuncDef), CONFIG_SCOPE_ID, false, null, fileFuncDef, null, null))));

    parentConfigScopeResult = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID,
      analysisId, List.of(fileIssueUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(parentConfigScopeResult.getFailedAnalysisFiles()).isEmpty();
    rawIssueCaptor = ArgumentCaptor.forClass(RawIssueDto.class);
    verify(client).didRaiseIssue(eq(CONFIG_SCOPE_ID), eq(analysisId), rawIssueCaptor.capture());
    var rawIssue = rawIssueCaptor.getValue();
    assertThat(rawIssue.getSeverity()).isEqualTo(IssueSeverity.BLOCKER);
    assertThat(rawIssue.getRuleKey()).isEqualTo("python:S930");
  }

  @Disabled
  @Test
  void it_should_update_module_file_system_on_file_events_deleting_file(@TempDir Path baseDir) {
    var fileIssue = createFile(baseDir, "fileIssue.py",
      "from fileFuncDef import foo\n" +
        "foo(1,2,3)\n");
    var fileFuncDef = createFile(baseDir, "fileFuncDef.py",
      "def foo(a):\n" +
        "    print(a)\n");
    var fileIssueUri = fileIssue.toUri();
    var fileFuncDefUri = fileFuncDef.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileIssueUri, baseDir.relativize(fileIssue),
        CONFIG_SCOPE_ID, false, null, fileIssue, null, null),
        new ClientFileDto(fileFuncDefUri, baseDir.relativize(fileFuncDef),
          CONFIG_SCOPE_ID, false, null, fileFuncDef, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    var rawIssueCaptor = ArgumentCaptor.forClass(RawIssueDto.class);
    verify(client).didRaiseIssue(eq(CONFIG_SCOPE_ID), eq(analysisId), rawIssueCaptor.capture());
    var rawIssue = rawIssueCaptor.getValue();
    assertThat(rawIssue.getSeverity()).isEqualTo(IssueSeverity.BLOCKER);
    assertThat(rawIssue.getRuleKey()).isEqualTo("python:S930");

    removeFile(baseDir, "fileFuncDef.py");
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(fileFuncDefUri), List.of()));

    result = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    rawIssueCaptor = ArgumentCaptor.forClass(RawIssueDto.class);
    verify(client, times(0)).didRaiseIssue(eq(CONFIG_SCOPE_ID), eq(analysisId), rawIssueCaptor.capture());
  }

  @Disabled
  @Test
  void it_should_update_module_file_system_on_file_events_editing_file(@TempDir Path baseDir) {
    var fileIssue = createFile(baseDir, "fileIssue.py",
      "from fileFuncDef import foo\n" +
        "foo(1,2,3)\n");
    var fileFuncDef = createFile(baseDir, "fileFuncDef.py",
      "def foo(a):\n" +
        "    print(a)\n");
    var fileIssueUri = fileIssue.toUri();
    var fileFuncDefUri = fileFuncDef.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileIssueUri, baseDir.relativize(fileIssue),
        CONFIG_SCOPE_ID, false, null, fileIssue, null, null),
        new ClientFileDto(fileFuncDefUri, baseDir.relativize(fileFuncDef),
          CONFIG_SCOPE_ID, false, null, fileFuncDef, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .build(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    var rawIssueCaptor = ArgumentCaptor.forClass(RawIssueDto.class);
    verify(client).didRaiseIssue(eq(CONFIG_SCOPE_ID), eq(analysisId), rawIssueCaptor.capture());
    var rawIssue = rawIssueCaptor.getValue();
    assertThat(rawIssue.getSeverity()).isEqualTo(IssueSeverity.BLOCKER);
    assertThat(rawIssue.getRuleKey()).isEqualTo("python:S930");

    editFile(baseDir, "fileFuncDef.py", "");
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(),
      List.of(new ClientFileDto(fileFuncDefUri, baseDir.relativize(fileFuncDef),
        CONFIG_SCOPE_ID, false, null, fileFuncDef, "", null))));

    result = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, analysisId,
      List.of(fileIssueUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    rawIssueCaptor = ArgumentCaptor.forClass(RawIssueDto.class);
    verify(client, times(0)).didRaiseIssue(eq(CONFIG_SCOPE_ID), eq(analysisId), rawIssueCaptor.capture());
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
}
