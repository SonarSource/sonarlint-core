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

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.analysis.AnalysisServiceImpl;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchServiceImpl;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.clientapi.backend.authentication.AuthenticationHelperService;
import org.sonarsource.sonarlint.core.clientapi.backend.branch.SonarProjectBranchService;
import org.sonarsource.sonarlint.core.clientapi.backend.config.ConfigurationService;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotService;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueService;
import org.sonarsource.sonarlint.core.commons.SonarLintUserHome;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.embedded.server.AwaitingUserTokenFutureRepository;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.hotspot.HotspotServiceImpl;
import org.sonarsource.sonarlint.core.http.AskClientCertificatePredicate;
import org.sonarsource.sonarlint.core.http.ClientProxyCredentialsProvider;
import org.sonarsource.sonarlint.core.http.ClientProxySelector;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.issue.IssueServiceImpl;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.PluginsRepository;
import org.sonarsource.sonarlint.core.plugin.PluginsServiceImpl;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.repository.vcs.ActiveSonarProjectBranchRepository;
import org.sonarsource.sonarlint.core.rules.RulesExtractionHelper;
import org.sonarsource.sonarlint.core.rules.RulesServiceImpl;
import org.sonarsource.sonarlint.core.serverconnection.StorageService;
import org.sonarsource.sonarlint.core.smartnotifications.SmartNotifications;
import org.sonarsource.sonarlint.core.sync.SynchronizationServiceImpl;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;

public class InitializedSonarLintBackend implements SonarLintBackend {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConfigurationServiceImpl configurationService;
  private final ConnectionServiceImpl connectionService;
  private final RulesServiceImpl rulesService;
  private final HotspotServiceImpl hotspotService;
  private final IssueServiceImpl issueService;
  private final TelemetryServiceImpl telemetryService;
  private final EmbeddedServer embeddedServer;

  private final ExecutorService clientEventsExecutorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "SonarLint Client Events Processor"));

  private final BindingSuggestionProviderImpl bindingSuggestionProvider;
  private final PluginsServiceImpl pluginsService;
  private final AuthenticationHelperServiceImpl authenticationHelperService;
  private final RulesExtractionHelper rulesExtractionHelper;
  private final SmartNotifications smartNotifications;
  private final SonarProjectBranchServiceImpl sonarProjectBranchService;
  private final SynchronizationServiceImpl synchronizationServiceImpl;
  private final AnalysisServiceImpl analysisService;
  private final StorageService storageService;
  private final LanguageSupportRepository languageSupportRepository;
  private final HttpClientProvider httpClientProvider;
  private final ConnectionAwareHttpClientProvider connectionAwareHttpClientProvider;

  InitializedSonarLintBackend(SonarLintClient client, InitializeParams params) {
    var sonarlintUserHome = Optional.ofNullable(params.getSonarlintUserHome()).map(Paths::get).orElse(SonarLintUserHome.get());
    var workDir = params.getWorkDir();
    if (workDir == null) {
      workDir = sonarlintUserHome.resolve("work");
    }
    createFolderIfNeeded(sonarlintUserHome);
    createFolderIfNeeded(workDir);

    EventBus clientEventBus = new AsyncEventBus("clientEvents", clientEventsExecutorService);
    var configurationRepository = new ConfigurationRepository();
    this.configurationService = new ConfigurationServiceImpl(clientEventBus, configurationRepository);
    var connectionConfigurationRepository = new ConnectionConfigurationRepository();
    var awaitingUserTokenFutureRepository = new AwaitingUserTokenFutureRepository();
    var clientProxySelector = new ClientProxySelector(client);
    var clientProxyCredentialsProvider = new ClientProxyCredentialsProvider(client);
    var askClientCertificatePredicate = new AskClientCertificatePredicate(client);
    this.httpClientProvider = new HttpClientProvider(params.getUserAgent(), sonarlintUserHome, askClientCertificatePredicate, clientProxySelector,
      clientProxyCredentialsProvider);
    this.connectionService = new ConnectionServiceImpl(clientEventBus, connectionConfigurationRepository, params.getSonarQubeConnections(), params.getSonarCloudConnections(),
      httpClientProvider);
    var pluginRepository = new PluginsRepository();
    this.storageService = new StorageService(params.getStorageRoot(), workDir);
    this.languageSupportRepository = new LanguageSupportRepository(params.getEnabledLanguagesInStandaloneMode(), params.getExtraEnabledLanguagesInConnectedMode());
    pluginsService = new PluginsServiceImpl(pluginRepository, languageSupportRepository, storageService, params.getEmbeddedPluginPaths(),
      params.getConnectedModeEmbeddedPluginPathsByKey());
    rulesExtractionHelper = new RulesExtractionHelper(pluginsService, languageSupportRepository, params.isEnableSecurityHotspots());
    var rulesRepository = new RulesRepository(rulesExtractionHelper);
    this.connectionAwareHttpClientProvider = new ConnectionAwareHttpClientProvider(client, httpClientProvider);
    var serverApiProvider = new ServerApiProvider(connectionConfigurationRepository, connectionAwareHttpClientProvider);
    rulesService = new RulesServiceImpl(serverApiProvider, configurationRepository, rulesRepository, storageService, params.getStandaloneRuleConfigByKey());
    this.telemetryService = new TelemetryServiceImpl(params.getTelemetryProductKey(), sonarlintUserHome);
    this.hotspotService = new HotspotServiceImpl(client, storageService, configurationRepository, connectionConfigurationRepository, serverApiProvider, telemetryService);
    this.issueService = new IssueServiceImpl(configurationRepository, serverApiProvider, storageService, telemetryService);
    var bindingClueProvider = new BindingClueProvider(connectionConfigurationRepository, client);
    var sonarProjectCache = new SonarProjectsCache(serverApiProvider);
    bindingSuggestionProvider = new BindingSuggestionProviderImpl(configurationRepository, connectionConfigurationRepository, client, bindingClueProvider, sonarProjectCache);
    this.embeddedServer = new EmbeddedServer(client, connectionService, awaitingUserTokenFutureRepository, configurationService, bindingSuggestionProvider, serverApiProvider,
      telemetryService, params.getHostInfo());
    this.authenticationHelperService = new AuthenticationHelperServiceImpl(client, embeddedServer, awaitingUserTokenFutureRepository, params.getHostInfo().getName(),
      httpClientProvider);
    smartNotifications = new SmartNotifications(configurationRepository, connectionConfigurationRepository, serverApiProvider, client, storageService, telemetryService);
    var sonarProjectBranchRepository = new ActiveSonarProjectBranchRepository();
    this.sonarProjectBranchService = new SonarProjectBranchServiceImpl(sonarProjectBranchRepository, configurationRepository, clientEventBus);
    this.synchronizationServiceImpl = new SynchronizationServiceImpl(client, configurationRepository, languageSupportRepository, sonarProjectBranchService, serverApiProvider,
      storageService, params.getConnectedModeEmbeddedPluginPathsByKey().keySet());
    this.analysisService = new AnalysisServiceImpl(configurationRepository, languageSupportRepository, storageService);
    clientEventBus.register(bindingSuggestionProvider);
    clientEventBus.register(sonarProjectCache);
    clientEventBus.register(rulesRepository);
    clientEventBus.register(sonarProjectBranchService);
    clientEventBus.register(synchronizationServiceImpl);

    if (params.shouldManageLocalServer()) {
      embeddedServer.start();
    }
    if (params.shouldManageSmartNotifications()) {
      smartNotifications.initialize();
    }
    if (params.shouldSynchronizeProjects()) {
      synchronizationServiceImpl.startScheduledSync();
    }
  }

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    return CompletableFuture.failedFuture(new UnsupportedOperationException("Already initialized"));
  }

  private static void createFolderIfNeeded(Path path) {
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create directory '" + path.toString() + "'", e);
    }
  }

  @Override
  public ConnectionServiceImpl getConnectionService() {
    return connectionService;
  }

  @Override
  public AuthenticationHelperService getAuthenticationHelperService() {
    return authenticationHelperService;
  }

  @Override
  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  @Override
  public HotspotService getHotspotService() {
    return hotspotService;
  }

  @Override
  public TelemetryServiceImpl getTelemetryService() {
    return telemetryService;
  }

  @Override
  public AnalysisService getAnalysisService() {
    return analysisService;
  }

  @Override
  public RulesServiceImpl getRulesService() {
    return rulesService;
  }

  @Override
  public BindingSuggestionProviderImpl getBindingService() {
    return bindingSuggestionProvider;
  }

  public SonarProjectBranchService getSonarProjectBranchService() {
    return sonarProjectBranchService;
  }

  @Override
  public IssueService getIssueService() {
    return issueService;
  }

  @Override
  public CompletableFuture<Void> shutdown() {
    return CompletableFuture.runAsync(() -> {
      var shutdownTasks = List.<Runnable>of(
        () -> MoreExecutors.shutdownAndAwaitTermination(clientEventsExecutorService, 10, TimeUnit.SECONDS),
        this.pluginsService::shutdown,
        this.bindingSuggestionProvider::shutdown,
        this.embeddedServer::shutdown,
        this.smartNotifications::shutdown,
        this.synchronizationServiceImpl::shutdown,
        this.storageService::close,
        this.httpClientProvider::close);
      shutdownTasks.forEach(InitializedSonarLintBackend::shutdown);
    });
  }

  private static void shutdown(Runnable task) {
    try {
      task.run();
    } catch (Exception e) {
      LOG.error("Error when shutting down", e);
    }
  }

  public int getEmbeddedServerPort() {
    return embeddedServer.getPort();
  }

  @Override
  public HttpClient getHttpClient(String connectionId) {
    return connectionAwareHttpClientProvider.getHttpClient(connectionId);
  }

  @Override
  public HttpClient getHttpClientNoAuth() {
    return connectionAwareHttpClientProvider.getHttpClient();
  }
}
