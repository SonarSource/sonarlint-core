/*
 * SonarLint Language Server
 * Copyright (C) 2009-2017 SonarSource SA
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
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.commons.lang.SystemUtils;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.internal.apachecommons.io.FileUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.fail;
import static org.sonarlint.languageserver.SonarLintLanguageServer.DISABLE_TELEMETRY;
import static org.sonarlint.languageserver.SonarLintLanguageServer.TEST_FILE_PATTERN;
import static org.sonarlint.languageserver.SonarLintLanguageServer.TYPESCRIPT_LOCATION;

public class ServerMainTest {

  @ClassRule
  public static TemporaryFolder globalTemp = new TemporaryFolder();
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  private static ServerSocket serverSocket;
  private static LanguageServer lsProxy;
  private static FakeLanguageClient client;

  @BeforeClass
  public static void startServer() throws Exception {
    System.out.println("Max memory: " + Runtime.getRuntime().maxMemory());
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    serverSocket = new ServerSocket(0);
    int port = serverSocket.getLocalPort();

    client = new FakeLanguageClient();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Callable<LanguageServer> callable = () -> {
      Socket socket = serverSocket.accept();
      Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(client,
        socket.getInputStream(),
        socket.getOutputStream());
      launcher.startListening();
      return launcher.getRemoteProxy();
    };
    Future<LanguageServer> future = executor.submit(callable);
    executor.shutdown();

    String js = new File("target/plugins/javascript.jar").getAbsoluteFile().toURI().toURL().toString();
    String php = new File("target/plugins/php.jar").getAbsoluteFile().toURI().toURL().toString();
    String py = new File("target/plugins/python.jar").getAbsoluteFile().toURI().toURL().toString();
    String ts = new File("target/plugins/typescript.jar").getAbsoluteFile().toURI().toURL().toString();

    Path fakeTypeScriptProjectPath = globalTemp.newFolder().toPath();
    Path packagejson = fakeTypeScriptProjectPath.resolve("package.json");
    FileUtils.write(packagejson.toFile(), "{"
      + "\"devDependencies\": {\n" +
      "    \"typescript\": \"2.6.1\"\n" +
      "  }"
      + "}", StandardCharsets.UTF_8);
    ProcessBuilder pb = new ProcessBuilder("npm" + (SystemUtils.IS_OS_WINDOWS ? ".cmd" : ""), "install")
      .directory(fakeTypeScriptProjectPath.toFile())
      .inheritIO();
    Process process = pb.start();
    if (process.waitFor() != 0) {
      fail("Unable to run npm install");
    }

    try {
      ServerMain.main("" + port, js, php, py, ts);
    } catch (Exception e) {
      e.printStackTrace();
      future.get(1, TimeUnit.SECONDS);
      if (!future.isDone()) {
        future.cancel(true);
      }
      throw e;
    }

    lsProxy = future.get();

    InitializeParams initializeParams = new InitializeParams();
    initializeParams.setInitializationOptions(ImmutableMap.builder()
      .put(TEST_FILE_PATTERN, "{**/test/**,**/*test*,**/*Test*}")
      .put(TYPESCRIPT_LOCATION, fakeTypeScriptProjectPath.resolve("node_modules").toString())
      .put(DISABLE_TELEMETRY, true)
      .put("telemetryStorage", "not/exists")
      .put("productName", "SLCORE tests")
      .put("productVersion", "0.1")
      .build());
    lsProxy.initialize(initializeParams).get();
  }

  @AfterClass
  public static void stop() throws Exception {
    System.clearProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY);
    try {
      if (lsProxy != null) {
        lsProxy.shutdown().get();
        lsProxy.exit();
        // Give some time to the server to stop
        Thread.sleep(1000);
      }
    } finally {
      serverSocket.close();
    }
  }

  @Before
  public void cleanup() {
    client.clear();
  }

  @Test
  public void analyzeSimpleJsFileOnOpen() throws Exception {
    String uri = getUri("foo.js");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "javascript", 1, "function foo() {\n  alert('toto');\n}")));

    assertThat(waitForDiagnostics(uri))
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(1, 2, 1, 15, "javascript:S1442", "sonarlint", "Remove this usage of alert(...). (javascript:S1442)", DiagnosticSeverity.Information));
  }

  @Test
  public void analyzeSimplePythonFileOnOpen() throws Exception {
    String uri = getUri("foo.py");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "python", 1, "def foo():\n  print 'toto'\n")));

    assertThat(waitForDiagnostics(uri))
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(
        tuple(1, 2, 1, 7, "python:PrintStatementUsage", "sonarlint", "Replace print statement by built-in function. (python:PrintStatementUsage)", DiagnosticSeverity.Error));
  }

  @Test
  public void analyzeSimplePhpFileOnOpen() throws Exception {
    String uri = getUri("foo.php");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "php", 1, "<?php\nfunction foo() {\n  echo(\"Hello\");\n}\n?>")));

    assertThat(waitForDiagnostics(uri))
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(2, 2, 2, 6, "php:S2041", "sonarlint", "Remove the parentheses from this \"echo\" call. (php:S2041)", DiagnosticSeverity.Error));
  }

  @Test
  public void noIssueOnTestJSFiles() throws Exception {
    lsProxy.getWorkspaceService().didChangeConfiguration(changedConfiguration("{**/*Test*}", false));

    String fooTestUri = getUri("fooTest.js");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(fooTestUri, "javascript", 1, "function foo() {\n  alert('toto');\n}")));

    assertThat(waitForDiagnostics(fooTestUri)).isEmpty();
    client.clear();

    lsProxy.getWorkspaceService().didChangeConfiguration(changedConfiguration("{**/*MyTest*}", false));

    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(fooTestUri, "javascript", 1, "function foo() {\n  alert('toto');\n}")));
    assertThat(waitForDiagnostics(fooTestUri)).hasSize(1);

    String fooMyTestUri = getUri("fooMyTest.js");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(fooMyTestUri, "javascript", 1, "function foo() {\n  alert('toto');\n}")));

    assertThat(waitForDiagnostics(fooMyTestUri)).isEmpty();
  }

  private DidChangeConfigurationParams changedConfiguration(@Nullable String testFilePattern, boolean disableTelemetry) {
    Map<String, Object> values = new HashMap<>();
    values.put(TEST_FILE_PATTERN, testFilePattern);
    values.put(DISABLE_TELEMETRY, disableTelemetry);
    return new DidChangeConfigurationParams(ImmutableMap.of("sonarlint", values));
  }

  @Test
  public void analyzeSimpleJsFileOnChange() throws Exception {
    String uri = getUri("foo.js");
    VersionedTextDocumentIdentifier docId = new VersionedTextDocumentIdentifier(1);
    docId.setUri(uri);
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(docId, Collections.singletonList(new TextDocumentContentChangeEvent("function foo() {\n  alert('toto');\n}"))));

    assertThat(waitForDiagnostics(uri))
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(1, 2, 1, 15, "javascript:S1442", "sonarlint", "Remove this usage of alert(...). (javascript:S1442)", DiagnosticSeverity.Information));
  }

  @Test
  public void analyzeSimpleJsFileOnSave() throws Exception {
    String uri = getUri("foo.js");
    lsProxy.getTextDocumentService()
      .didSave(new DidSaveTextDocumentParams(new TextDocumentIdentifier(uri), "function foo() {\n  alert('toto');\n}"));

    assertThat(waitForDiagnostics(uri))
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(1, 2, 1, 15, "javascript:S1442", "sonarlint", "Remove this usage of alert(...). (javascript:S1442)", DiagnosticSeverity.Information));
  }

  @Test
  public void analyzeSimpleTsFileOnChange() throws Exception {
    File tsconfig = temp.newFile("tsconfig.json");
    FileUtils.write(tsconfig, "{}", StandardCharsets.UTF_8);
    String uri = getUri("foo.ts");
    VersionedTextDocumentIdentifier docId = new VersionedTextDocumentIdentifier(1);
    docId.setUri(uri);
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(docId, Collections.singletonList(new TextDocumentContentChangeEvent("function foo() {\n if(bar() && bar()) { return 42; }\n}"))));

    List<Diagnostic> diagnostics = waitForDiagnostics(uri);
    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(1, 4, 1, 18, "typescript:S1764", "sonarlint", "Correct one of the identical sub-expressions on both sides of operator \"&&\" (typescript:S1764)",
        DiagnosticSeverity.Warning));
  }

  @Test
  public void cleanDiagnosticsOnClose() throws Exception {
    String uri = getUri("foo.js");
    lsProxy.getTextDocumentService()
      .didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));

    assertThat(waitForDiagnostics(uri)).isEmpty();
  }

  @Test
  public void optOutTelemetry() throws Exception {
    lsProxy.getWorkspaceService().didChangeConfiguration(changedConfiguration(null, true));

    // Wait for the JSON RPC request to be processed
    Thread.sleep(500);
  }

  @Test
  public void testCodeActionRuleDescription() throws Exception {
    String uri = getUri("foo.js");
    VersionedTextDocumentIdentifier docId = new VersionedTextDocumentIdentifier(1);
    docId.setUri(uri);
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(docId, Collections.singletonList(new TextDocumentContentChangeEvent("function foo() {\n  alert('toto');\n}"))));

    List<? extends Command> codeActions = lsProxy.getTextDocumentService()
      .codeAction(new CodeActionParams(new TextDocumentIdentifier(uri), new Range(new Position(1, 4), new Position(1, 4)),
        new CodeActionContext(waitForDiagnostics(uri))))
      .get();

    assertThat(codeActions).hasSize(1);

    String ruleKey = (String) codeActions.get(0).getArguments().get(0);
    assertThat(ruleKey).isEqualTo("javascript:S1442");

    String ruleName = (String) codeActions.get(0).getArguments().get(1);
    assertThat(ruleName).contains("\"alert(...)\" should not be used");

    String ruleDesc = (String) codeActions.get(0).getArguments().get(2);
    assertThat(ruleDesc).contains("can be useful for debugging during development");

    String ruleType = (String) codeActions.get(0).getArguments().get(3);
    assertThat(ruleType).isEqualTo("VULNERABILITY");

    String ruleSev = (String) codeActions.get(0).getArguments().get(4);
    assertThat(ruleSev).isEqualTo("MINOR");
  }

  private String getUri(String filename) throws IOException {
    return temp.newFile(filename).toURI().toString();
  }

  private List<Diagnostic> waitForDiagnostics(String uri) throws InterruptedException {
    await().atMost(12, TimeUnit.SECONDS).until(() -> client.containsDiagnostics(uri));
    return client.getDiagnostics(uri);
  }

  private static class FakeLanguageClient implements LanguageClient {

    Map<String, List<Diagnostic>> diagnostics = new ConcurrentHashMap<>();

    void clear() {
      diagnostics.clear();
    }

    @Override
    public void telemetryEvent(Object object) {
    }

    boolean containsDiagnostics(String uri) {
      return diagnostics.containsKey(uri);
    }

    List<Diagnostic> getDiagnostics(String uri) {
      return diagnostics.get(uri);
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
      this.diagnostics.put(diagnostics.getUri(), diagnostics.getDiagnostics());
    }

    @Override
    public void showMessage(MessageParams messageParams) {

    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
      return null;
    }

    @Override
    public void logMessage(MessageParams message) {
      System.out.println(message.getMessage());
    }
  }
}
