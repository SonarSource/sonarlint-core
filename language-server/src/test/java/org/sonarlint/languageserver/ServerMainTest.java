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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.internal.apachecommons.io.IOUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.sonarlint.languageserver.SonarLintLanguageServer.DISABLE_TELEMETRY;
import static org.sonarlint.languageserver.SonarLintLanguageServer.TEST_FILE_PATTERN;

public class ServerMainTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  private static ServerSocket serverSocket;
  private static LanguageServer lsProxy;
  private static FakeLanguageClient client;

  @BeforeClass
  public static void startServer() throws Exception {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    serverSocket = new ServerSocket(0);
    int port = serverSocket.getLocalPort();

    client = new FakeLanguageClient();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Callable<LanguageServer> callable = new Callable<LanguageServer>() {
      @Override
      public LanguageServer call() throws IOException {
        Socket socket = serverSocket.accept();
        Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(client,
          socket.getInputStream(),
          socket.getOutputStream(),
          true, new PrintWriter(System.out));
        launcher.startListening();
        return launcher.getRemoteProxy();
      }
    };
    Future<LanguageServer> future = executor.submit(callable);
    executor.shutdown();

    try {
      ServerMain.main("" + port);
    } catch (Exception e) {
      e.printStackTrace();
      future.get(1, TimeUnit.SECONDS);
      if (!future.isDone()) {
        future.cancel(true);
      }
    }

    lsProxy = future.get();

    InitializeParams initializeParams = new InitializeParams();
    initializeParams.setInitializationOptions(ImmutableMap.of(
      TEST_FILE_PATTERN, "{**/test/**,**/*test*,**/*Test*}",
      "disableTelemetry", true,
      "telemetryStorage", "not/exists",
      "productName", "SLCORE tests",
      "productVersion", "0.1"));
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
        Thread.sleep(100);
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
    File tempFile = temp.newFile("foo.js");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(tempFile.toURI().toString(), "javascript", 1, "function foo() {\n  alert('toto');\n}")));

    waitForDiagnostics();

    assertThat(client.diagnosticsParamsList.get(0).getDiagnostics())
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(1, 2, 1, 15, "javascript:S1442", "sonarlint", "Remove this usage of alert(...). (javascript:S1442)", DiagnosticSeverity.Information));
  }

  @Test
  public void analyzeSimplePythonFileOnOpen() throws Exception {
    File tempFile = temp.newFile("foo.py");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(tempFile.toURI().toString(), "python", 1, "def foo():\n  print 'toto'\n")));

    waitForDiagnostics();

    assertThat(client.diagnosticsParamsList.get(0).getDiagnostics())
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(
        tuple(1, 2, 1, 7, "python:PrintStatementUsage", "sonarlint", "Replace print statement by built-in function. (python:PrintStatementUsage)", DiagnosticSeverity.Error));
  }

  @Test
  public void analyzeSimplePhpFileOnOpen() throws Exception {
    File tempFile = temp.newFile("foo.php");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(tempFile.toURI().toString(), "php", 1, "<?php\nfunction foo() {\n  echo(\"Hello\");\n}\n?>")));

    waitForDiagnostics();

    assertThat(client.diagnosticsParamsList.get(0).getDiagnostics())
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(2, 2, 2, 6, "php:S2041", "sonarlint", "Remove the parentheses from this \"echo\" call. (php:S2041)", DiagnosticSeverity.Error));
  }

  @Test
  public void noIssueOnTestJSFiles() throws Exception {
    lsProxy.getWorkspaceService().didChangeConfiguration(changedConfiguration("{**/*Test*}", false));

    File tempFile = temp.newFile("fooTest.js");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(tempFile.toURI().toString(), "javascript", 1, "function foo() {\n  alert('toto');\n}")));

    waitForDiagnostics();
    assertThat(client.diagnosticsParamsList.get(0).getDiagnostics()).isEmpty();
    client.clear();

    lsProxy.getWorkspaceService().didChangeConfiguration(changedConfiguration("{**/*MyTest*}", false));

    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(tempFile.toURI().toString(), "javascript", 1, "function foo() {\n  alert('toto');\n}")));
    waitForDiagnostics();
    assertThat(client.diagnosticsParamsList.get(0).getDiagnostics()).hasSize(1);
    client.clear();

    File anotherTempFile = temp.newFile("fooMyTest.js");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(anotherTempFile.toURI().toString(), "javascript", 1, "function foo() {\n  alert('toto');\n}")));

    waitForDiagnostics();
    assertThat(client.diagnosticsParamsList.get(0).getDiagnostics()).isEmpty();
  }

  private DidChangeConfigurationParams changedConfiguration(@Nullable String testFilePattern, boolean disableTelemetry) {
    Map<String, Object> values = new HashMap<>();
    values.put(TEST_FILE_PATTERN, testFilePattern);
    values.put(DISABLE_TELEMETRY, disableTelemetry);
    return new DidChangeConfigurationParams(ImmutableMap.of("sonarlint", values));
  }

  @Test
  public void analyzeSimpleJsFileOnChange() throws Exception {
    File tempFile = temp.newFile("foo.js");
    VersionedTextDocumentIdentifier docId = new VersionedTextDocumentIdentifier(1);
    docId.setUri(tempFile.toURI().toString());
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(docId, Arrays.asList(new TextDocumentContentChangeEvent("function foo() {\n  alert('toto');\n}"))));

    waitForDiagnostics();

    assertThat(client.diagnosticsParamsList.get(0).getDiagnostics())
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(1, 2, 1, 15, "javascript:S1442", "sonarlint", "Remove this usage of alert(...). (javascript:S1442)", DiagnosticSeverity.Information));
  }

  @Test
  public void analyzeSimpleJsFileOnSave() throws Exception {
    File tempFile = temp.newFile("foo.js");
    lsProxy.getTextDocumentService()
      .didSave(new DidSaveTextDocumentParams(new TextDocumentIdentifier(tempFile.toURI().toString()), "function foo() {\n  alert('toto');\n}"));

    waitForDiagnostics();

    assertThat(client.diagnosticsParamsList.get(0).getDiagnostics())
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(1, 2, 1, 15, "javascript:S1442", "sonarlint", "Remove this usage of alert(...). (javascript:S1442)", DiagnosticSeverity.Information));
  }

  @Test
  public void cleanDiagnosticsOnClose() throws Exception {
    File tempFile = temp.newFile("foo.js");
    lsProxy.getTextDocumentService()
      .didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(tempFile.toURI().toString())));

    waitForDiagnostics();

    assertThat(client.diagnosticsParamsList.get(0).getDiagnostics()).isEmpty();
  }

  @Test
  public void optOutTelemetry() throws Exception {
    lsProxy.getWorkspaceService().didChangeConfiguration(changedConfiguration(null, true));

    // Wait for the JSON RPC request to be processed
    Thread.sleep(500);
  }

  @Test
  public void testCodeActionRuleDescription() throws Exception {
    File tempFile = temp.newFile("foo.js");
    VersionedTextDocumentIdentifier docId = new VersionedTextDocumentIdentifier(1);
    docId.setUri(tempFile.toURI().toString());
    lsProxy.getTextDocumentService()
      .didChange(new DidChangeTextDocumentParams(docId, Arrays.asList(new TextDocumentContentChangeEvent("function foo() {\n  alert('toto');\n}"))));

    waitForDiagnostics();

    Diagnostic diagnostic = client.diagnosticsParamsList.get(0).getDiagnostics().get(0);

    List<? extends Command> codeActions = lsProxy.getTextDocumentService()
      .codeAction(new CodeActionParams(new TextDocumentIdentifier(tempFile.toURI().toString()), new Range(new Position(1, 4), new Position(1, 4)),
        new CodeActionContext(Arrays.asList(diagnostic))))
      .get();

    assertThat(codeActions).hasSize(1);

    String ruleKey = (String) codeActions.get(0).getArguments().get(0);
    int port = ((Double) codeActions.get(0).getArguments().get(1)).intValue();
    assertThat(ruleKey).isEqualTo("javascript:S1442");

    try {
      IOUtils.toString(new URL("http://localhost:" + port + "/"));
      Assert.fail("Expected exception");
    } catch (Exception e) {
      assertThat(e.getMessage()).contains("400");
    }

    try {
      IOUtils.toString(new URL("http://localhost:" + port + "/?ruleKey=foo"));
      Assert.fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(FileNotFoundException.class);
    }

    String ruleDesc = IOUtils.toString(new URL("http://localhost:" + port + "/?ruleKey=" + ruleKey));
    assertThat(ruleDesc).contains("\"alert(...)\" should not be used");
  }

  private void waitForDiagnostics() throws InterruptedException {
    int maxLoop = 40;
    do {
      Thread.sleep(100);
      maxLoop--;
    } while (client.diagnosticsParamsList.isEmpty() && maxLoop > 0);

  }

  private static class FakeLanguageClient implements LanguageClient {

    List<PublishDiagnosticsParams> diagnosticsParamsList = new ArrayList<>();

    public void clear() {
      diagnosticsParamsList.clear();
    }

    @Override
    public void telemetryEvent(Object object) {
      // TODO Auto-generated method stub

    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
      diagnosticsParamsList.add(diagnostics);
    }

    @Override
    public void showMessage(MessageParams messageParams) {
      // TODO Auto-generated method stub

    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void logMessage(MessageParams message) {
      System.out.println(message.getMessage());
    }

  }

}
