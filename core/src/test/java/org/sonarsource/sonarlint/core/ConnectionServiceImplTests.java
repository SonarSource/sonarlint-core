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
package org.sonarsource.sonarlint.core;

import com.google.common.eventbus.EventBus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    underTest.initialize(List.of(SQ_DTO_1, SQ_DTO_2), List.of(SC_DTO_1, SC_DTO_2));

    assertThat(repository.getConnectionsById()).containsOnlyKeys("sq1", "sq2", "sc1", "sc2");
  }

  @Test
  void add_new_connection_and_post_event() {
    underTest.initialize(List.of(), List.of());

    underTest.didUpdateConnections(new DidUpdateConnectionsParams(List.of(SQ_DTO_1), List.of()));
    assertThat(repository.getConnectionsById()).containsOnlyKeys("sq1");
    assertThat(repository.getConnectionById("sq1")).usingRecursiveComparison().isEqualTo(SQ_DTO_1);

    underTest.didUpdateConnections(new DidUpdateConnectionsParams(List.of(SQ_DTO_1, SQ_DTO_2), List.of()));
    assertThat(repository.getConnectionsById()).containsOnlyKeys("sq1", "sq2");

    underTest.didUpdateConnections(new DidUpdateConnectionsParams(List.of(SQ_DTO_1, SQ_DTO_2), List.of(SC_DTO_1)));
    assertThat(repository.getConnectionsById()).containsOnlyKeys("sq1", "sq2", "sc1");
    assertThat(repository.getConnectionById("sc1")).usingRecursiveComparison().isEqualTo(SC_DTO_1);

    var captor = ArgumentCaptor.forClass(ConnectionConfigurationAddedEvent.class);
    verify(eventBus, times(3)).post(captor.capture());
    var events = captor.getAllValues();

    assertThat(events).extracting(ConnectionConfigurationAddedEvent::getAddedConnectionId).containsExactly("sq1", "sq2", "sc1");
  }

  @Test
  void multiple_connections_with_same_id_should_log_and_ignore() {
    underTest.initialize(List.of(), List.of());
    underTest.didUpdateConnections(new DidUpdateConnectionsParams(List.of(SQ_DTO_1), List.of()));

    underTest.didUpdateConnections(new DidUpdateConnectionsParams(List.of(SQ_DTO_1, SQ_DTO_1_DUP), List.of()));

    assertThat(repository.getConnectionById("sq1")).usingRecursiveComparison().isEqualTo(SQ_DTO_1_DUP);

    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).containsExactly("Duplicate connection registered: sq1");
  }

  @Test
  void remove_connection() {
    underTest.initialize(List.of(SQ_DTO_1), List.of(SC_DTO_1));
    assertThat(repository.getConnectionsById()).containsKeys("sq1", "sc1");

    underTest.didUpdateConnections(new DidUpdateConnectionsParams(List.of(SQ_DTO_1), List.of()));
    assertThat(repository.getConnectionsById()).containsKeys("sq1");

    underTest.didUpdateConnections(new DidUpdateConnectionsParams(List.of(), List.of()));
    assertThat(repository.getConnectionsById()).isEmpty();

    var captor = ArgumentCaptor.forClass(ConnectionConfigurationRemovedEvent.class);
    verify(eventBus, times(2)).post(captor.capture());
    var events = captor.getAllValues();

    assertThat(events).extracting(ConnectionConfigurationRemovedEvent::getRemovedConnectionId).containsExactly("sc1", "sq1");
  }

  @Test
  void remove_connection_should_log_if_unknown_connection_and_ignore() {
    var mockedRepo = mock(ConnectionConfigurationRepository.class);
    underTest = new ConnectionServiceImpl(eventBus, mockedRepo);

    // Emulate a race condition on the repository: the connection is gone between get and remove
    when(mockedRepo.getConnectionsById()).thenReturn(Map.of("id", new SonarQubeConnectionConfiguration("id", "http://foo")));
    when(mockedRepo.remove("id")).thenReturn(null);

    underTest.didUpdateConnections(new DidUpdateConnectionsParams(List.of(), List.of()));

    assertThat(logTester.logs(ClientLogOutput.Level.DEBUG)).containsExactly("Attempt to remove connection 'id' that was not registered. Possibly a race condition?");
  }

  @Test
  void update_connection() {
    underTest.initialize(List.of(SQ_DTO_1), List.of());

    underTest.didUpdateConnections(new DidUpdateConnectionsParams(List.of(SQ_DTO_1_DUP), List.of()));

    assertThat(repository.getConnectionById("sq1")).usingRecursiveComparison().isEqualTo(SQ_DTO_1_DUP);

    var captor = ArgumentCaptor.forClass(ConnectionConfigurationUpdatedEvent.class);
    verify(eventBus, times(1)).post(captor.capture());
    var events = captor.getAllValues();

    assertThat(events).extracting(ConnectionConfigurationUpdatedEvent::getUpdatedConnectionId).containsExactly("sq1");
  }

  @Test
  void update_connection_should_log_if_unknown_connection_and_add() {
    var mockedRepo = mock(ConnectionConfigurationRepository.class);
    underTest = new ConnectionServiceImpl(eventBus, mockedRepo);

    // Emulate a race condition on the repository: the connection is gone between get and add
    when(mockedRepo.getConnectionsById()).thenReturn(Map.of(SQ_DTO_2.getConnectionId(), new SonarQubeConnectionConfiguration(SQ_DTO_2.getConnectionId(), "http://foo")));
    when(mockedRepo.addOrReplace(any())).thenReturn(null);

    underTest.didUpdateConnections(new DidUpdateConnectionsParams(List.of(SQ_DTO_2), List.of()));

    assertThat(logTester.logs(ClientLogOutput.Level.DEBUG)).containsExactly("Attempt to update connection 'sq2' that was not registered. Possibly a race condition?");
  }
}
