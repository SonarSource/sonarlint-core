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

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangeAutomaticAnalysisSettingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidCloseFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidOpenFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.UpdateStandaloneRulesConfigurationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static testutils.AnalysisUtils.createFile;
import static testutils.AnalysisUtils.getPublishedIssues;

class AnalysisTriggeringMediumTests {

  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";
  private SonarLintTestRpcServer backend;

  @AfterEach
  void stop() {
    if (backend != null) {
      backend.shutdown();
    }
  }

  @Test
  void it_should_analyze_file_on_open(@TempDir Path baseDir) {
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
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);

    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));

    var publishedIssues = getPublishedIssues(client, null, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> {
        assertThat(issues)
          .extracting(RaisedIssueDto::getPrimaryMessage)
          .containsExactly("Replace \"pom.version\" with \"project.version\".");
      });
  }

  @Test
  void it_should_analyze_open_file_on_content_change(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", "");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));
    var publishedIssues = getPublishedIssues(client, null, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> assertThat(issues).isEmpty());
    reset(client);

    backend.getFileService()
      .didUpdateFileSystem(new DidUpdateFileSystemParams(Collections.emptyList(),
        List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<project>\n"
          + "  <modelVersion>4.0.0</modelVersion>\n"
          + "  <groupId>com.foo</groupId>\n"
          + "  <artifactId>bar</artifactId>\n"
          + "  <version>${pom.version}</version>\n"
          + "</project>", null, true))));

    publishedIssues = getPublishedIssues(client, null, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> assertThat(issues)
        .extracting(RaisedIssueDto::getPrimaryMessage)
        .containsExactly("Replace \"pom.version\" with \"project.version\"."));
  }

  @Test
  void it_should_analyze_closed_file_on_content_change(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", "");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));
    var publishedIssues = getPublishedIssues(client, null, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> assertThat(issues).isEmpty());
    reset(client);
    backend.getFileService().didCloseFile(new DidCloseFileParams(CONFIG_SCOPE_ID, fileUri));

    backend.getFileService()
      .didUpdateFileSystem(new DidUpdateFileSystemParams(Collections.emptyList(),
        List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<project>\n"
          + "  <modelVersion>4.0.0</modelVersion>\n"
          + "  <groupId>com.foo</groupId>\n"
          + "  <artifactId>bar</artifactId>\n"
          + "  <version>${pom.version}</version>\n"
          + "</project>", null, true))));

    verify(client, timeout(500).times(0)).raiseIssues(eq(CONFIG_SCOPE_ID), any(), eq(false), any());
  }

  @Test
  void it_should_analyze_open_files_when_re_enabling_automatic_analysis(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<project>\n"
      + "  <modelVersion>4.0.0</modelVersion>\n"
      + "  <groupId>com.foo</groupId>\n"
      + "  <artifactId>bar</artifactId>\n"
      + "  <version>${pom.version}</version>\n"
      + "</project>");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withAutomaticAnalysisEnabled(false)
      .build(client);
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));

    backend.getAnalysisService().didChangeAutomaticAnalysisSetting(new DidChangeAutomaticAnalysisSettingParams(true));

    var publishedIssues = getPublishedIssues(client, null, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> assertThat(issues)
        .extracting(RaisedIssueDto::getPrimaryMessage)
        .containsExactly("Replace \"pom.version\" with \"project.version\"."));
  }

  @Test
  void it_should_analyze_open_files_when_enabling_rule(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<project>\n"
      + "  <modelVersion>4.0.0</modelVersion>\n"
      + "  <groupId>com.foo</groupId>\n"
      + "  <artifactId>My_Project</artifactId>\n"
      + "</project>");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));
    var publishedIssues = getPublishedIssues(client, null, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> assertThat(issues).isEmpty());
    reset(client);

    backend.getRulesService().updateStandaloneRulesConfiguration(new UpdateStandaloneRulesConfigurationParams(Map.of("xml:S3420", new StandaloneRuleConfigDto(true, Map.of()))));

    publishedIssues = getPublishedIssues(client, null, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> assertThat(issues)
        .extracting(RaisedIssueDto::getPrimaryMessage)
        .containsExactly("Update this \"artifactId\" to match the provided regular expression: '[a-z][a-z-0-9]+'"));
  }
}
