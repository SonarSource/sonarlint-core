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

import java.io.IOException;
import java.net.ProxySelector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Named;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.core5.util.Timeout;
import org.sonarsource.sonarlint.core.BindingCandidatesFinder;
import org.sonarsource.sonarlint.core.BindingClueProvider;
import org.sonarsource.sonarlint.core.BindingSuggestionProvider;
import org.sonarsource.sonarlint.core.ConfigurationService;
import org.sonarsource.sonarlint.core.ConnectionService;
import org.sonarsource.sonarlint.core.ConnectionSuggestionProvider;
import org.sonarsource.sonarlint.core.OrganizationsCache;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.SharedConnectedModeSettingsProvider;
import org.sonarsource.sonarlint.core.SonarCloudActiveEnvironment;
import org.sonarsource.sonarlint.core.SonarProjectsCache;
import org.sonarsource.sonarlint.core.TokenGeneratorHelper;
import org.sonarsource.sonarlint.core.VersionSoonUnsupportedHelper;
import org.sonarsource.sonarlint.core.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.SonarLintUserHome;
import org.sonarsource.sonarlint.core.embedded.server.AwaitingUserTokenFutureRepository;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.embedded.server.GeneratedUserTokenHandler;
import org.sonarsource.sonarlint.core.embedded.server.RequestHandlerBindingAssistant;
import org.sonarsource.sonarlint.core.embedded.server.ShowHotspotRequestHandler;
import org.sonarsource.sonarlint.core.embedded.server.ShowIssueRequestHandler;
import org.sonarsource.sonarlint.core.embedded.server.StatusRequestHandler;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.file.ServerFilePathsProvider;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.fs.FileExclusionService;
import org.sonarsource.sonarlint.core.hotspot.HotspotService;
import org.sonarsource.sonarlint.core.http.AskClientCertificatePredicate;
import org.sonarsource.sonarlint.core.http.ClientProxyCredentialsProvider;
import org.sonarsource.sonarlint.core.http.ClientProxySelector;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpConfig;
import org.sonarsource.sonarlint.core.http.ssl.CertificateStore;
import org.sonarsource.sonarlint.core.http.ssl.SslConfig;
import org.sonarsource.sonarlint.core.issue.IssueService;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueStorageService;
import org.sonarsource.sonarlint.core.newcode.NewCodeService;
import org.sonarsource.sonarlint.core.plugin.PluginsRepository;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SslConfigurationDto;
import org.sonarsource.sonarlint.core.rules.RulesExtractionHelper;
import org.sonarsource.sonarlint.core.rules.RulesService;
import org.sonarsource.sonarlint.core.server.event.ServerEventsService;
import org.sonarsource.sonarlint.core.smartnotifications.SmartNotifications;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.HotspotSynchronizationService;
import org.sonarsource.sonarlint.core.sync.IssueSynchronizationService;
import org.sonarsource.sonarlint.core.sync.SonarProjectBranchesSynchronizationService;
import org.sonarsource.sonarlint.core.sync.SynchronizationService;
import org.sonarsource.sonarlint.core.sync.TaintSynchronizationService;
import org.sonarsource.sonarlint.core.tracking.IssueMatchingService;
import org.sonarsource.sonarlint.core.tracking.LocalOnlyIssueRepository;
import org.sonarsource.sonarlint.core.tracking.SecurityHotspotMatchingService;
import org.sonarsource.sonarlint.core.tracking.TaintVulnerabilityTrackingService;
import org.sonarsource.sonarlint.core.usertoken.UserTokenService;
import org.sonarsource.sonarlint.core.websocket.WebSocketService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.scheduling.support.TaskUtils;

import static org.sonarsource.sonarlint.core.http.ssl.CertificateStore.DEFAULT_PASSWORD;
import static org.sonarsource.sonarlint.core.http.ssl.CertificateStore.DEFAULT_STORE_TYPE;

@Configuration
// Can't use classpath scanning in OSGi, so waiting to move out of process, we have to declare our beans manually
// @ComponentScan(basePackages = "org.sonarsource.sonarlint.core")
@Import({
  AskClientCertificatePredicate.class,
  ClientProxySelector.class,
  ClientProxyCredentialsProvider.class,
  ConnectionAwareHttpClientProvider.class,
  ConfigurationService.class,
  ConfigurationRepository.class,
  RulesService.class,
  ServerApiProvider.class,
  ConnectionConfigurationRepository.class,
  RulesRepository.class,
  RulesExtractionHelper.class,
  PluginsService.class,
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
  ConnectionSuggestionProvider.class,
  BindingClueProvider.class,
  SonarProjectsCache.class,
  SonarProjectBranchTrackingService.class,
  SynchronizationService.class,
  HotspotService.class,
  IssueService.class,
  AnalysisService.class,
  SmartNotifications.class,
  IssueMatchingService.class,
  LocalOnlyIssueRepository.class,
  WebSocketService.class,
  ServerEventsService.class,
  VersionSoonUnsupportedHelper.class,
  LocalOnlyIssueStorageService.class,
  StorageService.class,
  NewCodeService.class,
  SecurityHotspotMatchingService.class,
  UserTokenService.class,
  RequestHandlerBindingAssistant.class,
  TaintVulnerabilityTrackingService.class,
  SonarProjectBranchesSynchronizationService.class,
  TaintSynchronizationService.class,
  IssueSynchronizationService.class,
  HotspotSynchronizationService.class,
  ClientFileSystemService.class,
  PathTranslationService.class,
  ServerFilePathsProvider.class,
  FileExclusionService.class,
  NodeJsService.class,
  OrganizationsCache.class,
  BindingCandidatesFinder.class,
  SharedConnectedModeSettingsProvider.class
})
public class SonarLintSpringAppConfig {

  @Bean(name = "applicationEventMulticaster")
  public ApplicationEventMulticaster simpleApplicationEventMulticaster() {
    var eventMulticaster = new SimpleApplicationEventMulticaster();
    eventMulticaster.setErrorHandler(TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER);
    return eventMulticaster;
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
  SonarCloudActiveEnvironment provideSonarCloudActiveEnvironment(InitializeParams params) {
    var alternativeSonarCloudEnv = params.getAlternativeSonarCloudEnvironment();
    return alternativeSonarCloudEnv == null ? SonarCloudActiveEnvironment.prod()
      : new SonarCloudActiveEnvironment(alternativeSonarCloudEnv.getUri(), alternativeSonarCloudEnv.getWebSocketsEndpointUri());
  }

  @Bean
  HttpClientProvider provideHttpClientProvider(InitializeParams params, @Named("userHome") Path sonarlintUserHome, AskClientCertificatePredicate askClientCertificatePredicate,
    ProxySelector proxySelector, CredentialsProvider proxyCredentialsProvider) {
    return new HttpClientProvider(params.getClientConstantInfo().getUserAgent(), adapt(params.getHttpConfiguration(), sonarlintUserHome), askClientCertificatePredicate,
      proxySelector, proxyCredentialsProvider);
  }

  private static HttpConfig adapt(HttpConfigurationDto dto, @Nullable Path sonarlintUserHome) {
    return new HttpConfig(adapt(dto.getSslConfiguration(), sonarlintUserHome), toTimeout(dto.getConnectTimeout()), toTimeout(dto.getSocketTimeout()),
      toTimeout(dto.getConnectionRequestTimeout()), toTimeout(dto.getResponseTimeout()));
  }

  private static SslConfig adapt(SslConfigurationDto dto, @Nullable Path sonarlintUserHome) {
    return new SslConfig(
      adaptStore(dto.getKeyStorePath(), dto.getKeyStorePassword(), dto.getKeyStoreType(), sonarlintUserHome, "keystore"),
      adaptStore(dto.getTrustStorePath(), dto.getTrustStorePassword(), dto.getTrustStoreType(), sonarlintUserHome, "truststore"));
  }

  private static CertificateStore adaptStore(@Nullable Path storePathConfig, @Nullable String storePasswordConfig, @Nullable String storeTypeConfig,
    @Nullable Path sonarlintUserHome,
    String defaultStoreName) {
    var storePath = storePathConfig;
    if (storePath == null && sonarlintUserHome != null) {
      storePath = sonarlintUserHome.resolve("ssl/" + defaultStoreName + ".p12");
    }
    if (storePath != null) {
      var keyStorePassword = storePasswordConfig == null ? DEFAULT_PASSWORD : storePasswordConfig;
      var keyStoreType = storeTypeConfig == null ? DEFAULT_STORE_TYPE : storeTypeConfig;
      return new CertificateStore(storePath, keyStorePassword, keyStoreType);
    }
    return null;
  }

  @CheckForNull
  private static Timeout toTimeout(@Nullable Duration duration) {
    return duration == null ? null : Timeout.of(duration);
  }

  private static void createFolderIfNeeded(Path path) {
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create directory '" + path + "'", e);
    }
  }
}
