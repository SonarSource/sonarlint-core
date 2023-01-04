/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.HostInfoDto;
import org.sonarsource.sonarlint.core.clientapi.backend.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FoundFileDto;
import org.sonarsource.sonarlint.core.clientapi.client.host.GetHostInfoResponse;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import testutils.MockWebServerExtensionWithProtobuf;

public class SonarLintBackendFixture {

  public static final String MEDIUM_TESTS_PRODUCT_KEY = "mediumTests";

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
    private final Set<Path> embeddedPluginPaths = new HashSet<>();
    private final Map<String, Path> extraPluginPathsByKey = new HashMap<>();
    private final Set<Language> enabledLanguages = new HashSet<>();
    private Path storageRoot = Paths.get(".");
    private Path sonarlintUserHome = Paths.get(".");
    private boolean startEmbeddedServer;

    public SonarLintBackendBuilder withSonarQubeConnection(String connectionId, String serverUrl) {
      sonarQubeConnections.add(new SonarQubeConnectionConfigurationDto(connectionId, serverUrl));
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

    public SonarLintBackendBuilder withChildConfigScope(String configurationScopeId, String parentScopeId) {
      return withConfigScope(configurationScopeId, configurationScopeId, parentScopeId, new BindingConfigurationDto(null, null, false));
    }

    public SonarLintBackendBuilder withConfigScope(String configurationScopeId, String name, String parentScopeId, BindingConfigurationDto bindingConfiguration) {
      configurationScopes.add(new ConfigurationScopeDto(configurationScopeId, parentScopeId, true, name, bindingConfiguration));
      return this;
    }

    public SonarLintBackendBuilder withStorageRoot(Path storageRoot) {
      this.storageRoot = storageRoot;
      return this;
    }

    public SonarLintBackendBuilder withSonarLintUserHome(Path sonarlintUserHome) {
      this.sonarlintUserHome = sonarlintUserHome;
      return this;
    }

    public SonarLintBackendBuilder withEmbeddedPlugin(TestPlugin plugin) {
      this.embeddedPluginPaths.add(plugin.getPath());
      return withEnabledLanguage(plugin.getLanguage());
    }

    public SonarLintBackendBuilder withExtraPlugin(TestPlugin plugin) {
      extraPluginPathsByKey.put(plugin.getLanguage().getPluginKey(), plugin.getPath());
      return withEnabledLanguage(plugin.getLanguage());
    }

    public SonarLintBackendBuilder withEnabledLanguage(Language language) {
      this.enabledLanguages.add(language);
      return this;
    }

    public SonarLintBackendImpl build(FakeSonarLintClient client) {
      var sonarLintBackend = new SonarLintBackendImpl(client);
      sonarLintBackend
        .initialize(new InitializeParams(client.getClientInfo(), MEDIUM_TESTS_PRODUCT_KEY, storageRoot, embeddedPluginPaths, extraPluginPathsByKey, Collections.emptyMap(),
          enabledLanguages, Collections.emptySet(), false, sonarQubeConnections, sonarCloudConnections, sonarlintUserHome.toString(), startEmbeddedServer));
      sonarLintBackend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(configurationScopes));
      return sonarLintBackend;
    }

    public SonarLintBackendImpl build() {
      return build(newFakeClient().build());
    }

    public SonarLintBackendBuilder withEmbeddedServer() {
      startEmbeddedServer = true;
      return this;
    }
  }

  public static class SonarLintClientBuilder {
    private final List<FoundFileDto> foundFiles = new ArrayList<>();
    private final List<String> textsOfActionsToApply = new ArrayList<>();
    private String hostDescription = "";
    private String hostName = "";
    private AssistCreatingConnectionResponse connectionCreationAssistResponse;
    private AssistBindingResponse bindingAssistResponse;

    public SonarLintClientBuilder withFoundFile(String name, String path, String content) {
      foundFiles.add(new FoundFileDto(name, path, content));
      return this;
    }

    public SonarLintClientBuilder applyingAction(String actionText) {
      textsOfActionsToApply.add(actionText);
      return this;
    }

    public SonarLintClientBuilder withHostDescription(String hostDescription) {
      this.hostDescription = hostDescription;
      return this;
    }

    public SonarLintClientBuilder withHostName(String hostName) {
      this.hostName = hostName;
      return this;
    }

    public SonarLintClientBuilder assistingConnectingAndBindingToSonarQube(String scopeId, String connectionId, String baseUrl, String projectKey) {
      this.connectionCreationAssistResponse = new AssistCreatingConnectionResponse(Either.forLeft(new SonarQubeConnectionConfigurationDto(connectionId, baseUrl)));
      this.bindingAssistResponse = new AssistBindingResponse(new BindingConfigurationDto(connectionId, projectKey, true), scopeId);
      return this;
    }

    public FakeSonarLintClient build() {
      return new FakeSonarLintClient(new HostInfoDto(hostName), foundFiles, textsOfActionsToApply, hostDescription, connectionCreationAssistResponse, bindingAssistResponse);
    }
  }

  public static class FakeSonarLintClient implements SonarLintClient {
    private static final HttpClient httpClient = MockWebServerExtensionWithProtobuf.httpClient();
    private final Map<String, List<BindingSuggestionDto>> bindingSuggestions = new HashMap<>();

    private final List<String> urlsToOpen = new ArrayList<>();
    private final List<ShowMessageParams> messagesToShow = new ArrayList<>();
    private final HostInfoDto clientInfo;
    private final List<FoundFileDto> foundFiles;
    private final Queue<String> textsOfActionsToApply;
    private final String workspaceTitle;
    private final AssistCreatingConnectionResponse assistCreatingConnectionResponse;
    private final AssistBindingResponse bindingAssistResponse;
    private final Map<String, Collection<HotspotDetailsDto>> hotspotToShowByConfigScopeId = new HashMap<>();

    public FakeSonarLintClient(HostInfoDto clientInfo, List<FoundFileDto> foundFiles, List<String> textsOfActionsToApply, String workspaceTitle,
      AssistCreatingConnectionResponse assistCreatingConnectionResponse, AssistBindingResponse bindingAssistResponse) {
      this.clientInfo = clientInfo;
      this.foundFiles = foundFiles;
      this.textsOfActionsToApply = new LinkedList<>(textsOfActionsToApply);
      this.workspaceTitle = workspaceTitle;
      this.assistCreatingConnectionResponse = assistCreatingConnectionResponse;
      this.bindingAssistResponse = bindingAssistResponse;
    }

    public HostInfoDto getClientInfo() {
      return clientInfo;
    }

    @Override
    public void suggestBinding(SuggestBindingParams params) {
      bindingSuggestions.putAll(params.getSuggestions());
    }

    @Override
    public CompletableFuture<FindFileByNamesInScopeResponse> findFileByNamesInScope(FindFileByNamesInScopeParams params) {
      return CompletableFuture.completedFuture(new FindFileByNamesInScopeResponse(foundFiles));
    }

    @Nullable
    @Override
    public HttpClient getHttpClient(String connectionId) {
      return httpClient;
    }

    @Nullable
    @Override
    public HttpClient getHttpClientNoAuth(String forUrl) {
      return httpClient;
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
    public CompletableFuture<GetHostInfoResponse> getHostInfo() {
      return CompletableFuture.completedFuture(new GetHostInfoResponse(workspaceTitle));
    }

    @Override
    public void showHotspot(ShowHotspotParams params) {
      hotspotToShowByConfigScopeId.computeIfAbsent(params.getConfigurationScopeId(), k -> new ArrayList<>()).add(params.getHotspotDetails());
    }

    @Override
    public CompletableFuture<AssistCreatingConnectionResponse> assistCreatingConnection(AssistCreatingConnectionParams params) {
      if (assistCreatingConnectionResponse == null) {
        var completableFuture = new CompletableFuture<AssistCreatingConnectionResponse>();
        completableFuture.cancel(false);
        return completableFuture;
      }
      return CompletableFuture.completedFuture(assistCreatingConnectionResponse);
    }

    @Override
    public CompletableFuture<AssistBindingResponse> assistBinding(AssistBindingParams params) {
      if (bindingAssistResponse == null) {
        var completableFuture = new CompletableFuture<AssistBindingResponse>();
        completableFuture.cancel(false);
        return completableFuture;
      }
      return CompletableFuture.completedFuture(bindingAssistResponse);
    }

    public boolean hasReceivedSuggestions() {
      return !bindingSuggestions.isEmpty();
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

    public Map<String, Collection<HotspotDetailsDto>> getHotspotToShowByConfigScopeId() {
      return hotspotToShowByConfigScopeId;
    }
  }
}
