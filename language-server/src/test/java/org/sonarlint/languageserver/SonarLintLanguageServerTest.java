/*
 * SonarLint Language Server
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarlint.languageserver;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.IllegalSelectorException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Answers;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.sonarlint.languageserver.SonarLintLanguageServer.findBaseDir;
import static org.sonarlint.languageserver.SonarLintLanguageServer.getStoragePath;
import static org.sonarlint.languageserver.SonarLintLanguageServer.parseWorkspaceFolders;

public class SonarLintLanguageServerTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNotConvertGlobalIssues() {
    Issue issue = mock(Issue.class);
    when(issue.getStartLine()).thenReturn(null);
    assertThat(SonarLintLanguageServer.convert(issue)).isEmpty();
  }

  @Test
  public void testNotConvertSeverity() {
    Issue issue = mock(Issue.class);
    when(issue.getStartLine()).thenReturn(1);
    when(issue.getSeverity()).thenReturn("BLOCKER");
    assertThat(SonarLintLanguageServer.convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Error);
    when(issue.getSeverity()).thenReturn("CRITICAL");
    assertThat(SonarLintLanguageServer.convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Error);
    when(issue.getSeverity()).thenReturn("MAJOR");
    assertThat(SonarLintLanguageServer.convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn("MINOR");
    assertThat(SonarLintLanguageServer.convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Information);
    when(issue.getSeverity()).thenReturn("INFO");
    assertThat(SonarLintLanguageServer.convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Hint);
  }

  @Test
  public void makeQualityGateHappy() throws Exception {
    SonarLintLanguageServer server = new SonarLintLanguageServer(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), Collections.emptyList());
    server.error("Foo", new IllegalSelectorException());
    server.warn("Foo");
    assertThat(server.getTextDocumentService().codeLens(null)).isNull();
    assertThat(server.getTextDocumentService().completion(null)).isNull();
    assertThat(server.getTextDocumentService().definition(null)).isNull();
    assertThat(server.getTextDocumentService().documentHighlight(null)).isNull();
    assertThat(server.getTextDocumentService().documentSymbol(null)).isNull();
    assertThat(server.getTextDocumentService().formatting(null)).isNull();
    assertThat(server.getTextDocumentService().hover(null)).isNull();
    assertThat(server.getTextDocumentService().onTypeFormatting(null)).isNull();
    assertThat(server.getTextDocumentService().rangeFormatting(null)).isNull();
    assertThat(server.getTextDocumentService().references(null)).isNull();
    assertThat(server.getTextDocumentService().rename(null)).isNull();
    assertThat(server.getTextDocumentService().resolveCodeLens(null)).isNull();
    assertThat(server.getTextDocumentService().resolveCompletionItem(null)).isNull();
    assertThat(server.getTextDocumentService().signatureHelp(null)).isNull();

    server.getWorkspaceService().didChangeWatchedFiles(null);
    assertThat(server.getWorkspaceService().symbol(null)).isNull();
  }

  @Test
  public void getStoragePath_should_return_null_when_configuration_missing() {
    assertThat(getStoragePath(null, null)).isNull();
  }

  @Test
  public void getStoragePath_should_return_old_path_when_product_key_missing() {
    String oldStorage = "dummy";
    assertThat(getStoragePath(null, oldStorage)).isEqualTo(Paths.get(oldStorage));
  }

  @Test
  public void getStoragePath_should_return_new_path_when_product_key_present() {
    String productKey = "vim";
    assertThat(getStoragePath(productKey, "dummy")).isEqualTo(TelemetryPathManager.getPath(productKey));
  }

  @Test
  public void initialize_should_not_crash_when_disableTelemetry_param_missing() {
    SonarLintLanguageServer ls = newLanguageServer();
    InitializeParams params = mockInitializeParams();
    when(params.getInitializationOptions()).thenReturn(new JsonObject());
    ls.initialize(params);
  }

  @Test
  public void initialize_should_not_crash_when_client_doesnt_support_folders() {
    SonarLintLanguageServer ls = newLanguageServer();
    InitializeParams params = mockInitializeParams();
    when(params.getInitializationOptions()).thenReturn(new JsonObject());
    when(params.getWorkspaceFolders()).thenReturn(null);
    ls.initialize(params);
  }

  @Test
  public void findBaseDir_returns_correct_folder_when_exists() {
    Path basedir = Paths.get("path/to/base").toAbsolutePath();
    Path file = basedir.resolve("some/sub/file.java");
    List<String> folders = Arrays.asList(
      Paths.get("other/path").toAbsolutePath().toString(),
      basedir.toString(),
      Paths.get("other/path2").toString()
    );
    assertThat(findBaseDir(folders, file.toUri())).isEqualTo(basedir);
  }

  @Test
  public void findBaseDir_returns_parent_dir_when_no_folders() {
    Path basedir = Paths.get("path/to/base").toAbsolutePath();
    Path file = basedir.resolve("some/sub/file.java");
    List<String> folders = Collections.emptyList();
    assertThat(findBaseDir(folders, file.toUri())).isEqualTo(file.getParent());
  }

  @Test
  public void findBaseDir_falls_back_to_parent_dir_when_no_folder_matched() {
    Path basedir = Paths.get("path/to/base").toAbsolutePath();
    Path file = basedir.resolve("some/sub/file.java");
    Path workspaceRoot = Paths.get("other/path");
    List<String> folders = Arrays.asList(
      workspaceRoot.toAbsolutePath().toString(),
      Paths.get("other/path2").toAbsolutePath().toString()
    );
    assertThat(findBaseDir(folders, file.toUri())).isEqualTo(file.getParent());
  }

  @Test
  public void findBaseDir_finds_longest_match() {
    SonarLintLanguageServer ls = newLanguageServer();
    InitializeParams params = mockInitializeParams();
    when(params.getInitializationOptions()).thenReturn(new JsonObject());

    Path basedir = Paths.get("path/to/base").toAbsolutePath();
    Path subFolder = basedir.resolve("sub");
    Path file = subFolder.resolve("file.java");

    List<WorkspaceFolder> ordering1 = Stream.of(subFolder.toString(), basedir.toString()).map(this::mockWorkspaceFolder).collect(Collectors.toList());
    when(params.getWorkspaceFolders()).thenReturn(ordering1);
    ls.initialize(params);
    assertThat(ls.findBaseDir(file.toUri())).isEqualTo(subFolder);

    List<WorkspaceFolder> ordering2 = Stream.of(basedir.toString(), subFolder.toString()).map(this::mockWorkspaceFolder).collect(Collectors.toList());
    when(params.getWorkspaceFolders()).thenReturn(ordering2);
    ls.initialize(params);
    assertThat(ls.findBaseDir(file.toUri())).isEqualTo(subFolder);
  }

  @Test
  public void parseWorkspaceFolders_ignores_rootUri_when_folders_are_present() {
    List<String> folderPaths = Arrays.asList("path/to/base", "other/path");
    List<WorkspaceFolder> folders = folderPaths.stream().map(this::mockWorkspaceFolder).collect(Collectors.toList());
    assertThat(parseWorkspaceFolders(folders, "foo")).isEqualTo(folderPaths);
  }

  @Test
  public void parseWorkspaceFolders_falls_back_to_rootUri_when_folders_is_null_or_empty() {
    List<String> folders = Collections.singletonList("path/to/base");
    String rootUri = folders.get(0);
    assertThat(parseWorkspaceFolders(null, rootUri)).isEqualTo(folders);
    assertThat(parseWorkspaceFolders(Collections.emptyList(), rootUri)).isEqualTo(folders);
  }

  @Test
  public void parseWorkspaceFolders_does_not_crash_when_no_folders() {
    parseWorkspaceFolders(null, null);
  }

  private InitializeParams mockInitializeParams() {
    return mock(InitializeParams.class, withSettings().defaultAnswer(Answers.RETURNS_MOCKS));
  }

  private SonarLintLanguageServer newLanguageServer() {
    NullInputStream input = new NullInputStream(1000);
    NullOutputStream output = new NullOutputStream();
    SonarLintLanguageServer server = new SonarLintLanguageServer(input, output, Collections.emptyList());
    return server;
  }

  private WorkspaceFolder mockWorkspaceFolder(String path) {
    WorkspaceFolder workspaceFolder = mock(WorkspaceFolder.class);
    when(workspaceFolder.getUri()).thenReturn(path);
    return workspaceFolder;
  }
}
