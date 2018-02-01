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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration.Builder;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;

public class SonarLintLanguageServer implements LanguageServer, WorkspaceService, TextDocumentService {

  static final String DISABLE_TELEMETRY = "disableTelemetry";
  static final String TYPESCRIPT_LOCATION = "typeScriptLocation";
  static final String TEST_FILE_PATTERN = "testFilePattern";
  static final String ANALYZER_PROPERTIES = "analyzerProperties";
  private static final String SONARLINT_CONFIGURATION_NAMESPACE = "sonarlint";
  private static final String SONARLINT_SOURCE = SONARLINT_CONFIGURATION_NAMESPACE;
  private static final String SONARLINT_OPEN_RULE_DESCRIPTION_COMMAND = "SonarLint.OpenRuleDesc";

  private final LanguageClient client;
  private final Future<?> backgroundProcess;
  private final LanguageClientLogOutput logOutput;

  private final Map<URI, String> languageIdPerFileURI = new HashMap<>();
  private final SonarLintTelemetry telemetry = new SonarLintTelemetry();
  private final Collection<URL> analyzers;

  private UserSettings userSettings = new UserSettings();

  private StandaloneSonarLintEngine engine;
  @Nullable
  private URI workspaceRoot;

  public SonarLintLanguageServer(InputStream inputStream, OutputStream outputStream, Collection<URL> analyzers) {

    this.analyzers = analyzers;
    Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(this,
      inputStream,
      outputStream,
      true, new PrintWriter(System.out));

    this.client = launcher.getRemoteProxy();
    this.logOutput = new LanguageClientLogOutput(client);

    backgroundProcess = launcher.startListening();
  }

  public static SonarLintLanguageServer bySocket(int port, Collection<URL> analyzers) throws IOException {
    Socket socket = new Socket("localhost", port);
    return new SonarLintLanguageServer(socket.getInputStream(), socket.getOutputStream(), analyzers);
  }

  private static class UserSettings {
    @CheckForNull
    final String testFilePattern;
    final Map<String, String> analyzerProperties;
    final boolean disableTelemetry;

    private UserSettings() {
      this(Collections.emptyMap());
    }

    private UserSettings(Map<String, Object> params) {
      this.testFilePattern = (String) params.get(TEST_FILE_PATTERN);
      this.analyzerProperties = getAnalyzerProperties(params);
      this.disableTelemetry = (Boolean) params.getOrDefault(DISABLE_TELEMETRY, false);
    }

    private static Map getAnalyzerProperties(Map<String, Object> params) {
      Map analyzerProperties = (Map) params.get(ANALYZER_PROPERTIES);
      if (analyzerProperties == null) {
        analyzerProperties = Collections.emptyMap();
      }
      return analyzerProperties;
    }
  }

  private void debug(String message) {
    client.logMessage(new MessageParams(MessageType.Log, message));
  }

  private void info(String message) {
    client.logMessage(new MessageParams(MessageType.Info, message));
  }

  void warn(String message) {
    client.logMessage(new MessageParams(MessageType.Warning, message));
  }

  void error(String message, Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    client.logMessage(new MessageParams(MessageType.Error, message + "\n" + sw.toString()));
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    String rootUri = params.getRootUri();
    // rootURI is null when no folder is open (like opening a single file in VSCode)
    if (rootUri != null) {
      try {
        workspaceRoot = new URI(rootUri);
      } catch (URISyntaxException e) {
        throw new IllegalStateException(e);
      }
    }

    Map<String, Object> options = (Map<String, Object>) params.getInitializationOptions();
    userSettings = new UserSettings(options);

    String productKey = (String) options.get("productKey");
    // deprecated, will be ignored when productKey present
    String telemetryStorage = (String) options.get("telemetryStorage");

    String productName = (String) options.get("productName");
    String productVersion = (String) options.get("productVersion");

    telemetry.init(getStoragePath(productKey, telemetryStorage), productName, productVersion);
    telemetry.optOut(userSettings.disableTelemetry);

    info("Starting SonarLint engine...");
    info("Using " + analyzers.size() + " analyzers");

    try {
      Map<String, String> extraProperties = new HashMap<>();
      extraProperties.put("sonar.typescript.internal.typescriptLocation", (String) options.get(TYPESCRIPT_LOCATION));
      Builder builder = StandaloneGlobalConfiguration.builder()
        .setLogOutput(logOutput)
        .setExtraProperties(extraProperties)
        .addPlugins(analyzers.toArray(new URL[0]));

      this.engine = new StandaloneSonarLintEngineImpl(builder.build());
    } catch (Exception e) {
      error("Error starting SonarLint engine", e);
      throw new IllegalStateException(e);
    }

    info("SonarLint engine started");

    InitializeResult result = new InitializeResult();
    ServerCapabilities c = new ServerCapabilities();
    TextDocumentSyncOptions textDocumentSyncOptions = new TextDocumentSyncOptions();
    textDocumentSyncOptions.setOpenClose(true);
    textDocumentSyncOptions.setChange(TextDocumentSyncKind.Full);
    textDocumentSyncOptions.setSave(new SaveOptions(true));
    c.setTextDocumentSync(textDocumentSyncOptions);
    c.setCodeActionProvider(true);
    result.setCapabilities(c);
    return CompletableFuture.completedFuture(result);
  }

  @VisibleForTesting
  static Path getStoragePath(@Nullable String productKey, @Nullable String telemetryStorage) {
    if (productKey != null) {
      if (telemetryStorage != null) {
        TelemetryPathManager.migrate(productKey, Paths.get(telemetryStorage));
      }
      return TelemetryPathManager.getPath(productKey);
    }
    return telemetryStorage != null ? Paths.get(telemetryStorage) : null;
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    engine.stop();
    telemetry.stop();
    return CompletableFuture.completedFuture("Stopped");
  }

  @Override
  public void exit() {
    backgroundProcess.cancel(true);
  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return this;
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(TextDocumentPositionParams position) {
    return null;
  }

  @Override
  public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
    return null;
  }

  @Override
  public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
    return null;
  }

  @Override
  public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
    return null;
  }

  @Override
  public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
    return null;
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    return null;
  }

  @Override
  public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
    return null;
  }

  @Override
  public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
    return null;
  }

  @Override
  public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
    List<Command> commands = new ArrayList<>();
    for (Diagnostic d : params.getContext().getDiagnostics()) {
      if (SONARLINT_SOURCE.equals(d.getSource())) {
        RuleDetails ruleDetails = engine.getRuleDetails(d.getCode());
        String ruleName = ruleDetails.getName();
        String htmlDescription = ruleDetails.getHtmlDescription();
        String type = ruleDetails.getType();
        String severity = ruleDetails.getSeverity();
        commands.add(
          new Command("Open description of rule " + d.getCode(),
            SONARLINT_OPEN_RULE_DESCRIPTION_COMMAND,
            Arrays.asList(d.getCode(), ruleName, htmlDescription, type, severity)));
      }
    }
    return CompletableFuture.completedFuture(commands);
  }

  @Override
  public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
    return null;
  }

  @Override
  public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
    return null;
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
    return null;
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
    return null;
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
    return null;
  }

  @Override
  public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
    return null;
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    URI uri = parseURI(params.getTextDocument().getUri());
    languageIdPerFileURI.put(uri, params.getTextDocument().getLanguageId());
    analyze(uri, params.getTextDocument().getText());
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    URI uri = parseURI(params.getTextDocument().getUri());
    analyze(uri, params.getContentChanges().get(0).getText());
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    URI uri = parseURI(params.getTextDocument().getUri());
    languageIdPerFileURI.remove(uri);
    // Clear issues
    client.publishDiagnostics(newPublishDiagnostics(uri));
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    String content = params.getText();
    if (content != null) {
      URI uri = parseURI(params.getTextDocument().getUri());
      analyze(uri, params.getText());
    }
  }

  private static URI parseURI(String uriStr) {
    URI uri;
    try {
      uri = new URI(uriStr);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
    return uri;
  }

  private void analyze(URI uri, String content) {
    Map<URI, PublishDiagnosticsParams> files = new HashMap<>();
    files.put(uri, newPublishDiagnostics(uri));
    Path baseDir = workspaceRoot != null ? Paths.get(workspaceRoot) : Paths.get(uri).getParent();
    Objects.requireNonNull(baseDir);
    Objects.requireNonNull(engine);
    StandaloneAnalysisConfiguration configuration = new StandaloneAnalysisConfiguration(baseDir, baseDir.resolve(".sonarlint"),
      Arrays.asList(new DefaultClientInputFile(baseDir, uri, content, userSettings.testFilePattern, languageIdPerFileURI.get(uri))),
      userSettings.analyzerProperties);
    debug("Analysis triggered on " + uri + " with configuration: \n" + configuration.toString());
    telemetry.usedAnalysis();
    AnalysisResults analysisResults = engine.analyze(
      configuration,
      issue -> {
        ClientInputFile inputFile = issue.getInputFile();
        if (inputFile != null) {
          URI uri1 = inputFile.getClientObject();
          PublishDiagnosticsParams publish = files.computeIfAbsent(uri1, SonarLintLanguageServer::newPublishDiagnostics);

          convert(issue).ifPresent(publish.getDiagnostics()::add);
        }
      }, logOutput, null);
    // Ignore files with parsing error
    analysisResults.failedAnalysisFiles().stream().map(ClientInputFile::getClientObject).forEach(files::remove);
    files.values().forEach(client::publishDiagnostics);

  }

  static Optional<Diagnostic> convert(Issue issue) {
    if (issue.getStartLine() != null) {
      Range range = position(issue);
      Diagnostic diagnostic = new Diagnostic();
      DiagnosticSeverity severity = severity(issue.getSeverity());

      diagnostic.setSeverity(severity);
      diagnostic.setRange(range);
      diagnostic.setCode(issue.getRuleKey());
      diagnostic.setMessage(issue.getMessage() + " (" + issue.getRuleKey() + ")");
      diagnostic.setSource(SONARLINT_SOURCE);

      return Optional.of(diagnostic);
    }
    return Optional.empty();
  }

  private static DiagnosticSeverity severity(String severity) {
    switch (severity.toUpperCase(Locale.ENGLISH)) {
      case "BLOCKER":
      case "CRITICAL":
        return DiagnosticSeverity.Error;
      case "MAJOR":
        return DiagnosticSeverity.Warning;
      case "MINOR":
        return DiagnosticSeverity.Information;
      case "INFO":
      default:
        return DiagnosticSeverity.Hint;
    }
  }

  private static Range position(Issue issue) {
    return new Range(
      new Position(
        issue.getStartLine() - 1,
        issue.getStartLineOffset()),
      new Position(
        issue.getEndLine() - 1,
        issue.getEndLineOffset()));
  }

  private static PublishDiagnosticsParams newPublishDiagnostics(URI newUri) {
    PublishDiagnosticsParams p = new PublishDiagnosticsParams();

    p.setDiagnostics(new ArrayList<>());
    p.setUri(newUri.toString());

    return p;
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return this;
  }

  @Override
  public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
    // No command
    return null;
  }

  @Override
  public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
    return null;
  }

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {
    Map<String, Object> settings = (Map<String, Object>) params.getSettings();
    Map<String, Object> entries = (Map<String, Object>) settings.get(SONARLINT_CONFIGURATION_NAMESPACE);
    userSettings = new UserSettings(entries);
    telemetry.optOut(userSettings.disableTelemetry);
  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    // No watched files
  }

  public StandaloneSonarLintEngine getEngine() {
    return engine;
  }

}
