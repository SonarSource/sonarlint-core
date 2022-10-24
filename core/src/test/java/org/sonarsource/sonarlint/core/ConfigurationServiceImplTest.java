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
package org.sonarsource.sonarlint.core;

import com.google.common.eventbus.EventBus;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.clientapi.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.clientapi.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.config.scope.DidAddConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.config.scope.InitializeParams;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeAddedEvent;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConfigurationServiceImplTest {

  public static final BindingConfigurationDto BINDING_DTO_1 = new BindingConfigurationDto("connection1", "projectKey1", false);
  public static final BindingConfigurationDto BINDING_DTO_2 = new BindingConfigurationDto("connection1", "projectKey2", true);
  public static final ConfigurationScopeDto CONFIG_DTO_1 = new ConfigurationScopeDto("id1", null, true, "Scope 1", BINDING_DTO_1);
  public static final ConfigurationScopeDto CONFIG_DTO_1_DUP = new ConfigurationScopeDto("id1", null, false, "Scope 1 dup", BINDING_DTO_2);
  public static final ConfigurationScopeDto CONFIG_DTO_2 = new ConfigurationScopeDto("id2", null, true, "Scope 2", BINDING_DTO_2);
  private final ConfigurationRepository repository = new ConfigurationRepository();
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private EventBus eventBus;
  private ConfigurationServiceImpl underTest;

  @BeforeEach
  public void setUp() {
    eventBus = mock(EventBus.class);
    underTest = new ConfigurationServiceImpl(eventBus, repository);
  }

  @Test
  void initialize_empty() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of())).get();

    assertThat(repository.getConfigScopeIds()).isEmpty();
  }

  @Test
  void get_binding_of_unknown_config_returns_null() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of())).get();

    assertThat(repository.getBindingConfiguration("not_found")).isNull();
  }

  @Test
  void initialize_provides_bindings() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of(CONFIG_DTO_1))).get();

    assertThat(repository.getConfigScopeIds()).containsOnly("id1");
    assertThat(repository.getBindingConfiguration("id1")).usingRecursiveComparison().isEqualTo(BINDING_DTO_1);
  }

  @Test
  void add_configuration_and_post_event() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of(CONFIG_DTO_1))).get();

    underTest.didAddConfigurationScope(new DidAddConfigurationScopeParams(CONFIG_DTO_2));

    assertThat(repository.getConfigScopeIds()).containsOnly("id1", "id2");
    assertThat(repository.getBindingConfiguration("id2")).usingRecursiveComparison().isEqualTo(BINDING_DTO_2);

    ArgumentCaptor<ConfigurationScopeAddedEvent> captor = ArgumentCaptor.forClass(ConfigurationScopeAddedEvent.class);
    verify(eventBus).post(captor.capture());
    var event = captor.getValue();

    assertThat(event.getAddedConfigurationScopeId()).isEqualTo("id2");
  }

  @Test
  void add_duplicate_should_log_and_update() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of(CONFIG_DTO_1))).get();

    underTest.didAddConfigurationScope(new DidAddConfigurationScopeParams(CONFIG_DTO_1_DUP));

    assertThat(repository.getConfigScopeIds()).containsOnly("id1");
    assertThat(repository.getBindingConfiguration("id1")).usingRecursiveComparison().isEqualTo(BINDING_DTO_2);

    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).containsExactly("Duplicate configuration scope registered: id1");
  }

  @Test
  void remove_configuration() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of(CONFIG_DTO_1))).get();

    underTest.didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams("id1"));

    assertThat(repository.getConfigScopeIds()).isEmpty();
  }

  @Test
  void remove_unknown_configuration_should_log() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of(CONFIG_DTO_1))).get();

    underTest.didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams("id2"));

    assertThat(repository.getConfigScopeIds()).containsOnly("id1");
    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).containsExactly("Attempt to remove configuration scope 'id2' that was not registered");
  }

  @Test
  void update_binding_config_and_post_event() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of(CONFIG_DTO_1))).get();
    assertThat(repository.getBindingConfiguration("id1")).usingRecursiveComparison().isEqualTo(BINDING_DTO_1);

    underTest.didUpdateBinding(new DidUpdateBindingParams("id1", BINDING_DTO_2));

    assertThat(repository.getConfigScopeIds()).containsOnly("id1");
    assertThat(repository.getBindingConfiguration("id1")).usingRecursiveComparison().isEqualTo(BINDING_DTO_2);

    ArgumentCaptor<BindingConfigChangedEvent> captor = ArgumentCaptor.forClass(BindingConfigChangedEvent.class);
    verify(eventBus).post(captor.capture());
    var event = captor.getValue();

    assertThat(event.getPreviousConfig().getConfigScopeId()).isEqualTo("id1");
    assertThat(event.getPreviousConfig().getConnectionId()).isEqualTo("connection1");
    assertThat(event.getPreviousConfig().getSonarProjectKey()).isEqualTo("projectKey1");
    assertThat(event.getPreviousConfig().isAutoBindEnabled()).isFalse();

    assertThat(event.getNewConfig().getConfigScopeId()).isEqualTo("id1");
    assertThat(event.getNewConfig().getConnectionId()).isEqualTo("connection1");
    assertThat(event.getNewConfig().getSonarProjectKey()).isEqualTo("projectKey2");
    assertThat(event.getNewConfig().isAutoBindEnabled()).isTrue();
  }

  @Test
  void update_binding_config_for_unknown_config_scope_should_log() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of(CONFIG_DTO_1))).get();

    underTest.didUpdateBinding(new DidUpdateBindingParams("id2", BINDING_DTO_2));

    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).containsExactly("Attempt to update binding in configuration scope 'id2' that was not registered");
  }

}
