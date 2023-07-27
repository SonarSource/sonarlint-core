/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.clientapi.backend.binding.BindingService;
import org.sonarsource.sonarlint.core.clientapi.backend.branch.SonarProjectBranchService;
import org.sonarsource.sonarlint.core.clientapi.backend.config.ConfigurationService;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.ConnectionService;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotService;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueService;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RulesService;
import org.sonarsource.sonarlint.core.clientapi.backend.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.spring.SonarLintSpringAppConfig;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class SonarLintBackendImpl implements SonarLintBackend {
  private final SonarLintClient client;
  private final AtomicBoolean initializeCalled = new AtomicBoolean(false);
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();

  public SonarLintBackendImpl(SonarLintClient client) {
    this.client = client;
  }

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    return CompletableFuture.runAsync(() -> {
      if (initializeCalled.compareAndSet(false, true) && !initialized.get()) {
        applicationContext.register(SonarLintSpringAppConfig.class);
        applicationContext.registerBean("sonarlintClient", SonarLintClient.class, () -> client);
        applicationContext.registerBean("initializeParams", InitializeParams.class, () -> params);
        applicationContext.refresh();
        initialized.set(true);
      } else {
        throw new UnsupportedOperationException("Already initialized");
      }
    });
  }

  private ConfigurableApplicationContext getInitializedApplicationContext() {
    if (!initialized.get()) {
      throw new IllegalStateException("Backend is not initialized");
    }
    return applicationContext;
  }

  @Override
  public ConnectionService getConnectionService() {
    return getInitializedApplicationContext().getBean(ConnectionService.class);
  }

  @Override
  public ConfigurationService getConfigurationService() {
    return getInitializedApplicationContext().getBean(ConfigurationService.class);
  }

  @Override
  public HotspotService getHotspotService() {
    return getInitializedApplicationContext().getBean(HotspotService.class);
  }

  @Override
  public TelemetryService getTelemetryService() {
    return getInitializedApplicationContext().getBean(TelemetryService.class);
  }

  @Override
  public AnalysisService getAnalysisService() {
    return getInitializedApplicationContext().getBean(AnalysisService.class);
  }

  @Override
  public RulesService getRulesService() {
    return getInitializedApplicationContext().getBean(RulesService.class);
  }

  @Override
  public BindingService getBindingService() {
    return getInitializedApplicationContext().getBean(BindingService.class);
  }

  public SonarProjectBranchService getSonarProjectBranchService() {
    return getInitializedApplicationContext().getBean(SonarProjectBranchService.class);
  }

  @Override
  public IssueService getIssueService() {
    return getInitializedApplicationContext().getBean(IssueService.class);
  }

  @Override
  public CompletableFuture<Void> shutdown() {
    return CompletableFuture.runAsync(() -> {
      initialized.set(false);
      applicationContext.close();
    });
  }

  public int getEmbeddedServerPort() {
    return getInitializedApplicationContext().getBean(EmbeddedServer.class).getPort();
  }

  @Override
  public HttpClient getHttpClientNoAuth() {
    return getInitializedApplicationContext().getBean(ConnectionAwareHttpClientProvider.class).getHttpClient();
  }

  @Override
  public HttpClient getHttpClient(String connectionId) {
    return getInitializedApplicationContext().getBean(ConnectionAwareHttpClientProvider.class).getHttpClient(connectionId);
  }
}
