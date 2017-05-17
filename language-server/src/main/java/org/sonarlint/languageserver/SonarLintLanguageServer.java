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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
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
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration.Builder;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

public class SonarLintLanguageServer implements LanguageServer, LanguageClientAware {

  private final LogOutputImplementation LOG_OUTPUT = new LogOutputImplementation();

  private final class LogOutputImplementation implements LogOutput {
    @Override
    public void log(String formattedMessage, Level level) {
      client.thenAccept(resolved -> resolved.logMessage(new MessageParams(messageType(level), formattedMessage)));
    }

    private MessageType messageType(Level level) {
      switch (level) {
        case ERROR:
          return MessageType.Error;
        case WARN:
          return MessageType.Warning;
        case INFO:
          return MessageType.Info;
        case DEBUG:
        case TRACE:
          return MessageType.Log;
        default:
          throw new IllegalStateException("Unexpected level: " + level);
      }
    }
  }

  private CompletableFuture<LanguageClient> client = new CompletableFuture<>();

  private StandaloneSonarLintEngine engine;

  private Path workspaceDir;

  private void info(String message) {
    client.thenAccept(resolved -> resolved.logMessage(new MessageParams(MessageType.Info, message)));
  }

  private void warn(String message) {
    client.thenAccept(resolved -> resolved.logMessage(new MessageParams(MessageType.Warning, message)));
  }

  private void error(String message, Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    client.thenAccept(resolved -> resolved.logMessage(new MessageParams(MessageType.Error, message + "\n" + sw.toString())));
  }

  @Override
  public void connect(LanguageClient client) {
    this.client.complete(client);

    info("Starting SonarLint engine");

    try {
      Builder builder = StandaloneGlobalConfiguration.builder()
        .setLogOutput(LOG_OUTPUT)
        .addPlugins(getAnalyzers().toArray(new URL[0]));

      this.engine = new StandaloneSonarLintEngineImpl(builder.build());
    } catch (Exception e) {
      error("Error starting SonarLint engine", e);
      throw new IllegalStateException(e);
    }

    info("SonarLint engine started");
  }

  private Collection<URL> getAnalyzers() throws IOException, URISyntaxException {
    info("Load analyzers");
    List<URL> plugins = new ArrayList<>();
    URI uri = SonarLintLanguageServer.class.getResource("/plugins").toURI();
    Path myPath;
    if ("jar".equals(uri.getScheme())) {
      try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap())) {
        myPath = fileSystem.getPath("/plugins");
        collect(plugins, myPath);
      }
    } else {
      myPath = Paths.get(uri);
      collect(plugins, myPath);
    }

    if (plugins.isEmpty()) {
      throw new IllegalStateException("Found no analyzers in server");
    }
    info("Found " + plugins.size() + " analyzers");
    return plugins;
  }

  private static void collect(List<URL> plugins, Path myPath) throws MalformedURLException, IOException {
    try (Stream<Path> walk = Files.walk(myPath, 1)) {
      for (Iterator<Path> it = walk.iterator(); it.hasNext();) {
        Path file = it.next();
        if (file.toString().toLowerCase().endsWith(".jar")) {
          plugins.add(file.toUri().toURL());
        }
      }
    }
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    info("initialize");
    String rootUri = params.getRootUri();
    if (rootUri != null) {
      try {
        workspaceDir = Paths.get(new URI(rootUri));
      } catch (URISyntaxException e) {
        throw new IllegalStateException(e);
      }
    }

    InitializeResult result = new InitializeResult();
    ServerCapabilities c = new ServerCapabilities();
    TextDocumentSyncOptions textDocumentSyncOptions = new TextDocumentSyncOptions();
    textDocumentSyncOptions.setOpenClose(true);
    textDocumentSyncOptions.setChange(TextDocumentSyncKind.Full);
    textDocumentSyncOptions.setSave(new SaveOptions(true));
    c.setTextDocumentSync(textDocumentSyncOptions);
    result.setCapabilities(c);
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    info("shutdown");
    engine.stop();
    return CompletableFuture.completedFuture("Stopped");
  }

  @Override
  public void exit() {
    System.exit(0);
  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return new TextDocumentService() {

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
        return null;
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
        URI uri;
        try {
          uri = new URI(params.getTextDocument().getUri());
        } catch (URISyntaxException e) {
          throw new IllegalStateException(e.getMessage(), e);
        }
        analyze(uri, params.getTextDocument().getText());
      }

      @Override
      public void didChange(DidChangeTextDocumentParams params) {
        URI uri;
        try {
          uri = new URI(params.getTextDocument().getUri());
        } catch (URISyntaxException e) {
          throw new IllegalStateException(e.getMessage(), e);
        }
        analyze(uri, params.getContentChanges().get(0).getText());
      }

      @Override
      public void didClose(DidCloseTextDocumentParams params) {
        // Nothing to do
      }

      @Override
      public void didSave(DidSaveTextDocumentParams params) {
        URI uri;
        try {
          uri = new URI(params.getTextDocument().getUri());
        } catch (URISyntaxException e) {
          throw new IllegalStateException(e.getMessage(), e);
        }
        analyze(uri, params.getText());
      }

      private void analyze(URI uri, String content) {
        info("Analysis triggered on " + uri);
        Map<URI, PublishDiagnosticsParams> files = new HashMap<>();
        files.put(uri, newPublishDiagnostics(uri));
        Path baseDir = workspaceDir != null ? workspaceDir : Paths.get(uri).getParent();
        Objects.requireNonNull(baseDir);
        Objects.requireNonNull(engine);
        AnalysisResults analysisResults = engine.analyze(
          new StandaloneAnalysisConfiguration(baseDir, baseDir.resolve(".sonarlint"), Arrays.asList(new DefaultClientInputFile(uri, content)), Collections.emptyMap()),
          issue -> {
            ClientInputFile inputFile = issue.getInputFile();
            if (inputFile != null) {
              URI uri1 = inputFile.getClientObject();
              PublishDiagnosticsParams publish = files.computeIfAbsent(uri1, SonarLintLanguageServer::newPublishDiagnostics);

              convert(issue).ifPresent(publish.getDiagnostics()::add);
            }
          }, LOG_OUTPUT);
        // Ignore files with parsing error
        analysisResults.failedAnalysisFiles().stream().map(ClientInputFile::getClientObject).forEach(files::remove);
        client.thenAccept(resolved -> files.values().forEach(resolved::publishDiagnostics));
      }

    };
  }

  static Optional<Diagnostic> convert(Issue issue) {
    if (issue.getStartLine() != null) {
      Range range = position(issue);
      Diagnostic diagnostic = new Diagnostic();
      DiagnosticSeverity severity = severity(issue.getSeverity());

      diagnostic.setSeverity(severity);
      diagnostic.setRange(range);
      diagnostic.setCode(issue.getRuleKey());
      diagnostic.setMessage(issue.getMessage());
      diagnostic.setSource("SonarLint");

      return Optional.of(diagnostic);
    } else return Optional.empty();
  }

  private static DiagnosticSeverity severity(String severity) {
    switch (severity.toUpperCase()) {
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

  private static class DefaultClientInputFile implements ClientInputFile {

    private final URI fileUri;
    private final String content;

    public DefaultClientInputFile(URI uri, String content) {
      this.fileUri = uri;
      this.content = content;
    }

    @Override
    public Charset getCharset() {
      return StandardCharsets.UTF_8;
    }

    @Override
    public <G> G getClientObject() {
      return (G) fileUri;
    }

    @Override
    public String getPath() {
      return Paths.get(fileUri).toString();
    }

    @Override
    public boolean isTest() {
      // FIXME
      return false;
    }

    @Override
    public String contents() throws IOException {
      return content;
    }

    @Override
    public InputStream inputStream() throws IOException {
      return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return new WorkspaceService() {
      @Override
      public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        info(params.toString());

        switch (params.getCommand()) {
          // case "Java.importClass":
          // String fileString = (String) params.getArguments().get(0);
          // URI fileUri = URI.create(fileString);
          // String packageName = (String) params.getArguments().get(1);
          // String className = (String) params.getArguments().get(2);
          //
          // findCompiler(fileUri).ifPresent(compiler -> {
          // FocusedResult compiled = compiler.compileFocused(fileUri, activeContent(fileUri), 1, 1, false);
          //
          // if (compiled.compilationUnit.getSourceFile().toUri().equals(fileUri)) {
          // List<TextEdit> edits = new RefactorFile(compiled.task, compiled.compilationUnit)
          // .addImport(packageName, className);
          //
          // client.join().applyEdit(new ApplyWorkspaceEditParams(new WorkspaceEdit(
          // Collections.singletonMap(fileString, edits),
          // null)));
          // }
          // });
          //
          // break;
          default:
            warn("Don't know what to do with " + params.getCommand());
        }

        return CompletableFuture.completedFuture("Done");
      }

      @Override
      public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
        return null;
      }

      @Override
      public void didChangeConfiguration(DidChangeConfigurationParams params) {
      }

      @Override
      public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
      }
    };
  }

}
