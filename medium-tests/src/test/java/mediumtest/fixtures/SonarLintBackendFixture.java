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
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
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
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.ConfigScopeNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.X509CertificateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.taint.vulnerability.DidChangeTaintVulnerabilitiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static java.util.Collections.emptyMap;
import static mediumtest.fixtures.storage.StorageFixture.newStorage;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

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
    private boolean enableDataflowBugDetection;

    private Path clientNodeJsPath;

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

    public SonarLintBackendBuilder withDataflowBugDetectionEnabled() {
      this.enableDataflowBugDetection = true;
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

    public SonarLintBackendBuilder withClientNodeJsPath(Path path) {
      clientNodeJsPath = path;
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
        var telemetryInitDto = new TelemetryClientConstantAttributesDto("mediumTests", "mediumTests",
          "1.2.3", "4.5.6", emptyMap());
        var clientInfo = new ClientConstantInfoDto(clientName, userAgent);
        var featureFlags = new FeatureFlagsDto(manageSmartNotifications, taintVulnerabilitiesEnabled, synchronizeProjects, startEmbeddedServer, areSecurityHotspotsEnabled,
          manageServerSentEvents, enableDataflowBugDetection, shouldManageFullSynchronization);

        sonarLintBackend
          .initialize(new InitializeParams(clientInfo, telemetryInitDto, featureFlags,
            storageRoot, workDir, embeddedPluginPaths, connectedModeEmbeddedPluginPathsByKey,
            enabledLanguages, extraEnabledLanguagesInConnectedMode, sonarQubeConnections, sonarCloudConnections, sonarlintUserHome.toString(),
            standaloneConfigByKey, isFocusOnNewCode, clientNodeJsPath))
          .get();
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
    private Map<String, Either<TokenDto, UsernamePasswordDto>> credentialsByConnectionId = new HashMap<>();
    private boolean printLogsToStdOut;
    private Map<String, List<ClientFileDto>> initialFilesByConfigScope = new HashMap<>();

    private final Map<String, String> matchedBranchPerScopeId = new HashMap<>();

    public SonarLintClientBuilder withCredentials(String connectionId, String user, String password) {
      credentialsByConnectionId.put(connectionId, Either.forRight(new UsernamePasswordDto(user, password)));
      return this;
    }

    public SonarLintClientBuilder withToken(String connectionId, String token) {
      credentialsByConnectionId.put(connectionId, Either.forLeft(new TokenDto(token)));
      return this;
    }

    public FakeSonarLintRpcClient build() {
      return spy(new FakeSonarLintRpcClient(credentialsByConnectionId, printLogsToStdOut, matchedBranchPerScopeId, initialFilesByConfigScope));
    }

    public SonarLintClientBuilder printLogsToStdOut() {
      this.printLogsToStdOut = true;
      return this;
    }

    public SonarLintClientBuilder withMatchedBranch(String configurationScopeId, String matchedBranchName) {
      matchedBranchPerScopeId.put(configurationScopeId, matchedBranchName);
      return this;
    }

    public SonarLintClientBuilder withInitialFs(String configScopeId, List<ClientFileDto> clientFileDtos) {
      initialFilesByConfigScope.put(configScopeId, clientFileDtos);
      return this;
    }
  }

  public static class FakeSonarLintRpcClient implements SonarLintRpcClientDelegate {
    private final Queue<ShowSoonUnsupportedMessageParams> soonUnsupportedMessagesToShow = new ConcurrentLinkedQueue<>();
    private final Queue<ShowSmartNotificationParams> smartNotificationsToShow = new ConcurrentLinkedQueue<>();
    private final Map<String, List<HotspotDetailsDto>> hotspotToShowByConfigScopeId = new ConcurrentHashMap<>();
    private final Map<String, List<IssueDetailsDto>> issueToShowByConfigScopeId = new ConcurrentHashMap<>();
    private final Map<String, ProgressReport> progressReportsByTaskId = new ConcurrentHashMap<>();
    private final Set<String> synchronizedConfigScopeIds = new HashSet<>();
    private final Map<String, Either<TokenDto, UsernamePasswordDto>> credentialsByConnectionId;
    private final boolean printLogsToStdOut;
    private final Queue<LogParams> logs = new ConcurrentLinkedQueue<>();
    private final List<DidChangeTaintVulnerabilitiesParams> taintVulnerabilityChanges = new CopyOnWriteArrayList<>();
    private final Map<String, String> matchedBranchPerScopeId;
    private final Map<String, List<ClientFileDto>> initialFilesByConfigScope;

    public FakeSonarLintRpcClient(Map<String, Either<TokenDto, UsernamePasswordDto>> credentialsByConnectionId, boolean printLogsToStdOut,
      Map<String, String> matchedBranchPerScopeId, Map<String, List<ClientFileDto>> initialFilesByConfigScope) {
      this.credentialsByConnectionId = credentialsByConnectionId;
      this.printLogsToStdOut = printLogsToStdOut;
      this.matchedBranchPerScopeId = matchedBranchPerScopeId;
      this.initialFilesByConfigScope = initialFilesByConfigScope;
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
    public String getClientLiveDescription() {
      return "";
    }

    @Override
    public void showHotspot(String configurationScopeId, HotspotDetailsDto hotspotDetails) {
      hotspotToShowByConfigScopeId.computeIfAbsent(configurationScopeId, k -> new ArrayList<>()).add(hotspotDetails);
    }

    @Override
    public void showIssue(String configurationScopeId, IssueDetailsDto issueDetails) {
      issueToShowByConfigScopeId.computeIfAbsent(configurationScopeId, k -> new ArrayList<>()).add(issueDetails);
    }

    @Override
    public AssistCreatingConnectionResponse assistCreatingConnection(AssistCreatingConnectionParams params, CancelChecker cancelChecker) {
      throw new CancellationException("Not stubbed in medium tests");
    }

    @Override
    public AssistBindingResponse assistBinding(AssistBindingParams params, CancelChecker cancelChecker) {
      throw new CancellationException("Not stubbed in medium tests");
    }

    @Override
    public void startProgress(StartProgressParams params) throws UnsupportedOperationException {
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
    public Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) {
      return credentialsByConnectionId.get(connectionId);
    }

    @Override
    public List<ProxyDto> selectProxies(URI uri) {
      return List.of();
    }

    @Override
    public GetProxyPasswordAuthenticationResponse getProxyPasswordAuthentication(String host, int port, String protocol, String prompt, String scheme, URL targetHost) {
      return null;
    }

    @Override
    public boolean checkServerTrusted(List<X509CertificateDto> chain, String authType) {
      return false;
    }

    public void setToken(String connectionId, String token) {
      credentialsByConnectionId.put(connectionId, Either.forLeft(new TokenDto(token)));
    }

    @Override
    public void didReceiveServerHotspotEvent(DidReceiveServerHotspotEvent params) {
    }

    @Override
    public String matchSonarProjectBranch(String configurationScopeId, String mainBranchName, Set<String> allBranchesNames, CancelChecker cancelChecker) {
      if (matchedBranchPerScopeId.containsKey(configurationScopeId)) {
        return matchedBranchPerScopeId.get(configurationScopeId);
      }
      return mainBranchName;
    }

    @Override
    public void didChangeMatchedSonarProjectBranch(String configScopeId, String newMatchedBranchName) {

    }

    @Override
    public void suggestBinding(Map<String, List<BindingSuggestionDto>> suggestionsByConfigScope) {

    }

    @Override
    public void openUrlInBrowser(URL url) {

    }

    @Override
    public void showMessage(MessageType type, String text) {

    }

    @Override
    public TelemetryClientLiveAttributesResponse getTelemetryLiveAttributes() {
      return new TelemetryClientLiveAttributesResponse(emptyMap());
    }

    @Override
    public void didChangeTaintVulnerabilities(String configurationScopeId, Set<UUID> closedTaintVulnerabilityIds, List<TaintVulnerabilityDto> addedTaintVulnerabilities,
      List<TaintVulnerabilityDto> updatedTaintVulnerabilities) {
      this.taintVulnerabilityChanges
        .add(new DidChangeTaintVulnerabilitiesParams(configurationScopeId, closedTaintVulnerabilityIds, addedTaintVulnerabilities, updatedTaintVulnerabilities));
    }

    @Override
    public void log(LogParams params) {
      this.logs.add(params);
      if (printLogsToStdOut) {
        System.out.println(params.getLevel() + " " + (params.getConfigScopeId() != null ? ("[" + params.getConfigScopeId() + "] ") : "") + params.getMessage());
      }
    }

    @Override
    public void didUpdatePlugins(String connectionId) {

    }

    @Override
    public List<ClientFileDto> listFiles(String configScopeId) throws ConfigScopeNotFoundException {
      return initialFilesByConfigScope.getOrDefault(configScopeId, List.of());
    }

    @Override
    public void didChangeNodeJs(@org.jetbrains.annotations.Nullable Path nodeJsPath, @org.jetbrains.annotations.Nullable String version) {

    }

    public Queue<ShowSmartNotificationParams> getSmartNotificationsToShow() {
      return smartNotificationsToShow;
    }

    public Map<String, List<HotspotDetailsDto>> getHotspotToShowByConfigScopeId() {
      return hotspotToShowByConfigScopeId;
    }

    public Map<String, List<IssueDetailsDto>> getIssueToShowByConfigScopeId() {
      return issueToShowByConfigScopeId;
    }

    public Queue<LogParams> getLogs() {
      return logs;
    }

    public List<String> getLogMessages() {
      return logs.stream().map(LogParams::getMessage).collect(Collectors.toList());
    }

    public List<DidChangeTaintVulnerabilitiesParams> getTaintVulnerabilityChanges() {
      return taintVulnerabilityChanges;
    }

    public void clearLogs() {
      logs.clear();
    }

    public void waitForSynchronization() {
      verify(this, timeout(5000)).didSynchronizeConfigurationScopes(any());
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
