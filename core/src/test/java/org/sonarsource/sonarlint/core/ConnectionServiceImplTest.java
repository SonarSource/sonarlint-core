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
import org.mockito.Mockito;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidAddConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidRemoveConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidUpdateConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.connection.config.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.clientapi.connection.config.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionAddedEvent;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ConnectionServiceImplTest {

  private final ConnectionConfigurationReferential referential = new ConnectionConfigurationReferential();
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  public static final SonarQubeConnectionConfiguration SQ_1 = new SonarQubeConnectionConfiguration("sq1", "url1");
  public static final SonarQubeConnectionConfiguration SQ_1_DUP = new SonarQubeConnectionConfiguration("sq1", "url1_dup");
  public static final SonarQubeConnectionConfiguration SQ_2 = new SonarQubeConnectionConfiguration("sq2", "url2");
  public static final SonarCloudConnectionConfiguration SC_1 = new SonarCloudConnectionConfiguration("sc1", "org1");
  public static final SonarCloudConnectionConfiguration SC_2 = new SonarCloudConnectionConfiguration("sc2", "org2");

  EventBus eventBus;
  ConnectionServiceImpl underTest;

  @BeforeEach
  public void setUp() {
    eventBus = mock(EventBus.class);
    underTest = new ConnectionServiceImpl(eventBus, referential);
  }


  @Test
  void initialize_provide_connections() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of(SQ_1, SQ_2), List.of(SC_1, SC_2))).get();

    assertThat(referential.getConnectionsById()).containsOnly(entry("sq1", SQ_1), entry("sq2", SQ_2), entry("sc1", SC_1), entry("sc2", SC_2));
  }

  @Test
  void add_new_connection_and_post_event() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of(), List.of())).get();

    underTest.didAddConnection(new DidAddConnectionParams(SQ_1));
    assertThat(referential.getConnectionsById()).containsOnly(entry("sq1", SQ_1));

    underTest.didAddConnection(new DidAddConnectionParams(SQ_2));
    assertThat(referential.getConnectionsById()).containsOnly(entry("sq1", SQ_1), entry("sq2", SQ_2));

    underTest.didAddConnection(new DidAddConnectionParams(SC_1));
    assertThat(referential.getConnectionsById()).containsOnly(entry("sq1", SQ_1), entry("sq2", SQ_2), entry("sc1", SC_1));

    ArgumentCaptor<ConnectionAddedEvent> captor = ArgumentCaptor.forClass(ConnectionAddedEvent.class);
    verify(eventBus, times(3)).post(captor.capture());
    var events = captor.getAllValues();

    assertThat(events).extracting(ConnectionAddedEvent::getAddedConnectionId).containsExactly("sq1", "sq2", "sc1");
  }

  @Test
  void add_duplicate_connection_should_log_and_update() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of(), List.of())).get();

    underTest.didAddConnection(new DidAddConnectionParams(SQ_1));

    underTest.didAddConnection(new DidAddConnectionParams(SQ_1_DUP));
    assertThat(referential.getConnectionsById()).containsOnly(entry("sq1", SQ_1_DUP));

    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).containsExactly("Duplicate connection registered: sq1");
  }

  @Test
  void remove_connection() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of(SQ_1), List.of())).get();

    underTest.didAddConnection(new DidAddConnectionParams(SC_1));
    assertThat(referential.getConnectionsById()).containsKeys("sq1", "sc1");

    underTest.didRemoveConnection(new DidRemoveConnectionParams("sc1"));
    assertThat(referential.getConnectionsById()).containsKeys("sq1");

    underTest.didRemoveConnection(new DidRemoveConnectionParams("sq1"));
    assertThat(referential.getConnectionsById()).isEmpty();
  }

  @Test
  void remove_connection_should_log_if_unknown_connection_and_ignore() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of(SQ_1), List.of())).get();

    underTest.didRemoveConnection(new DidRemoveConnectionParams("sc1"));

    assertThat(referential.getConnectionsById()).containsKeys("sq1");
    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).containsExactly("Attempt to remove connection 'sc1' that was not registered");
  }

  @Test
  void update_connection() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of(SQ_1), List.of())).get();

    underTest.didUpdateConnection(new DidUpdateConnectionParams(SQ_1_DUP));

    assertThat(referential.getConnectionsById()).containsOnly(entry("sq1", SQ_1_DUP));
  }

  @Test
  void update_connection_should_log_if_unknown_connection_and_add() throws ExecutionException, InterruptedException {
    underTest.initialize(new InitializeParams(List.of(SQ_1), List.of())).get();

    underTest.didUpdateConnection(new DidUpdateConnectionParams(SQ_2));

    assertThat(referential.getConnectionsById()).containsOnly(entry("sq1", SQ_1), entry("sq2", SQ_2));
    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).containsExactly("Attempt to update connection 'sq2' that was not registered");
  }
}
