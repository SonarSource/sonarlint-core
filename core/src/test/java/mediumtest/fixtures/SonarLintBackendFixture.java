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
package mediumtest.fixtures;

import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.annotation.CheckForNull;
import mediumtest.fixtures.storage.ConfigurationScopeStorageFixture;
import mediumtest.fixtures.storage.StorageFixture;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.branch.DidChangeActiveSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.ClientInfoDto;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.clientapi.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FoundFileDto;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.GetProxyPasswordAuthenticationParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.GetProxyPasswordAuthenticationResponse;
import org.sonarsource.sonarlint.core.clientapi.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.clientapi.client.http.SelectProxiesParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.SelectProxiesResponse;
import org.sonarsource.sonarlint.core.clientapi.client.info.GetClientInfoResponse;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.clientapi.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.common.TokenDto;
import org.sonarsource.sonarlint.core.clientapi.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.commons.Language;

import static mediumtest.fixtures.storage.StorageFixture.newStorage;

public class SonarLintBackendFixture {

  public static SonarLintBackendBuilder newBackend() {
    return new SonarLintBackendBuilder();
  }

  public static SonarLintClientBuilder newFakeClient() {
    return new SonarLintClientBuilder();
  }

  public static class SonarLintBackendBuilder {
    private final List<SonarQubeConnectionConfigurationDto> sonarQubeConnections = new ArrayList<>();
    private final List<SonarCloudConnectionConfigurationDto> sonarCloudConnections = new ArrayList<>();
    private final List<ConfigurationScopeDto> configurationScopes = new ArrayList<>();
    private final List<ConfigurationScopeStorageFixture.ConfigurationScopeStorageBuilder> configurationScopeStorages = new ArrayList<>();
    private final Map<String, String> activeBranchPerScopeId = new HashMap<>();
    private final Set<Path> embeddedPluginPaths = new HashSet<>();
    private final Map<String, Path> connectedModeEmbeddedPluginPathsByKey = new HashMap<>();
    private final Set<Language> enabledLanguages = new HashSet<>();
    private final Set<Language> extraEnabledLanguagesInConnectedMode = new HashSet<>();
    private boolean startEmbeddedServer;
    private boolean manageSmartNotifications;
    private boolean areSecurityHotspotsEnabled;
    private boolean synchronizeProjects;
    private boolean taintVulnerabilitiesEnabled = true;
    private String userAgent = "SonarLintBackendFixture";
    private String clientName = "SonarLint Backend Fixture";

    private final Map<String, StandaloneRuleConfigDto> standaloneConfigByKey = new HashMap<>();
    private final List<StorageFixture.StorageBuilder> storages = new ArrayList<>();

    public SonarLintBackendBuilder withSonarQubeConnection() {
      return withSonarQubeConnection("connectionId");
    }

    public SonarLintBackendBuilder withSonarQubeConnection(String connectionId) {
      return withSonarQubeConnection(connectionId, "http://not-used", true, null);
    }

    public SonarLintBackendBuilder withSonarQubeConnection(String connectionId, Consumer<StorageFixture.StorageBuilder> storageBuilder) {
      return withSonarQubeConnection(connectionId, "http://not-used", true, storageBuilder);
    }

    public SonarLintBackendBuilder withSonarQubeConnection(String connectionId, String serverUrl) {
      return withSonarQubeConnection(connectionId, serverUrl, true, null);
    }

    public SonarLintBackendBuilder withSonarQubeConnection(String connectionId, String serverUrl, Consumer<StorageFixture.StorageBuilder> storageBuilder) {
      return withSonarQubeConnection(connectionId, serverUrl, true, storageBuilder);
    }

    public SonarLintBackendBuilder withSonarQubeConnection(String connectionId, ServerFixture.Server server) {
      return withSonarQubeConnection(connectionId, server.baseUrl(), true, null);
    }

    public SonarLintBackendBuilder withSonarQubeConnectionAndNotifications(String connectionId, String serverUrl) {
      return withSonarQubeConnection(connectionId, serverUrl, false, null);
    }

    public SonarLintBackendBuilder withSonarQubeConnectionAndNotifications(String connectionId, String serverUrl, Consumer<StorageFixture.StorageBuilder> storageBuilder) {
      return withSonarQubeConnection(connectionId, serverUrl, false, storageBuilder);
    }

    public SonarLintBackendBuilder withSonarCloudConnectionAndNotifications(String connectionId, String organisationKey, Consumer<StorageFixture.StorageBuilder> storageBuilder) {
      return withSonarCloudConnection(connectionId, organisationKey, false, storageBuilder);
    }

    private SonarLintBackendBuilder withSonarQubeConnection(String connectionId, String serverUrl, boolean disableNotifications,
      Consumer<StorageFixture.StorageBuilder> storageBuilder) {
      if (storageBuilder != null) {
        var storage = newStorage(connectionId);
        storageBuilder.accept(storage);
        storages.add(storage);
      }
      sonarQubeConnections.add(new SonarQubeConnectionConfigurationDto(connectionId, serverUrl, disableNotifications));
      return this;
    }

    // use only when the storage needs to be present but not the corresponding connection
    public SonarLintBackendBuilder withStorage(String connectionId, Consumer<StorageFixture.StorageBuilder> storageBuilder) {
      var storage = newStorage(connectionId);
      storageBuilder.accept(storage);
      storages.add(storage);
      return this;
    }

    public SonarLintBackendBuilder withSonarCloudConnection(String connectionId, String organizationKey, boolean disableNotifications, Consumer<StorageFixture.StorageBuilder> storageBuilder) {
      if (storageBuilder != null) {
        var storage = newStorage(connectionId);
        storageBuilder.accept(storage);
        storages.add(storage);
      }
      sonarCloudConnections.add(new SonarCloudConnectionConfigurationDto(connectionId, organizationKey, disableNotifications));
      return this;
    }

    public SonarLintBackendBuilder withSonarCloudConnection(String connectionId, String organizationKey) {
      sonarCloudConnections.add(new SonarCloudConnectionConfigurationDto(connectionId, organizationKey, true));
      return this;
    }

    public SonarLintBackendBuilder withUnboundConfigScope(String configurationScopeId) {
      return withUnboundConfigScope(configurationScopeId, configurationScopeId);
    }

    public SonarLintBackendBuilder withUnboundConfigScope(String configurationScopeId, String name) {
      return withConfigScope(configurationScopeId, name, null, new BindingConfigurationDto(null, null, false));
    }

    public SonarLintBackendBuilder withBoundConfigScope(String configurationScopeId, String connectionId, String projectKey) {
      return withConfigScope(configurationScopeId, configurationScopeId, null, new BindingConfigurationDto(connectionId, projectKey, false));
    }

    public SonarLintBackendBuilder withBoundConfigScope(String configurationScopeId, String connectionId, String projectKey,
      Consumer<ConfigurationScopeStorageFixture.ConfigurationScopeStorageBuilder> storageBuilder) {
      return withConfigScope(configurationScopeId, configurationScopeId, null, new BindingConfigurationDto(connectionId, projectKey, false), storageBuilder);
    }

    public SonarLintBackendBuilder withBoundConfigScope(String configurationScopeId, String connectionId, String projectKey, String activeBranchName) {
      withConfigScope(configurationScopeId, configurationScopeId, null, new BindingConfigurationDto(connectionId, projectKey, false));
      return withActiveBranch(configurationScopeId, activeBranchName);
    }

    public SonarLintBackendBuilder withActiveBranch(String configurationScopeId, String activeBranchName) {
      activeBranchPerScopeId.put(configurationScopeId, activeBranchName);
      return this;
    }

    public SonarLintBackendBuilder withChildConfigScope(String configurationScopeId, String parentScopeId) {
      return withConfigScope(configurationScopeId, configurationScopeId, parentScopeId, new BindingConfigurationDto(null, null, false));
    }

    public SonarLintBackendBuilder withConfigScope(String configurationScopeId, String name, String parentScopeId, BindingConfigurationDto bindingConfiguration) {
      return withConfigScope(configurationScopeId, name, parentScopeId, bindingConfiguration, null);
    }

    public SonarLintBackendBuilder withConfigScope(String configurationScopeId, String name, String parentScopeId, BindingConfigurationDto bindingConfiguration,
      @Nullable Consumer<ConfigurationScopeStorageFixture.ConfigurationScopeStorageBuilder> storageBuilder) {
      if (storageBuilder != null) {
        var builder = ConfigurationScopeStorageFixture.newBuilder(configurationScopeId);
        storageBuilder.accept(builder);
        configurationScopeStorages.add(builder);
      }
      configurationScopes.add(new ConfigurationScopeDto(configurationScopeId, parentScopeId, true, name, bindingConfiguration));
      return this;
    }

    public SonarLintBackendBuilder withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin plugin) {
      return withStandaloneEmbeddedPlugin(plugin)
        .withEnabledLanguage(plugin.getLanguage());
    }

    public SonarLintBackendBuilder withStandaloneEmbeddedPlugin(TestPlugin plugin) {
      this.embeddedPluginPaths.add(plugin.getPath());
      return this;
    }

    public SonarLintBackendBuilder withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin plugin) {
      this.embeddedPluginPaths.add(plugin.getPath());
      this.connectedModeEmbeddedPluginPathsByKey.put(plugin.getLanguage().getPluginKey(), plugin.getPath());
      return withEnabledLanguage(plugin.getLanguage());
    }

    public SonarLintBackendBuilder withEnabledLanguage(Language language) {
      this.enabledLanguages.add(language);
      return this;
    }

    public SonarLintBackendBuilder withExtraEnabledLanguagesInConnectedMode(Language language) {
      this.extraEnabledLanguagesInConnectedMode.add(language);
      return this;
    }

    public SonarLintBackendBuilder withSecurityHotspotsEnabled() {
      this.areSecurityHotspotsEnabled = true;
      return this;
    }

    public SonarLintBackendBuilder withStandaloneRuleConfig(String ruleKey, boolean isActive, Map<String, String> params) {
      this.standaloneConfigByKey.put(ruleKey, new StandaloneRuleConfigDto(isActive, params));
      return this;
    }

    public SonarLintBackendBuilder withEmbeddedServer() {
      startEmbeddedServer = true;
      return this;
    }

    public SonarLintBackendBuilder withProjectSynchronization() {
      synchronizeProjects = true;
      return this;
    }

    public SonarLintBackendBuilder withTaintVulnerabilitiesDisabled() {
      taintVulnerabilitiesEnabled = false;
      return this;
    }

    public SonarLintBackendBuilder withUserAgent(String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    public SonarLintBackendBuilder withClientName(String clientName) {
      this.clientName = clientName;
      return this;
    }

    public SonarLintBackendBuilder withSmartNotifications() {
      manageSmartNotifications = true;
      return this;
    }

    public SonarLintTestBackend build(FakeSonarLintClient client) {
      var sonarlintUserHome = tempDirectory("slUserHome");
      var workDir = tempDirectory("work");
      var storageParentPath = tempDirectory("storage");
      storages.forEach(storage -> storage.create(storageParentPath));
      var storageRoot = storageParentPath.resolve("storage");
      if (!configurationScopeStorages.isEmpty()) {
        configurationScopeStorages.forEach(storage -> storage.create(storageRoot));
      }
      var sonarLintBackend = new SonarLintTestBackend(client);
      client.setBackend(sonarLintBackend);
      var clientInfo = new ClientInfoDto(clientName, "mediumTests", userAgent);
      var featureFlags = new FeatureFlagsDto(manageSmartNotifications, taintVulnerabilitiesEnabled, synchronizeProjects, startEmbeddedServer, areSecurityHotspotsEnabled);
      try {
        sonarLintBackend
          .initialize(new InitializeParams(clientInfo, featureFlags,
            storageRoot, workDir, embeddedPluginPaths, connectedModeEmbeddedPluginPathsByKey,
            enabledLanguages, extraEnabledLanguagesInConnectedMode, sonarQubeConnections, sonarCloudConnections, sonarlintUserHome.toString(),
            standaloneConfigByKey))
          .get();
      } catch (Exception e) {
        throw new IllegalStateException("Cannot initialize the backend", e);
      }
      sonarLintBackend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(configurationScopes));
      activeBranchPerScopeId.forEach(
        (scopeId, branch) -> sonarLintBackend.getSonarProjectBranchService().didChangeActiveSonarProjectBranch(new DidChangeActiveSonarProjectBranchParams(scopeId, branch)));
      return sonarLintBackend;
    }

    private static Path tempDirectory(String prefix) {
      try {
        return Files.createTempDirectory(prefix);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public SonarLintTestBackend build() {
      return build(newFakeClient().build());
    }
  }

  public static class SonarLintClientBuilder {
    private final List<FoundFileDto> foundFiles = new ArrayList<>();
    private String clientDescription = "";
    private final LinkedHashMap<String, SonarQubeConnectionConfigurationDto> cannedAssistCreatingSonarQubeConnectionByBaseUrl = new LinkedHashMap<>();
    private final LinkedHashMap<String, ConfigurationScopeDto> cannedBindingAssistByProjectKey = new LinkedHashMap<>();
    private boolean rejectingProgress;

    private ProxyDto proxy;
    private GetProxyPasswordAuthenticationResponse proxyAuth;
    private Map<String, Either<TokenDto, UsernamePasswordDto>> credentialsByConnectionId = new HashMap<>();

    public SonarLintClientBuilder withFoundFile(String name, String path, String content) {
      foundFiles.add(new FoundFileDto(name, path, content));
      return this;
    }

    public SonarLintClientBuilder withClientDescription(String clientDescription) {
      this.clientDescription = clientDescription;
      return this;
    }

    public SonarLintClientBuilder rejectingProgress() {
      this.rejectingProgress = true;
      return this;
    }

    public SonarLintClientBuilder withCredentials(String connectionId, String user, String password) {
      credentialsByConnectionId.put(connectionId, Either.forRight(new UsernamePasswordDto(user, password)));
      return this;
    }

    public SonarLintClientBuilder withToken(String connectionId, String token) {
      credentialsByConnectionId.put(connectionId, Either.forLeft(new TokenDto(token)));
      return this;
    }

    public SonarLintClientBuilder withHttpProxy(String hostname, int port) {
      this.proxy = new ProxyDto(Proxy.Type.HTTP, hostname, port);
      return this;
    }

    public SonarLintClientBuilder withDirectProxy() {
      this.proxy = ProxyDto.NO_PROXY;
      return this;
    }

    public SonarLintClientBuilder withHttpProxyAuth(String username, String password) {
      this.proxyAuth = new GetProxyPasswordAuthenticationResponse(username, password);
      return this;
    }

    public SonarLintClientBuilder assistingConnectingAndBindingToSonarQube(String scopeId, String connectionId, String baseUrl, String projectKey) {
      this.cannedAssistCreatingSonarQubeConnectionByBaseUrl.put(baseUrl, new SonarQubeConnectionConfigurationDto(connectionId, baseUrl, true));
      this.cannedBindingAssistByProjectKey.put(projectKey, new ConfigurationScopeDto(scopeId, null, true, scopeId, new BindingConfigurationDto(connectionId, projectKey, false)));
      return this;
    }

    public FakeSonarLintClient build() {
      return new FakeSonarLintClient(foundFiles, clientDescription, cannedAssistCreatingSonarQubeConnectionByBaseUrl,
        cannedBindingAssistByProjectKey,
        rejectingProgress, proxy, proxyAuth, credentialsByConnectionId);
    }
  }

  public static class FakeSonarLintClient implements SonarLintClient {
    private final Map<String, List<BindingSuggestionDto>> bindingSuggestions = new HashMap<>();

    private final List<String> urlsToOpen = new ArrayList<>();
    private final List<ShowMessageParams> messagesToShow = new ArrayList<>();
    private final List<ShowSmartNotificationParams> smartNotificationsToShow = new ArrayList<>();
    private final List<FoundFileDto> foundFiles;
    private final String clientDescription;
    private final LinkedHashMap<String, SonarQubeConnectionConfigurationDto> cannedAssistCreatingSonarQubeConnectionByBaseUrl;
    private final LinkedHashMap<String, ConfigurationScopeDto> bindingAssistResponseByProjectKey;
    private final boolean rejectingProgress;
    private final Map<String, Collection<HotspotDetailsDto>> hotspotToShowByConfigScopeId = new HashMap<>();
    private final Map<String, ProgressReport> progressReportsByTaskId = new ConcurrentHashMap<>();
    private final Set<String> synchronizedConfigScopeIds = new HashSet<>();
    private final ProxyDto proxy;
    private final GetProxyPasswordAuthenticationResponse proxyAuth;
    private final Map<String, Either<TokenDto, UsernamePasswordDto>> credentialsByConnectionId;
    private SonarLintBackendImpl backend;

    public FakeSonarLintClient(List<FoundFileDto> foundFiles, String clientDescription,
      LinkedHashMap<String, SonarQubeConnectionConfigurationDto> cannedAssistCreatingSonarQubeConnectionByBaseUrl,
      LinkedHashMap<String, ConfigurationScopeDto> bindingAssistResponseByProjectKey, boolean rejectingProgress, @Nullable ProxyDto proxy,
      @Nullable GetProxyPasswordAuthenticationResponse proxyAuth, Map<String, Either<TokenDto, UsernamePasswordDto>> credentialsByConnectionId) {
      this.foundFiles = foundFiles;
      this.clientDescription = clientDescription;
      this.cannedAssistCreatingSonarQubeConnectionByBaseUrl = cannedAssistCreatingSonarQubeConnectionByBaseUrl;
      this.bindingAssistResponseByProjectKey = bindingAssistResponseByProjectKey;
      this.rejectingProgress = rejectingProgress;
      this.proxy = proxy;
      this.proxyAuth = proxyAuth;
      this.credentialsByConnectionId = credentialsByConnectionId;
    }

    public void setBackend(SonarLintBackendImpl backend) {
      this.backend = backend;
    }

    @Override
    public void suggestBinding(SuggestBindingParams params) {
      bindingSuggestions.putAll(params.getSuggestions());
    }

    @Override
    public CompletableFuture<FindFileByNamesInScopeResponse> findFileByNamesInScope(FindFileByNamesInScopeParams params) {
      return CompletableFuture.completedFuture(new FindFileByNamesInScopeResponse(foundFiles));
    }

    @Override
    public void openUrlInBrowser(OpenUrlInBrowserParams params) {
      urlsToOpen.add(params.getUrl());
    }

    @Override
    public void showMessage(ShowMessageParams params) {
      messagesToShow.add(params);
    }

    @Override
    public void showSmartNotification(ShowSmartNotificationParams params) {
      smartNotificationsToShow.add(params);
    }

    @Override
    public CompletableFuture<GetClientInfoResponse> getClientInfo() {
      return CompletableFuture.completedFuture(new GetClientInfoResponse(clientDescription));
    }

    @Override
    public void showHotspot(ShowHotspotParams params) {
      hotspotToShowByConfigScopeId.computeIfAbsent(params.getConfigurationScopeId(), k -> new ArrayList<>()).add(params.getHotspotDetails());
    }

    @Override
    public CompletableFuture<AssistCreatingConnectionResponse> assistCreatingConnection(AssistCreatingConnectionParams params) {
      var cannedSonarQubeConnection = cannedAssistCreatingSonarQubeConnectionByBaseUrl.remove(params.getServerUrl());
      if (cannedSonarQubeConnection == null) {
        return canceledFuture();
      }
      backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(List.of(cannedSonarQubeConnection), Collections.emptyList()));
      return CompletableFuture.completedFuture(new AssistCreatingConnectionResponse(cannedSonarQubeConnection.getConnectionId()));
    }

    @Override
    public CompletableFuture<AssistBindingResponse> assistBinding(AssistBindingParams params) {
      var cannedResponse = bindingAssistResponseByProjectKey.remove(params.getProjectKey());
      if (cannedResponse == null) {
        return canceledFuture();
      }
      var scopeId = cannedResponse.getId();
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(scopeId, cannedResponse.getBinding()));
      return CompletableFuture.completedFuture(new AssistBindingResponse(scopeId));
    }

    @Override
    public CompletableFuture<Void> startProgress(StartProgressParams params) {
      if (rejectingProgress) {
        return CompletableFuture.failedFuture(new RuntimeException("Failed to start progress"));
      }
      progressReportsByTaskId.put(params.getTaskId(),
        new ProgressReport(params.getConfigurationScopeId(), params.getTitle(), params.getMessage(), params.isIndeterminate(), params.isCancellable()));
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void reportProgress(ReportProgressParams params) {
      var progressReport = progressReportsByTaskId.computeIfAbsent(params.getTaskId(), k -> {
        throw new IllegalStateException("Cannot update a progress that has not been started before");
      });
      var notification = params.getNotification();
      if (notification.isLeft()) {
        var updateNotification = notification.getLeft();
        progressReport.addStep(new ProgressStep(updateNotification.getMessage(), updateNotification.getPercentage()));
      } else {
        progressReport.complete();
      }
    }

    public Map<String, ProgressReport> getProgressReportsByTaskId() {
      return progressReportsByTaskId;
    }

    @Override
    public void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams params) {
      synchronizedConfigScopeIds.addAll(params.getConfigurationScopeIds());
    }

    public Set<String> getSynchronizedConfigScopeIds() {
      return synchronizedConfigScopeIds;
    }

    public CompletableFuture<GetCredentialsResponse> getCredentials(GetCredentialsParams params) {
      var response = new GetCredentialsResponse(credentialsByConnectionId.get(params.getConnectionId()));
      return CompletableFuture.completedFuture(response);
    }

    public void setToken(String connectionId, String secondToken) {
      credentialsByConnectionId.put(connectionId, Either.forLeft(new TokenDto(secondToken)));
    }

    @Override
    public CompletableFuture<SelectProxiesResponse> selectProxies(SelectProxiesParams params) {
      if (proxy != null) {
        return CompletableFuture.completedFuture(new SelectProxiesResponse(List.of(proxy)));
      }
      return CompletableFuture.completedFuture(new SelectProxiesResponse(List.of()));
    }

    @Override
    public CompletableFuture<GetProxyPasswordAuthenticationResponse> getProxyPasswordAuthentication(GetProxyPasswordAuthenticationParams params) {
      if (proxyAuth != null) {
        return CompletableFuture.completedFuture(proxyAuth);
      }
      return CompletableFuture.completedFuture(new GetProxyPasswordAuthenticationResponse(null, null));
    }

    public boolean hasReceivedSuggestions() {
      return !bindingSuggestions.isEmpty();
    }

    public boolean hasReceivedSmartNotifications() {
      return !smartNotificationsToShow.isEmpty();
    }

    public Map<String, List<BindingSuggestionDto>> getBindingSuggestions() {
      return bindingSuggestions;
    }

    public List<String> getUrlsToOpen() {
      return urlsToOpen;
    }

    public List<ShowMessageParams> getMessagesToShow() {
      return messagesToShow;
    }

    public List<ShowSmartNotificationParams> getSmartNotificationsToShow() {
      return smartNotificationsToShow;
    }

    public Map<String, Collection<HotspotDetailsDto>> getHotspotToShowByConfigScopeId() {
      return hotspotToShowByConfigScopeId;
    }

    private static <T> CompletableFuture<T> canceledFuture() {
      var completableFuture = new CompletableFuture<T>();
      completableFuture.cancel(false);
      return completableFuture;
    }

    public static class ProgressReport {
      @CheckForNull
      private final String configurationScopeId;
      private final String title;
      @CheckForNull
      private final String startingMessage;
      private final boolean indeterminate;
      private final boolean cancellable;
      private final Collection<ProgressStep> steps;
      private boolean complete;

      private ProgressReport(@Nullable String configurationScopeId, String title, @Nullable String startingMessage, boolean indeterminate, boolean cancellable) {
        this(configurationScopeId, title, startingMessage, indeterminate, cancellable, new ArrayList<>(), false);
      }

      public ProgressReport(@Nullable String configurationScopeId, String title, @Nullable String startingMessage, boolean indeterminate, boolean cancellable,
        Collection<ProgressStep> steps, boolean complete) {
        this.configurationScopeId = configurationScopeId;
        this.title = title;
        this.startingMessage = startingMessage;
        this.indeterminate = indeterminate;
        this.cancellable = cancellable;
        this.steps = steps;
        this.complete = complete;
      }

      private void addStep(ProgressStep step) {
        steps.add(step);
      }

      @CheckForNull
      public String getConfigurationScopeId() {
        return configurationScopeId;
      }

      public String getTitle() {
        return title;
      }

      @CheckForNull
      public String getStartingMessage() {
        return startingMessage;
      }

      public boolean isIndeterminate() {
        return indeterminate;
      }

      public boolean isCancellable() {
        return cancellable;
      }

      public Collection<ProgressStep> getSteps() {
        return steps;
      }

      public void complete() {
        this.complete = true;
      }

      public boolean isComplete() {
        return complete;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o)
          return true;
        if (o == null || getClass() != o.getClass())
          return false;
        ProgressReport that = (ProgressReport) o;
        return indeterminate == that.indeterminate && cancellable == that.cancellable && complete == that.complete
          && Objects.equals(configurationScopeId, that.configurationScopeId) && title.equals(that.title) && Objects.equals(startingMessage, that.startingMessage)
          && steps.equals(that.steps);
      }

      @Override
      public int hashCode() {
        return Objects.hash(configurationScopeId, title, startingMessage, indeterminate, cancellable, steps, complete);
      }

      @Override
      public String toString() {
        return "ProgressReport{" +
          "configurationScopeId='" + configurationScopeId + '\'' +
          ", title='" + title + '\'' +
          ", startingMessage='" + startingMessage + '\'' +
          ", indeterminate=" + indeterminate +
          ", cancellable=" + cancellable +
          ", steps=" + steps +
          ", complete=" + complete +
          '}';
      }
    }

    public static class ProgressStep {
      @CheckForNull
      private final String message;
      @CheckForNull
      private final Integer percentage;

      public ProgressStep(@Nullable String message, @Nullable Integer percentage) {
        this.message = message;
        this.percentage = percentage;
      }

      @CheckForNull
      public String getMessage() {
        return message;
      }

      @CheckForNull
      public Integer getPercentage() {
        return percentage;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o)
          return true;
        if (o == null || getClass() != o.getClass())
          return false;
        ProgressStep that = (ProgressStep) o;
        return Objects.equals(message, that.message) && Objects.equals(percentage, that.percentage);
      }

      @Override
      public int hashCode() {
        return Objects.hash(message, percentage);
      }

      @Override
      public String toString() {
        return "ProgressStep{" +
          "message='" + message + '\'' +
          ", percentage=" + percentage +
          '}';
      }
    }

  }
}
