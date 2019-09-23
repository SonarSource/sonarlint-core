/*
 * SonarLint Language Server
 * Copyright (C) 2009-2019 SonarSource SA
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

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.Answers;
import org.sonarlint.languageserver.log.ClientLogger;
import org.sonarlint.languageserver.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalStorageUpdateRequiredException;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.sonarlint.languageserver.SonarLintLanguageServer.SONARLINT_REFRESH_DIAGNOSTICS_COMMAND;
import static org.sonarlint.languageserver.SonarLintLanguageServer.SONARLINT_UPDATE_PROJECT_BINDING_COMMAND;
import static org.sonarlint.languageserver.SonarLintLanguageServer.SONARLINT_UPDATE_SERVER_STORAGE_COMMAND;
import static org.sonarlint.languageserver.SonarLintLanguageServer.convert;
import static org.sonarlint.languageserver.SonarLintLanguageServer.findBaseDir;
import static org.sonarlint.languageserver.SonarLintLanguageServer.getHtmlDescription;
import static org.sonarlint.languageserver.SonarLintLanguageServer.getStoragePath;
import static org.sonarlint.languageserver.SonarLintLanguageServer.normalizeUriString;
import static org.sonarlint.languageserver.SonarLintLanguageServer.parseWorkspaceFolders;
import static org.sonarlint.languageserver.UserSettings.CONNECTED_MODE_PROJECT_PROP;
import static org.sonarlint.languageserver.UserSettings.CONNECTED_MODE_SERVERS_PROP;
import static org.sonarlint.languageserver.UserSettings.RULES;

public class SonarLintLanguageServerTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

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
    when(issue.getMessage()).thenReturn("Do this, don't do that");
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
  public void makeQualityGateHappy() {
    BiFunction<LanguageClientLogOutput, ClientLogger, EngineCache> engineCacheFactory = (a, b) -> mock(EngineCache.class);
    Function<SonarLintExtendedLanguageClient, ClientLogger> loggerFactory = client -> new FakeClientLogger();
    SonarLintLanguageServer server = new SonarLintLanguageServer(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(),
      engineCacheFactory, loggerFactory);

    server.getWorkspaceService().didChangeWatchedFiles(null);
  }

  @Test
  public void getHtmlDescription_appends_extended_description_when_non_empty() {
    String htmlDescription = "foo";
    String extendedDescription = "bar";

    RuleDetails ruleDetails = mock(RuleDetails.class);
    when(ruleDetails.getHtmlDescription()).thenReturn(htmlDescription);
    when(ruleDetails.getExtendedDescription()).thenReturn("");

    assertThat(getHtmlDescription(ruleDetails)).isEqualTo(htmlDescription);

    when(ruleDetails.getExtendedDescription()).thenReturn(extendedDescription);
    assertThat(getHtmlDescription(ruleDetails)).isEqualTo(htmlDescription + "<div>" + extendedDescription + "</div>");
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
    when(params.getTrace()).thenReturn(null);
    when(params.getInitializationOptions()).thenReturn(new JsonObject());
    ls.initialize(params).join();
    verify(params).getInitializationOptions();
  }

  @Test
  public void initialize_should_not_crash_when_client_doesnt_support_folders() {
    SonarLintLanguageServer ls = newLanguageServer();
    InitializeParams params = mockInitializeParams();
    when(params.getTrace()).thenReturn(null);
    when(params.getInitializationOptions()).thenReturn(new JsonObject());
    when(params.getWorkspaceFolders()).thenReturn(null);
    ls.initialize(params).join();
    verify(params).getInitializationOptions();
    verify(params).getWorkspaceFolders();
  }

  @Test
  public void findBaseDir_returns_correct_folder_when_exists() {
    Path basedir = Paths.get("path/to/base").toAbsolutePath();
    Path file = basedir.resolve("some/sub/file.java");
    List<String> folders = Arrays.asList(
      Paths.get("other/path").toAbsolutePath().toString(),
      basedir.toString(),
      Paths.get("other/path2").toString());
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
      Paths.get("other/path2").toAbsolutePath().toString());
    assertThat(findBaseDir(folders, file.toUri())).isEqualTo(file.getParent());
  }

  @Test
  public void findBaseDir_finds_longest_match() {
    SonarLintLanguageServer ls = newLanguageServer();
    InitializeParams params = mockInitializeParams();
    when(params.getTrace()).thenReturn(null);
    when(params.getInitializationOptions()).thenReturn(new JsonObject());

    String basedir = "file:///path/to/base";
    String subFolder = basedir + "/sub";
    URI file = URI.create(subFolder + "/file.java");

    List<WorkspaceFolder> ordering1 = Stream.of(subFolder, basedir)
      .map(SonarLintLanguageServerTest::mockWorkspaceFolder)
      .collect(Collectors.toList());
    when(params.getWorkspaceFolders()).thenReturn(ordering1);
    ls.initialize(params);
    assertThat(ls.findBaseDir(file).toString()).isEqualTo(normalizeUriString(subFolder));

    List<WorkspaceFolder> ordering2 = Stream.of(basedir, subFolder)
      .map(SonarLintLanguageServerTest::mockWorkspaceFolder)
      .collect(Collectors.toList());
    when(params.getWorkspaceFolders()).thenReturn(ordering2);
    ls.initialize(params);
    assertThat(ls.findBaseDir(file).toString()).isEqualTo(normalizeUriString(subFolder));
  }

  @Test
  public void parseWorkspaceFolders_ignores_rootUri_when_folders_are_present() {
    List<String> folderPaths = Arrays.asList("file:///path/to/base", "file:///other/path");
    List<String> normalizedFolderPaths = folderPaths.stream().map(SonarLintLanguageServer::normalizeUriString).collect(Collectors.toList());
    List<WorkspaceFolder> folders = folderPaths.stream().map(SonarLintLanguageServerTest::mockWorkspaceFolder).collect(Collectors.toList());
    assertThat(parseWorkspaceFolders(folders, "foo")).isEqualTo(normalizedFolderPaths);
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
    assertThat(parseWorkspaceFolders(null, null)).isEmpty();
  }

  static class EngineWrapper {
    final StandaloneSonarLintEngine standaloneEngine;
    final ConnectedSonarLintEngine connectedEngine;
    final String serverId;
    final boolean standalone;

    EngineWrapper(StandaloneSonarLintEngine standaloneEngine) {
      this.standaloneEngine = standaloneEngine;
      this.connectedEngine = null;
      this.serverId = null;
      this.standalone = true;
    }

    EngineWrapper(ConnectedSonarLintEngine connectedEngine, String serverId) {
      this.standaloneEngine = null;
      this.connectedEngine = connectedEngine;
      this.serverId = serverId;
      this.standalone = false;
    }

    static EngineWrapper standalone() {
      StandaloneSonarLintEngine engine = mock(StandaloneSonarLintEngine.class, withSettings().defaultAnswer(Answers.RETURNS_MOCKS));
      return new EngineWrapper(engine);
    }

    static EngineWrapper connected(String serverId) {
      ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class, withSettings().defaultAnswer(Answers.RETURNS_MOCKS));
      return new EngineWrapper(engine, serverId);
    }
  }

  @Test
  public void analyze_in_standalone_mode_when_not_bound() {
    LanguageServerTester tester = newLanguageServerTester();
    tester.initialize();

    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.loggedErrors()).isEmpty();
    assertThat(tester.debugMessages()).doesNotContain("Local rules settings are ignored, using quality profile from server");
    assertThat(tester.lastEngine().standalone).isTrue();
  }

  @Test
  public void analyze_in_connected_mode_using_the_specified_server_at_initialization() {
    LanguageServerTester tester = newLanguageServerTester();
    String serverId = "local1";
    tester.setInitialServers("foo", "bar", serverId, "baz");
    tester.setInitialBinding("local1", "project1");
    tester.initialize("xoo:SomeRule");

    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.loggedErrors()).isEmpty();
    assertThat(tester.debugMessages()).contains("Local rules settings are ignored, using quality profile from server");
    assertThat(tester.lastEngine().standalone).isFalse();
    assertThat(tester.lastEngine().serverId).isEqualTo(serverId);
  }

  @Test
  public void analyze_in_connected_mode_using_the_specified_server_by_update() {
    LanguageServerTester tester = newLanguageServerTester();
    tester.initialize();

    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.loggedErrors()).isEmpty();
    assertThat(tester.lastEngine().standalone).isTrue();

    String serverId = "local1";
    Map<String, String> server = ImmutableMap.<String, String>builder()
      .put("serverId", serverId)
      .put("serverUrl", "bar")
      .put("token", "baz")
      .build();
    Map<String, String> binding = ImmutableMap.<String, String>builder()
      .put("serverId", serverId)
      .put("projectKey", "bar")
      .build();

    tester.updateServers(server);
    tester.updateBinding(binding);

    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.loggedErrors()).isEmpty();
    assertThat(tester.lastEngine().standalone).isFalse();
    assertThat(tester.lastEngine().serverId).isEqualTo(serverId);
  }

  @Test
  public void analyze_in_standalone_mode_when_binding_incomplete() {
    LanguageServerTester tester = newLanguageServerTester();
    tester.setInitialServers("foo", "bar", "baz");
    tester.setInitialBinding("local1", "");
    tester.initialize();

    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.loggedErrors()).isEmpty();
    assertThat(tester.lastEngine().standalone).isTrue();
  }

  @Test
  public void analyze_in_standalone_mode_when_binding_references_nonexistent_server() {
    LanguageServerTester tester = newLanguageServerTester();
    tester.setInitialServers("foo", "bar", "baz");
    tester.setInitialBinding("local1", "project1");
    tester.initialize();

    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.loggedErrors()).isEmpty();
    assertThat(tester.lastEngine().standalone).isTrue();
  }

  @Test
  public void warn_when_server_configuration_is_incomplete_on_startup_or_update() {
    LanguageServerTester tester = newLanguageServerTester();

    Map<String, String> serverWithoutToken = ImmutableMap.<String, String>builder()
      .put("serverId", "foo")
      .put("serverUrl", "bar")
      .build();

    Map<String, String> serverWithoutUrl = ImmutableMap.<String, String>builder()
      .put("serverId", "foo")
      .put("token", "baz")
      .build();

    Map<String, String> serverWithoutServerId = ImmutableMap.<String, String>builder()
      .put("serverUrl", "bar")
      .put("token", "baz")
      .build();

    List<Map<String, String>> incompleteConfigs = Arrays.asList(serverWithoutServerId, serverWithoutUrl, serverWithoutToken);

    for (Map<String, String> serverConfig : incompleteConfigs) {
      tester.clearLogs();

      tester.setInitialServers(serverConfig);
      tester.initialize();
      assertThat(tester.loggedErrors()).containsExactly(ClientLogger.ErrorType.INCOMPLETE_SERVER_CONFIG);

      tester.updateServers(serverConfig);
      assertThat(tester.loggedErrors()).containsExactly(ClientLogger.ErrorType.INCOMPLETE_SERVER_CONFIG, ClientLogger.ErrorType.INCOMPLETE_SERVER_CONFIG);
    }
  }

  @Test
  public void warn_when_binding_is_incomplete_on_startup_or_update() {
    LanguageServerTester tester = newLanguageServerTester();

    Map<String, String> bindingWithoutServerId = Collections.singletonMap("projectKey", "bar");
    Map<String, String> bindingWithoutProjectKey = Collections.singletonMap("serverId", "foo");

    List<Map<String, String>> incompleteConfigs = Arrays.asList(bindingWithoutServerId, bindingWithoutProjectKey);

    for (Map<String, String> binding : incompleteConfigs) {
      tester.clearLogs();

      tester.setInitialBinding(binding);
      tester.initialize();
      assertThat(tester.loggedErrors()).containsExactly(ClientLogger.ErrorType.INCOMPLETE_BINDING);

      tester.updateBinding(binding);
      assertThat(tester.loggedErrors()).containsExactly(ClientLogger.ErrorType.INCOMPLETE_BINDING, ClientLogger.ErrorType.INCOMPLETE_BINDING);
    }
  }

  @Test
  public void do_not_warn_when_binding_is_empty_on_startup_or_update() {
    LanguageServerTester tester = newLanguageServerTester();

    tester.setInitialBinding(Collections.emptyMap());
    tester.initialize();
    assertThat(tester.loggedErrors()).isEmpty();

    tester.updateBinding(Collections.emptyMap());
    assertThat(tester.loggedErrors()).isEmpty();
  }

  @Test
  public void warn_when_binding_references_nonexistent_server_on_startup_or_update() {
    LanguageServerTester tester = newLanguageServerTester();

    Map<String, String> binding = ImmutableMap.<String, String>builder()
      .put("serverId", "foo")
      .put("projectKey", "bar")
      .build();

    tester.setInitialBinding(binding);
    tester.initialize();
    assertThat(tester.loggedErrors()).containsExactly(ClientLogger.ErrorType.INVALID_BINDING_SERVER);

    tester.updateBinding(binding);
    assertThat(tester.loggedErrors()).containsExactly(ClientLogger.ErrorType.INVALID_BINDING_SERVER, ClientLogger.ErrorType.INVALID_BINDING_SERVER);
  }

  @Test
  public void warn_when_cannot_start_connected_engine_on_startup_or_update() {
    LanguageServerTester tester = newLanguageServerTester();

    Map<String, String> serverConfig = ImmutableMap.<String, String>builder()
      .put("serverId", "foo")
      .put("serverUrl", "bar")
      .put("token", "baz")
      .build();

    Map<String, String> binding = ImmutableMap.<String, String>builder()
      .put("serverId", "foo")
      .put("projectKey", "bar")
      .build();

    tester.setInitialServers(serverConfig);
    tester.setInitialBinding(binding);
    tester.setEngine("foo", null);
    tester.initialize();
    assertThat(tester.loggedErrors()).containsExactly(ClientLogger.ErrorType.START_CONNECTED_ENGINE_FAILED);

    tester.updateBinding(binding);
    assertThat(tester.loggedErrors()).containsExactly(ClientLogger.ErrorType.START_CONNECTED_ENGINE_FAILED, ClientLogger.ErrorType.START_CONNECTED_ENGINE_FAILED);
  }

  @Test
  public void server_and_binding_updates_replace_previous_configuration() {
    LanguageServerTester tester = newLanguageServerTester();
    String serverId = "local1";
    tester.setInitialServers(serverId);
    tester.setInitialBinding(serverId, "project1");
    tester.initialize();

    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.loggedErrors()).isEmpty();
    assertThat(tester.lastEngine().standalone).isFalse();
    assertThat(tester.lastEngine().serverId).isEqualTo(serverId);

    serverId = "other1";
    Map<String, String> server = ImmutableMap.<String, String>builder()
      .put("serverId", serverId)
      .put("serverUrl", "bar")
      .put("token", "baz")
      .build();
    tester.updateServers(server);

    // binding is broken by replaced server -> fall back to standalone analysis
    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.loggedErrors()).isEmpty();
    assertThat(tester.lastEngine().standalone).isTrue();

    Map<String, String> binding = ImmutableMap.<String, String>builder()
      .put("serverId", serverId)
      .put("projectKey", "bar")
      .build();
    tester.updateBinding(binding);

    // fix binding -> connected analysis
    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.loggedErrors()).isEmpty();
    assertThat(tester.lastEngine().standalone).isFalse();
    assertThat(tester.lastEngine().serverId).isEqualTo(serverId);
  }

  @Test
  public void update_server_storage_on_update() {
    LanguageServerTester tester = newLanguageServerTester();
    String serverId = "local1";
    tester.setInitialServers(serverId);
    tester.initialize();

    // do not update server storage on startup
    assertThat(tester.engine(serverId)).isNull();

    Map<String, String> server = ImmutableMap.<String, String>builder()
      .put("serverId", serverId)
      .put("serverUrl", "bar")
      .put("token", "baz")
      .build();
    tester.updateServers(server);
    verify(tester.lastEngine().connectedEngine, times(1)).update(any(), any());

    tester.updateServers(server);
    verify(tester.lastEngine().connectedEngine, times(2)).update(any(), any());
  }

  @Test
  public void update_server_and_module_storage_on_failed_analysis() {
    LanguageServerTester tester = newLanguageServerTester();
    String serverId = "local1";
    tester.setInitialServers(serverId);
    tester.setInitialBinding(serverId, "project1");
    tester.initialize();

    ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class, withSettings().defaultAnswer(Answers.RETURNS_MOCKS));
    tester.setEngine(serverId, engine);
    tester.analyze(true);
    assertThat(tester.lastWasSuccess()).isTrue();
    verify(engine).getExcludedFiles(any(), any(), any(), any());
    verify(engine).analyze(any(), any(), any(), any());
    verifyNoMoreInteractions(engine);

    engine = mock(ConnectedSonarLintEngine.class, withSettings().defaultAnswer(Answers.RETURNS_MOCKS));
    when(engine.analyze(any(), any(), any(), any()))
      .thenThrow(new GlobalStorageUpdateRequiredException("foo"))
      .thenReturn(mock(AnalysisResults.class));
    tester.setEngine(serverId, engine);
    tester.analyze(true);
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.loggedErrors()).isEmpty();
    verify(engine).getExcludedFiles(any(), any(), any(), any());
    verify(engine).update(any(), any());
    verify(engine).updateProject(any(), any(), any());
    verify(engine, times(2)).analyze(any(), any(), any(), any());
    verifyNoMoreInteractions(engine);

    engine = mock(ConnectedSonarLintEngine.class, withSettings().defaultAnswer(Answers.RETURNS_MOCKS));
    when(engine.analyze(any(), any(), any(), any()))
      .thenThrow(new StorageException("foo", false))
      .thenReturn(mock(AnalysisResults.class));
    tester.setEngine(serverId, engine);
    tester.analyze(true);
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.loggedErrors()).isEmpty();
    verify(engine).getExcludedFiles(any(), any(), any(), any());
    verify(engine).updateProject(any(), any(), any());
    verify(engine, times(2)).analyze(any(), any(), any(), any());
    verifyNoMoreInteractions(engine);

    engine = mock(ConnectedSonarLintEngine.class, withSettings().defaultAnswer(Answers.RETURNS_MOCKS));
    when(engine.analyze(any(), any(), any(), any())).thenThrow(new RuntimeException("Unkown analysis error"));
    tester.setEngine(serverId, engine);
    tester.analyze();
    assertThat(tester.lastWasSuccess()).isFalse();
    assertThat(tester.loggedErrors()).containsExactly(ClientLogger.ErrorType.ANALYSIS_FAILED);
    verify(engine).getExcludedFiles(any(), any(), any(), any());
    verify(engine, times(1)).analyze(any(), any(), any(), any());
    verifyNoMoreInteractions(engine);
  }

  @Test
  public void fetch_server_issues_on_file_open_only() {
    LanguageServerTester tester = newLanguageServerTester();
    String serverId = "local1";
    tester.setInitialServers("foo", "bar", serverId, "baz");
    tester.setInitialBinding("local1", "project1");
    tester.initialize();

    Map<String, String> binding = ImmutableMap.<String, String>builder()
      .put("serverId", serverId)
      .put("projectKey", "bar")
      .build();

    Supplier<ConnectedSonarLintEngine> resetEngineWithIssue = () -> {
      ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class, withSettings().defaultAnswer(Answers.RETURNS_MOCKS));
      doAnswer(invocation -> {
        Object[] args = invocation.getArguments();
        IssueListener issueListener = (IssueListener) args[1];
        issueListener.handle(mock(Issue.class));
        return null;
      }).when(engine).analyze(any(), any(), any(), any());
      tester.setEngine(serverId, engine);
      tester.updateBinding(binding);
      verify(engine).updateProject(any(), any(), any());
      return engine;
    };

    // didChange -> do not fetch server issues
    ConnectedSonarLintEngine engine = resetEngineWithIssue.get();
    tester.analyze((ls, uri) -> {
      VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier();
      textDocument.setUri(uri.toString());
      TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent("dummy content");
      List<TextDocumentContentChangeEvent> contentChanges = Collections.singletonList(change);
      DidChangeTextDocumentParams params = new DidChangeTextDocumentParams(textDocument, contentChanges);
      ls.didChange(params);
    });
    verify(engine).calculatePathPrefixes(eq("bar"), any());
    verify(engine).getExcludedFiles(any(), any(), any(), any());
    verify(engine).analyze(any(), any(), any(), any());
    verify(engine).getServerIssues(any(), any());
    verifyNoMoreInteractions(engine);

    // didSave -> do not fetch server issues
    engine = resetEngineWithIssue.get();
    tester.analyze((ls, uri) -> {
      VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier();
      textDocument.setUri(uri.toString());
      DidSaveTextDocumentParams params = new DidSaveTextDocumentParams(textDocument, "dummy content");
      ls.didSave(params);
    });
    verify(engine).calculatePathPrefixes(eq("bar"), any());
    verify(engine).getExcludedFiles(any(), any(), any(), any());
    verify(engine).analyze(any(), any(), any(), any());
    verify(engine).getServerIssues(any(), any());
    verifyNoMoreInteractions(engine);

    // didOpen -> fetch server issues
    engine = resetEngineWithIssue.get();
    tester.analyze((ls, uri) -> {
      TextDocumentItem textDocumentItem = new TextDocumentItem();
      textDocumentItem.setUri(uri.toString());
      DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(textDocumentItem);
      ls.didOpen(params);
    });
    verify(engine).calculatePathPrefixes(eq("bar"), any());
    verify(engine).getExcludedFiles(any(), any(), any(), any());
    verify(engine).analyze(any(), any(), any(), any());
    verify(engine).downloadServerIssues(any(), any(), any());
    verifyNoMoreInteractions(engine);
  }

  @Test
  public void update_module_storage_on_update() {
    LanguageServerTester tester = newLanguageServerTester();
    String serverId = "local1";
    String projectKey1 = "project1";
    tester.setInitialServers(serverId);
    tester.setInitialBinding(serverId, projectKey1);
    tester.initialize();

    String projectKey2 = "project2";
    Map<String, String> binding = ImmutableMap.<String, String>builder()
      .put("serverId", serverId)
      .put("projectKey", projectKey2)
      .build();
    tester.updateBinding(binding);
    verify(tester.lastEngine().connectedEngine, times(1)).updateProject(any(), eq(projectKey1), any());
    verify(tester.lastEngine().connectedEngine, times(1)).updateProject(any(), eq(projectKey2), any());

    tester.updateBinding(binding);
    verify(tester.lastEngine().connectedEngine, times(2)).updateProject(any(), eq(projectKey2), any());
  }

  @Test
  public void should_update_workspace_folders_in_standalone_mode() throws IOException {
    LanguageServerTester tester = newLanguageServerTester();
    tester.initialize();

    WorkspaceFoldersChangeEvent event = new WorkspaceFoldersChangeEvent();
    WorkspaceFolder addedFolder = new WorkspaceFolder();
    addedFolder.setUri(tester.temporaryFolder.newFolder("added").toURI().toString());
    List<WorkspaceFolder> added = Collections.singletonList(addedFolder);
    event.setAdded(added);
    DidChangeWorkspaceFoldersParams params = new DidChangeWorkspaceFoldersParams(event);

    tester.languageServer.didChangeWorkspaceFolders(params);

    assertThat(tester.lastEngine()).isNull();
  }

  @Test
  public void should_update_workspace_folders_in_connected_mode() throws IOException {
    LanguageServerTester tester = newLanguageServerTester();
    String serverId = "local1";
    tester.setInitialServers(serverId);
    tester.setInitialBinding(serverId, "project1");
    tester.initialize();

    Map<String, String> server = ImmutableMap.<String, String>builder()
      .put("serverId", serverId)
      .put("serverUrl", "bar")
      .put("token", "baz")
      .build();
    tester.updateServers(server);
    verify(tester.lastEngine().connectedEngine, times(1)).update(any(), any());

    WorkspaceFoldersChangeEvent event = new WorkspaceFoldersChangeEvent();
    WorkspaceFolder addedFolder = new WorkspaceFolder();
    addedFolder.setUri(tester.temporaryFolder.newFolder("added").toURI().toString());
    List<WorkspaceFolder> added = Collections.singletonList(addedFolder);
    event.setAdded(added);
    DidChangeWorkspaceFoldersParams params = new DidChangeWorkspaceFoldersParams(event);

    tester.languageServer.didChangeWorkspaceFolders(params);
    verify(tester.lastEngine().connectedEngine, times(3)).calculatePathPrefixes(eq("project1"), any());
  }

  @Test
  public void should_reanalyze_files_upon_request() throws Exception {
    LanguageServerTester tester = newLanguageServerTester();

    ExecuteCommandParams params = new ExecuteCommandParams();
    params.setCommand(SONARLINT_REFRESH_DIAGNOSTICS_COMMAND);
    String uri1 = temporaryFolder.newFile().toPath().toUri().toString() + ".js", text1 = "'use strict';\nconsole.log('polop');";
    String uri2 = temporaryFolder.newFile().toPath().toUri().toString() + ".php", text2 = "<?php\necho 'polop';\n?>";
    Gson gson = new Gson();
    params.setArguments(Arrays.asList(
      gson.toJson(new SonarLintLanguageServer.Document(uri1, text1)),
      gson.toJson(new SonarLintLanguageServer.Document(uri2, text2))));
    tester.languageServer.executeCommand(params).join();

    assertThat(tester.fakeLogger.debugMessages).hasSize(2);
    assertThat(tester.fakeLogger.debugMessages.get(0)).contains("Analysis triggered on " + uri1);
    assertThat(tester.fakeLogger.debugMessages.get(1)).contains("Analysis triggered on " + uri2);
  }

  @Test
  public void should_reanalyze_nothing_upon_request() throws Exception {
    LanguageServerTester tester = newLanguageServerTester();

    ExecuteCommandParams params = new ExecuteCommandParams();
    params.setCommand(SONARLINT_REFRESH_DIAGNOSTICS_COMMAND);
    tester.languageServer.executeCommand(params).join();

    assertThat(tester.fakeLogger.debugMessages).isEmpty();
  }

  private LanguageServerTester newLanguageServerTester() {
    return new LanguageServerTester(temporaryFolder);
  }

  /**
   * Tracks the message types received during processing.
   */
  static class FakeClientLogger implements ClientLogger {
    List<ErrorType> errors = new ArrayList<>();

    List<String> debugMessages = new ArrayList<>();

    @Override
    public void error(ErrorType errorType) {
      errors.add(errorType);
    }

    @Override
    public void error(ErrorType errorType, Throwable t) {
      this.error(errorType);
    }

    @Override
    public void error(String message, Throwable t) {
    }

    @Override
    public void error(String message) {
    }

    @Override
    public void warn(String message) {
    }

    @Override
    public void debug(String message) {
      debugMessages.add(message);
    }

    @Override
    public void info(String message) {
    }

  }

  /**
   * Tracks the engines being used.
   */
  static class FakeEngineCache implements EngineCache {
    private EngineWrapper lastEngine;
    private Map<String, EngineWrapper> engines = new HashMap<>();

    @Override
    public StandaloneSonarLintEngine getOrCreateStandaloneEngine() {
      lastEngine = EngineWrapper.standalone();
      return lastEngine.standaloneEngine;
    }

    @Override
    public void stopStandaloneEngine() {
      if (lastEngine != null) {
        lastEngine.standaloneEngine.stop();
      }
    }

    @CheckForNull
    @Override
    public ConnectedSonarLintEngine getOrCreateConnectedEngine(ServerInfo serverInfo) {
      lastEngine = engines.computeIfAbsent(serverInfo.serverId, key -> EngineWrapper.connected(serverInfo.serverId));
      return lastEngine.connectedEngine;
    }

    @Override
    public void putExtraProperty(String name, String value) {
    }

    @Override
    public void clearConnectedEngines() {
      if (lastEngine != null && !lastEngine.standalone) {
        lastEngine = null;
      }
    }

    EngineWrapper lastEngine() {
      return lastEngine;
    }

    @CheckForNull
    EngineWrapper getEngine(String serverId) {
      return engines.get(serverId);
    }

    void setEngine(String serverId, ConnectedSonarLintEngine engine) {
      engines.put(serverId, new EngineWrapper(engine, serverId));
    }
  }

  /**
   * Configures boilerplate and tracker fakes.
   * Provides convenient helper methods for common operations.
   */
  private static class LanguageServerTester {

    private final TemporaryFolder temporaryFolder;

    private final FakeEngineCache fakeEngineCache = new FakeEngineCache();
    private final FakeClientLogger fakeLogger = new FakeClientLogger();

    private final SonarLintLanguageServer languageServer;

    private boolean initialized = false;
    private boolean success = false;
    private List<Map<String, String>> servers = new ArrayList<>();
    private Map<String, String> binding = null;

    LanguageServerTester(TemporaryFolder temporaryFolder) {
      this.temporaryFolder = temporaryFolder;
      this.languageServer = newLanguageServer(fakeEngineCache, fakeLogger);
    }

    void initialize(String... disabledRuleKeys) {
      InitializeParams params = mockInitializeParams();
      WorkspaceFolder workspaceFolder = mockWorkspaceFolder(temporaryFolder.getRoot().toURI().toString());
      when(params.getWorkspaceFolders()).thenReturn(Collections.singletonList(workspaceFolder));
      when(params.getTrace()).thenReturn(null);

      JsonObject options = new JsonObject();
      options.add(CONNECTED_MODE_SERVERS_PROP, toJson(servers));
      if (binding != null) {
        options.add(CONNECTED_MODE_PROJECT_PROP, SonarLintLanguageServerTest.toJson(binding));
      }
      if (disabledRuleKeys.length > 0) {
        ImmutableMap.Builder<String, Object> disabledRulesBuilder = ImmutableMap.builder();
        Stream.of(disabledRuleKeys).forEach(k -> disabledRulesBuilder.put(k, ImmutableMap.of("level", "off")));
        options.add(RULES, toJson(disabledRulesBuilder.build()));
      }
      params.setInitializationOptions(options);
      when(params.getInitializationOptions()).thenReturn(options);

      this.languageServer.initialize(params).join();
      this.initialized = true;
    }

    void analyze() {
      analyze(false);
    }

    void analyze(boolean shouldFetchServerIssues) {
      analyze((ls, uri) -> ls.analyze(uri, "dummy content", shouldFetchServerIssues));
    }

    void analyze(BiConsumer<SonarLintLanguageServer, URI> consumer) {
      if (!initialized) {
        throw new IllegalStateException("Must call .initialize() before .analyze()");
      }

      fakeLogger.errors.clear();
      success = false;

      URI uri;
      try {
        uri = temporaryFolder.newFile().toPath().toUri();
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
      consumer.accept(languageServer, uri);

      success = fakeLogger.errors.isEmpty();
    }

    boolean lastWasSuccess() {
      return success;
    }

    List<ClientLogger.ErrorType> loggedErrors() {
      return fakeLogger.errors;
    }

    List<String> debugMessages() {
      return fakeLogger.debugMessages;
    }

    void setInitialServers(String... serverIds) {
      List<Map<String, String>> servers = Stream.of(serverIds)
        .map(serverId -> ImmutableMap.<String, String>builder()
          .put("serverId", serverId)
          .put("serverUrl", "foo")
          .put("token", "bar")
          .build())
        .collect(Collectors.toList());
      this.servers.clear();
      this.servers.addAll(servers);
    }

    void setInitialServers(Map<String, String> server) {
      this.servers.clear();
      this.servers.add(server);
    }

    void updateServers(Map<String, String> server) {
      ExecuteCommandParams params = new ExecuteCommandParams();
      params.setCommand(SONARLINT_UPDATE_SERVER_STORAGE_COMMAND);
      params.setArguments(Collections.singletonList(toJson(server)));
      languageServer.executeCommand(params).join();
    }

    void setInitialBinding(String serverId, String projectKey) {
      setInitialBinding(ImmutableMap.<String, String>builder()
        .put("serverId", serverId)
        .put("projectKey", projectKey)
        .build());
    }

    void setInitialBinding(Map<String, String> binding) {
      this.binding = binding;
    }

    void updateBinding(Map<String, String> binding) {
      ExecuteCommandParams params = new ExecuteCommandParams();
      params.setCommand(SONARLINT_UPDATE_PROJECT_BINDING_COMMAND);
      params.setArguments(Collections.singletonList(toJson(binding)));
      languageServer.executeCommand(params).join();
    }

    EngineWrapper lastEngine() {
      return fakeEngineCache.lastEngine();
    }

    EngineWrapper engine(String serverId) {
      return fakeEngineCache.getEngine(serverId);
    }

    void clearLogs() {
      fakeLogger.errors.clear();
    }

    void setEngine(String serverId, ConnectedSonarLintEngine engine) {
      fakeEngineCache.setEngine(serverId, engine);
    }
  }

  private static JsonElement toJson(List<Map<String, String>> mapList) {
    JsonArray array = new JsonArray();
    mapList.forEach(map -> {
      JsonElement element = SonarLintLanguageServerTest.toJson(map);
      array.add(element);
    });
    return array;
  }

  private static JsonElement toJson(Map<? extends Object, ? extends Object> map) {
    return new Gson().toJsonTree(map);
  }

  private static InitializeParams mockInitializeParams() {
    return mock(InitializeParams.class, withSettings().defaultAnswer(Answers.RETURNS_MOCKS));
  }

  private static SonarLintLanguageServer newLanguageServer() {
    return newLanguageServer(new FakeEngineCache(), new FakeClientLogger());
  }

  private static SonarLintLanguageServer newLanguageServer(EngineCache engineCache, ClientLogger logger) {
    NullInputStream input = new NullInputStream(1000);
    NullOutputStream output = new NullOutputStream();
    return new SonarLintLanguageServer(input, output, (a, b) -> engineCache, c -> logger);
  }

  private static WorkspaceFolder mockWorkspaceFolder(String path) {
    WorkspaceFolder workspaceFolder = mock(WorkspaceFolder.class);
    when(workspaceFolder.getUri()).thenReturn(path);
    return workspaceFolder;
  }
}
