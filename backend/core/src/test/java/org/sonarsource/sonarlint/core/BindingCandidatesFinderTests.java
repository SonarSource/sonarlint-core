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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BindingCandidatesFinderTests {

  @RegisterExtension
  static final SonarLintLogTester logTester = new SonarLintLogTester();

  private ConfigurationRepository configRepository;
  private BindingClueProvider bindingClueProvider;
  private SonarProjectsCache sonarProjectsCache;
  private SonarLintCancelMonitor cancelMonitor;

  private BindingCandidatesFinder underTest;

  @BeforeEach
  void setUp() {
    configRepository = mock(ConfigurationRepository.class);
    bindingClueProvider = mock(BindingClueProvider.class);
    sonarProjectsCache = mock(SonarProjectsCache.class);
    cancelMonitor = mock(SonarLintCancelMonitor.class);
    underTest = new BindingCandidatesFinder(configRepository, bindingClueProvider, sonarProjectsCache);
  }

  @Test
  void should_mark_scope_as_shared_configuration_when_any_clue_has_shared_config_origin() {
    var scope = new ConfigurationScope("scope1", null, true, "Test Scope");
    var sharedConfigClue = new BindingClueProvider.UnknownBindingClue("projectKey", BindingSuggestionOrigin.SHARED_CONFIGURATION);
    var propertiesFileClue = new BindingClueProvider.UnknownBindingClue("projectKey", BindingSuggestionOrigin.PROPERTIES_FILE);
    
    when(configRepository.getAllBindableUnboundScopes()).thenReturn(List.of(scope));
    when(bindingClueProvider.collectBindingCluesWithConnections("scope1", Set.of("conn1"), cancelMonitor))
      .thenReturn(List.of(
        new BindingClueProvider.BindingClueWithConnections(sharedConfigClue, Set.of("conn1")),
        new BindingClueProvider.BindingClueWithConnections(propertiesFileClue, Set.of("conn1"))
      ));

    var candidates = underTest.findConfigScopesToBind("conn1", "projectKey", cancelMonitor);

    assertThat(candidates).hasSize(1);
    var candidate = candidates.iterator().next();
    assertThat(candidate.getConfigurationScope()).isEqualTo(scope);
    assertThat(candidate.getOrigin()).isEqualTo(BindingSuggestionOrigin.SHARED_CONFIGURATION);
  }

  @Test
  void should_not_mark_scope_as_shared_configuration_when_no_clue_has_shared_config_origin() {
    var scope = new ConfigurationScope("scope1", null, true, "Test Scope");
    var propertiesFileClue = new BindingClueProvider.UnknownBindingClue("projectKey", BindingSuggestionOrigin.PROPERTIES_FILE);
    var remoteUrlClue = new BindingClueProvider.UnknownBindingClue("projectKey", BindingSuggestionOrigin.REMOTE_URL);
    
    when(configRepository.getAllBindableUnboundScopes()).thenReturn(List.of(scope));
    when(bindingClueProvider.collectBindingCluesWithConnections("scope1", Set.of("conn1"), cancelMonitor))
      .thenReturn(List.of(
        new BindingClueProvider.BindingClueWithConnections(propertiesFileClue, Set.of("conn1")),
        new BindingClueProvider.BindingClueWithConnections(remoteUrlClue, Set.of("conn1"))
      ));

    var candidates = underTest.findConfigScopesToBind("conn1", "projectKey", cancelMonitor);

    assertThat(candidates).hasSize(1);
    var candidate = candidates.iterator().next();
    assertThat(candidate.getConfigurationScope()).isEqualTo(scope);
    assertThat(candidate.getOrigin()).isEqualTo(BindingSuggestionOrigin.PROPERTIES_FILE);
  }

  @Test
  void should_select_project_name_when_name_matches_and_no_shared_or_properties_file_clues() {
    var scope = new ConfigurationScope("scope1", null, true, "MyProj");

    when(configRepository.getAllBindableUnboundScopes()).thenReturn(List.of(scope));
    when(bindingClueProvider.collectBindingCluesWithConnections("scope1", Set.of("conn1"), cancelMonitor))
      .thenReturn(List.of(
      ));
    when(sonarProjectsCache.getSonarProject("conn1", "projectKey", cancelMonitor))
      .thenReturn(Optional.of(new ServerProject("projectKey", "MyProj", false)));

    var candidates = underTest.findConfigScopesToBind("conn1", "projectKey", cancelMonitor);

    assertThat(candidates).hasSize(1);
    var candidate = candidates.iterator().next();
    assertThat(candidate.getConfigurationScope()).isEqualTo(scope);
    assertThat(candidate.getOrigin()).isEqualTo(BindingSuggestionOrigin.PROJECT_NAME);
  }
}
