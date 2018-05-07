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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.channels.IllegalSelectorException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Answers;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.sonarlint.languageserver.SonarLintLanguageServer.*;
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
    assertThat(convert(issue)).isEmpty();
  }

  @Test
  public void testNotConvertSeverity() {
    Issue issue = mock(Issue.class);
    when(issue.getStartLine()).thenReturn(1);
    when(issue.getSeverity()).thenReturn("BLOCKER");
    assertThat(convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Error);
    when(issue.getSeverity()).thenReturn("CRITICAL");
    assertThat(convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Error);
    when(issue.getSeverity()).thenReturn("MAJOR");
    assertThat(convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn("MINOR");
    assertThat(convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Information);
    when(issue.getSeverity()).thenReturn("INFO");
    assertThat(convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Hint);
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

  @Test
  public void executeCommand_update_server_storage() throws ExecutionException, InterruptedException {
    Map<String, String> server1 = new HashMap<>();
    server1.put("serverId", "local1");
    server1.put("serverUrl", "http://localhost:9000");
    server1.put("token", "foo");
    Map<String, String> server2 = new HashMap<>();
    server2.put("serverId", "local2");
    server2.put("serverUrl", "http://localhost:9000");
    server2.put("token", "foo");

    SonarLintLanguageServer ls = newLanguageServer();
    SonarLintLanguageServer.EngineCache engineCache = mock(SonarLintLanguageServer.EngineCache.class);
    when(engineCache.getOrCreate(any())).thenReturn(mock(ConnectedSonarLintEngine.class));
    ls.setEngineCache(engineCache);

    // execute server storage update with 2 servers
    ExecuteCommandParams params = new ExecuteCommandParams();
    params.setCommand(SONARLINT_UPDATE_SERVER_STORAGE_COMMAND);
    params.setArguments(Arrays.asList(toJson(server1), toJson(server2)));
    ls.executeCommand(params).get();

    verify(engineCache).clear();
    verify(engineCache, times(2)).getOrCreate(any());

    assertThat(ls.serverInfoCache).hasSize(2);

    // execute server storage update with 1 server
    engineCache = mock(SonarLintLanguageServer.EngineCache.class);
    ls.setEngineCache(engineCache);
    params.setArguments(Collections.singletonList(toJson(server2)));
    ls.executeCommand(params).get();

    verify(engineCache).clear();
    verify(engineCache).getOrCreate(any());

    assertThat(ls.serverInfoCache).hasSize(1);

    // execute server storage update with null
    engineCache = mock(SonarLintLanguageServer.EngineCache.class);
    ls.setEngineCache(engineCache);
    params.setArguments(null);
    ls.executeCommand(params).get();

    verify(engineCache).clear();
    verifyNoMoreInteractions(engineCache);

    assertThat(ls.serverInfoCache).isEmpty();

    // restore 1 server
    engineCache = mock(SonarLintLanguageServer.EngineCache.class);
    ls.setEngineCache(engineCache);
    params.setArguments(Collections.singletonList(toJson(server2)));
    ls.executeCommand(params).get();

    verify(engineCache).clear();
    verify(engineCache).getOrCreate(any());

    assertThat(ls.serverInfoCache).hasSize(1);

    // execute server storage update with empty list
    engineCache = mock(SonarLintLanguageServer.EngineCache.class);
    ls.setEngineCache(engineCache);
    params.setArguments(Collections.emptyList());
    ls.executeCommand(params).get();

    verify(engineCache).clear();
    verifyNoMoreInteractions(engineCache);

    assertThat(ls.serverInfoCache).isEmpty();

    // restore 1 server
    engineCache = mock(SonarLintLanguageServer.EngineCache.class);
    ls.setEngineCache(engineCache);
    params.setArguments(Collections.singletonList(toJson(server2)));
    ls.executeCommand(params).get();

    verify(engineCache).clear();
    verify(engineCache).getOrCreate(any());

    assertThat(ls.serverInfoCache).hasSize(1);

    // execute server storage update with incomplete server configuration
    engineCache = mock(SonarLintLanguageServer.EngineCache.class);
    ls.setEngineCache(engineCache);
    params.setArguments(Collections.singletonList(new Gson().toJsonTree(Collections.singletonMap("foo", -1))));
    ls.executeCommand(params).get();

    verify(engineCache).clear();
    verifyNoMoreInteractions(engineCache);

    assertThat(ls.serverInfoCache).isEmpty();
  }

  @Test
  public void executeCommand_update_project_binding() throws ExecutionException, InterruptedException {
    Map<String, String> server1 = new HashMap<>();
    server1.put("serverId", "local1");
    server1.put("serverUrl", "http://localhost:9000");
    server1.put("token", "foo");

    SonarLintLanguageServer ls = newLanguageServer();
    SonarLintLanguageServer.EngineCache engineCache = mock(SonarLintLanguageServer.EngineCache.class);
    ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
    when(engineCache.getOrCreate(any())).thenReturn(engine);
    ls.setEngineCache(engineCache);

    // set some servers
    ExecuteCommandParams updateServerParams = new ExecuteCommandParams();
    updateServerParams.setCommand(SONARLINT_UPDATE_SERVER_STORAGE_COMMAND);
    updateServerParams.setArguments(Collections.singletonList(toJson(server1)));
    ls.executeCommand(updateServerParams).get();

    Map<String, String> binding = new HashMap<>();
    binding.put("serverId", "local1");
    binding.put("projectKey", "project1");

    // update binding; happy path
    ExecuteCommandParams updateBindingParams = new ExecuteCommandParams();
    updateBindingParams.setCommand(SONARLINT_UPDATE_PROJECT_BINDING_COMMAND);
    updateBindingParams.setArguments(Collections.singletonList(toJson(binding)));
    ls.executeCommand(updateBindingParams).get();

    assertThat(ls.binding).isNotNull();
    verify(engine).updateModule(any(), any(), any());

    // binding to non-existent server clears binding
    binding.put("serverId", "nonexistent");
    updateBindingParams.setArguments(null);
    // TODO
//    updateBindingParams.setArguments(Collections.singletonList(toJson(binding)));
    // TODO update binding replaces binding
    // TODO invalid server or binding falls back to no binding
    ls.executeCommand(updateBindingParams).get();

    assertThat(ls.binding).isNull();
  }

  private JsonElement toJson(Map<String, String> map) {
    return new Gson().toJsonTree(map);
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
