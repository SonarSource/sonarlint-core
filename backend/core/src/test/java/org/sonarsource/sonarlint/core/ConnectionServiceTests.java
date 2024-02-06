/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionServiceTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private final ConnectionConfigurationRepository repository = new ConnectionConfigurationRepository();

  public static final SonarQubeConnectionConfigurationDto SQ_DTO_1 = new SonarQubeConnectionConfigurationDto("sq1", "url1", true);
  public static final SonarQubeConnectionConfigurationDto SQ_DTO_1_DUP = new SonarQubeConnectionConfigurationDto("sq1", "url1_dup", true);
  public static final SonarQubeConnectionConfigurationDto SQ_DTO_2 = new SonarQubeConnectionConfigurationDto("sq2", "url2", true);
  public static final SonarCloudConnectionConfigurationDto SC_DTO_1 = new SonarCloudConnectionConfigurationDto("sc1", "org1", true);
  public static final SonarCloudConnectionConfigurationDto SC_DTO_2 = new SonarCloudConnectionConfigurationDto("sc2", "org2", true);

  ApplicationEventPublisher eventPublisher;
  ConnectionService underTest;

  @BeforeEach
  public void setUp() {
    eventPublisher = mock(ApplicationEventPublisher.class);
  }

  @Test
  void initialize_provide_connections() {
    underTest = new ConnectionService(eventPublisher, repository, List.of(SQ_DTO_1, SQ_DTO_2), List.of(SC_DTO_1, SC_DTO_2), null, null);

    assertThat(repository.getConnectionsById()).containsOnlyKeys("sq1", "sq2", "sc1", "sc2");
  }

  @Test
  void add_new_connection_and_post_event() {
    underTest = new ConnectionService(eventPublisher, repository, List.of(), List.of(), null, null);

    underTest.didUpdateConnections(List.of(SQ_DTO_1), List.of());
    assertThat(repository.getConnectionsById()).containsOnlyKeys("sq1");
    assertThat(repository.getConnectionById("sq1"))
      .asInstanceOf(InstanceOfAssertFactories.type(SonarQubeConnectionConfiguration.class))
      .extracting(SonarQubeConnectionConfiguration::getConnectionId, SonarQubeConnectionConfiguration::getUrl, SonarQubeConnectionConfiguration::isDisableNotifications,
        SonarQubeConnectionConfiguration::getKind)
      .containsOnly("sq1", "url1", true, ConnectionKind.SONARQUBE);

    underTest.didUpdateConnections(List.of(SQ_DTO_1, SQ_DTO_2), List.of());
    assertThat(repository.getConnectionsById()).containsOnlyKeys("sq1", "sq2");

    underTest.didUpdateConnections(List.of(SQ_DTO_1, SQ_DTO_2), List.of(SC_DTO_1));
    assertThat(repository.getConnectionsById()).containsOnlyKeys("sq1", "sq2", "sc1");
    assertThat(repository.getConnectionById("sc1"))
      .asInstanceOf(InstanceOfAssertFactories.type(SonarCloudConnectionConfiguration.class))
      .extracting(SonarCloudConnectionConfiguration::getConnectionId, SonarCloudConnectionConfiguration::getUrl, SonarCloudConnectionConfiguration::isDisableNotifications,
        SonarCloudConnectionConfiguration::getKind, SonarCloudConnectionConfiguration::getOrganization)
      .containsOnly("sc1", "https://sonarcloud.io", true, ConnectionKind.SONARCLOUD, "org1");

    var captor = ArgumentCaptor.forClass(ConnectionConfigurationAddedEvent.class);
    verify(eventPublisher, times(3)).publishEvent(captor.capture());
    var events = captor.getAllValues();

    assertThat(events).extracting(ConnectionConfigurationAddedEvent::getAddedConnectionId).containsExactly("sq1", "sq2", "sc1");
  }

  @Test
  void multiple_connections_with_same_id_should_log_and_ignore() {
    underTest = new ConnectionService(eventPublisher, repository, List.of(), List.of(), null, null);
    underTest.didUpdateConnections(List.of(SQ_DTO_1), List.of());

    underTest.didUpdateConnections(List.of(SQ_DTO_1, SQ_DTO_1_DUP), List.of());

    assertThat(repository.getConnectionById("sq1"))
      .asInstanceOf(InstanceOfAssertFactories.type(SonarQubeConnectionConfiguration.class))
      .extracting(SonarQubeConnectionConfiguration::getConnectionId, SonarQubeConnectionConfiguration::getUrl, SonarQubeConnectionConfiguration::isDisableNotifications,
        SonarQubeConnectionConfiguration::getKind)
      .containsOnly("sq1", "url1_dup", true, ConnectionKind.SONARQUBE);

    assertThat(logTester.logs(LogOutput.Level.ERROR)).containsExactly("Duplicate connection registered: sq1");
  }

  @Test
  void remove_connection() {
    underTest = new ConnectionService(eventPublisher, repository, List.of(SQ_DTO_1), List.of(SC_DTO_1), null, null);
    assertThat(repository.getConnectionsById()).containsKeys("sq1", "sc1");

    underTest.didUpdateConnections(List.of(SQ_DTO_1), List.of());
    assertThat(repository.getConnectionsById()).containsKeys("sq1");

    underTest.didUpdateConnections(List.of(), List.of());
    assertThat(repository.getConnectionsById()).isEmpty();

    var captor = ArgumentCaptor.forClass(ConnectionConfigurationRemovedEvent.class);
    verify(eventPublisher, times(2)).publishEvent(captor.capture());
    var events = captor.getAllValues();

    assertThat(events).extracting(ConnectionConfigurationRemovedEvent::getRemovedConnectionId).containsExactly("sc1", "sq1");
  }

  @Test
  void remove_connection_should_log_if_unknown_connection_and_ignore() {
    var mockedRepo = mock(ConnectionConfigurationRepository.class);
    underTest = new ConnectionService(eventPublisher, mockedRepo, List.of(), List.of(), null, null);

    // Emulate a race condition on the repository: the connection is gone between get and remove
    when(mockedRepo.getConnectionsById()).thenReturn(Map.of("id", new SonarQubeConnectionConfiguration("id", "http://foo", true)));
    when(mockedRepo.remove("id")).thenReturn(null);

    underTest.didUpdateConnections(List.of(), List.of());

    assertThat(logTester.logs(LogOutput.Level.DEBUG)).containsExactly("Attempt to remove connection 'id' that was not registered. Possibly a race condition?");
  }

  @Test
  void update_connection() {
    underTest = new ConnectionService(eventPublisher, repository, List.of(SQ_DTO_1), List.of(), null, null);

    underTest.didUpdateConnections(List.of(SQ_DTO_1_DUP), List.of());

    assertThat(repository.getConnectionById("sq1"))
      .asInstanceOf(InstanceOfAssertFactories.type(SonarQubeConnectionConfiguration.class))
      .extracting(SonarQubeConnectionConfiguration::getConnectionId, SonarQubeConnectionConfiguration::getUrl, SonarQubeConnectionConfiguration::isDisableNotifications,
        SonarQubeConnectionConfiguration::getKind)
      .containsOnly("sq1", "url1_dup", true, ConnectionKind.SONARQUBE);

    var captor = ArgumentCaptor.forClass(ConnectionConfigurationUpdatedEvent.class);
    verify(eventPublisher, times(1)).publishEvent(captor.capture());
    var events = captor.getAllValues();

    assertThat(events).extracting(ConnectionConfigurationUpdatedEvent::getUpdatedConnectionId).containsExactly("sq1");
  }

  @Test
  void update_connection_should_log_if_unknown_connection_and_add() {
    var mockedRepo = mock(ConnectionConfigurationRepository.class);
    underTest = new ConnectionService(eventPublisher, mockedRepo, List.of(), List.of(), null, null);

    // Emulate a race condition on the repository: the connection is gone between get and add
    when(mockedRepo.getConnectionsById()).thenReturn(Map.of(SQ_DTO_2.getConnectionId(), new SonarQubeConnectionConfiguration(SQ_DTO_2.getConnectionId(), "http://foo", true)));
    when(mockedRepo.addOrReplace(any())).thenReturn(null);

    underTest.didUpdateConnections(List.of(SQ_DTO_2), List.of());

    assertThat(logTester.logs(LogOutput.Level.DEBUG)).containsExactly("Attempt to update connection 'sq2' that was not registered. Possibly a race condition?");
  }

}
