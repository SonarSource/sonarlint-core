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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.Answers;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalUpdateRequiredException;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.sonarlint.languageserver.SonarLintLanguageServer.CONNECTED_MODE_PROJECT_PROP;
import static org.sonarlint.languageserver.SonarLintLanguageServer.CONNECTED_MODE_SERVERS_PROP;
import static org.sonarlint.languageserver.SonarLintLanguageServer.SONARLINT_UPDATE_PROJECT_BINDING_COMMAND;
import static org.sonarlint.languageserver.SonarLintLanguageServer.SONARLINT_UPDATE_SERVER_STORAGE_COMMAND;
import static org.sonarlint.languageserver.SonarLintLanguageServer.convert;
import static org.sonarlint.languageserver.SonarLintLanguageServer.findBaseDir;
import static org.sonarlint.languageserver.SonarLintLanguageServer.getStoragePath;
import static org.sonarlint.languageserver.SonarLintLanguageServer.parseWorkspaceFolders;

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
    Function<SonarLintLanguageClient, ClientLogger> loggerFactory = client -> new FakeClientLogger();
    SonarLintLanguageServer server = new SonarLintLanguageServer(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(),
      engineCacheFactory, loggerFactory);

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
    when(params.getInitializationOptions()).thenReturn(new JsonObject());

    Path basedir = Paths.get("path/to/base").toAbsolutePath();
    Path subFolder = basedir.resolve("sub");
    Path file = subFolder.resolve("file.java");

    List<WorkspaceFolder> ordering1 = Stream.of(subFolder.toString(), basedir.toString()).map(SonarLintLanguageServerTest::mockWorkspaceFolder)
      .collect(Collectors.toList());
    when(params.getWorkspaceFolders()).thenReturn(ordering1);
    ls.initialize(params);
    assertThat(ls.findBaseDir(file.toUri())).isEqualTo(subFolder);

    List<WorkspaceFolder> ordering2 = Stream.of(basedir.toString(), subFolder.toString()).map(SonarLintLanguageServerTest::mockWorkspaceFolder).collect(Collectors.toList());
    when(params.getWorkspaceFolders()).thenReturn(ordering2);
    ls.initialize(params);
    assertThat(ls.findBaseDir(file.toUri())).isEqualTo(subFolder);
  }

  @Test
  public void parseWorkspaceFolders_ignores_rootUri_when_folders_are_present() {
    List<String> folderPaths = Arrays.asList("path/to/base", "other/path");
    List<WorkspaceFolder> folders = folderPaths.stream().map(SonarLintLanguageServerTest::mockWorkspaceFolder).collect(Collectors.toList());
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
    assertThat(tester.logs()).isEmpty();
    assertThat(tester.lastEngine().standalone).isTrue();
  }

  @Test
  public void analyze_in_connected_mode_using_the_specified_server_at_initialization() {
    LanguageServerTester tester = newLanguageServerTester();
    String serverId = "local1";
    tester.setInitialServers("foo", "bar", serverId, "baz");
    tester.setInitialBinding("local1", "project1");
    tester.initialize();

    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.logs()).isEmpty();
    assertThat(tester.lastEngine().standalone).isFalse();
    assertThat(tester.lastEngine().serverId).isEqualTo(serverId);
  }

  @Test
  public void analyze_in_connected_mode_using_the_specified_server_by_update() {
    LanguageServerTester tester = newLanguageServerTester();
    tester.initialize();

    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.logs()).isEmpty();
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
    assertThat(tester.logs()).isEmpty();
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
    assertThat(tester.logs()).isEmpty();
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
    assertThat(tester.logs()).isEmpty();
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
      assertThat(tester.logs()).containsExactly(ClientLogger.ErrorType.INCOMPLETE_SERVER_CONFIG);

      tester.updateServers(serverConfig);
      assertThat(tester.logs()).containsExactly(ClientLogger.ErrorType.INCOMPLETE_SERVER_CONFIG, ClientLogger.ErrorType.INCOMPLETE_SERVER_CONFIG);
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
      assertThat(tester.logs()).containsExactly(ClientLogger.ErrorType.INCOMPLETE_BINDING);

      tester.updateBinding(binding);
      assertThat(tester.logs()).containsExactly(ClientLogger.ErrorType.INCOMPLETE_BINDING, ClientLogger.ErrorType.INCOMPLETE_BINDING);
    }
  }

  @Test
  public void warn_when_binding_references_non_existent_server_on_startup_or_update() {
    LanguageServerTester tester = newLanguageServerTester();

    Map<String, String> binding = ImmutableMap.<String, String>builder()
      .put("serverId", "foo")
      .put("projectKey", "bar")
      .build();

    tester.setInitialBinding(binding);
    tester.initialize();
    assertThat(tester.logs()).containsExactly(ClientLogger.ErrorType.INVALID_BINDING_SERVER);

    tester.updateBinding(binding);
    assertThat(tester.logs()).containsExactly(ClientLogger.ErrorType.INVALID_BINDING_SERVER, ClientLogger.ErrorType.INVALID_BINDING_SERVER);
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
    assertThat(tester.logs()).isEmpty();
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
    assertThat(tester.logs()).isEmpty();
    assertThat(tester.lastEngine().standalone).isTrue();

    Map<String, String> binding = ImmutableMap.<String, String>builder()
      .put("serverId", serverId)
      .put("projectKey", "bar")
      .build();
    tester.updateBinding(binding);

    // fix binding -> connected analysis
    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.logs()).isEmpty();
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

    assertThat(tester.engine(serverId)).isNull();

    ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class, withSettings().defaultAnswer(Answers.RETURNS_MOCKS));
    tester.setEngine(serverId, engine);
    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    verify(engine).analyze(any(), any(), any(), any());
    verify(engine).downloadServerIssues(any(), any());
    verifyNoMoreInteractions(engine);

    engine = mock(ConnectedSonarLintEngine.class, withSettings().defaultAnswer(Answers.RETURNS_MOCKS));
    when(engine.analyze(any(), any(), any(), any()))
      .thenThrow(new GlobalUpdateRequiredException("foo"))
      .thenReturn(mock(AnalysisResults.class));
    tester.setEngine(serverId, engine);
    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.logs()).isEmpty();
    verify(engine).update(any(), any());
    verify(engine).updateModule(any(), any(), any());
    verify(engine, times(2)).analyze(any(), any(), any(), any());
    verify(engine).downloadServerIssues(any(), any());
    verifyNoMoreInteractions(engine);

    engine = mock(ConnectedSonarLintEngine.class, withSettings().defaultAnswer(Answers.RETURNS_MOCKS));
    when(engine.analyze(any(), any(), any(), any()))
      .thenThrow(new StorageException("foo", false))
      .thenReturn(mock(AnalysisResults.class));
    tester.setEngine(serverId, engine);
    tester.analyze();
    assertThat(tester.lastWasSuccess()).isTrue();
    assertThat(tester.logs()).isEmpty();
    verify(engine).updateModule(any(), any(), any());
    verify(engine, times(2)).analyze(any(), any(), any(), any());
    verify(engine).downloadServerIssues(any(), any());
    verifyNoMoreInteractions(engine);

    engine = mock(ConnectedSonarLintEngine.class, withSettings().defaultAnswer(Answers.RETURNS_MOCKS));
    when(engine.analyze(any(), any(), any(), any())).thenThrow(new RuntimeException("Unkown analysis error"));
    tester.setEngine(serverId, engine);
    tester.analyze();
    assertThat(tester.lastWasSuccess()).isFalse();
    assertThat(tester.logs()).containsExactly(ClientLogger.ErrorType.ANALYSIS_FAILED);
    verify(engine, times(1)).analyze(any(), any(), any(), any());
    verifyNoMoreInteractions(engine);
  }

  @Test
  public void update_module_storage_on_update() {
    LanguageServerTester tester = newLanguageServerTester();
    String serverId = "local1";
    String projectKey = "project1";
    tester.setInitialServers(serverId);
    tester.setInitialBinding(serverId, projectKey);
    tester.initialize();

    // do not update module storage on startup
    assertThat(tester.engine(serverId)).isNull();

    Map<String, String> binding = ImmutableMap.<String, String>builder()
      .put("serverId", serverId)
      .put("projectKey", projectKey)
      .build();
    tester.updateBinding(binding);
    verify(tester.lastEngine().connectedEngine, times(1)).updateModule(any(), eq(projectKey), any());

    tester.updateBinding(binding);
    verify(tester.lastEngine().connectedEngine, times(2)).updateModule(any(), eq(projectKey), any());
  }

  private LanguageServerTester newLanguageServerTester() {
    return new LanguageServerTester(temporaryFolder);
  }

  /**
   * Tracks the message types received during processing.
   */
  static class FakeClientLogger implements ClientLogger {
    List<ErrorType> logs = new ArrayList<>();

    @Override
    public void error(ErrorType errorType) {
      logs.add(errorType);
    }

    @Override
    public void error(ErrorType errorType, Throwable t) {
      this.error(errorType);
    }

    @Override
    public void error(String message, Throwable t) {
    }

    @Override
    public void warn(String message) {
    }

    @Override
    public void debug(String message) {
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
      if (!lastEngine.standalone) {
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

    void initialize() {
      InitializeParams params = mockInitializeParams();
      JsonObject options = new JsonObject();
      options.add(CONNECTED_MODE_SERVERS_PROP, toJson(servers));
      if (binding != null) {
        options.add(CONNECTED_MODE_PROJECT_PROP, SonarLintLanguageServerTest.toJson(binding));
      }
      params.setInitializationOptions(options);
      when(params.getInitializationOptions()).thenReturn(options);

      this.languageServer.initialize(params);
      this.initialized = true;
    }

    void analyze() {
      if (!initialized) {
        throw new IllegalStateException("Must call .initialize() before .analyze()");
      }

      fakeLogger.logs.clear();
      success = false;

      URI uri;
      try {
        uri = temporaryFolder.newFile().toPath().toUri();
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
      languageServer.analyze(uri, "dummy content");

      success = fakeLogger.logs.isEmpty();
    }

    boolean lastWasSuccess() {
      return success;
    }

    List<ClientLogger.ErrorType> logs() {
      return fakeLogger.logs;
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
      languageServer.executeCommand(params);
    }

    void setInitialBinding(String serverId, String projectKey) {
      setInitialBinding(ImmutableMap.<String, String>builder()
        .put("serverId", serverId)
        .put("projectKey", "bar")
        .build());
    }

    void setInitialBinding(Map<String, String> binding) {
      this.binding = binding;
    }

    void updateBinding(Map<String, String> binding) {
      ExecuteCommandParams params = new ExecuteCommandParams();
      params.setCommand(SONARLINT_UPDATE_PROJECT_BINDING_COMMAND);
      params.setArguments(Collections.singletonList(toJson(binding)));
      languageServer.executeCommand(params);
    }

    EngineWrapper lastEngine() {
      return fakeEngineCache.lastEngine();
    }

    EngineWrapper engine(String serverId) {
      return fakeEngineCache.getEngine(serverId);
    }

    void clearLogs() {
      fakeLogger.logs.clear();
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

  private static JsonElement toJson(Map<String, String> map) {
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
