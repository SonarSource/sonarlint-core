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
import java.util.List;
import java.util.concurrent.TimeUnit;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFileListParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeOpenFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeVCSChangedFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidOpenFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.commit;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createRepository;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.modifyFile;
import static testutils.AnalysisUtils.createFile;

class AnalysysForcedByClientMediumTests {

  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";
  private SonarLintTestRpcServer backend;

  @AfterEach
  void stop() {
    if (backend != null) {
      backend.shutdown();
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
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri1, baseDir.relativize(filePath1), CONFIG_SCOPE_ID, false, null, filePath1, null, null),
        new ClientFileDto(fileUri2, baseDir.relativize(filePath2), CONFIG_SCOPE_ID, false, null, filePath2, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    backend.getAnalysisService().analyzeFileList(
        new AnalyzeFileListParams(CONFIG_SCOPE_ID, List.of(fileUri1, fileUri2)));
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).hasSize(2));

    var raisedIssues = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID);
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
        new ClientFileDto(fileUri1, baseDir.relativize(filePath1), CONFIG_SCOPE_ID, false, null, filePath1, null, null),
        new ClientFileDto(fileUri2, baseDir.relativize(filePath2), CONFIG_SCOPE_ID, false, null, filePath2, null, null),
        new ClientFileDto(fileUri3, baseDir.relativize(filePath3), CONFIG_SCOPE_ID, false, null, filePath3, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri1));
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri2));

    backend.getAnalysisService().analyzeOpenFiles(new AnalyzeOpenFilesParams(CONFIG_SCOPE_ID));
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).hasSize(2));

    var raisedIssues = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID);
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
        new ClientFileDto(fileFooUri, baseDir.relativize(fileFoo), CONFIG_SCOPE_ID, false, null, fileFoo, null, null),
        new ClientFileDto(fileBarUri, baseDir.relativize(fileBar), CONFIG_SCOPE_ID, false, null, fileBar, null, null),
        new ClientFileDto(fileBazUri, baseDir.relativize(fileBaz), CONFIG_SCOPE_ID, false, null, fileBaz, null, null),
        new ClientFileDto(fileQuxUri, baseDir.relativize(fileQux), CONFIG_SCOPE_ID, false, null, fileQux, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    backend.getAnalysisService().analyzeVCSChangedFiles(new AnalyzeVCSChangedFilesParams(CONFIG_SCOPE_ID));
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).hasSize(2));

    var raisedIssues = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID);
    assertThat(raisedIssues).hasSize(2);
  }

}
