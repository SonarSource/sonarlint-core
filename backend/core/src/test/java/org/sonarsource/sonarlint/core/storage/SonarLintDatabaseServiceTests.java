/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.storage;

import java.util.List;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverconnection.aicodefix.AiCodeFixRepository;
import org.sonarsource.sonarlint.core.serverconnection.issues.LocalOnlyIssuesRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarLintDatabaseServiceTests {

  @Test
  void should_give_the_database_a_chance_to_log_corruption_when_startup_cleanup_fails() {
    var database = mock(SonarLintDatabase.class);
    var aiCodeFixRepository = mock(AiCodeFixRepository.class);
    var failure = new DataAccessException("General error");
    doThrow(failure).when(aiCodeFixRepository).deleteUnknownConnections(any());
    var params = mock(InitializeParams.class);
    when(params.getSonarQubeConnections()).thenReturn(List.of());
    when(params.getSonarCloudConnections()).thenReturn(List.of());
    var service = new SonarLintDatabaseService(database, mock(LocalOnlyIssuesRepository.class), aiCodeFixRepository, params);

    assertThatThrownBy(service::postConstruct).isSameAs(failure);
    verify(database).logIfCorrupted(failure);
  }

}
