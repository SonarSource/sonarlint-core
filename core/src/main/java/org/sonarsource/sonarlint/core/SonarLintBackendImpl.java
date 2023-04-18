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
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchServiceImpl;
import org.sonarsource.sonarlint.core.analysis.AnalysisServiceImpl;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.clientapi.backend.authentication.AuthenticationHelperService;
import org.sonarsource.sonarlint.core.clientapi.backend.branch.SonarProjectBranchService;
import org.sonarsource.sonarlint.core.clientapi.backend.config.ConfigurationService;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotService;
import org.sonarsource.sonarlint.core.commons.SonarLintUserHome;
import org.sonarsource.sonarlint.core.embedded.server.AwaitingUserTokenFutureRepository;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.hotspot.HotspotServiceImpl;
import org.sonarsource.sonarlint.core.plugin.PluginsRepository;
import org.sonarsource.sonarlint.core.plugin.PluginsServiceImpl;
import org.sonarsource.sonarlint.core.serverconnection.StorageFacade;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.repository.vcs.ActiveSonarProjectBranchRepository;
import org.sonarsource.sonarlint.core.rules.RulesExtractionHelper;
import org.sonarsource.sonarlint.core.rules.RulesServiceImpl;
import org.sonarsource.sonarlint.core.smartnotifications.SmartNotifications;
import org.sonarsource.sonarlint.core.sync.SynchronizationServiceImpl;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;

public class SonarLintBackendImpl implements SonarLintBackend {

  private final ConfigurationServiceImpl configurationService;
  private final ConnectionServiceImpl connectionService;
  private final RulesServiceImpl rulesService;
  private final HotspotServiceImpl hotspotService;
  private final TelemetryServiceImpl telemetryService;
  private final EmbeddedServer embeddedServer;

  private final ExecutorService clientEventsExecutorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "SonarLint Client Events Processor"));

  private final BindingSuggestionProvider bindingSuggestionProvider;
  private final PluginsServiceImpl pluginsService;
  private final AuthenticationHelperServiceImpl authenticationHelperService;
  private final RulesExtractionHelper rulesExtractionHelper;
  private final SmartNotifications smartNotifications;
  private final SonarProjectBranchServiceImpl sonarProjectBranchService;
  private final SynchronizationServiceImpl synchronizationServiceImpl;
  private final AnalysisServiceImpl analysisService;
  private final StorageFacade storageFacade;

  public SonarLintBackendImpl(SonarLintClient client) {
    EventBus clientEventBus = new AsyncEventBus("clientEvents", clientEventsExecutorService);
    var configurationRepository = new ConfigurationRepository();
    this.configurationService = new ConfigurationServiceImpl(clientEventBus, configurationRepository);
    var connectionConfigurationRepository = new ConnectionConfigurationRepository();
    var awaitingUserTokenFutureRepository = new AwaitingUserTokenFutureRepository();
    this.connectionService = new ConnectionServiceImpl(clientEventBus, connectionConfigurationRepository);
    var pluginRepository = new PluginsRepository();
    pluginsService = new PluginsServiceImpl(pluginRepository);
    rulesExtractionHelper = new RulesExtractionHelper(pluginsService);
    var rulesRepository = new RulesRepository(rulesExtractionHelper);
    var serverApiProvider = new ServerApiProvider(connectionConfigurationRepository, client);
    rulesService = new RulesServiceImpl(serverApiProvider, configurationRepository, rulesRepository);
    this.telemetryService = new TelemetryServiceImpl();
    this.hotspotService = new HotspotServiceImpl(client, configurationRepository, connectionConfigurationRepository, telemetryService);
    var bindingClueProvider = new BindingClueProvider(connectionConfigurationRepository, client);
    var sonarProjectCache = new SonarProjectsCache(serverApiProvider);
    bindingSuggestionProvider = new BindingSuggestionProvider(configurationRepository, connectionConfigurationRepository, client, bindingClueProvider, sonarProjectCache);
    this.embeddedServer = new EmbeddedServer(client, connectionService, awaitingUserTokenFutureRepository, configurationService, bindingSuggestionProvider, serverApiProvider,
      telemetryService);
    this.authenticationHelperService = new AuthenticationHelperServiceImpl(client, embeddedServer, awaitingUserTokenFutureRepository);
    smartNotifications = new SmartNotifications(configurationRepository, connectionConfigurationRepository, serverApiProvider, client, telemetryService);
    var sonarProjectBranchRepository = new ActiveSonarProjectBranchRepository();
    this.sonarProjectBranchService = new SonarProjectBranchServiceImpl(sonarProjectBranchRepository, configurationRepository, clientEventBus);
    this.synchronizationServiceImpl = new SynchronizationServiceImpl(client, configurationRepository, sonarProjectBranchService, serverApiProvider);
    this.storageFacade = new StorageFacade();
    this.analysisService = new AnalysisServiceImpl(configurationRepository, storageFacade);
    clientEventBus.register(bindingSuggestionProvider);
    clientEventBus.register(sonarProjectCache);
    clientEventBus.register(rulesRepository);
    clientEventBus.register(sonarProjectBranchService);
    clientEventBus.register(synchronizationServiceImpl);
  }

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    var enabledLanguagesInConnectedMode = new HashSet<>(params.getEnabledLanguagesInStandaloneMode());
    enabledLanguagesInConnectedMode.addAll(params.getExtraEnabledLanguagesInConnectedMode());
    connectionService
      .initialize(params.getSonarQubeConnections(), params.getSonarCloudConnections());
    pluginsService.initialize(params.getStorageRoot(), params.getEmbeddedPluginPaths(), params.getConnectedModeEmbeddedPluginPathsByKey(),
      params.getEnabledLanguagesInStandaloneMode(), enabledLanguagesInConnectedMode);
    rulesExtractionHelper.initialize(params.getEnabledLanguagesInStandaloneMode(), enabledLanguagesInConnectedMode, params.isEnableSecurityHotspots());
    rulesService.initialize(params.getStorageRoot(), params.getStandaloneRuleConfigByKey());
    hotspotService.initialize(params.getStorageRoot());
    var sonarlintUserHome = Optional.ofNullable(params.getSonarlintUserHome()).map(Paths::get).orElse(SonarLintUserHome.get());
    var workDir = params.getWorkDir();
    if (workDir == null) {
      workDir = sonarlintUserHome.resolve("work");
    }
    createFolderIfNeeded(sonarlintUserHome);
    createFolderIfNeeded(workDir);
    telemetryService.initialize(params.getTelemetryProductKey(), sonarlintUserHome);
    if (params.shouldManageLocalServer()) {
      embeddedServer.initialize(params.getHostInfo());
    }
    authenticationHelperService.initialize(params.getHostInfo().getName());
    if (params.shouldManageSmartNotifications()) {
      smartNotifications.initialize(params.getStorageRoot());
    }
    if (params.shouldSynchronizeProjects()) {
      synchronizationServiceImpl.initialize(params.getStorageRoot(), workDir, enabledLanguagesInConnectedMode, params.getConnectedModeEmbeddedPluginPathsByKey().keySet(),
        params.areTaintVulnerabilitiesEnabled());
    }
    storageFacade.initialize(params.getStorageRoot());
    analysisService.initialize(params.getEnabledLanguagesInStandaloneMode(), enabledLanguagesInConnectedMode);

    return CompletableFuture.completedFuture(null);
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

  public SonarProjectBranchService getSonarProjectBranchService() {
    return sonarProjectBranchService;
  }

  @Override
  public CompletableFuture<Void> shutdown() {
    return CompletableFuture.runAsync(() -> {
      MoreExecutors.shutdownAndAwaitTermination(clientEventsExecutorService, 10, TimeUnit.SECONDS);
      this.pluginsService.shutdown();
      this.bindingSuggestionProvider.shutdown();
      this.embeddedServer.shutdown();
      this.smartNotifications.shutdown();
      this.synchronizationServiceImpl.shutdown();
    });
  }

  public int getEmbeddedServerPort() {
    return embeddedServer.getPort();
  }
}
