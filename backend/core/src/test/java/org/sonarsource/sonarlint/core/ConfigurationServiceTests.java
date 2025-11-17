/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedWithBindingEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingMode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConfigurationServiceTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String CONNECTION_1 = "connection1";
  private static final String CONNECTION_2 = "connection2";
  public static final BindingConfigurationDto BINDING_DTO_1 = new BindingConfigurationDto(CONNECTION_1, "projectKey1", false);
  public static final BindingConfigurationDto BINDING_DTO_2 = new BindingConfigurationDto(CONNECTION_1, "projectKey2", true);
  public static final BindingConfigurationDto BINDING_DTO_3 = new BindingConfigurationDto(CONNECTION_2, "projectKey3", true);
  public static final ConfigurationScopeDto CONFIG_DTO_1 = new ConfigurationScopeDto("id1", null, true, "Scope 1", BINDING_DTO_1);
  public static final ConfigurationScopeDto CONFIG_DTO_1_DUP = new ConfigurationScopeDto("id1", null, false, "Scope 1 dup", BINDING_DTO_2);
  public static final ConfigurationScopeDto CONFIG_DTO_2 = new ConfigurationScopeDto("id2", null, true, "Scope 2", BINDING_DTO_2);
  public static final ConfigurationScopeDto CONFIG_DTO_3 = new ConfigurationScopeDto("id3", null, true, "Scope 2", BINDING_DTO_3);
  private final ConfigurationRepository repository = new ConfigurationRepository();

  private ApplicationEventPublisher eventPublisher;
  private ConfigurationService underTest;

  @BeforeEach
  void setUp() {
    eventPublisher = mock(ApplicationEventPublisher.class);
    underTest = new ConfigurationService(eventPublisher, repository);
  }

  @Test
  void initialize_empty() {
    assertThat(repository.getConfigScopeIds()).isEmpty();
  }

  @Test
  void get_binding_of_unknown_config_returns_null() {
    assertThat(repository.getBindingConfiguration("not_found")).isNull();
  }

  @Test
  void add_configuration_should_post_event() {
    underTest.didAddConfigurationScopes(List.of(CONFIG_DTO_2));

    assertThat(repository.getConfigScopeIds()).containsOnly("id2");
    assertThat(repository.getBindingConfiguration("id2")).usingRecursiveComparison().isEqualTo(BINDING_DTO_2);

    ArgumentCaptor<ConfigurationScopesAddedWithBindingEvent> captor = ArgumentCaptor.forClass(ConfigurationScopesAddedWithBindingEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    var event = captor.getValue();

    assertThat(event.getConfigScopeIds()).containsOnly("id2");
  }

  @Test
  void add_multiple_configurations_should_post_batch_event() {
    underTest.didAddConfigurationScopes(List.of(CONFIG_DTO_1, CONFIG_DTO_2));

    assertThat(repository.getConfigScopeIds()).containsOnly("id1", "id2");
    assertThat(repository.getBindingConfiguration("id1")).usingRecursiveComparison().isEqualTo(BINDING_DTO_1);
    assertThat(repository.getBindingConfiguration("id2")).usingRecursiveComparison().isEqualTo(BINDING_DTO_2);

    ArgumentCaptor<ConfigurationScopesAddedWithBindingEvent> captor = ArgumentCaptor.forClass(ConfigurationScopesAddedWithBindingEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    var event = captor.getValue();

    assertThat(event.getConfigScopeIds()).containsOnly("id1", "id2");
  }

  @Test
  void add_duplicate_should_log_and_update() {
    underTest.didAddConfigurationScopes(List.of(CONFIG_DTO_1));
    assertThat(repository.getBindingConfiguration("id1")).usingRecursiveComparison().isEqualTo(BINDING_DTO_1);

    underTest.didAddConfigurationScopes(List.of(CONFIG_DTO_1_DUP));

    assertThat(repository.getConfigScopeIds()).containsOnly("id1");
    assertThat(repository.getBindingConfiguration("id1")).usingRecursiveComparison().isEqualTo(BINDING_DTO_2);

    assertThat(logTester.logs(LogOutput.Level.ERROR)).containsExactly("Duplicate configuration scope registered: id1");
  }

  @Test
  void remove_configuration() {
    underTest.didAddConfigurationScopes(List.of(CONFIG_DTO_1));
    assertThat(repository.getConfigScopeIds()).containsOnly("id1");

    underTest.didRemoveConfigurationScope("id1");

    assertThat(repository.getConfigScopeIds()).isEmpty();
  }

  @Test
  void remove_unknown_configuration_should_log() {
    underTest.didAddConfigurationScopes(List.of(CONFIG_DTO_1));
    assertThat(repository.getConfigScopeIds()).containsOnly("id1");

    underTest.didRemoveConfigurationScope("id2");

    assertThat(repository.getConfigScopeIds()).containsOnly("id1");
    assertThat(logTester.logs(LogOutput.Level.DEBUG)).contains("Attempt to remove configuration scope 'id2' that was not registered");
    assertThat(logTester.logs(LogOutput.Level.ERROR)).isEmpty();
  }

  @Test
  void update_binding_config_and_post_event() {
    underTest.didAddConfigurationScopes(List.of(CONFIG_DTO_1));
    assertThat(repository.getBindingConfiguration("id1")).usingRecursiveComparison().isEqualTo(BINDING_DTO_1);

    // Ignore add event
    Mockito.reset(eventPublisher);

    underTest.didUpdateBinding("id1", BINDING_DTO_2, null, null);

    assertThat(repository.getConfigScopeIds()).containsOnly("id1");
    assertThat(repository.getBindingConfiguration("id1")).usingRecursiveComparison().isEqualTo(BINDING_DTO_2);

    ArgumentCaptor<BindingConfigChangedEvent> captor = ArgumentCaptor.forClass(BindingConfigChangedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    var event = captor.getValue();

    assertThat(event.configScopeId()).isEqualTo("id1");
    assertThat(event.previousConfig().connectionId()).isEqualTo(CONNECTION_1);
    assertThat(event.previousConfig().sonarProjectKey()).isEqualTo("projectKey1");
    assertThat(event.previousConfig().bindingSuggestionDisabled()).isFalse();

    assertThat(event.newConfig().connectionId()).isEqualTo(CONNECTION_1);
    assertThat(event.newConfig().sonarProjectKey()).isEqualTo("projectKey2");
    assertThat(event.newConfig().bindingSuggestionDisabled()).isTrue();
  }

  @Test
  void update_binding_config_for_unknown_config_scope_should_log() {
    underTest.didAddConfigurationScopes(List.of(CONFIG_DTO_1));

    underTest.didUpdateBinding("id2", BINDING_DTO_2, null, null);

    assertThat(logTester.logs(LogOutput.Level.ERROR)).containsExactly("Attempt to update binding in configuration scope 'id2' that was not registered");
  }

  @Test
  void should_clear_binding_if_connection_removed() {
    underTest.didAddConfigurationScopes(List.of(CONFIG_DTO_1, CONFIG_DTO_3));
    assertThat(repository.getConfigScopeIds()).containsOnly("id1", "id3");

    underTest.connectionRemoved(new ConnectionConfigurationRemovedEvent(CONNECTION_1));
    assertThat(repository.getAllBoundScopes()).hasSize(1);
    assertThat(repository.getBoundScope("id3"))
      .isNotNull()
      .extracting(BoundScope::getConnectionId).isEqualTo(CONNECTION_2);
    assertThat(repository.getBindingConfiguration(CONFIG_DTO_1.getId()))
      .extracting(BindingConfiguration::connectionId, BindingConfiguration::sonarProjectKey, BindingConfiguration::bindingSuggestionDisabled)
      .containsExactly(null, null, false);
    assertThat(repository.getBindingConfiguration(CONFIG_DTO_3.getId()))
      .extracting(BindingConfiguration::connectionId, BindingConfiguration::sonarProjectKey, BindingConfiguration::bindingSuggestionDisabled)
      .containsExactly(BINDING_DTO_3.getConnectionId(), BINDING_DTO_3.getSonarProjectKey(), BINDING_DTO_3.isBindingSuggestionDisabled());
  }

  @Test
  void should_propagate_origin_and_binding_mode_in_didUpdateBinding_for_shared_configuration() {
    underTest.didAddConfigurationScopes(List.of(CONFIG_DTO_1));

    Mockito.reset(eventPublisher);
    underTest.didUpdateBinding("id1", BINDING_DTO_2, BindingMode.MANUAL, BindingSuggestionOrigin.SHARED_CONFIGURATION);

    ArgumentCaptor<BindingConfigChangedEvent> captor = ArgumentCaptor.forClass(BindingConfigChangedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    var event = captor.getValue();
    assertThat(event.bindingMode()).isEqualTo(BindingMode.MANUAL);
    assertThat(event.origin()).isEqualTo(BindingSuggestionOrigin.SHARED_CONFIGURATION);
  }

  @Test
  void should_propagate_origin_and_binding_mode_in_didUpdateBinding_for_remote_url() {
    underTest.didAddConfigurationScopes(List.of(CONFIG_DTO_1));

    Mockito.reset(eventPublisher);
    underTest.didUpdateBinding("id1", BINDING_DTO_2, BindingMode.FROM_SUGGESTION, BindingSuggestionOrigin.REMOTE_URL);

    ArgumentCaptor<BindingConfigChangedEvent> captor = ArgumentCaptor.forClass(BindingConfigChangedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    var event = captor.getValue();
    assertThat(event.bindingMode()).isEqualTo(BindingMode.FROM_SUGGESTION);
    assertThat(event.origin()).isEqualTo(BindingSuggestionOrigin.REMOTE_URL);
  }

  @Test
  void should_propagate_origin_and_binding_mode_in_didUpdateBinding_for_properties_file() {
    underTest.didAddConfigurationScopes(List.of(CONFIG_DTO_1));

    Mockito.reset(eventPublisher);
    underTest.didUpdateBinding("id1", BINDING_DTO_2, BindingMode.FROM_SUGGESTION, BindingSuggestionOrigin.PROPERTIES_FILE);

    ArgumentCaptor<BindingConfigChangedEvent> captor = ArgumentCaptor.forClass(BindingConfigChangedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    var event = captor.getValue();
    assertThat(event.bindingMode()).isEqualTo(BindingMode.FROM_SUGGESTION);
    assertThat(event.origin()).isEqualTo(BindingSuggestionOrigin.PROPERTIES_FILE);
  }

  @Test
  void should_propagate_origin_and_binding_mode_in_didUpdateBinding_for_project_name() {
    underTest.didAddConfigurationScopes(List.of(CONFIG_DTO_1));

    Mockito.reset(eventPublisher);
    underTest.didUpdateBinding("id1", BINDING_DTO_2, BindingMode.FROM_SUGGESTION, BindingSuggestionOrigin.PROJECT_NAME);

    ArgumentCaptor<BindingConfigChangedEvent> captor = ArgumentCaptor.forClass(BindingConfigChangedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    var event = captor.getValue();
    assertThat(event.bindingMode()).isEqualTo(BindingMode.FROM_SUGGESTION);
    assertThat(event.origin()).isEqualTo(BindingSuggestionOrigin.PROJECT_NAME);
  }

}
