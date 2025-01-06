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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static testutils.AnalysisUtils.createFile;
import static testutils.AnalysisUtils.getPublishedIssues;

class AnalysisTriggeringMediumTests {

  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";
  private SonarLintTestRpcServer backend;

  @AfterEach
  void stop() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
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
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false,
        null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);

    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));

    var publishedIssues = getPublishedIssues(client, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> {
        assertThat(issues)
          .extracting(RaisedIssueDto::getPrimaryMessage)
          .containsExactly("Replace \"pom.version\" with \"project.version\".");
      });
  }

  @Test
  void it_should_not_fail_an_analysis_of_windows_shortcut_file_and_skip_the_file_analysis() {
    var baseDir = new File("src/test/projects/windows-shortcut").getAbsoluteFile().toPath();
    var actualFile = Paths.get(baseDir.toString(), "hello.py");
    var windowsShortcut = Paths.get(baseDir.toString(), "hello.py.lnk");
    var fakeWindowsShortcut = Paths.get(baseDir.toString(), "hello.py.fake.lnk");

    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(actualFile.toUri(), baseDir.relativize(actualFile), CONFIG_SCOPE_ID, false, null, actualFile, null, null, true),
        new ClientFileDto(windowsShortcut.toUri(), baseDir.relativize(windowsShortcut), CONFIG_SCOPE_ID, false, null, windowsShortcut,
          null, null, true),
        new ClientFileDto(fakeWindowsShortcut.toUri(), baseDir.relativize(fakeWindowsShortcut), CONFIG_SCOPE_ID, false, null,
          fakeWindowsShortcut, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.TEXT)
      .build(client);

    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, windowsShortcut.toUri()));
    await().during(5, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isEmpty();
      assertThat(client.getLogMessages().stream()
        .filter(message -> message.startsWith("Filtered out URIs that are Windows shortcuts: "))
        .collect(Collectors.toList())
      ).isNotEmpty();
    });

    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fakeWindowsShortcut.toUri()));
    await().during(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isNotEmpty());
  }

  @Test
  void it_should_not_fail_an_analysis_of_symlink_file_and_skip_the_file_analysis(@TempDir Path baseDir) throws IOException {
    var filePath = createFile(baseDir, "pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<project>\n"
        + "  <modelVersion>4.0.0</modelVersion>\n"
        + "  <groupId>com.foo</groupId>\n"
        + "  <artifactId>bar</artifactId>\n"
        + "  <version>${pom.version}</version>\n"
        + "</project>");
    var link = Paths.get(baseDir.toString(), "pom-link.xml");
    Files.createSymbolicLink(link, filePath);
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(link.toUri(), baseDir.relativize(link), CONFIG_SCOPE_ID, false, null, link, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);

    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, link.toUri()));
    await().during(5, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isEmpty();
      assertThat(client.getLogMessages().stream()
        .filter(message -> message.startsWith("Filtered out URIs that are symbolic links: "))
        .collect(Collectors.toList())
      ).isNotEmpty();
    });
  }

  @Test
  void it_should_analyze_open_file_on_content_change(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", "");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false,
        null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));
    var publishedIssues = getPublishedIssues(client, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> assertThat(issues).isEmpty());
    reset(client);

    backend.getFileService()
      .didUpdateFileSystem(new DidUpdateFileSystemParams(
        Collections.emptyList(),
        List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, "<?xml version=\"1.0\" " +
          "encoding=\"UTF-8\"?>\n"
          + "<project>\n"
          + "  <modelVersion>4.0.0</modelVersion>\n"
          + "  <groupId>com.foo</groupId>\n"
          + "  <artifactId>bar</artifactId>\n"
          + "  <version>${pom.version}</version>\n"
          + "</project>", null, true)),
        Collections.emptyList()
      ));

    publishedIssues = getPublishedIssues(client, CONFIG_SCOPE_ID);
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
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false,
        null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));
    var publishedIssues = getPublishedIssues(client, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> assertThat(issues).isEmpty());
    reset(client);
    backend.getFileService().didCloseFile(new DidCloseFileParams(CONFIG_SCOPE_ID, fileUri));

    backend.getFileService()
      .didUpdateFileSystem(new DidUpdateFileSystemParams(
        List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, "<?xml version=\"1.0\" " +
          "encoding=\"UTF-8\"?>\n"
          + "<project>\n"
          + "  <modelVersion>4.0.0</modelVersion>\n"
          + "  <groupId>com.foo</groupId>\n"
          + "  <artifactId>bar</artifactId>\n"
          + "  <version>${pom.version}</version>\n"
          + "</project>", null, true)),
        Collections.emptyList(),
        Collections.emptyList()
      ));

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
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false,
        null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withAutomaticAnalysisEnabled(false)
      .build(client);
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));

    backend.getAnalysisService().didChangeAutomaticAnalysisSetting(new DidChangeAutomaticAnalysisSettingParams(true));

    var publishedIssues = getPublishedIssues(client, CONFIG_SCOPE_ID);
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
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false,
        null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));
    var publishedIssues = getPublishedIssues(client, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> assertThat(issues).isEmpty());
    reset(client);

    backend.getRulesService().updateStandaloneRulesConfiguration(new UpdateStandaloneRulesConfigurationParams(Map.of("xml:S3420",
      new StandaloneRuleConfigDto(true, Map.of()))));

    publishedIssues = getPublishedIssues(client, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> assertThat(issues)
        .extracting(RaisedIssueDto::getPrimaryMessage)
        .containsExactly("Update this \"artifactId\" to match the provided regular expression: '[a-z][a-z-0-9]+'"));
  }

  @Test
  void it_should_not_analyze_open_files_but_should_clear_and_report_issues_when_disabling_rule(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<project>\n"
      + "  <modelVersion>4.0.0</modelVersion>\n"
      + "  <groupId>com.foo</groupId>\n"
      + "  <artifactId>bar</artifactId>\n"
      + "  <version>${pom.version}</version>\n"
      + "</project>");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false,
        null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));
    var publishedIssues = getPublishedIssues(client, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> assertThat(issues).extracting(RaisedFindingDto::getRuleKey).containsOnly("xml:S3421"));
    reset(client);

    backend.getRulesService().updateStandaloneRulesConfiguration(new UpdateStandaloneRulesConfigurationParams(Map.of("xml:S3421",
      new StandaloneRuleConfigDto(false, Map.of()))));

    //No new analysis triggered
    verify(client, never()).log(any());

    //issues related to the disabled rule has been removed and reported
    publishedIssues = getPublishedIssues(client, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> assertThat(issues).isEmpty());
  }
}
