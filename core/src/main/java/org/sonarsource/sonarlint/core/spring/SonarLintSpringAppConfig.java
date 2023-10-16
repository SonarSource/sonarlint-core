/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.spring;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.ProxySelector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.sonarsource.sonarlint.core.BindingClueProvider;
import org.sonarsource.sonarlint.core.BindingSuggestionProvider;
import org.sonarsource.sonarlint.core.ConfigurationService;
import org.sonarsource.sonarlint.core.ConnectionService;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.SonarProjectsCache;
import org.sonarsource.sonarlint.core.TokenGeneratorHelper;
import org.sonarsource.sonarlint.core.VersionSoonUnsupportedHelper;
import org.sonarsource.sonarlint.core.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchService;
import org.sonarsource.sonarlint.core.commons.SonarLintUserHome;
import org.sonarsource.sonarlint.core.embedded.server.AwaitingUserTokenFutureRepository;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.embedded.server.GeneratedUserTokenHandler;
import org.sonarsource.sonarlint.core.embedded.server.RequestHandlerBindingAssistant;
import org.sonarsource.sonarlint.core.embedded.server.ShowHotspotRequestHandler;
import org.sonarsource.sonarlint.core.embedded.server.ShowIssueRequestHandler;
import org.sonarsource.sonarlint.core.embedded.server.StatusRequestHandler;
import org.sonarsource.sonarlint.core.hotspot.HotspotService;
import org.sonarsource.sonarlint.core.http.AskClientCertificatePredicate;
import org.sonarsource.sonarlint.core.http.ClientProxyCredentialsProvider;
import org.sonarsource.sonarlint.core.http.ClientProxySelector;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.ConnectionUnawareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.issue.IssueService;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueStorageService;
import org.sonarsource.sonarlint.core.newcode.NewCodeService;
import org.sonarsource.sonarlint.core.plugin.PluginsRepository;
import org.sonarsource.sonarlint.core.plugin.PluginsServiceImpl;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.repository.vcs.ActiveSonarProjectBranchRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rules.RulesExtractionHelper;
import org.sonarsource.sonarlint.core.rules.RulesService;
import org.sonarsource.sonarlint.core.server.event.ServerEventsService;
import org.sonarsource.sonarlint.core.smartnotifications.SmartNotifications;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.SynchronizationServiceImpl;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.tracking.IssueTrackingService;
import org.sonarsource.sonarlint.core.tracking.LocalOnlyIssueRepository;
import org.sonarsource.sonarlint.core.tracking.SecurityHotspotMatchingService;
import org.sonarsource.sonarlint.core.usertoken.UserTokenService;
import org.sonarsource.sonarlint.core.websocket.WebSocketService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
// FIXME can't use classpath scanning in OSGi, so waiting to move out of process, we have to declare our bean manually
// @ComponentScan(basePackages = "org.sonarsource.sonarlint.core")
@Import({
  EventBusListenersRegistererBeanPostProcessor.class,
  AskClientCertificatePredicate.class,
  ClientProxySelector.class,
  ClientProxyCredentialsProvider.class,
  ConnectionAwareHttpClientProvider.class,
  ConnectionUnawareHttpClientProvider.class,
  ConfigurationService.class,
  ConfigurationRepository.class,
  RulesService.class,
  ServerApiProvider.class,
  ConnectionConfigurationRepository.class,
  RulesRepository.class,
  RulesExtractionHelper.class,
  PluginsServiceImpl.class,
  PluginsRepository.class,
  LanguageSupportRepository.class,
  ConnectionService.class,
  TokenGeneratorHelper.class,
  EmbeddedServer.class,
  StatusRequestHandler.class,
  GeneratedUserTokenHandler.class,
  AwaitingUserTokenFutureRepository.class,
  ShowHotspotRequestHandler.class,
  ShowIssueRequestHandler.class,
  BindingSuggestionProvider.class,
  BindingClueProvider.class,
  SonarProjectsCache.class,
  SonarProjectBranchService.class,
  ActiveSonarProjectBranchRepository.class,
  SynchronizationServiceImpl.class,
  HotspotService.class,
  IssueService.class,
  AnalysisService.class,
  SmartNotifications.class,
  IssueTrackingService.class,
  LocalOnlyIssueRepository.class,
  WebSocketService.class,
  ServerEventsService.class,
  VersionSoonUnsupportedHelper.class,
  LocalOnlyIssueStorageService.class,
  StorageService.class,
  NewCodeService.class,
  SecurityHotspotMatchingService.class,
  UserTokenService.class,
  RequestHandlerBindingAssistant.class
})
public class SonarLintSpringAppConfig {

  private final ExecutorService eventExecutorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "SonarLint Client Events Processor"));

  @Bean
  EventBus provideClientEventBus() {
    return new AsyncEventBus("clientEvents", eventExecutorService);
  }

  @PreDestroy
  void stopClientEventBus() {
    MoreExecutors.shutdownAndAwaitTermination(eventExecutorService, 1, TimeUnit.SECONDS);
  }

  @Bean
  TelemetryService provideTelemetryService(InitializeParams params, @Named("userHome") Path sonarlintUserHome) {
    return new TelemetryService(params.getClientInfo().getTelemetryProductKey(), sonarlintUserHome, params.isFocusOnNewCode());
  }

  @Bean(name = "userHome")
  Path provideSonarLintUserHome(InitializeParams params) {
    var sonarlintUserHome = Optional.ofNullable(params.getSonarlintUserHome()).map(Paths::get).orElse(SonarLintUserHome.get());
    createFolderIfNeeded(sonarlintUserHome);
    return sonarlintUserHome;
  }

  @Bean(name = "workDir")
  Path provideSonarLintWorkDir(InitializeParams params, @Named("userHome") Path sonarlintUserHome) {
    var workDir = Optional.ofNullable(params.getWorkDir()).orElse(sonarlintUserHome.resolve("work"));
    createFolderIfNeeded(workDir);
    return workDir;
  }

  @Bean(name = "storageRoot")
  Path provideSonarLintStorageRoot(InitializeParams params, @Named("userHome") Path sonarlintUserHome) {
    var storageRoot = Optional.ofNullable(params.getStorageRoot()).orElse(sonarlintUserHome.resolve("storage"));
    createFolderIfNeeded(storageRoot);
    return storageRoot;
  }

  @Bean
  HttpClientProvider provideHttpClientProvider(InitializeParams params, @Named("userHome") Path sonarlintUserHome, AskClientCertificatePredicate askClientCertificatePredicate,
    ProxySelector proxySelector,
    CredentialsProvider proxyCredentialsProvider) {
    return new HttpClientProvider(params.getClientInfo().getUserAgent(), sonarlintUserHome, askClientCertificatePredicate, proxySelector, proxyCredentialsProvider);
  }

  private static void createFolderIfNeeded(Path path) {
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create directory '" + path + "'", e);
    }
  }

}
