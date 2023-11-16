/*
 * SonarLint Core - Medium Tests
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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import mediumtest.fixtures.storage.ConfigurationScopeStorageFixture;
import mediumtest.fixtures.storage.StorageFixture;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.ClientErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.DidChangeMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.SonarProjectBranches;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerTaintVulnerabilityChangedOrClosedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerTaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListAllFilePathsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListAllFilePathsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.info.GetClientInfoResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidUpdatePluginsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryConstantAttributesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryLiveAttributesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
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
    private final Map<String, String> matchedBranchPerScopeId = new HashMap<>();
    private final Set<Path> embeddedPluginPaths = new HashSet<>();
    private final Map<String, Path> connectedModeEmbeddedPluginPathsByKey = new HashMap<>();
    private final Set<Language> enabledLanguages = new HashSet<>();
    private final Set<Language> extraEnabledLanguagesInConnectedMode = new HashSet<>();
    private boolean startEmbeddedServer;
    private boolean manageSmartNotifications;
    private boolean areSecurityHotspotsEnabled;
    private boolean synchronizeProjects;
    private boolean shouldManageFullSynchronization;
    private boolean taintVulnerabilitiesEnabled = true;
    private boolean manageServerSentEvents;
    private String userAgent = "SonarLintBackendFixture";
    private String clientName = "SonarLint Backend Fixture";

    private final Map<String, StandaloneRuleConfigDto> standaloneConfigByKey = new HashMap<>();
    private final List<StorageFixture.StorageBuilder> storages = new ArrayList<>();
    private boolean isFocusOnNewCode;

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

    public SonarLintBackendBuilder withSonarQubeConnection(String connectionId, ServerFixture.Server server, Consumer<StorageFixture.StorageBuilder> storageBuilder) {
      return withSonarQubeConnection(connectionId, server.baseUrl(), true, storageBuilder);
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

    public SonarLintBackendBuilder withSonarCloudConnection(String connectionId, String organizationKey, boolean disableNotifications,
      Consumer<StorageFixture.StorageBuilder> storageBuilder) {
      if (storageBuilder != null) {
        var storage = newStorage(connectionId);
        storageBuilder.accept(storage);
        storages.add(storage);
      }
      sonarCloudConnections.add(new SonarCloudConnectionConfigurationDto(connectionId, organizationKey, disableNotifications));
      return this;
    }

    public SonarLintBackendBuilder withSonarCloudConnection(String connectionId) {
      return withSonarCloudConnection(connectionId, "orgKey");
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

    public SonarLintBackendBuilder withBoundConfigScope(String configurationScopeId, String connectionId, String projectKey, String matchedBranchName) {
      withConfigScope(configurationScopeId, configurationScopeId, null, new BindingConfigurationDto(connectionId, projectKey, false));
      return withMatchedBranch(configurationScopeId, matchedBranchName);
    }

    public SonarLintBackendBuilder withMatchedBranch(String configurationScopeId, String matchedBranchName) {
      matchedBranchPerScopeId.put(configurationScopeId, matchedBranchName);
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
        .withEnabledLanguageInStandaloneMode(plugin.getLanguage());
    }

    public SonarLintBackendBuilder withStandaloneEmbeddedPlugin(TestPlugin plugin) {
      this.embeddedPluginPaths.add(plugin.getPath());
      return this;
    }

    public SonarLintBackendBuilder withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin plugin) {
      this.embeddedPluginPaths.add(plugin.getPath());
      this.connectedModeEmbeddedPluginPathsByKey.put(plugin.getPluginKey(), plugin.getPath());
      return withEnabledLanguageInStandaloneMode(plugin.getLanguage());
    }

    public SonarLintBackendBuilder withEnabledLanguageInStandaloneMode(Language language) {
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

    /**
     * Also used to enable Web Sockets
     */
    public SonarLintBackendBuilder withServerSentEventsEnabled() {
      this.manageServerSentEvents = true;
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

    public SonarLintBackendBuilder withFullSynchronization() {
      shouldManageFullSynchronization = true;
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

    public SonarLintBackendBuilder withFocusOnNewCode() {
      isFocusOnNewCode = true;
      return this;
    }

    public SonarLintTestRpcServer build(FakeSonarLintRpcClient client) {
      var sonarlintUserHome = tempDirectory("slUserHome");
      var workDir = tempDirectory("work");
      var storageParentPath = tempDirectory("storage");
      storages.forEach(storage -> storage.create(storageParentPath));
      var storageRoot = storageParentPath.resolve("storage");
      if (!configurationScopeStorages.isEmpty()) {
        configurationScopeStorages.forEach(storage -> storage.create(storageRoot));
      }
      try {
        var sonarLintBackend = createTestBackend(client);

        client.setBackend(sonarLintBackend);
        var telemetryInitDto = new TelemetryConstantAttributesDto("mediumTests", "mediumTests",
          "1.2.3", "4.5.6", "linux", "x64", emptyMap());
        var clientInfo = new ClientInfoDto(clientName, userAgent, telemetryInitDto);
        var featureFlags = new FeatureFlagsDto(manageSmartNotifications, taintVulnerabilitiesEnabled, synchronizeProjects, startEmbeddedServer, areSecurityHotspotsEnabled,
          manageServerSentEvents, shouldManageFullSynchronization);

        sonarLintBackend
          .initialize(new InitializeParams(clientInfo, featureFlags,
            storageRoot, workDir, embeddedPluginPaths, connectedModeEmbeddedPluginPathsByKey,
            enabledLanguages, extraEnabledLanguagesInConnectedMode, sonarQubeConnections, sonarCloudConnections, sonarlintUserHome.toString(),
            standaloneConfigByKey, isFocusOnNewCode))
          .get();
        matchedBranchPerScopeId.forEach(
          (scopeId, branch) -> sonarLintBackend.getMatchedSonarProjectBranchRepository().setMatchedBranchName(scopeId, branch));
        sonarLintBackend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(configurationScopes));
        return sonarLintBackend;
      } catch (Exception e) {
        throw new IllegalStateException("Cannot initialize the backend", e);
      }
    }

    private static SonarLintTestRpcServer createTestBackend(FakeSonarLintRpcClient client) throws IOException {
      var clientToServerOutputStream = new PipedOutputStream();
      var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

      var serverToClientOutputStream = new PipedOutputStream();
      var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

      var serverLauncher = new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);

      var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, client);

      return new SonarLintTestRpcServer(serverLauncher, clientLauncher);
    }

    private static Path tempDirectory(String prefix) {
      try {
        return Files.createTempDirectory(prefix);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public SonarLintTestRpcServer build() {
      return build(newFakeClient().build());
    }
  }

  public static class SonarLintClientBuilder {
    private String clientDescription = "";
    private final LinkedHashMap<String, SonarQubeConnectionConfigurationDto> cannedAssistCreatingSonarQubeConnectionByBaseUrl = new LinkedHashMap<>();
    private final LinkedHashMap<String, ConfigurationScopeDto> cannedBindingAssistByProjectKey = new LinkedHashMap<>();
    private boolean rejectingProgress;

    private ProxyDto proxy;
    private GetProxyPasswordAuthenticationResponse proxyAuth;
    private Map<String, Either<TokenDto, UsernamePasswordDto>> credentialsByConnectionId = new HashMap<>();
    private boolean printLogsToStdOut;
    private String resolvedSonarProjectBranch;
    private boolean isResolvedSonarProjectBranchSet;
    private boolean shouldErrorProjectBranchResolution;
    private final Map<String, List<String>> relativeFilePathsByConfigScopeId = new HashMap<>();

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

    public FakeSonarLintRpcClient build() {
      return new FakeSonarLintRpcClient(clientDescription, cannedAssistCreatingSonarQubeConnectionByBaseUrl,
        cannedBindingAssistByProjectKey,
        rejectingProgress, proxy, proxyAuth, credentialsByConnectionId, printLogsToStdOut, resolvedSonarProjectBranch, isResolvedSonarProjectBranchSet,
        shouldErrorProjectBranchResolution, relativeFilePathsByConfigScopeId);
    }

    public SonarLintClientBuilder printLogsToStdOut() {
      this.printLogsToStdOut = true;
      return this;
    }

    public SonarLintClientBuilder withMatchedSonarProjectBranch(String branchName) {
      this.resolvedSonarProjectBranch = branchName;
      this.isResolvedSonarProjectBranchSet = true;
      return this;
    }

    public SonarLintClientBuilder withSonarProjectBranchMatchingError() {
      this.shouldErrorProjectBranchResolution = true;
      return this;
    }

    public SonarLintClientBuilder withFile(String configScopeId, String fileRelativePath) {
      this.relativeFilePathsByConfigScopeId.computeIfAbsent(configScopeId, k -> new ArrayList<>()).add(fileRelativePath);
      return this;
    }
  }

  public static class FakeSonarLintRpcClient implements SonarLintRpcClientDelegate {
    private final List<ShowMessageParams> messagesToShow = new ArrayList<>();
    private final List<ShowSoonUnsupportedMessageParams> soonUnsupportedMessagesToShow = new ArrayList<>();
    private final List<ShowSmartNotificationParams> smartNotificationsToShow = new ArrayList<>();
    private final String clientDescription;
    private final LinkedHashMap<String, SonarQubeConnectionConfigurationDto> cannedAssistCreatingSonarQubeConnectionByBaseUrl;
    private final LinkedHashMap<String, ConfigurationScopeDto> bindingAssistResponseByProjectKey;
    private final boolean rejectingProgress;
    private final Map<String, Collection<HotspotDetailsDto>> hotspotToShowByConfigScopeId = new HashMap<>();
    private final Map<String, ShowIssueParams> issueParamsToShowByIssueKey = new HashMap<>();
    private final Map<String, ProgressReport> progressReportsByTaskId = new ConcurrentHashMap<>();
    private final Set<String> synchronizedConfigScopeIds = new HashSet<>();
    private final Map<String, String> matchedSonarProjectBranchPerConfigScopeId = new HashMap<>();
    private final Map<String, SonarProjectBranches> toResolveSonarProjectBranchesPerConfigScopeId = new HashMap<>();
    private final String resolvedSonarProjectBranch;
    private final boolean isResolvedSonarProjectBranchSet;
    private final boolean shouldErrorProjectBranchResolution;
    private final Map<String, List<String>> relativeFilePathsByConfigScopeId;
    private final ProxyDto proxy;
    private final GetProxyPasswordAuthenticationResponse proxyAuth;
    private final Map<String, Either<TokenDto, UsernamePasswordDto>> credentialsByConnectionId;
    private final boolean printLogsToStdOut;
    private SonarLintRpcServer backend;
    private final Queue<LogParams> logs = new ConcurrentLinkedQueue<>();
    private final List<String> connectionsWhichUpdatedPlugins = new CopyOnWriteArrayList<>();

    public FakeSonarLintRpcClient(String clientDescription,
      LinkedHashMap<String, SonarQubeConnectionConfigurationDto> cannedAssistCreatingSonarQubeConnectionByBaseUrl,
      LinkedHashMap<String, ConfigurationScopeDto> bindingAssistResponseByProjectKey, boolean rejectingProgress, @Nullable ProxyDto proxy,
      @Nullable GetProxyPasswordAuthenticationResponse proxyAuth, Map<String, Either<TokenDto, UsernamePasswordDto>> credentialsByConnectionId,
      boolean printLogsToStdOut, String resolvedSonarProjectBranch, boolean isResolvedSonarProjectBranchSet,
      boolean shouldErrorProjectBranchResolution, Map<String, List<String>> relativeFilePathsByConfigScopeId) {
      this.clientDescription = clientDescription;
      this.cannedAssistCreatingSonarQubeConnectionByBaseUrl = cannedAssistCreatingSonarQubeConnectionByBaseUrl;
      this.bindingAssistResponseByProjectKey = bindingAssistResponseByProjectKey;
      this.rejectingProgress = rejectingProgress;
      this.proxy = proxy;
      this.proxyAuth = proxyAuth;
      this.credentialsByConnectionId = credentialsByConnectionId;
      this.printLogsToStdOut = printLogsToStdOut;
      this.resolvedSonarProjectBranch = resolvedSonarProjectBranch;
      this.isResolvedSonarProjectBranchSet = isResolvedSonarProjectBranchSet;
      this.shouldErrorProjectBranchResolution = shouldErrorProjectBranchResolution;
      this.relativeFilePathsByConfigScopeId = relativeFilePathsByConfigScopeId;
    }

    public void setBackend(SonarLintTestRpcServer backend) {
      this.backend = backend;
    }

    @Override
    public void showMessage(ShowMessageParams params) {
      messagesToShow.add(params);
    }

    @Override
    public void showSoonUnsupportedMessage(ShowSoonUnsupportedMessageParams params) {
      soonUnsupportedMessagesToShow.add(params);
    }

    @Override
    public void showSmartNotification(ShowSmartNotificationParams params) {
      smartNotificationsToShow.add(params);
    }

    @Override
    public GetClientInfoResponse getClientInfo(CancelChecker cancelChecker) {
      return new GetClientInfoResponse(clientDescription);
    }

    @Override
    public void showHotspot(ShowHotspotParams params) {
      hotspotToShowByConfigScopeId.computeIfAbsent(params.getConfigurationScopeId(), k -> new ArrayList<>()).add(params.getHotspotDetails());
    }

    @Override
    public void showIssue(ShowIssueParams params) {
      issueParamsToShowByIssueKey.putIfAbsent(params.getIssueKey(), params);
    }

    @Override
    public AssistCreatingConnectionResponse assistCreatingConnection(AssistCreatingConnectionParams params, CancelChecker cancelChecker) {
      var cannedSonarQubeConnection = cannedAssistCreatingSonarQubeConnectionByBaseUrl.remove(params.getServerUrl());
      if (cannedSonarQubeConnection == null) {
        throw new CancellationException();
      }
      backend.getConnectionService().didUpdateConnections(new DidUpdateConnectionsParams(List.of(cannedSonarQubeConnection), emptyList()));
      return new AssistCreatingConnectionResponse(cannedSonarQubeConnection.getConnectionId());
    }

    @Override
    public AssistBindingResponse assistBinding(AssistBindingParams params, CancelChecker cancelChecker) {
      var cannedResponse = bindingAssistResponseByProjectKey.remove(params.getProjectKey());
      if (cannedResponse == null) {
        throw new CancellationException();
      }
      var scopeId = cannedResponse.getId();
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(scopeId, cannedResponse.getBinding()));
      return new AssistBindingResponse(scopeId);
    }

    @Override
    public void startProgress(StartProgressParams params, CancelChecker cancelChecker) {
      if (rejectingProgress) {
        throw new ResponseErrorException(new ResponseError(ClientErrorCode.PROGRESS_CREATION_FAILED, "Failed to start progress", null));
      }
      progressReportsByTaskId.put(params.getTaskId(),
        new ProgressReport(params.getConfigurationScopeId(), params.getTitle(), params.getMessage(), params.isIndeterminate(), params.isCancellable()));
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

    @Override
    public GetCredentialsResponse getCredentials(GetCredentialsParams params, CancelChecker cancelChecker) {
      return new GetCredentialsResponse(credentialsByConnectionId.get(params.getConnectionId()));
    }

    public void setToken(String connectionId, String token) {
      credentialsByConnectionId.put(connectionId, Either.forLeft(new TokenDto(token)));
    }

    @Override
    public SelectProxiesResponse selectProxies(SelectProxiesParams params, CancelChecker cancelChecker) {
      if (proxy != null) {
        return new SelectProxiesResponse(List.of(proxy));
      }
      return new SelectProxiesResponse(List.of());
    }

    @Override
    public GetProxyPasswordAuthenticationResponse getProxyPasswordAuthentication(GetProxyPasswordAuthenticationParams params, CancelChecker cancelChecker) {
      if (proxyAuth != null) {
        return proxyAuth;
      }
      return new GetProxyPasswordAuthenticationResponse(null, null);
    }

    @Override
    public void didReceiveServerTaintVulnerabilityRaisedEvent(DidReceiveServerTaintVulnerabilityRaisedEvent params) {
    }

    @Override
    public void didReceiveServerTaintVulnerabilityChangedOrClosedEvent(DidReceiveServerTaintVulnerabilityChangedOrClosedEvent params) {
    }

    @Override
    public void didReceiveServerHotspotEvent(DidReceiveServerHotspotEvent params) {
    }

    @Override
    public MatchSonarProjectBranchResponse matchSonarProjectBranch(MatchSonarProjectBranchParams params, CancelChecker cancelChecker) {
      var sonarProjectBranches = params.getSonarProjectBranches();
      toResolveSonarProjectBranchesPerConfigScopeId.put(params.getConfigurationScopeId(), sonarProjectBranches);
      if (shouldErrorProjectBranchResolution) {
        throw new RuntimeException("Error resolving Sonar project branch");
      }
      return new MatchSonarProjectBranchResponse(isResolvedSonarProjectBranchSet ? resolvedSonarProjectBranch : sonarProjectBranches.getMainBranchName());
    }

    @Override
    public void didChangeMatchedSonarProjectBranch(DidChangeMatchedSonarProjectBranchParams params) {
      matchedSonarProjectBranchPerConfigScopeId.put(params.getConfigScopeId(), params.getNewMatchedBranchName());
    }

    public Map<String, SonarProjectBranches> getToMatchSonarProjectBranchesPerConfigScopeId() {
      return toResolveSonarProjectBranchesPerConfigScopeId;
    }

    public Map<String, String> getMatchedSonarProjectBranchPerConfigScopeId() {
      return matchedSonarProjectBranchPerConfigScopeId;
    }

    @Override
    public TelemetryLiveAttributesResponse getTelemetryLiveAttributes() {
      return new TelemetryLiveAttributesResponse(false, false, null, false, emptyList(), emptyList(), emptyMap());
    }

    @Override
    public void log(LogParams params) {
      this.logs.add(params);
      if (printLogsToStdOut) {
        System.out.println(params.getLevel() + " " + (params.getConfigScopeId() != null ? ("[" + params.getConfigScopeId() + "] ") : "") + params.getMessage());
      }
    }

    @Override
    public void didUpdatePlugins(DidUpdatePluginsParams params) {
      this.connectionsWhichUpdatedPlugins.add(params.getConnectionId());
    }

    @Override
    public ListAllFilePathsResponse listAllFilePaths(ListAllFilePathsParams params) {
      return new ListAllFilePathsResponse(relativeFilePathsByConfigScopeId.getOrDefault(params.getConfigurationScopeId(), emptyList()));
    }

    public boolean hasReceivedSmartNotifications() {
      return !smartNotificationsToShow.isEmpty();
    }

    public List<ShowMessageParams> getMessagesToShow() {
      return messagesToShow;
    }

    public List<ShowSoonUnsupportedMessageParams> getSoonUnsupportedMessagesToShow() {
      return soonUnsupportedMessagesToShow;
    }

    public List<ShowSmartNotificationParams> getSmartNotificationsToShow() {
      return smartNotificationsToShow;
    }

    public Map<String, Collection<HotspotDetailsDto>> getHotspotToShowByConfigScopeId() {
      return hotspotToShowByConfigScopeId;
    }

    public Map<String, ShowIssueParams> getIssueParamsToShowByIssueKey() {
      return issueParamsToShowByIssueKey;
    }

    public Queue<LogParams> getLogs() {
      return logs;
    }

    public List<String> getLogMessages() {
      return logs.stream().map(LogParams::getMessage).collect(Collectors.toList());
    }

    public List<String> getConnectionsWhichUpdatedPlugins() {
      return connectionsWhichUpdatedPlugins;
    }

    public void clearLogs() {
      logs.clear();
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
