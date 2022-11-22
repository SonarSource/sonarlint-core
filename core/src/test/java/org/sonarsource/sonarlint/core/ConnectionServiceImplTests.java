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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidAddConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidRemoveConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidUpdateConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ConnectionServiceImplTests {

  private final ConnectionConfigurationRepository repository = new ConnectionConfigurationRepository();
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  public static final SonarQubeConnectionConfigurationDto SQ_DTO_1 = new SonarQubeConnectionConfigurationDto("sq1", "url1");
  public static final SonarQubeConnectionConfigurationDto SQ_DTO_1_DUP = new SonarQubeConnectionConfigurationDto("sq1", "url1_dup");
  public static final SonarQubeConnectionConfigurationDto SQ_DTO_2 = new SonarQubeConnectionConfigurationDto("sq2", "url2");
  public static final SonarCloudConnectionConfigurationDto SC_DTO_1 = new SonarCloudConnectionConfigurationDto("sc1", "org1");
  public static final SonarCloudConnectionConfigurationDto SC_DTO_2 = new SonarCloudConnectionConfigurationDto("sc2", "org2");

  EventBus eventBus;
  ConnectionServiceImpl underTest;

  @BeforeEach
  public void setUp() {
    eventBus = mock(EventBus.class);
    underTest = new ConnectionServiceImpl(eventBus, repository);
  }

  @Test
  void initialize_provide_connections() {
    underTest.initialize(new InitializeParams(List.of(SQ_DTO_1, SQ_DTO_2), List.of(SC_DTO_1, SC_DTO_2)));

    assertThat(repository.getConnectionsById()).containsOnlyKeys("sq1", "sq2", "sc1", "sc2");
  }

  @Test
  void add_new_connection_and_post_event() {
    underTest.initialize(new InitializeParams(List.of(), List.of()));

    underTest.didAddConnection(new DidAddConnectionParams(SQ_DTO_1));
    assertThat(repository.getConnectionsById()).containsOnlyKeys("sq1");
    assertThat(repository.getConnectionById("sq1")).usingRecursiveComparison().isEqualTo(SQ_DTO_1);

    underTest.didAddConnection(new DidAddConnectionParams(SQ_DTO_2));
    assertThat(repository.getConnectionsById()).containsOnlyKeys("sq1", "sq2");

    underTest.didAddConnection(new DidAddConnectionParams(SC_DTO_1));
    assertThat(repository.getConnectionsById()).containsOnlyKeys("sq1", "sq2", "sc1");
    assertThat(repository.getConnectionById("sc1")).usingRecursiveComparison().isEqualTo(SC_DTO_1);

    ArgumentCaptor<ConnectionConfigurationAddedEvent> captor = ArgumentCaptor.forClass(ConnectionConfigurationAddedEvent.class);
    verify(eventBus, times(3)).post(captor.capture());
    var events = captor.getAllValues();

    assertThat(events).extracting(ConnectionConfigurationAddedEvent::getAddedConnectionId).containsExactly("sq1", "sq2", "sc1");
  }

  @Test
  void add_duplicate_connection_should_log_and_update() {
    underTest.initialize(new InitializeParams(List.of(), List.of()));

    underTest.didAddConnection(new DidAddConnectionParams(SQ_DTO_1));

    underTest.didAddConnection(new DidAddConnectionParams(SQ_DTO_1_DUP));
    assertThat(repository.getConnectionById("sq1")).usingRecursiveComparison().isEqualTo(SQ_DTO_1_DUP);

    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).containsExactly("Duplicate connection registered: sq1");
  }

  @Test
  void remove_connection() {
    underTest.initialize(new InitializeParams(List.of(SQ_DTO_1), List.of()));

    underTest.didAddConnection(new DidAddConnectionParams(SC_DTO_1));
    assertThat(repository.getConnectionsById()).containsKeys("sq1", "sc1");

    underTest.didRemoveConnection(new DidRemoveConnectionParams("sc1"));
    assertThat(repository.getConnectionsById()).containsKeys("sq1");

    underTest.didRemoveConnection(new DidRemoveConnectionParams("sq1"));
    assertThat(repository.getConnectionsById()).isEmpty();
  }

  @Test
  void remove_connection_should_log_if_unknown_connection_and_ignore() {
    underTest.initialize(new InitializeParams(List.of(SQ_DTO_1), List.of()));

    underTest.didRemoveConnection(new DidRemoveConnectionParams("sc1"));

    assertThat(repository.getConnectionsById()).containsKeys("sq1");
    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).containsExactly("Attempt to remove connection 'sc1' that was not registered");
  }

  @Test
  void update_connection() {
    underTest.initialize(new InitializeParams(List.of(SQ_DTO_1), List.of()));

    underTest.didUpdateConnection(new DidUpdateConnectionParams(SQ_DTO_1_DUP));

    assertThat(repository.getConnectionById("sq1")).usingRecursiveComparison().isEqualTo(SQ_DTO_1_DUP);
  }

  @Test
  void update_connection_should_log_if_unknown_connection_and_add() {
    underTest.initialize(new InitializeParams(List.of(SQ_DTO_1), List.of()));

    underTest.didUpdateConnection(new DidUpdateConnectionParams(SQ_DTO_2));

    assertThat(repository.getConnectionsById()).containsOnlyKeys("sq1", "sq2");
    assertThat(repository.getConnectionById("sq2")).usingRecursiveComparison().isEqualTo(SQ_DTO_2);
    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).containsExactly("Attempt to update connection 'sq2' that was not registered");
  }
}
