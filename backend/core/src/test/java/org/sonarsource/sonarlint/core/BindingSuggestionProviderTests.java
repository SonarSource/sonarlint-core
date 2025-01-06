/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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

import com.google.common.collect.ImmutableSortedSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingSuggestionProviderTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  public static final String SQ_1_ID = "sq1";
  public static final String SC_1_ID = "sc1";
  public static final String SQ_2_ID = "sq2";
  public static final SonarQubeConnectionConfiguration SQ_1 = new SonarQubeConnectionConfiguration(SQ_1_ID, "http://mysonarqube.com", true);
  public static final SonarCloudConnectionConfiguration SC_1 = new SonarCloudConnectionConfiguration(SonarCloudActiveEnvironment.PRODUCTION_URI, SC_1_ID, "myorg", true);
  public static final String CONFIG_SCOPE_ID_1 = "configScope1";
  public static final String PROJECT_KEY_1 = "projectKey1";
  public static final ServerProject SERVER_PROJECT_1 = serverProject(PROJECT_KEY_1, "Project 1");

  private final ConfigurationRepository configRepository = mock(ConfigurationRepository.class);
  private final ConnectionConfigurationRepository connectionRepository = mock(ConnectionConfigurationRepository.class);
  private final SonarLintRpcClient client = mock(SonarLintRpcClient.class);
  private final BindingClueProvider bindingClueProvider = mock(BindingClueProvider.class);
  private final SonarProjectsCache sonarProjectsCache = mock(SonarProjectsCache.class);

  private final BindingSuggestionProvider underTest = new BindingSuggestionProvider(configRepository, connectionRepository, client, bindingClueProvider, sonarProjectsCache);

  @BeforeEach
  public void setup() {
    when(sonarProjectsCache.getTextSearchIndex(anyString(), any(SonarLintCancelMonitor.class))).thenReturn(new TextSearchIndex<>());
    logTester.clear();
  }

  @Test
  void trigger_suggest_binding_if_config_flag_turned_on() {
    when(connectionRepository.getConnectionsById()).thenReturn(Map.of(SQ_1_ID, SQ_1));

    underTest.bindingConfigChanged(new BindingConfigChangedEvent(CONFIG_SCOPE_ID_1, BindingConfiguration.noBinding(true),
      BindingConfiguration.noBinding()));

    assertThat(logTester.logs(LogOutput.Level.DEBUG)).contains("Binding suggestion computation queued for config scopes '" + CONFIG_SCOPE_ID_1 + "'...");
  }

  @Test
  void dont_trigger_suggest_binding_if_config_flag_turned_off() {
    when(connectionRepository.getConnectionsById()).thenReturn(Map.of(SQ_1_ID, SQ_1));

    underTest.bindingConfigChanged(new BindingConfigChangedEvent(CONFIG_SCOPE_ID_1, BindingConfiguration.noBinding(),
      BindingConfiguration.noBinding(true)));

    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void trigger_suggest_binding_if_connection_added_and_at_least_one_config_scope() {
    when(connectionRepository.getConnectionById(SQ_1_ID)).thenReturn(SQ_1);
    when(configRepository.getConfigScopeIds()).thenReturn(Set.of("id1"));
    underTest.connectionAdded(new ConnectionConfigurationAddedEvent(SQ_1_ID));

    assertThat(logTester.logs(LogOutput.Level.DEBUG)).contains("Binding suggestions computation queued for connection '" + SQ_1_ID + "'...");
  }

  @Test
  void dont_trigger_suggest_binding_if_connection_added_but_no_config_scopes() {
    when(connectionRepository.getConnectionById(SQ_1_ID)).thenReturn(SQ_1);
    when(configRepository.getConfigScopeIds()).thenReturn(Set.of());

    underTest.connectionAdded(new ConnectionConfigurationAddedEvent(SQ_1_ID));

    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void dont_trigger_suggest_binding_if_connection_added_but_then_gone() {
    when(connectionRepository.getConnectionById(SQ_1_ID)).thenReturn(null);

    underTest.connectionAdded(new ConnectionConfigurationAddedEvent(SQ_1_ID));

    assertThat(logTester.logs(LogOutput.Level.DEBUG)).isEmpty();
  }

  @Test
  void skip_suggestions_for_non_eligible_config_scopes() {
    when(connectionRepository.getConnectionsById()).thenReturn(Map.of(SQ_1_ID, SQ_1));
    when(connectionRepository.getConnectionById(SQ_1_ID)).thenReturn(SQ_1);

    when(configRepository.getConfigurationScope("configScopeWithNoBinding")).thenReturn(new ConfigurationScope("configScopeWithNoBinding", null, true, "Binding gone!"));

    when(configRepository.getBindingConfiguration("configScopeWithNoConfig")).thenReturn(BindingConfiguration.noBinding());

    when(configRepository.getConfigurationScope("configScopeNotBindable")).thenReturn(new ConfigurationScope("configScopeNotBindable", null, false, "Not bindable"));
    when(configRepository.getBindingConfiguration("configScopeNotBindable")).thenReturn(BindingConfiguration.noBinding());

    when(configRepository.getConfigurationScope("alreadyBound")).thenReturn(new ConfigurationScope("alreadyBound", null, true, "Already bound"));
    when(configRepository.getBindingConfiguration("alreadyBound")).thenReturn(new BindingConfiguration(SQ_1_ID, PROJECT_KEY_1, false));

    when(configRepository.getConfigurationScope("suggestionsDisabled")).thenReturn(new ConfigurationScope("suggestionsDisabled", null, true, "Suggestion disabled"));
    when(configRepository.getBindingConfiguration("suggestionsDisabled")).thenReturn(BindingConfiguration.noBinding(true));

    underTest.suggestBindingForGivenScopesAndAllConnections(ImmutableSortedSet.of("configScopeWithNoBinding", "configScopeWithNoConfig", "configScopeNotBindable", "alreadyBound", "suggestionsDisabled"));

    await().untilAsserted(() -> assertThat(logTester.logs(LogOutput.Level.DEBUG))
      .contains(
        "Configuration scope 'configScopeWithNoBinding' is gone.",
        "Configuration scope 'configScopeWithNoConfig' is gone.",
        "Configuration scope 'configScopeNotBindable' is not bindable.",
        "Configuration scope 'alreadyBound' is already bound.",
        "Configuration scope 'suggestionsDisabled' has binding suggestions disabled."));
  }

  @Test
  void compute_suggestions_for_config_scope_with_invalid_binding() {
    when(connectionRepository.getConnectionsById()).thenReturn(Map.of(SQ_1_ID, SQ_1));
    when(connectionRepository.getConnectionById(SQ_1_ID)).thenReturn(SQ_1);

    when(configRepository.getConfigurationScope("brokenBinding1")).thenReturn(new ConfigurationScope("brokenBinding1", null, true, "Already bound"));
    when(configRepository.getBindingConfiguration("brokenBinding1")).thenReturn(new BindingConfiguration(null, PROJECT_KEY_1, false));

    when(configRepository.getConfigurationScope("brokenBinding2")).thenReturn(new ConfigurationScope("brokenBinding2", null, true, "Already bound"));
    when(configRepository.getBindingConfiguration("brokenBinding2")).thenReturn(new BindingConfiguration(SQ_1_ID, null, false));

    when(configRepository.getConfigurationScope("connectionGone")).thenReturn(new ConfigurationScope("connectionGone", null, true, "Already bound"));
    when(configRepository.getBindingConfiguration("connectionGone")).thenReturn(new BindingConfiguration(SQ_2_ID, PROJECT_KEY_1, false));

    underTest.suggestBindingForGivenScopesAndAllConnections(ImmutableSortedSet.of("brokenBinding1", "brokenBinding2", "connectionGone"));

    await().untilAsserted(() -> assertThat(logTester.logs(LogOutput.Level.DEBUG))
      .contains(
        "Found 0 suggestions for configuration scope 'brokenBinding1'",
        "Found 0 suggestions for configuration scope 'brokenBinding2'",
        "Found 0 suggestions for configuration scope 'connectionGone'"));
  }

  @Test
  void compute_suggestions_favor_search_by_project_key() {
    when(connectionRepository.getConnectionsById()).thenReturn(Map.of(SQ_1_ID, SQ_1));
    when(connectionRepository.getConnectionById(SQ_1_ID)).thenReturn(SQ_1);

    when(configRepository.getConfigurationScope(CONFIG_SCOPE_ID_1)).thenReturn(new ConfigurationScope(CONFIG_SCOPE_ID_1, null, true, "Config scope"));
    when(configRepository.getBindingConfiguration(CONFIG_SCOPE_ID_1)).thenReturn(BindingConfiguration.noBinding());

    when(bindingClueProvider.collectBindingCluesWithConnections(eq(CONFIG_SCOPE_ID_1), eq(Set.of(SQ_1_ID)), any(SonarLintCancelMonitor.class)))
      .thenReturn(List.of(new BindingClueProvider.BindingClueWithConnections(new BindingClueProvider.UnknownBindingClue(PROJECT_KEY_1, false), Set.of(SQ_1_ID))));

    when(sonarProjectsCache.getSonarProject(eq(SQ_1_ID), eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class))).thenReturn(Optional.of(SERVER_PROJECT_1));

    underTest.suggestBindingForGivenScopesAndAllConnections(Set.of(CONFIG_SCOPE_ID_1));

    var captor = ArgumentCaptor.forClass(SuggestBindingParams.class);
    verify(client, timeout(1000)).suggestBinding(captor.capture());

    assertThat(logTester.logs(LogOutput.Level.DEBUG))
      .containsExactly(
        "Binding suggestion computation queued for config scopes '" + CONFIG_SCOPE_ID_1 + "'...",
        "Found 1 suggestion for configuration scope '" + CONFIG_SCOPE_ID_1 + "'");

    verify(sonarProjectsCache, never()).getTextSearchIndex(anyString(), any(SonarLintCancelMonitor.class));

    var params = captor.getValue();
    assertThat(params.getSuggestions()).containsOnlyKeys(CONFIG_SCOPE_ID_1);
    assertThat(params.getSuggestions().get(CONFIG_SCOPE_ID_1))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsOnly(tuple(SQ_1_ID, PROJECT_KEY_1, "Project 1"));
  }

  @Test
  void compute_suggestions_fallback_to_text_search_all_connections_if_no_matches_by_projectKey() {
    when(connectionRepository.getConnectionsById()).thenReturn(Map.of(SQ_1_ID, SQ_1, SC_1_ID, SC_1));
    when(connectionRepository.getConnectionById(SQ_1_ID)).thenReturn(SQ_1);
    when(connectionRepository.getConnectionById(SC_1_ID)).thenReturn(SC_1);

    when(configRepository.getConfigurationScope(CONFIG_SCOPE_ID_1)).thenReturn(new ConfigurationScope(CONFIG_SCOPE_ID_1, null, true, "KEYWORD"));
    when(configRepository.getBindingConfiguration(CONFIG_SCOPE_ID_1)).thenReturn(BindingConfiguration.noBinding());

    when(bindingClueProvider.collectBindingCluesWithConnections(eq(CONFIG_SCOPE_ID_1), eq(Set.of(SQ_1_ID)), any(SonarLintCancelMonitor.class)))
      .thenReturn(List.of(new BindingClueProvider.BindingClueWithConnections(new BindingClueProvider.UnknownBindingClue(PROJECT_KEY_1, false), Set.of(SQ_1_ID))));

    when(sonarProjectsCache.getSonarProject(eq(SQ_1_ID), eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class))).thenReturn(Optional.empty());
    when(sonarProjectsCache.getSonarProject(eq(SC_1_ID), eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class))).thenReturn(Optional.empty());
    var searchIndex = new TextSearchIndex<ServerProject>();
    searchIndex.index(SERVER_PROJECT_1, "foo bar keyword");
    when(sonarProjectsCache.getTextSearchIndex(eq(SC_1_ID), any(SonarLintCancelMonitor.class))).thenReturn(searchIndex);

    underTest.suggestBindingForGivenScopesAndAllConnections(Set.of(CONFIG_SCOPE_ID_1));

    var captor = ArgumentCaptor.forClass(SuggestBindingParams.class);
    verify(client, timeout(1000)).suggestBinding(captor.capture());

    assertThat(logTester.logs(LogOutput.Level.DEBUG))
      .containsExactlyInAnyOrder(
        "Binding suggestion computation queued for config scopes '" + CONFIG_SCOPE_ID_1 + "'...",
        "Attempt to find a good match for 'KEYWORD' on connection '" + SQ_1_ID + "'...",
        "Attempt to find a good match for 'KEYWORD' on connection '" + SC_1_ID + "'...",
        "Best score = 0.33",
        "Found 1 suggestion for configuration scope '" + CONFIG_SCOPE_ID_1 + "'");

    var params = captor.getValue();
    assertThat(params.getSuggestions()).containsOnlyKeys(CONFIG_SCOPE_ID_1);
    assertThat(params.getSuggestions().get(CONFIG_SCOPE_ID_1))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsOnly(tuple(SC_1_ID, PROJECT_KEY_1, "Project 1"));
  }

  @Test
  void compute_suggestions_fallback_to_text_search_all_connections_if_no_matches_by_projectKey_and_no_other_clue() {
    when(connectionRepository.getConnectionsById()).thenReturn(Map.of(SQ_1_ID, SQ_1, SC_1_ID, SC_1));
    when(connectionRepository.getConnectionById(SQ_1_ID)).thenReturn(SQ_1);
    when(connectionRepository.getConnectionById(SC_1_ID)).thenReturn(SC_1);

    when(configRepository.getConfigurationScope(CONFIG_SCOPE_ID_1)).thenReturn(new ConfigurationScope(CONFIG_SCOPE_ID_1, null, true, "KEYWORD"));
    when(configRepository.getBindingConfiguration(CONFIG_SCOPE_ID_1)).thenReturn(BindingConfiguration.noBinding());

    when(bindingClueProvider.collectBindingCluesWithConnections(eq(CONFIG_SCOPE_ID_1), eq(Set.of(SQ_1_ID)), any(SonarLintCancelMonitor.class)))
      .thenReturn(List.of(new BindingClueProvider.BindingClueWithConnections(new BindingClueProvider.UnknownBindingClue(PROJECT_KEY_1, false), Set.of(SQ_1_ID))));

    when(sonarProjectsCache.getSonarProject(eq(SQ_1_ID), eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class))).thenReturn(Optional.empty());
    when(sonarProjectsCache.getSonarProject(eq(SC_1_ID), eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class))).thenReturn(Optional.empty());
    var searchIndex = new TextSearchIndex<ServerProject>();
    searchIndex.index(SERVER_PROJECT_1, "foo bar keyword");
    when(sonarProjectsCache.getTextSearchIndex(eq(SC_1_ID), any(SonarLintCancelMonitor.class))).thenReturn(searchIndex);

    underTest.suggestBindingForGivenScopesAndAllConnections(Set.of(CONFIG_SCOPE_ID_1));

    var captor = ArgumentCaptor.forClass(SuggestBindingParams.class);
    verify(client, timeout(1000)).suggestBinding(captor.capture());

    assertThat(logTester.logs(LogOutput.Level.DEBUG))
      .containsExactlyInAnyOrder(
        "Binding suggestion computation queued for config scopes '" + CONFIG_SCOPE_ID_1 + "'...",
        "Attempt to find a good match for 'KEYWORD' on connection '" + SQ_1_ID + "'...",
        "Attempt to find a good match for 'KEYWORD' on connection '" + SC_1_ID + "'...",
        "Best score = 0.33",
        "Found 1 suggestion for configuration scope '" + CONFIG_SCOPE_ID_1 + "'");

    var params = captor.getValue();
    assertThat(params.getSuggestions()).containsOnlyKeys(CONFIG_SCOPE_ID_1);
    assertThat(params.getSuggestions().get(CONFIG_SCOPE_ID_1))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsOnly(tuple(SC_1_ID, PROJECT_KEY_1, "Project 1"));
  }

  @Test
  void get_suggested_binding() {
    var cancelMonitor = new SonarLintCancelMonitor();
    when(connectionRepository.getConnectionById(SQ_1_ID)).thenReturn(SQ_1);
    when(configRepository.getConfigurationScope(CONFIG_SCOPE_ID_1)).thenReturn(new ConfigurationScope(CONFIG_SCOPE_ID_1, null, true, "foo-bar"));
    when(configRepository.getBindingConfiguration(CONFIG_SCOPE_ID_1)).thenReturn(BindingConfiguration.noBinding());
    when(bindingClueProvider.collectBindingCluesWithConnections(CONFIG_SCOPE_ID_1, Set.of(SQ_1_ID), cancelMonitor))
      .thenReturn(List.of(
        new BindingClueProvider.BindingClueWithConnections(new BindingClueProvider.SonarQubeBindingClue(null, null, false), Set.of(SQ_1_ID))));
    var searchIndex = new TextSearchIndex<ServerProject>();
    searchIndex.index(SERVER_PROJECT_1, "foo bar garbage1");
    when(sonarProjectsCache.getTextSearchIndex(SQ_1_ID, cancelMonitor)).thenReturn(searchIndex);
    when(sonarProjectsCache.getSonarProject(SQ_1_ID, PROJECT_KEY_1, cancelMonitor)).thenReturn(Optional.empty());

    var bindingSuggestions = underTest.getBindingSuggestions(CONFIG_SCOPE_ID_1, SQ_1_ID, cancelMonitor);
    assertThat(bindingSuggestions).hasSize(1);
    assertThat(bindingSuggestions.get(CONFIG_SCOPE_ID_1))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsOnly(
        tuple(SQ_1_ID, PROJECT_KEY_1, "Project 1"));
  }

  @Test
  void search_only_among_connection_candidates() {
    when(connectionRepository.getConnectionsById()).thenReturn(Map.of(SQ_1_ID, SQ_1, SC_1_ID, SC_1));
    when(connectionRepository.getConnectionById(SQ_1_ID)).thenReturn(SQ_1);
    when(connectionRepository.getConnectionById(SC_1_ID)).thenReturn(SC_1);

    when(configRepository.getConfigurationScope(CONFIG_SCOPE_ID_1)).thenReturn(new ConfigurationScope(CONFIG_SCOPE_ID_1, null, true, "foo-bar"));
    when(configRepository.getConfigScopeIds()).thenReturn(Set.of(CONFIG_SCOPE_ID_1));
    when(configRepository.getBindingConfiguration(CONFIG_SCOPE_ID_1)).thenReturn(BindingConfiguration.noBinding());

    when(bindingClueProvider.collectBindingCluesWithConnections(eq(CONFIG_SCOPE_ID_1), eq(Set.of(SQ_1_ID)), any(SonarLintCancelMonitor.class)))
      .thenReturn(List.of(
        new BindingClueProvider.BindingClueWithConnections(new BindingClueProvider.UnknownBindingClue(PROJECT_KEY_1, false), Set.of(SQ_1_ID, SC_1_ID))));

    when(sonarProjectsCache.getSonarProject(eq(SQ_1_ID), eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class))).thenReturn(Optional.empty());
    when(sonarProjectsCache.getSonarProject(eq(SC_1_ID), eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class))).thenReturn(Optional.empty());

    var searchIndex = new TextSearchIndex<ServerProject>();
    searchIndex.index(SERVER_PROJECT_1, "foo bar garbage1");
    searchIndex.index(serverProject("key2", "Project 2"), "foo bar garbage2");
    searchIndex.index(serverProject("key3", "Project 3"), "foo bar more garbage");
    when(sonarProjectsCache.getTextSearchIndex(eq(SC_1_ID), any(SonarLintCancelMonitor.class))).thenReturn(searchIndex);
    when(sonarProjectsCache.getTextSearchIndex(eq(SQ_1_ID), any(SonarLintCancelMonitor.class))).thenReturn(searchIndex);

    underTest.connectionAdded(new ConnectionConfigurationAddedEvent(SQ_1_ID));

    var captor = ArgumentCaptor.forClass(SuggestBindingParams.class);
    verify(client, timeout(1000)).suggestBinding(captor.capture());

    var params = captor.getValue();
    assertThat(params.getSuggestions()).containsOnlyKeys(CONFIG_SCOPE_ID_1);
    assertThat(params.getSuggestions().get(CONFIG_SCOPE_ID_1))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsOnly(
        tuple(SQ_1_ID, PROJECT_KEY_1, "Project 1"),
        tuple(SQ_1_ID, "key2", "Project 2"));
  }

  @Test
  void text_search_should_retain_only_top_scores() {
    when(connectionRepository.getConnectionsById()).thenReturn(Map.of(SQ_1_ID, SQ_1));
    when(connectionRepository.getConnectionById(SQ_1_ID)).thenReturn(SQ_1);

    when(configRepository.getConfigurationScope(CONFIG_SCOPE_ID_1)).thenReturn(new ConfigurationScope(CONFIG_SCOPE_ID_1, null, true, "foo-bar"));
    when(configRepository.getBindingConfiguration(CONFIG_SCOPE_ID_1)).thenReturn(BindingConfiguration.noBinding());

    when(bindingClueProvider.collectBindingCluesWithConnections(eq(CONFIG_SCOPE_ID_1), eq(Set.of(SQ_1_ID)), any(SonarLintCancelMonitor.class)))
      .thenReturn(List.of(
        new BindingClueProvider.BindingClueWithConnections(new BindingClueProvider.UnknownBindingClue(PROJECT_KEY_1, false), Set.of(SQ_1_ID, SC_1_ID)),
        new BindingClueProvider.BindingClueWithConnections(new BindingClueProvider.SonarCloudBindingClue(null, null, false), Set.of(SC_1_ID))));

    when(sonarProjectsCache.getSonarProject(eq(SQ_1_ID), eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class))).thenReturn(Optional.empty());
    when(sonarProjectsCache.getSonarProject(eq(SC_1_ID), eq(PROJECT_KEY_1), any(SonarLintCancelMonitor.class))).thenReturn(Optional.empty());

    var searchIndex = new TextSearchIndex<ServerProject>();
    searchIndex.index(SERVER_PROJECT_1, "foo bar garbage1");
    searchIndex.index(serverProject("key2", "Project 2"), "foo bar garbage2");
    searchIndex.index(serverProject("key3", "Project 3"), "foo bar more garbage");
    when(sonarProjectsCache.getTextSearchIndex(eq(SC_1_ID), any(SonarLintCancelMonitor.class))).thenReturn(searchIndex);

    underTest.suggestBindingForGivenScopesAndAllConnections(Set.of(CONFIG_SCOPE_ID_1));

    var captor = ArgumentCaptor.forClass(SuggestBindingParams.class);
    verify(client, timeout(1000)).suggestBinding(captor.capture());

    assertThat(logTester.logs(LogOutput.Level.DEBUG))
      .containsExactly(
        "Binding suggestion computation queued for config scopes '" + CONFIG_SCOPE_ID_1 + "'...",
        "Attempt to find a good match for 'foo-bar' on connection '" + SC_1_ID + "'...",
        "Best score = 0.67",
        "Found 2 suggestions for configuration scope '" + CONFIG_SCOPE_ID_1 + "'");

    var params = captor.getValue();
    assertThat(params.getSuggestions()).containsOnlyKeys(CONFIG_SCOPE_ID_1);
    assertThat(params.getSuggestions().get(CONFIG_SCOPE_ID_1))
      .extracting(BindingSuggestionDto::getConnectionId, BindingSuggestionDto::getSonarProjectKey, BindingSuggestionDto::getSonarProjectName)
      .containsOnly(
        tuple(SC_1_ID, PROJECT_KEY_1, "Project 1"),
        tuple(SC_1_ID, "key2", "Project 2"));
  }

  private static ServerProject serverProject(String projectKey, String name) {
    return new ServerProject() {
      @Override
      public String getKey() {
        return projectKey;
      }

      @Override
      public String getName() {
        return name;
      }
    };
  }

}
