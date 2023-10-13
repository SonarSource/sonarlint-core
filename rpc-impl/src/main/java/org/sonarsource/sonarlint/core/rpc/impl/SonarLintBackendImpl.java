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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.SpringApplicationContextInitializer;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueStorageService;
import org.sonarsource.sonarlint.core.rpc.protocol.SingleThreadedMessageConsumer;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintBackend;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintClient;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.PathTypeAdapter;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.BackendErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.BindingService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.SonarProjectBranchService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.ConfigurationService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.IssueService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.NewCodeService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RulesService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.IssueTrackingService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.SecurityHotspotMatchingService;
import org.springframework.context.ConfigurableApplicationContext;

public class SonarLintBackendImpl implements SonarLintBackend {
  private final SonarLintClient client;
  private final AtomicBoolean initializeCalled = new AtomicBoolean(false);
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final Future<Void> launcherFuture;
  private SpringApplicationContextInitializer springApplicationContextInitializer;

  public SonarLintBackendImpl(InputStream in, OutputStream out, ExecutorService messageReaderExecutor, ExecutorService messageWriterExecutor) {
    var launcher = new Launcher.Builder<SonarLintClient>()
      .setLocalService(this)
      .setRemoteInterface(SonarLintClient.class)
      .setInput(in)
      .setOutput(out)
      .setExecutorService(messageReaderExecutor)
      .configureGson(gsonBuilder -> gsonBuilder
        .registerTypeHierarchyAdapter(Path.class, new PathTypeAdapter())
      )
      .wrapMessages(m -> new SingleThreadedMessageConsumer(m, messageWriterExecutor))
      .create();

    this.client = launcher.getRemoteProxy();

    this.launcherFuture = launcher.startListening();

    // TODO log that startup is completed
  }

  public Future<Void> getLauncherFuture() {
    return launcherFuture;
  }

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      if (initializeCalled.compareAndSet(false, true) && !initialized.get()) {
        springApplicationContextInitializer = new SpringApplicationContextInitializer(client, params);
        initialized.set(true);
      } else {
        ResponseError error = new ResponseError(BackendErrorCode.BACKEND_ALREADY_INITIALIZED, "Backend already initialized", null);
        throw new ResponseErrorException(error);
      }
      return null;
    });
  }

  protected ConfigurableApplicationContext getInitializedApplicationContext() {
    if (!initialized.get()) {
      throw new IllegalStateException("Backend is not initialized");
    }
    return springApplicationContextInitializer.getInitializedApplicationContext();
  }

  @Override
  public ConnectionService getConnectionService() {
    return new ConnectionServiceDelegate(() -> getInitializedApplicationContext().getBean(ConnectionService.class));
  }

  @Override
  public ConfigurationService getConfigurationService() {
    return new ConfigurationServiceDelegate(() -> getInitializedApplicationContext().getBean(ConfigurationService.class));
  }

  @Override
  public HotspotService getHotspotService() {
    return new HotspotServiceDelegate(() -> getInitializedApplicationContext().getBean(HotspotService.class));
  }

  @Override
  public TelemetryService getTelemetryService() {
    return new TelemetryServiceDelegate(() -> getInitializedApplicationContext().getBean(TelemetryService.class));
  }

  @Override
  public AnalysisService getAnalysisService() {
    return new AnalysisServiceDelegate(() -> getInitializedApplicationContext().getBean(AnalysisService.class));
  }

  @Override
  public RulesService getRulesService() {
    return new RulesServiceDelegate(() -> getInitializedApplicationContext().getBean(RulesService.class));
  }

  @Override
  public BindingService getBindingService() {
    return new BindingServiceDelegate(() -> getInitializedApplicationContext().getBean(BindingService.class));
  }

  public SonarProjectBranchService getSonarProjectBranchService() {
    return new SonarProjectBranchServiceDelegate(() -> getInitializedApplicationContext().getBean(SonarProjectBranchService.class));
  }

  @Override
  public IssueService getIssueService() {
    return new IssueServiceDelegate(() -> getInitializedApplicationContext().getBean(IssueService.class));
  }

  @Override
  public IssueTrackingService getIssueTrackingService() {
    return new IssueTrackingServiceDelegate(() -> getInitializedApplicationContext().getBean(IssueTrackingService.class));
  }

  @Override
  public SecurityHotspotMatchingService getSecurityHotspotMatchingService() {
    return new SecurityHotspotMatchingServiceDelegate(() -> getInitializedApplicationContext().getBean(SecurityHotspotMatchingService.class));
  }

  @Override
  public NewCodeService getNewCodeService() {
    return new NewCodeServiceDelegate(() -> getInitializedApplicationContext().getBean(NewCodeService.class));
  }

  @Override
  public CompletableFuture<Void> shutdown() {
    return CompletableFutures.computeAsync(cancelChecker -> {
      var wasInitialized = initialized.getAndSet(false);
      if (wasInitialized) {
        try {
          springApplicationContextInitializer.close();
        } catch (Exception e) {
          throw new IllegalStateException("Error while closing spring context", e);
        }
      }
      return null;
    });
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

}
