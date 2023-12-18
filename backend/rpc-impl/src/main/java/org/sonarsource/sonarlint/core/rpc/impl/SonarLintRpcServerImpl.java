/*
 * SonarLint Core - RPC Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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

import com.google.common.util.concurrent.MoreExecutors;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.exodus.core.execution.JobProcessor;
import jetbrains.exodus.core.execution.ThreadJobProcessorPool;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.spring.SpringApplicationContextInitializer;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueStorageService;
import org.sonarsource.sonarlint.core.rpc.protocol.SingleThreadedMessageConsumer;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.InstantTypeAdapter;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.PathTypeAdapter;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.UuidTypeAdapter;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.BindingRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.SonarProjectBranchRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.ConfigurationRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.FileRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.IssueRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.NewCodeRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RulesRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.IssueTrackingRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.SecurityHotspotMatchingRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityTrackingRpcService;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ConfigurableApplicationContext;

public class SonarLintRpcServerImpl implements SonarLintRpcServer {
  private final SonarLintRpcClient client;
  private final AtomicBoolean initializeCalled = new AtomicBoolean(false);
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final Future<Void> launcherFuture;
  private final ExecutorService requestsExecutor;
  private final ExecutorService requestAndNotificationsSequentialExecutor;
  private final RpcClientLogOutput logOutput;
  private SpringApplicationContextInitializer springApplicationContextInitializer;

  public SonarLintRpcServerImpl(InputStream in, OutputStream out, ExecutorService messageReaderExecutor, ExecutorService messageWriterExecutor) {
    this.requestAndNotificationsSequentialExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "SonarLint Server RPC sequential executor"));
    this.requestsExecutor = Executors.newCachedThreadPool(r -> new Thread(r, "SonarLint Server RPC request executor"));
    var launcher = new Launcher.Builder<SonarLintRpcClient>()
      .setLocalService(this)
      .setRemoteInterface(SonarLintRpcClient.class)
      .setInput(in)
      .setOutput(out)
      .setExecutorService(messageReaderExecutor)
      .configureGson(gsonBuilder -> gsonBuilder
        .registerTypeHierarchyAdapter(Path.class, new PathTypeAdapter())
        .registerTypeHierarchyAdapter(Instant.class, new InstantTypeAdapter())
        .registerTypeHierarchyAdapter(UUID.class, new UuidTypeAdapter()))
      .wrapMessages(m -> new SingleThreadedMessageConsumer(m, messageWriterExecutor, System.err::println))
      .create();

    this.client = launcher.getRemoteProxy();
    this.logOutput = new RpcClientLogOutput(client);

    this.launcherFuture = launcher.startListening();

    logOutput.log("SonarLint backend started", ClientLogOutput.Level.INFO);
  }

  public Future<Void> getLauncherFuture() {
    return launcherFuture;
  }

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    return CompletableFutures.computeAsync(requestAndNotificationsSequentialExecutor, cancelChecker -> {
      SonarLintLogger.setTarget(logOutput);
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
  public IssueTrackingRpcService getIssueTrackingService() {
    return new IssueTrackingRpcServiceDelegate(this);
  }

  @Override
  public SecurityHotspotMatchingRpcService getSecurityHotspotMatchingService() {
    return new SecurityHotspotMatchingRpcServiceDelegate(this);
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
  public CompletableFuture<Void> shutdown() {
    var executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "SonarLint Server shutdown"));
    CompletableFuture<Void> future = CompletableFutures.computeAsync(executor, cancelChecker -> {
      SonarLintLogger.setTarget(logOutput);
      var wasInitialized = initialized.getAndSet(false);
      MoreExecutors.shutdownAndAwaitTermination(requestsExecutor, 1, java.util.concurrent.TimeUnit.SECONDS);
      MoreExecutors.shutdownAndAwaitTermination(requestAndNotificationsSequentialExecutor, 1, java.util.concurrent.TimeUnit.SECONDS);
      if (wasInitialized) {
        try {
          springApplicationContextInitializer.close();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      ThreadJobProcessorPool.getProcessors().forEach(JobProcessor::finish);
      launcherFuture.cancel(true);
      return null;
    });
    executor.shutdown();
    return future;
  }

  public int getEmbeddedServerPort() {
    return getInitializedApplicationContext().getBean(EmbeddedServer.class).getPort();
  }

  public HttpClient getHttpClientNoAuth() {
    return getInitializedApplicationContext().getBean(ConnectionAwareHttpClientProvider.class).getHttpClient();
  }

  public HttpClient getHttpClient(String connectionId) {
    return getInitializedApplicationContext().getBean(ConnectionAwareHttpClientProvider.class).getHttpClient(connectionId);
  }

  public LocalOnlyIssueStorageService getLocalOnlyIssueStorageService() {
    return getInitializedApplicationContext().getBean(LocalOnlyIssueStorageService.class);
  }

  public StorageService getIssueStorageService() {
    return getInitializedApplicationContext().getBean(StorageService.class);
  }

  ExecutorService getRequestsExecutor() {
    return requestsExecutor;
  }

  ExecutorService getRequestAndNotificationsSequentialExecutor() {
    return requestAndNotificationsSequentialExecutor;
  }

  RpcClientLogOutput getLogOutput() {
    return logOutput;
  }
}
