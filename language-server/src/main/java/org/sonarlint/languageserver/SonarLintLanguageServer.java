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

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.sonar.api.internal.apachecommons.lang.StringUtils;
import org.sonar.api.utils.log.Loggers;
import org.sonarlint.languageserver.log.ClientLogger;
import org.sonarlint.languageserver.log.DefaultClientLogger;
import org.sonarlint.languageserver.log.LanguageClientLogOutput;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue.Flow;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalStorageUpdateRequiredException;
import org.sonarsource.sonarlint.core.client.api.exceptions.ProjectNotFoundException;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;

import static java.util.Collections.singleton;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.sonarlint.languageserver.UserSettings.CONNECTED_MODE_PROJECT_PROP;
import static org.sonarlint.languageserver.UserSettings.CONNECTED_MODE_SERVERS_PROP;
import static org.sonarlint.languageserver.UserSettings.TYPESCRIPT_LOCATION;

public class SonarLintLanguageServer implements SonarLintExtendedLanguageServer, WorkspaceService, TextDocumentService {
  private static final String USER_AGENT = "SonarLint Language Server";

  private static final String TYPESCRIPT_PATH_PROP = "sonar.typescript.internal.typescriptLocation";

  private static final String SONARLINT_CONFIGURATION_NAMESPACE = "sonarlint";
  private static final String SONARLINT_SOURCE = SONARLINT_CONFIGURATION_NAMESPACE;
  private static final String SONARLINT_OPEN_RULE_DESCRIPTION_COMMAND = "SonarLint.OpenRuleDesc";
  private static final String SONARLINT_DEACTIVATE_RULE_COMMAND = "SonarLint.DeactivateRule";
  static final String SONARLINT_UPDATE_SERVER_STORAGE_COMMAND = "SonarLint.UpdateServerStorage";
  static final String SONARLINT_UPDATE_PROJECT_BINDING_COMMAND = "SonarLint.UpdateProjectBinding";
  static final String SONARLINT_REFRESH_DIAGNOSTICS_COMMAND = "SonarLint.RefreshDiagnostics";
  private static final List<String> SONARLINT_COMMANDS = Arrays.asList(
    SONARLINT_UPDATE_SERVER_STORAGE_COMMAND,
    SONARLINT_UPDATE_PROJECT_BINDING_COMMAND,
    SONARLINT_REFRESH_DIAGNOSTICS_COMMAND);

  private final SonarLintExtendedLanguageClient client;
  private final Future<?> backgroundProcess;
  private final LanguageClientLogOutput logOutput;
  private final ClientLogger logger;

  private final Map<URI, String> languageIdPerFileURI = new HashMap<>();
  private final SonarLintTelemetry telemetry = new SonarLintTelemetry();

  private UserSettings userSettings = new UserSettings();
  private final List<String> workspaceFolders = new ArrayList<>();
  private final Map<String, ProjectBinding> workspaceBindings = new HashMap<>();
  private final Map<String, ServerIssueTracker> workspaceTrackers = new HashMap<>();

  private final EngineCache engineCache;
  private final ServerInfoCache serverInfoCache;

  private ServerProjectBinding binding;

  /**
   * Keep track of value 'sonarlint.trace.server' on client side. Not used currently, but keeping it just in case.
   */
  private TraceValues traceLevel;

  SonarLintLanguageServer(InputStream inputStream, OutputStream outputStream,
    BiFunction<LanguageClientLogOutput, ClientLogger, EngineCache> engineCacheFactory,
    Function<SonarLintExtendedLanguageClient, ClientLogger> loggerFactory) {
    Launcher<SonarLintExtendedLanguageClient> launcher = Launcher.createLauncher(this,
      SonarLintExtendedLanguageClient.class,
      inputStream,
      outputStream);

    this.client = launcher.getRemoteProxy();

    backgroundProcess = launcher.startListening();

    this.logger = loggerFactory.apply(this.client);
    this.logOutput = new LanguageClientLogOutput(this.client);
    this.engineCache = engineCacheFactory.apply(logOutput, logger);
    this.serverInfoCache = new ServerInfoCache(logger);
  }

  static SonarLintLanguageServer bySocket(int port, Collection<URL> analyzers) throws IOException {
    Socket socket = new Socket("localhost", port);

    BiFunction<LanguageClientLogOutput, ClientLogger, EngineCache> engineCacheFactory = (logOutput, logger) -> {
      StandaloneEngineFactory standaloneEngineFactory = new StandaloneEngineFactory(analyzers, logOutput, logger);
      ConnectedEngineFactory connectedEngineFactory = new ConnectedEngineFactory(logOutput, logger);
      return new DefaultEngineCache(standaloneEngineFactory, connectedEngineFactory);
    };

    Function<SonarLintExtendedLanguageClient, ClientLogger> loggerFactory = DefaultClientLogger::new;

    return new SonarLintLanguageServer(socket.getInputStream(), socket.getOutputStream(), engineCacheFactory, loggerFactory);
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      this.traceLevel = parseTraceLevel(params.getTrace());
      Loggers.setTarget(logOutput);

      workspaceFolders.addAll(parseWorkspaceFolders(params.getWorkspaceFolders(), params.getRootUri()));
      workspaceFolders.sort(Comparator.reverseOrder());

      Map<String, Object> options = UserSettings.parseToMap(params.getInitializationOptions());
      userSettings = new UserSettings(options);

      String productKey = (String) options.get("productKey");
      // deprecated, will be ignored when productKey present
      String telemetryStorage = (String) options.get("telemetryStorage");

      String productName = (String) options.get("productName");
      String productVersion = (String) options.get("productVersion");
      String ideVersion = (String) options.get("ideVersion");

      String typeScriptPath = (String) options.get(TYPESCRIPT_LOCATION);
      engineCache.putExtraProperty(TYPESCRIPT_PATH_PROP, typeScriptPath);

      serverInfoCache.replace(options.get(CONNECTED_MODE_SERVERS_PROP));
      updateBinding((Map<?, ?>) options.get(CONNECTED_MODE_PROJECT_PROP));

      telemetry.init(getStoragePath(productKey, telemetryStorage), productName, productVersion, ideVersion, this::usesConnectedMode, this::usesSonarCloud);
      telemetry.optOut(userSettings.disableTelemetry);

      InitializeResult result = new InitializeResult();
      ServerCapabilities c = new ServerCapabilities();
      c.setTextDocumentSync(getTextDocumentSyncOptions());
      c.setCodeActionProvider(true);
      c.setExecuteCommandProvider(new ExecuteCommandOptions(SONARLINT_COMMANDS));
      c.setWorkspace(getWorkspaceServerCapabilities());

      result.setCapabilities(c);
      return result;
    });
  }

  private boolean usesConnectedMode() {
    // TODO check if this is correct
    return !serverInfoCache.isEmpty();
  }

  private boolean usesSonarCloud() {
    // TODO check if this is correct
    return serverInfoCache.containsSonarCloud();
  }

  private static WorkspaceServerCapabilities getWorkspaceServerCapabilities() {
    WorkspaceFoldersOptions options = new WorkspaceFoldersOptions();
    options.setSupported(true);
    options.setChangeNotifications(true);

    WorkspaceServerCapabilities capabilities = new WorkspaceServerCapabilities();
    capabilities.setWorkspaceFolders(options);
    return capabilities;
  }

  private static TextDocumentSyncOptions getTextDocumentSyncOptions() {
    TextDocumentSyncOptions textDocumentSyncOptions = new TextDocumentSyncOptions();
    textDocumentSyncOptions.setOpenClose(true);
    textDocumentSyncOptions.setChange(TextDocumentSyncKind.Full);
    textDocumentSyncOptions.setSave(new SaveOptions(true));
    return textDocumentSyncOptions;
  }

  private void handleUpdateServerStorageCommand(@Nullable List<Object> arguments) {
    engineCache.clearConnectedEngines();

    serverInfoCache.replace(arguments);

    serverInfoCache.forEach((serverId, serverInfo) -> {
      ConnectedSonarLintEngine engine = engineCache.getOrCreateConnectedEngine(serverInfo);
      if (engine == null) {
        logger.warn("Could not start server: " + serverId);
      } else {
        updateServerStorage(engine, serverInfo);
      }
    });
  }

  private void updateServerStorage(ConnectedSonarLintEngine engine, ServerInfo serverInfo) {
    String serverId = serverInfo.serverId;
    logger.debug("Updating global storage of server " + serverId + ", may take some time...");
    ServerConfiguration serverConfig = getServerConfiguration(serverInfo);
    engine.update(serverConfig, null);
    logger.debug("Successfully updated global storage of server " + serverId);
  }

  private static ServerConfiguration getServerConfiguration(ServerInfo serverInfo) {
    return ServerConfiguration.builder()
      .url(serverInfo.serverUrl)
      .token(serverInfo.token)
      .organizationKey(serverInfo.organizationKey)
      .userAgent(USER_AGENT)
      .build();
  }

  private void updateBinding(@Nullable Map<?, ?> connectedModeProject) {
    binding = null;
    if (connectedModeProject == null) {
      return;
    }

    Map<String, String> map = (Map<String, String>) connectedModeProject;
    if (map.isEmpty()) {
      return;
    }

    String serverId = map.get("serverId");
    String projectKey = map.get("projectKey");
    if (isBlank(serverId) || isBlank(projectKey)) {
      logger.error(ClientLogger.ErrorType.INCOMPLETE_BINDING);
      return;
    }

    ServerInfo serverInfo = serverInfoCache.get(serverId);
    if (serverInfo == null) {
      logger.error(ClientLogger.ErrorType.INVALID_BINDING_SERVER);
      return;
    }

    ConnectedSonarLintEngine engine = engineCache.getOrCreateConnectedEngine(serverInfo);
    if (engine == null) {
      logger.error(ClientLogger.ErrorType.START_CONNECTED_ENGINE_FAILED);
      return;
    }

    binding = new ServerProjectBinding(serverId, projectKey);

    if (updateProjectStorage(engine, serverInfo)) {
      updateIssueTrackers(engine, serverInfo);
    } else {
      binding = null;
    }
  }

  private void updateIssueTrackers(ConnectedSonarLintEngine engine, ServerInfo serverInfo) {
    workspaceBindings.clear();
    workspaceTrackers.clear();

    workspaceFolders.forEach(folderRoot -> {
      Collection<String> ideFilePaths = FileUtils.allRelativePathsForFilesInTree(Paths.get(folderRoot));
      ProjectBinding projectBinding = engine.calculatePathPrefixes(binding.projectKey, ideFilePaths);
      workspaceBindings.put(folderRoot, projectBinding);
      logger.debug(String.format("Resolved sqPathPrefix:%s / idePathPrefix:%s / for folder %s",
        projectBinding.sqPathPrefix(),
        projectBinding.idePathPrefix(),
        folderRoot));
      workspaceTrackers.put(folderRoot,
        new ServerIssueTracker(engine, getServerConfiguration(serverInfo), projectBinding));
    });
  }

  private boolean updateProjectStorage(ConnectedSonarLintEngine engine, ServerInfo serverInfo) {
    ServerConfiguration serverConfig = getServerConfiguration(serverInfo);
    try {
      engine.updateProject(serverConfig, binding.projectKey, null);
      return true;
    } catch (ProjectNotFoundException e) {
      logger.error(ClientLogger.ErrorType.PROJECT_NOT_FOUND);
    } catch (Exception e) {
      logger.warn(e.getMessage());
    }
    return false;
  }

  // visible for testing
  static List<String> parseWorkspaceFolders(@Nullable List<WorkspaceFolder> workspaceFolders, @Nullable String rootUri) {
    if (workspaceFolders != null && !workspaceFolders.isEmpty()) {
      return toList(workspaceFolders);
    }

    // rootURI is null when no folder is open (like opening a single file in VSCode)
    if (rootUri != null) {
      return Collections.singletonList(rootUri);
    }

    return Collections.emptyList();
  }

  private static List<String> toList(List<WorkspaceFolder> workspaceFolders) {
    return workspaceFolders.stream()
      .filter(f -> f.getUri().startsWith("file:/"))
      .map(f -> normalizeUriString(f.getUri()))
      .collect(Collectors.toList());
  }

  static String normalizeUriString(String uriString) {
    return Paths.get(URI.create(uriString)).toFile().toString();
  }

  // visible for testing
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
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      engineCache.stopStandaloneEngine();
      engineCache.clearConnectedEngines();
      telemetry.stop();
      return new Object();
    });
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
  public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      List<Either<Command, CodeAction>> commands = new ArrayList<>();
      boolean standaloneMode = this.binding == null;
      for (Diagnostic d : params.getContext().getDiagnostics()) {
        if (SONARLINT_SOURCE.equals(d.getSource())) {
          String ruleKey = d.getCode();
          List<Object> ruleDescriptionParams = getOpenRuleDescriptionParams(ruleKey);
          // May take time to initialize the engine so check for cancellation just after
          cancelToken.checkCanceled();
          if (!ruleDescriptionParams.isEmpty()) {
            commands.add(Either.forLeft(
              new Command(String.format("Open description of SonarLint rule '%s'", ruleKey),
                SONARLINT_OPEN_RULE_DESCRIPTION_COMMAND,
                ruleDescriptionParams)));
          }
          if (standaloneMode) {
            commands.add(Either.forLeft(
              new Command(String.format("Deactivate rule '%s'", ruleKey),
                SONARLINT_DEACTIVATE_RULE_COMMAND,
                Collections.singletonList(ruleKey))));
          }
        }
      }
      return commands;
    });
  }

  private List<Object> getOpenRuleDescriptionParams(String ruleKey) {
    RuleDetails ruleDetails;
    if (binding == null) {
      ruleDetails = engineCache.getOrCreateStandaloneEngine().getRuleDetails(ruleKey)
        .orElseThrow(() -> new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Unknown rule with key: " + ruleKey, null)));
    } else {
      ServerInfo serverInfo = serverInfoCache.get(binding.serverId);
      ConnectedSonarLintEngine engine = engineCache.getOrCreateConnectedEngine(serverInfo);
      if (engine != null) {
        ruleDetails = engine.getRuleDetails(ruleKey);
      } else {
        return Collections.emptyList();
      }
    }
    String ruleName = ruleDetails.getName();
    String htmlDescription = getHtmlDescription(ruleDetails);
    String type = ruleDetails.getType();
    String severity = ruleDetails.getSeverity();
    return Arrays.asList(ruleKey, ruleName, htmlDescription, type, severity);
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    URI uri = parseURI(params.getTextDocument().getUri());
    languageIdPerFileURI.put(uri, params.getTextDocument().getLanguageId());
    analyze(uri, params.getTextDocument().getText(), true);
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    URI uri = parseURI(params.getTextDocument().getUri());
    analyze(uri, params.getContentChanges().get(0).getText(), false);
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
      analyze(uri, params.getText(), false);
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

  @Override
  public CompletableFuture<Map<String, List<RuleDescription>>> listAllRules() {
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      Map<String, List<RuleDescription>> result = new HashMap<>();
      Map<String, String> languagesNameByKey = engineCache.getOrCreateStandaloneEngine().getAllLanguagesNameByKey();
      engineCache.getOrCreateStandaloneEngine().getAllRuleDetails()
        .forEach(d -> {
          String languageName = languagesNameByKey.get(d.getLanguageKey());
          if (!result.containsKey(languageName)) {
            result.put(languageName, new ArrayList<>());
          }
          result.get(languageName).add(RuleDescription.of(d));
        });
      return result;
    });
  }

  // visible for testing
  void analyze(URI uri, String content, boolean shouldFetchServerIssues) {
    if (!uri.toString().startsWith("file:/")) {
      logger.warn("URI is not a file, analysis not supported");
      return;
    }

    Map<URI, PublishDiagnosticsParams> files = new HashMap<>();
    files.put(uri, newPublishDiagnostics(uri));

    AnalysisWrapper analysisWrapper = getAnalysisWrapper();
    if (analysisWrapper.isExcludedByServerSideExclusions(uri)) {
      logger.debug("Skip analysis of excluded file: " + uri);
      return;
    }

    IssueListener issueListener = issue -> {
      ClientInputFile inputFile = issue.getInputFile();
      if (inputFile != null) {
        URI uri1 = inputFile.getClientObject();
        PublishDiagnosticsParams publish = files.computeIfAbsent(uri1, SonarLintLanguageServer::newPublishDiagnostics);

        convert(issue).ifPresent(publish.getDiagnostics()::add);
      }
    };

    try {
      Path baseDir = findBaseDir(uri);
      AnalysisResultsWrapper analysisResults = analysisWrapper.analyze(baseDir, uri, content, issueListener, shouldFetchServerIssues);
      telemetry.analysisDoneOnSingleFile(StringUtils.substringAfterLast(uri.toString(), "."), analysisResults.analysisTime);

      // Ignore files with parsing error
      analysisResults.results.failedAnalysisFiles().stream()
        .map(ClientInputFile::getClientObject)
        .forEach(files::remove);
    } catch (Exception e) {
      logger.error(ClientLogger.ErrorType.ANALYSIS_FAILED, e);
    }

    files.values().forEach(client::publishDiagnostics);
  }

  private AnalysisWrapper getAnalysisWrapper() {
    return getConnectedEngine()
      .map(e -> (AnalysisWrapper) new ConnectedAnalysisWrapper(e, binding.projectKey))
      .orElse(new StandaloneAnalysisWrapper());
  }

  private Optional<ConnectedSonarLintEngine> getConnectedEngine() {
    return Optional.ofNullable(binding)
      .map(b -> serverInfoCache.get(b.serverId))
      .filter(Objects::nonNull)
      .map(engineCache::getOrCreateConnectedEngine);
  }

  static class AnalysisResultsWrapper {
    private final AnalysisResults results;
    private final int analysisTime;

    AnalysisResultsWrapper(AnalysisResults results, int analysisTime) {
      this.results = results;
      this.analysisTime = analysisTime;
    }
  }

  interface AnalysisWrapper {
    AnalysisResultsWrapper analyze(Path baseDir, URI uri, String content, IssueListener issueListener, boolean shouldFetchServerIssues);

    boolean isExcludedByServerSideExclusions(URI fileUri);
  }

  class StandaloneAnalysisWrapper implements AnalysisWrapper {
    @Override
    public boolean isExcludedByServerSideExclusions(URI fileUri) {
      return false;
    }

    @Override
    public AnalysisResultsWrapper analyze(Path baseDir, URI uri, String content, IssueListener issueListener, boolean shouldFetchServerIssues) {
      StandaloneAnalysisConfiguration configuration = StandaloneAnalysisConfiguration.builder()
        .setBaseDir(baseDir)
        .addInputFiles(new DefaultClientInputFile(uri, getFileRelativePath(baseDir, uri), content, isTest(uri), languageIdPerFileURI.get(uri)))
        .putAllExtraProperties(userSettings.analyzerProperties)
        .addExcludedRules(userSettings.excludedRules)
        .addIncludedRules(userSettings.includedRules)
        .build();
      logger.debug("Analysis triggered on " + uri + " with configuration: \n" + configuration.toString());

      long start = System.currentTimeMillis();
      StandaloneSonarLintEngine engine = engineCache.getOrCreateStandaloneEngine();
      AnalysisResults analysisResults = engine.analyze(configuration, issueListener, logOutput, null);
      int analysisTime = (int) (System.currentTimeMillis() - start);

      return new AnalysisResultsWrapper(analysisResults, analysisTime);
    }
  }

  class ConnectedAnalysisWrapper implements AnalysisWrapper {
    private final ConnectedSonarLintEngine engine;
    private final String projectKey;

    ConnectedAnalysisWrapper(ConnectedSonarLintEngine engine, String projectKey) {
      this.engine = engine;
      this.projectKey = projectKey;
    }

    @Override
    public boolean isExcludedByServerSideExclusions(URI fileUri) {
      Path baseDir = findBaseDir(fileUri);
      ProjectBinding projectBinding = workspaceBindings.getOrDefault(fileUri.toString(), new ProjectBinding(projectKey, "", ""));
      return !engine.getExcludedFiles(projectBinding,
        singleton(fileUri),
        uri -> getFileRelativePath(baseDir, uri),
        SonarLintLanguageServer.this::isTest)
        .isEmpty();
    }

    @Override
    public AnalysisResultsWrapper analyze(Path baseDir, URI uri, String content, IssueListener issueListener, boolean shouldFetchServerIssues) {
      ConnectedAnalysisConfiguration configuration = ConnectedAnalysisConfiguration.builder()
        .setProjectKey(projectKey)
        .setBaseDir(baseDir)
        .addInputFile(new DefaultClientInputFile(uri, getFileRelativePath(baseDir, uri), content, isTest(uri), languageIdPerFileURI.get(uri)))
        .putAllExtraProperties(userSettings.analyzerProperties)
        .build();
      if (userSettings.hasLocalRuleConfiguration()) {
        logger.debug("Local rules settings are ignored, using quality profile from server");
      }
      logger.debug("Analysis triggered on " + uri + " with configuration: \n" + configuration.toString());

      List<Issue> issues = new LinkedList<>();
      IssueListener collector = issues::add;
      ServerInfo serverInfo = serverInfoCache.get(binding.serverId);

      long start = System.currentTimeMillis();
      AnalysisResults analysisResults;
      try {
        analysisResults = analyze(configuration, collector);
      } catch (GlobalStorageUpdateRequiredException e) {
        updateServerStorage(engine, serverInfo);
        updateProjectStorage(engine, serverInfo);
        analysisResults = analyze(configuration, collector);
      } catch (StorageException e) {
        updateProjectStorage(engine, serverInfo);
        analysisResults = analyze(configuration, collector);
      }

      String filePath = FileUtils.toSonarQubePath(getFileRelativePath(baseDir, uri));
      ServerIssueTracker serverIssueTracker = workspaceTrackers.get(baseDir.toString());
      serverIssueTracker.matchAndTrack(filePath, issues, issueListener, shouldFetchServerIssues);

      int analysisTime = (int) (System.currentTimeMillis() - start);

      return new AnalysisResultsWrapper(analysisResults, analysisTime);
    }

    private AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener) {
      return engine.analyze(configuration, issueListener, logOutput, null);
    }
  }

  private boolean isTest(URI uri) {
    return userSettings.testMatcher.matches(Paths.get(uri));
  }

  // visible for testing
  Path findBaseDir(URI uri) {
    return findBaseDir(workspaceFolders, uri);
  }

  private static String getFileRelativePath(Path baseDir, URI uri) {
    return baseDir.relativize(Paths.get(uri)).toString();
  }

  // visible for testing
  static Path findBaseDir(List<String> workspaceFolders, URI uri) {
    Path inputFilePath = Paths.get(uri);
    if (!workspaceFolders.isEmpty()) {
      String uriString = inputFilePath.toString();
      for (String folder : workspaceFolders) {
        if (uriString.startsWith(folder)) {
          return Paths.get(folder);
        }
      }
    }

    return inputFilePath.getParent();
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
      diagnostic.setSource(SONARLINT_SOURCE);

      List<Flow> flows = issue.flows();
      // If multiple flows with more than 1 location, keep only the first flow
      if (flows.size() > 1 && flows.stream().anyMatch(f -> f.locations().size() > 1)) {
        flows = Collections.singletonList(flows.get(0));
      }
      diagnostic.setRelatedInformation(flows
        .stream()
        .flatMap(f -> f.locations().stream())
        // Message is mandatory in lsp
        .filter(l -> nonNull(l.getMessage()))
        // Ignore global issue locations
        .filter(l -> nonNull(l.getInputFile()))
        .map(l -> {
          DiagnosticRelatedInformation rel = new DiagnosticRelatedInformation();
          rel.setMessage(l.getMessage());
          rel.setLocation(new Location(l.getInputFile().uri().toString(), position(l)));
          return rel;
        }).collect(Collectors.toList()));

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

  private static Range position(IssueLocation location) {
    return new Range(
      new Position(
        location.getStartLine() - 1,
        location.getStartLineOffset()),
      new Position(
        location.getEndLine() - 1,
        location.getEndLineOffset()));
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
    return CompletableFutures.computeAsync(cancelToken -> {
      cancelToken.checkCanceled();
      List<Object> args = params.getArguments();
      switch (params.getCommand()) {
        case SONARLINT_UPDATE_SERVER_STORAGE_COMMAND:
          List<Object> list = args == null ? null : args.stream().map(UserSettings::parseToMap).collect(Collectors.toList());
          handleUpdateServerStorageCommand(list);
          break;
        case SONARLINT_UPDATE_PROJECT_BINDING_COMMAND:
          Map<String, Object> map = args == null || args.isEmpty() ? null : UserSettings.parseToMap(args.get(0));
          updateBinding(map);
          break;
        case SONARLINT_REFRESH_DIAGNOSTICS_COMMAND:
          Gson gson = new Gson();
          List<Document> docsToRefresh = args == null ? Collections.emptyList()
            : args.stream().map(arg -> gson.fromJson(arg.toString(), Document.class)).collect(Collectors.toList());
          docsToRefresh.forEach(doc -> analyze(parseURI(doc.uri), doc.text, false));
          break;
        default:
          throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Unsupported command: " + params.getCommand(), null));
      }
      return null;
    });
  }

  // visible for testing
  static String getHtmlDescription(RuleDetails ruleDetails) {
    String htmlDescription = ruleDetails.getHtmlDescription();
    String extendedDescription = ruleDetails.getExtendedDescription();
    if (!extendedDescription.isEmpty()) {
      htmlDescription += "<div>" + extendedDescription + "</div>";
    }
    return htmlDescription;
  }

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {
    Map<String, Object> settings = UserSettings.parseToMap(params.getSettings());
    Map<String, Object> entries = (Map<String, Object>) settings.get(SONARLINT_CONFIGURATION_NAMESPACE);
    userSettings = new UserSettings(entries);
    telemetry.optOut(userSettings.disableTelemetry);
  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    // No watched files
  }

  @Override
  public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
    WorkspaceFoldersChangeEvent event = params.getEvent();
    workspaceFolders.removeAll(toList(event.getRemoved()));
    workspaceFolders.addAll(toList(event.getAdded()));
    workspaceFolders.sort(Comparator.reverseOrder());

    if (binding != null) {
      ServerInfo serverInfo = serverInfoCache.get(binding.serverId);
      if (serverInfo != null) {
        ConnectedSonarLintEngine engine = engineCache.getOrCreateConnectedEngine(serverInfo);
        if (engine != null) {
          updateIssueTrackers(engine, serverInfo);
        }
      }
    }
  }

  @Override
  public void setTraceNotification(SetTraceNotificationParams params) {
    this.traceLevel = parseTraceLevel(params.getValue());
  }

  private static TraceValues parseTraceLevel(@Nullable String trace) {
    return Optional.ofNullable(trace)
      .map(String::toUpperCase)
      .map(TraceValues::valueOf)
      .orElse(TraceValues.OFF);
  }

  static class ServerProjectBinding {
    final String serverId;
    final String projectKey;

    ServerProjectBinding(String serverId, String projectKey) {
      this.serverId = serverId;
      this.projectKey = projectKey;
    }
  }

  static class Document {
    final String uri;
    final String text;

    public Document(String uri, String text) {
      this.uri = uri;
      this.text = text;
    }
  }
}
