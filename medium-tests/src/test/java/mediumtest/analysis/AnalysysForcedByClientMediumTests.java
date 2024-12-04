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
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.assertj.core.api.Assertions;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFileListParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFullProjectParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeOpenFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeVCSChangedFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidOpenFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.LogTestStartAndEnd;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.commit;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createRepository;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.modifyFile;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static testutils.AnalysisUtils.createFile;

@ExtendWith(LogTestStartAndEnd.class)
class AnalysisForcedByClientMediumTests {

  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";
  private SonarLintTestRpcServer backend;
  private ServerFixture.Server serverWithHotspots;

  @AfterEach
  void stop() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
    if (serverWithHotspots != null) {
      serverWithHotspots.shutdown();
    }
  }

  @Test
  void should_run_forced_analysis_for_list_of_files(@TempDir Path baseDir) {
    var filePath1 = createFile(baseDir, "Foo.java",
      "public interface Foo {}");
    var filePath2 = createFile(baseDir, "Bar.java",
      "public interface Bar {}");
    var fileUri1 = filePath1.toUri();
    var fileUri2 = filePath2.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri1, baseDir.relativize(filePath1), CONFIG_SCOPE_ID, false, null, filePath1, null, null, true),
        new ClientFileDto(fileUri2, baseDir.relativize(filePath2), CONFIG_SCOPE_ID, false, null, filePath2, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    backend.getAnalysisService().analyzeFileList(
      new AnalyzeFileListParams(CONFIG_SCOPE_ID, List.of(fileUri1, fileUri2)));
    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).hasSize(2));

    var raisedIssues = client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID);
    assertThat(raisedIssues).hasSize(2);
  }

  @Test
  void should_run_forced_analysis_for_open_files(@TempDir Path baseDir) {
    var filePath1 = createFile(baseDir, "Foo.java", "public interface Foo {}");
    var filePath2 = createFile(baseDir, "Bar.java", "public interface Bar {}");
    var filePath3 = createFile(baseDir, "Baz.java", "public interface Baz {}");
    var fileUri1 = filePath1.toUri();
    var fileUri2 = filePath2.toUri();
    var fileUri3 = filePath3.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileUri1, baseDir.relativize(filePath1), CONFIG_SCOPE_ID, false, null, filePath1, null, null, true),
        new ClientFileDto(fileUri2, baseDir.relativize(filePath2), CONFIG_SCOPE_ID, false, null, filePath2, null, null, true),
        new ClientFileDto(fileUri3, baseDir.relativize(filePath3), CONFIG_SCOPE_ID, false, null, filePath3, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri1));
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri2));

    backend.getAnalysisService().analyzeOpenFiles(new AnalyzeOpenFilesParams(CONFIG_SCOPE_ID));
    await().during(500, TimeUnit.MILLISECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).hasSize(2));

    var raisedIssues = client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID);
    assertThat(raisedIssues).hasSize(2);
  }

  @Test
  void should_run_forced_analysis_vcs_changed_files(@TempDir Path baseDir) throws IOException, GitAPIException {
    var git = createRepository(baseDir);

    var fileFoo = createFile(baseDir, "Foo.java", "public interface Foo {}");
    var fileBar = createFile(baseDir, "Bar.java", "");
    git.add().addFilepattern("Foo.java").call();
    git.add().addFilepattern("Bar.java").call();
    commit(git, "Foo.java");
    commit(git, "Bar.java");
    modifyFile(fileBar, "public interface Bar {}");
    var fileBaz = createFile(baseDir, "Baz.java", "public interface Baz {}");
    git.add().addFilepattern("Baz.java").call();
    var fileQux = createFile(baseDir, "Qux.java", "public interface Qux {}");
    var fileFooUri = fileFoo.toUri();
    var fileBarUri = fileBar.toUri();
    var fileBazUri = fileBaz.toUri();
    var fileQuxUri = fileQux.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileFooUri, baseDir.relativize(fileFoo), CONFIG_SCOPE_ID, false, null, fileFoo, null, null, true),
        new ClientFileDto(fileBarUri, baseDir.relativize(fileBar), CONFIG_SCOPE_ID, false, null, fileBar, null, null, true),
        new ClientFileDto(fileBazUri, baseDir.relativize(fileBaz), CONFIG_SCOPE_ID, false, null, fileBaz, null, null, true),
        new ClientFileDto(fileQuxUri, baseDir.relativize(fileQux), CONFIG_SCOPE_ID, false, null, fileQux, null, null, true)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    backend.getAnalysisService().analyzeVCSChangedFiles(new AnalyzeVCSChangedFilesParams(CONFIG_SCOPE_ID));
    await().during(500, TimeUnit.MILLISECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).hasSize(3));

    var raisedIssues = client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID);
    assertThat(raisedIssues).hasSize(3);
  }

  @Test
  void should_run_forced_full_project_analysis_only_for_hotspots(@TempDir Path baseDir) {
    var fileFoo = createFile(baseDir, "Foo.java", "public class Foo {\n" +
      "\n" +
      "  void foo() {\n" +
      "    String password = \"blue\";\n" +
      "  }\n" +
      "}\n");
    var fileBar = createFile(baseDir, "Bar.java", "");
    var fileFooUri = fileFoo.toUri();
    var fileBarUri = fileBar.toUri();

    var connectionId = "connectionId";
    var branchName = "branchName";
    var projectKey = "projectKey";
    serverWithHotspots = newSonarQubeServer("10.4")
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java")
        .withActiveRule("java:S2068", activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR)))
      .withProject(projectKey,
        project -> project
          .withQualityProfile("qpKey")
          .withBranch(branchName))
      .withPlugin(TestPlugin.JAVA)
      .start();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileFooUri, baseDir.relativize(fileFoo), CONFIG_SCOPE_ID, false, null, fileFoo, null, null, true),
        new ClientFileDto(fileBarUri, baseDir.relativize(fileBar), CONFIG_SCOPE_ID, false, null, fileBar, null, null, true)))
      .build();
    backend = newBackend()
      .withFullSynchronization()
      .withSecurityHotspotsEnabled()
      .withSonarQubeConnection(connectionId, serverWithHotspots)
      .withBoundConfigScope(CONFIG_SCOPE_ID, connectionId, projectKey)
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .build(client);
    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> Assertions.assertThat(client.getSynchronizedConfigScopeIds()).contains(CONFIG_SCOPE_ID));

    backend.getAnalysisService().analyzeFullProject(new AnalyzeFullProjectParams(CONFIG_SCOPE_ID, true));
    await().atMost(40, TimeUnit.SECONDS).untilAsserted(() ->
      assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID)).isEmpty());
    await().atMost(40, TimeUnit.SECONDS).untilAsserted(() ->
      assertThat(client.getRaisedHotspotsForScopeIdAsList(CONFIG_SCOPE_ID)).hasSize(1));

    var raisedIssuesForFoo = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID).get(fileFooUri);
    var raisedIssuesForBar = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID).get(fileBarUri);
    var raisedHotspotsForFoo = client.getRaisedHotspotsForScopeId(CONFIG_SCOPE_ID).get(fileFooUri);
    var raisedHotspotsForBar = client.getRaisedHotspotsForScopeId(CONFIG_SCOPE_ID).get(fileBarUri);
    assertThat(raisedIssuesForFoo).isEmpty();
    assertThat(raisedIssuesForBar).isEmpty();
    assertThat(raisedHotspotsForFoo).hasSize(1);
    assertThat(raisedHotspotsForBar).isEmpty();
  }

  @Test
  void should_run_forced_full_project_analysis_for_all_findings(@TempDir Path baseDir) {
    var fileFoo = createFile(baseDir, "Foo.java", "public class Foo {\n" +
      "\n" +
      "  void foo() {\n" +
      "    String password = \"blue\";\n" +
      "  }\n" +
      "}\n");
    var fileBar = createFile(baseDir, "Bar.java", "");
    var fileFooUri = fileFoo.toUri();
    var fileBarUri = fileBar.toUri();

    var connectionId = "connectionId";
    var branchName = "branchName";
    var projectKey = "projectKey";
    serverWithHotspots = newSonarQubeServer("10.4")
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java")
        .withActiveRule("java:S2068", activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR))
        .withActiveRule("java:S1220", activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR)))
      .withProject(projectKey,
        project -> project
          .withQualityProfile("qpKey")
          .withBranch(branchName))
      .withPlugin(TestPlugin.JAVA)
      .start();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileFooUri, baseDir.relativize(fileFoo), CONFIG_SCOPE_ID, false, null, fileFoo, null, null, true),
        new ClientFileDto(fileBarUri, baseDir.relativize(fileBar), CONFIG_SCOPE_ID, false, null, fileBar, null, null, true)))
      .build();
    backend = newBackend()
      .withFullSynchronization()
      .withSecurityHotspotsEnabled()
      .withSonarQubeConnection(connectionId, serverWithHotspots)
      .withBoundConfigScope(CONFIG_SCOPE_ID, connectionId, projectKey)
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .build(client);
    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> Assertions.assertThat(client.getSynchronizedConfigScopeIds()).contains(CONFIG_SCOPE_ID));

    backend.getAnalysisService().analyzeFullProject(new AnalyzeFullProjectParams(CONFIG_SCOPE_ID, false));
    await().atMost(40, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID)).hasSize(2));
    await().atMost(40, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedHotspotsForScopeIdAsList(CONFIG_SCOPE_ID)).hasSize(1));

    var raisedIssuesForFoo = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID).get(fileFooUri);
    var raisedIssuesForBar = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID).get(fileBarUri);
    var raisedHotspotsForFoo = client.getRaisedHotspotsForScopeId(CONFIG_SCOPE_ID).get(fileFooUri);
    var raisedHotspotsForBar = client.getRaisedHotspotsForScopeId(CONFIG_SCOPE_ID).get(fileBarUri);
    assertThat(raisedIssuesForFoo).hasSize(1);
    assertThat(raisedIssuesForBar).hasSize(1);
    assertThat(raisedHotspotsForFoo).hasSize(1);
    assertThat(raisedHotspotsForBar).isEmpty();
  }

  @Test
  void should_not_check_file_exclusions_for_forced_analysis(@TempDir Path baseDir) {
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
      .withFileExclusions(CONFIG_SCOPE_ID, Set.of("**/*.xml"))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);

    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));

    verify(client, never()).raiseIssues(eq(CONFIG_SCOPE_ID), any(), eq(false), any());

    backend.getAnalysisService().analyzeOpenFiles(new AnalyzeOpenFilesParams(CONFIG_SCOPE_ID));

    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID)).isNotEmpty());

    var issues = client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID);
    assertThat(issues).hasSize(1);
  }

}
