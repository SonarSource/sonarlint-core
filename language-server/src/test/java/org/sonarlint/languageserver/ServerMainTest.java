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
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang.SystemUtils;
import org.awaitility.core.ConditionTimeoutException;
import org.eclipse.lsp4j.CodeAction;
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
import org.eclipse.lsp4j.ExecuteCommandParams;
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
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.internal.apachecommons.io.FileUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.fail;
import static org.sonarlint.languageserver.UserSettings.DISABLE_TELEMETRY;
import static org.sonarlint.languageserver.UserSettings.RULES;
import static org.sonarlint.languageserver.UserSettings.TEST_FILE_PATTERN;
import static org.sonarlint.languageserver.UserSettings.TYPESCRIPT_LOCATION;

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
    String html = new File("target/plugins/html.jar").getAbsoluteFile().toURI().toURL().toString();

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
      ServerMain.main("" + port, js, php, py, ts, html);
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
    lsProxy.getWorkspaceService().didChangeConfiguration(changedConfiguration("**/*Test.js", true));

    String uri = getUri("foo.js");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "javascript", 1, "function foo() {\n  alert('toto');\n  const plouf;\n}")));

    assertThat(waitForDiagnostics(uri))
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(
        tuple(1, 2, 1, 15, "javascript:S1442", "sonarlint", "Remove this usage of alert(...). (javascript:S1442)", DiagnosticSeverity.Information),
        tuple(2, 8, 2, 13, "javascript:UnusedVariable", "sonarlint", "Remove the declaration of the unused 'plouf' variable. (javascript:UnusedVariable)", DiagnosticSeverity.Information)
      );
  }

  @Test
  public void analyzeSimpleJsFileOnOpenWithDisabledRule() throws Exception {
    lsProxy.getWorkspaceService().didChangeConfiguration(changedConfiguration("**/*Test.js", true, "javascript:UnusedVariable"));

    String uri = getUri("foo.js");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "javascript", 1, "function foo() {\n  alert('toto');\n  const plouf;\n}")));

    assertThat(waitForDiagnostics(uri))
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(1, 2, 1, 15, "javascript:S1442", "sonarlint", "Remove this usage of alert(...). (javascript:S1442)", DiagnosticSeverity.Information));
      // Expected issue on javascript:UnusedVariable is suppressed by rule configuration
  }

  @Test
  public void analyzeSimpleTsFileOnOpen() throws Exception {
    // TODO enable it again once https://github.com/SonarSource/SonarTS/issues/598 is fixed
    Assume.assumeFalse(SystemUtils.IS_OS_WINDOWS);

    File tsconfig = temp.newFile("tsconfig.json");
    FileUtils.write(tsconfig, "{}", StandardCharsets.UTF_8);
    String uri = getUri("foo.ts");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "typescript", 1, "function foo() {\n if(bar() && bar()) { return 42; }\n}")));

    assertThat(waitForDiagnostics(uri))
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(tuple(1, 4, 1, 18, "typescript:S1764", "sonarlint", "Correct one of the identical sub-expressions on both sides of operator \"&&\" (typescript:S1764)",
        DiagnosticSeverity.Warning));
  }

  @Test
  public void analyzeSimplePythonFileOnOpen() throws Exception {
    String uri = getUri("foo.py");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "python", 1, "def foo():\n  print 'toto'\n")));

    assertThat(waitForDiagnostics(uri))
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(
        tuple(1, 2, 1, 7, "python:PrintStatementUsage", "sonarlint", "Replace print statement by built-in function. (python:PrintStatementUsage)", DiagnosticSeverity.Warning));
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
  public void analyzeSimpleHtmlFileOnOpen() throws Exception {
    String uri = getUri("foo.html");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "html", 1, "<html><body></body></html>")));

    assertThat(waitForDiagnostics(uri))
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactlyInAnyOrder(
        tuple(0, 0, 0, 26, "Web:DoctypePresenceCheck", "sonarlint", "Insert a <!DOCTYPE> declaration to before this <html> tag. (Web:DoctypePresenceCheck)",
          DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 26, "Web:PageWithoutTitleCheck", "sonarlint", "Add a <title> tag to this page. (Web:PageWithoutTitleCheck)", DiagnosticSeverity.Warning));
  }

  @Test
  public void analyzeSimpleJspFileOnOpen() throws Exception {
    String uri = getUri("foo.html");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "jsp", 1, "<html><body></body></html>")));

    assertThat(waitForDiagnostics(uri))
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactlyInAnyOrder(
        tuple(0, 0, 0, 26, "Web:DoctypePresenceCheck", "sonarlint", "Insert a <!DOCTYPE> declaration to before this <html> tag. (Web:DoctypePresenceCheck)",
          DiagnosticSeverity.Warning),
        tuple(0, 0, 0, 26, "Web:PageWithoutTitleCheck", "sonarlint", "Add a <title> tag to this page. (Web:PageWithoutTitleCheck)", DiagnosticSeverity.Warning));
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

  private DidChangeConfigurationParams changedConfiguration(@Nullable String testFilePattern, boolean disableTelemetry, String... disabledRules) {
    Map<String, Object> values = new HashMap<>();
    values.put(TEST_FILE_PATTERN, testFilePattern);
    values.put(DISABLE_TELEMETRY, disableTelemetry);
    Stream.of(disabledRules).forEach(
      key -> values.put(RULES, ImmutableMap.of(key, ImmutableMap.of("level", "off")))
    );
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
  public void diagnosticRelatedInfos() throws Exception {
    String uri = getUri("foo.js");
    lsProxy.getTextDocumentService()
      .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "javascript", 1, "function foo() {  \n" +
        "}\n" +
        "foo((1 && 2) && (3 && 4));\n")));

    List<Diagnostic> diagnostics = waitForDiagnostics(uri);
    assertThat(diagnostics)
      .extracting("range.start.line", "range.start.character", "range.end.line", "range.end.character", "code", "source", "message", "severity")
      .containsExactly(
        tuple(2, 3, 2, 25, "javascript:S930", "sonarlint", "\"foo\" declared at line 1 expects 0 arguments, but 1 was provided. (javascript:S930)", DiagnosticSeverity.Error));

    assertThat(diagnostics.get(0).getRelatedInformation())
      .extracting("location.range.start.line", "location.range.start.character", "location.range.end.line", "location.range.end.character", "location.uri", "message")
      .containsExactly(tuple(0, 12, 0, 14, uri, "Formal parameters"));
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

  @Test(expected = ExecutionException.class)
  public void testInvalidCommand() throws Exception {
    lsProxy.getWorkspaceService().executeCommand(new ExecuteCommandParams("SonarLint.OpenRuleDesc", Collections.singletonList("missingRule"))).get();
  }

  @Test
  public void testCommandFailure() throws Exception {
    try {
      lsProxy.getWorkspaceService().executeCommand(new ExecuteCommandParams("SonarLint.UpdateServerStorage", Collections.singletonList("invalidMap"))).get();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ResponseErrorException.class);
      assertThat(((ResponseErrorException) e.getCause()).getResponseError().getMessage())
        .isEqualTo("Unable to process command 'SonarLint.UpdateServerStorage': Expected a JSON map but was: \"invalidMap\"");
    }
  }

  @Test
  public void testCodeAction_no_diagnostics() throws Exception {
    String uri = "file://foo.js";
    Range range = new Range(new Position(1, 0), new Position(1, 10));
    CodeActionParams codeActionParams = new CodeActionParams(new TextDocumentIdentifier(uri), range, new CodeActionContext(Collections.emptyList()));
    lsProxy.getTextDocumentService().codeAction(codeActionParams).get();
    try {
      await().atMost(2, TimeUnit.SECONDS).until(() -> client.containsDiagnostics(uri));
      failBecauseExceptionWasNotThrown(ConditionTimeoutException.class);
    } catch(ConditionTimeoutException expected) {
      assertThat(expected).isInstanceOf(ConditionTimeoutException.class);
    }
  }

  @Test
  public void testCodeAction_with_unknown_diagnostic_rule() throws Exception {
    Range range = new Range(new Position(1, 0), new Position(1, 10));
    Diagnostic d = new Diagnostic(range, "An issue");
    d.setSource("sonarlint");
    d.setCode("unknown:rule");
    CodeActionParams codeActionParams = new CodeActionParams(new TextDocumentIdentifier("file://foo.js"), range, new CodeActionContext(Arrays.asList(d)));
    try {
      lsProxy.getTextDocumentService().codeAction(codeActionParams).get();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ResponseErrorException.class);
      assertThat(((ResponseErrorException) e.getCause()).getResponseError().getMessage())
        .isEqualTo("Unknow rule with key: unknown:rule");
    }
  }

  @Test
  public void testCodeAction_with_diagnostic_rule() throws Exception {
    Range range = new Range(new Position(1, 0), new Position(1, 10));
    Diagnostic d = new Diagnostic(range, "An issue");
    d.setSource("sonarlint");
    d.setCode("javascript:S930");
    CodeActionParams codeActionParams = new CodeActionParams(new TextDocumentIdentifier("file://foo.js"), range, new CodeActionContext(Arrays.asList(d)));
    List<Either<Command, CodeAction>> list = lsProxy.getTextDocumentService().codeAction(codeActionParams).get();
    assertThat(list).hasSize(2);
    Command openRuleDesc = list.get(0).getLeft();
    assertThat(openRuleDesc.getCommand()).isEqualTo("SonarLint.OpenRuleDesc");
    assertThat(openRuleDesc.getArguments()).hasSize(5);
    assertThat(((JsonPrimitive) openRuleDesc.getArguments().get(0)).getAsString()).isEqualTo("javascript:S930");
    assertThat(((JsonPrimitive) openRuleDesc.getArguments().get(1)).getAsString()).isEqualTo("Function calls should not pass extra arguments");
    assertThat(((JsonPrimitive) openRuleDesc.getArguments().get(2)).getAsString()).contains("<h2>Noncompliant Code Example");
    assertThat(((JsonPrimitive) openRuleDesc.getArguments().get(3)).getAsString()).isEqualTo("BUG");
    assertThat(((JsonPrimitive) openRuleDesc.getArguments().get(4)).getAsString()).isEqualTo("CRITICAL");
    Command disableRule = list.get(1).getLeft();
    assertThat(disableRule.getCommand()).isEqualTo("SonarLint.DeactivateRule");
    assertThat(disableRule.getArguments()).hasSize(1);
    assertThat(((JsonPrimitive) disableRule.getArguments().get(0)).getAsString()).isEqualTo("javascript:S930");
  }

  private String getUri(String filename) throws IOException {
    return temp.newFile(filename).toPath().toUri().toString();
  }

  private List<Diagnostic> waitForDiagnostics(String uri) throws InterruptedException {
    await().atMost(12, TimeUnit.SECONDS).until(() -> client.containsDiagnostics(uri));
    return client.getDiagnostics(uri);
  }

  private static class FakeLanguageClient implements SonarLintLanguageClient {

    Map<String, List<Diagnostic>> diagnostics = new ConcurrentHashMap<>();
    List<RuleDescription> ruleDescs = new ArrayList<>();

    void clear() {
      diagnostics.clear();
      ruleDescs.clear();
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

    @Override
    public void openRuleDescription(RuleDescription notification) {
      ruleDescs.add(notification);
    }
  }
}
