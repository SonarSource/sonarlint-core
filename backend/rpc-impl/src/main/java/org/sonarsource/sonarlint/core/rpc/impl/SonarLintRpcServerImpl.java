/*
 * SonarLint Core - RPC Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.impl;

import ch.qos.logback.classic.Level;
import com.google.common.util.concurrent.MoreExecutors;
import io.sentry.Attachment;
import io.sentry.Hint;
import io.sentry.Sentry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.exodus.core.execution.JobProcessor;
import jetbrains.exodus.core.execution.ThreadJobProcessorPool;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ExecutorServiceShutdownWatchable;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.serverconnection.issues.LocalOnlyIssuesRepository;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.log.LogService;
import org.sonarsource.sonarlint.core.rpc.protocol.SingleThreadedMessageConsumer;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintLauncherBuilder;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.BindingRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.SonarProjectBranchRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.ConfigurationRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.dogfooding.DogfoodingRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.FileRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.flightrecorder.FlightRecordingRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.IssueRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.labs.IdeLabsRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.log.LogRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.NewCodeRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.progress.TaskProgressRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.AiCodeFixRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RulesRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityTrackingRpcService;
import org.sonarsource.sonarlint.core.serverapi.exception.ServerRequestException;
import org.sonarsource.sonarlint.core.spring.SpringApplicationContextInitializer;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ConfigurableApplicationContext;

public class SonarLintRpcServerImpl implements SonarLintRpcServer {

  private static final Logger LOG = LoggerFactory.getLogger(SonarLintRpcServerImpl.class);
  private final SonarLintRpcClient client;
  private final AtomicBoolean initializeCalled = new AtomicBoolean(false);
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final Future<Void> clientListener;
  private final ExecutorServiceShutdownWatchable<ExecutorService> requestsExecutor;
  private final ExecutorService requestAndNotificationsSequentialExecutor;
  private final RpcClientLogOutput logOutput;
  private final ExecutorService messageReaderExecutor;
  private final ExecutorService messageWriterExecutor;
  private SpringApplicationContextInitializer springApplicationContextInitializer;

  public SonarLintRpcServerImpl(InputStream in, OutputStream out) {
    this.messageReaderExecutor = Executors.newCachedThreadPool(r -> {
      var t = new Thread(r);
      t.setName("Server message reader");
      return t;
    });
    this.messageWriterExecutor = Executors.newCachedThreadPool(r -> {
      var t = new Thread(r);
      t.setName("Server message writer");
      return t;
    });
    this.requestAndNotificationsSequentialExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "SonarLint Server RPC sequential executor"));
    this.requestsExecutor = new ExecutorServiceShutdownWatchable<>(Executors.newCachedThreadPool(r -> new Thread(r, "SonarLint Server RPC request executor")));
    var launcher = new SonarLintLauncherBuilder<SonarLintRpcClient>()
      .setLocalService(this)
      .setRemoteInterface(SonarLintRpcClient.class)
      .setInput(in)
      .setOutput(out)
      .setExecutorService(messageReaderExecutor)
      .wrapMessages(m -> new SingleThreadedMessageConsumer(m, messageWriterExecutor, System.err::println))
      .traceMessages(getMessageTracer())
      .setExceptionHandler(this::handleError)
      .create();

    this.client = launcher.getRemoteProxy();
    this.logOutput = new RpcClientLogOutput(client);

    // Remove existing handlers attached to j.u.l root logger
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
    // the initialization phase of your application
    SLF4JBridgeHandler.install();

    var rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    rootLogger.detachAndStopAllAppenders();
    var rpcAppender = new SonarLintRpcClientLogbackAppender(client);
    rpcAppender.start();
    rootLogger.addAppender(rpcAppender);

    this.clientListener = launcher.startListening();
  }

  private ResponseError handleError(Throwable throwable) {
    if (throwable instanceof ResponseErrorException responseErrorException) {
      return responseErrorException.getResponseError();
    } else if ((throwable instanceof CompletionException || throwable instanceof InvocationTargetException)
      && throwable.getCause() instanceof ResponseErrorException responseErrorException) {
        return responseErrorException.getResponseError();
      } else if (throwable instanceof ServerRequestException) {
        return new ResponseError(ResponseErrorCode.RequestFailed, throwable.getMessage(), toStringStacktrace(throwable));
      } else {
        return fallbackResponseError("Internal error", throwable);
      }
  }

  private static ResponseError fallbackResponseError(String header, Throwable throwable) {
    LOG.error("{}: {}", header, throwable.getMessage(), throwable);
    var error = new ResponseError();
    error.setMessage(header + ".");
    error.setCode(ResponseErrorCode.InternalError);
    var string = toStringStacktrace(throwable);
    error.setData(string);

    // Send to Sentry with hint being the full stacktrace
    var stackTraceAttachment = new Attachment(string.getBytes(StandardCharsets.UTF_8), "stacktrace.txt");
    Sentry.captureException(throwable, Hint.withAttachment(stackTraceAttachment));
    return error;
  }

  private static String toStringStacktrace(Throwable throwable) {
    var stackTrace = new ByteArrayOutputStream();
    var stackTraceWriter = new PrintWriter(stackTrace);
    throwable.printStackTrace(stackTraceWriter);
    stackTraceWriter.flush();

    return stackTrace.toString(StandardCharsets.UTF_8);
  }

  private static PrintWriter getMessageTracer() {
    if ("true".equals(System.getProperty("sonarlint.debug.rpc"))) {
      try {
        return new PrintWriter(Paths.get(System.getProperty("user.home")).resolve(".sonarlint").resolve("rpc_backend_session.log").toFile(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        System.err.println("Cannot write rpc debug logs file");
        e.printStackTrace();
      }
    }
    return null;
  }

  public Future<Void> getClientListener() {
    return clientListener;
  }

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    return CompletableFutures.computeAsync(requestAndNotificationsSequentialExecutor, cancelChecker -> {
      SonarLintLogger.get().setLevel(LogService.convert(params.getLogLevel()));
      SonarLintLogger.get().setTarget(logOutput);
      // for flyway logging level
      setLogbackRootLogger(params);
      if (initializeCalled.compareAndSet(false, true) && !initialized.get()) {
        springApplicationContextInitializer = new SpringApplicationContextInitializer(client, params);
        initialized.set(true);
      } else {
        var error = new ResponseError(SonarLintRpcErrorCode.BACKEND_ALREADY_INITIALIZED, "Backend already initialized", null);
        throw new ResponseErrorException(error);
      }
      return null;
    });
  }

  private static void setLogbackRootLogger(InitializeParams params) {
    var root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    var logLevel = switch (params.getLogLevel()) {
      case OFF -> Level.OFF;
      case ERROR -> Level.ERROR;
      case WARN -> Level.WARN;
      case INFO -> Level.INFO;
      case DEBUG -> Level.DEBUG;
      case TRACE -> Level.TRACE;
    };
    root.setLevel(logLevel);
  }

  public ConfigurableApplicationContext getInitializedApplicationContext() {
    if (!initialized.get()) {
      throw new IllegalStateException("Backend is not initialized");
    }
    return springApplicationContextInitializer.getInitializedApplicationContext();
  }

  @Override
  public ConnectionRpcService getConnectionService() {
    return new ConnectionRpcServiceDelegate(this);
  }

  @Override
  public ConfigurationRpcService getConfigurationService() {
    return new ConfigurationRpcServiceDelegate(this);
  }

  @Override
  public FileRpcService getFileService() {
    return new FileRpcServiceDelegate(this);
  }

  @Override
  public HotspotRpcService getHotspotService() {
    return new HotspotRpcServiceDelegate(this);
  }

  @Override
  public TelemetryRpcService getTelemetryService() {
    return new TelemetryRpcServiceDelegate(this);
  }

  @Override
  public AnalysisRpcService getAnalysisService() {
    return new AnalysisRpcServiceDelegate(this);
  }

  @Override
  public RulesRpcService getRulesService() {
    return new RulesRpcServiceDelegate(this);
  }

  @Override
  public BindingRpcService getBindingService() {
    return new BindingRpcServiceDelegate(this);
  }

  public SonarProjectBranchRpcService getSonarProjectBranchService() {
    return new SonarProjectBranchRpcServiceDelegate(this);
  }

  @Override
  public IssueRpcService getIssueService() {
    return new IssueRpcServiceDelegate(this);
  }

  @Override
  public NewCodeRpcService getNewCodeService() {
    return new NewCodeRpcServiceDelegate(this);
  }

  @Override
  public TaintVulnerabilityTrackingRpcService getTaintVulnerabilityTrackingService() {
    return new TaintVulnerabilityTrackingRpcServiceDelegate(this);
  }

  @Override
  public DogfoodingRpcService getDogfoodingService() {
    return new DogfoodingRpcServiceDelegate(this);
  }

  @Override
  public AiCodeFixRpcService getAiCodeFixRpcService() {
    return new AiCodeFixRpcServiceDelegate(this);
  }

  @Override
  public TaskProgressRpcService getTaskProgressRpcService() {
    return new TaskProgressRpcServiceDelegate(this);
  }

  @Override
  public DependencyRiskRpcService getDependencyRiskService() {
    return new DependencyRiskRpcServiceDelegate(this);
  }

  @Override
  public FlightRecordingRpcService getFlightRecordingService() {
    return new FlightRecordingRpcServiceDelegate(this);
  }

  @Override
  public AiAgentRpcService getAiAgentService() {
    return new AiAgentRpcServiceDelegate(this);
  }

  @Override
  public LogRpcService getLogService() {
    return new LogServiceDelegate(this);
  }

  @Override
  public IdeLabsRpcService getIdeLabsService() {
    return new IdeLabsRpcServiceDelegate(this);
  }

  @Override
  public CompletableFuture<Void> shutdown() {
    LOG.info("SonarLint backend shutting down, instance={}", this);
    var executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "SonarLint Server shutdown"));
    CompletableFuture<Void> future = CompletableFutures.computeAsync(executor, cancelChecker -> {
      SonarLintLogger.get().setTarget(logOutput);
      var wasInitialized = initialized.getAndSet(false);
      MoreExecutors.shutdownAndAwaitTermination(requestsExecutor, 1, TimeUnit.SECONDS);
      MoreExecutors.shutdownAndAwaitTermination(requestAndNotificationsSequentialExecutor, 1, TimeUnit.SECONDS);
      if (wasInitialized) {
        try {
          springApplicationContextInitializer.close();
        } catch (Exception e) {
          SonarLintLogger.get().error("Error while closing Spring context", e);
        }
      }
      ThreadJobProcessorPool.getProcessors().forEach(JobProcessor::finish);
      shutdownReaderAndWriter();
      return null;
    });
    executor.shutdown();
    return future;
  }

  public void shutdownReaderAndWriter() {
    messageReaderExecutor.shutdownNow();

    // shutdown writer and disconnect from client asynchronously to make sure the client gets the response
    var scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    scheduledExecutorService.schedule(() -> {
      messageWriterExecutor.shutdownNow();
      disconnectFromClient();
    }, 1, TimeUnit.SECONDS);
    scheduledExecutorService.shutdown();
  }

  private void disconnectFromClient() {
    clientListener.cancel(true);
  }

  public boolean isReaderShutdown() {
    return messageReaderExecutor.isShutdown();
  }

  public int getEmbeddedServerPort() {
    return getInitializedApplicationContext().getBean(EmbeddedServer.class).getPort();
  }

  public StorageService getIssueStorageService() {
    return getInitializedApplicationContext().getBean(StorageService.class);
  }

  public LocalOnlyIssuesRepository getLocalOnlyIssuesRepository() {
    return getInitializedApplicationContext().getBean(LocalOnlyIssuesRepository.class);
  }

  public SonarLintDatabase getDatabase() {
    return getInitializedApplicationContext().getBean(SonarLintDatabase.class);
  }

  ExecutorServiceShutdownWatchable<ExecutorService> getRequestsExecutor() {
    return requestsExecutor;
  }

  ExecutorService getRequestAndNotificationsSequentialExecutor() {
    return requestAndNotificationsSequentialExecutor;
  }

  RpcClientLogOutput getLogOutput() {
    return logOutput;
  }
}
