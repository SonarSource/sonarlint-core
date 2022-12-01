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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.clientapi.config.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.fs.FoundFileDto;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import testutils.MockWebServerExtensionWithProtobuf;

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
    private final Set<Path> embeddedPluginPaths = new HashSet<>();
    private final Map<String, Path> extraPluginPathsByKey = new HashMap<>();
    private final Set<Language> enabledLanguages = new HashSet<>();
    private Path storageRoot = Paths.get(".");

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

    public SonarLintBackendImpl build(SonarLintClient client) {
      var sonarLintBackend = new SonarLintBackendImpl(client);
      sonarLintBackend.initialize(new org.sonarsource.sonarlint.core.clientapi.InitializeParams(storageRoot, embeddedPluginPaths, extraPluginPathsByKey, Collections.emptyMap(),
        enabledLanguages, Collections.emptySet(), null, false, sonarQubeConnections, sonarCloudConnections));
      sonarLintBackend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(configurationScopes));
      return sonarLintBackend;
    }

    public SonarLintBackendImpl build() {
      return build(newFakeClient().build());
    }
  }

  public static class SonarLintClientBuilder {
    private final List<FoundFileDto> foundFiles = new ArrayList<>();

    public SonarLintClientBuilder withFoundFile(String name, String path, String content) {
      foundFiles.add(new FoundFileDto(name, path, content));
      return this;
    }

    public FakeSonarLintClient build() {
      return new FakeSonarLintClient(foundFiles);
    }
  }

  public static class FakeSonarLintClient implements SonarLintClient {
    private static final HttpClient httpClient = MockWebServerExtensionWithProtobuf.httpClient();
    private final Map<String, List<BindingSuggestionDto>> bindingSuggestions = new HashMap<>();

    private List<String> urlsToOpen = new ArrayList<>();
    private final List<FoundFileDto> foundFiles;

    public FakeSonarLintClient(List<FoundFileDto> foundFiles) {
      this.foundFiles = foundFiles;
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

    @Override
    public void openUrlInBrowser(OpenUrlInBrowserParams params) {
      urlsToOpen.add(params.getUrl());
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
  }
}
