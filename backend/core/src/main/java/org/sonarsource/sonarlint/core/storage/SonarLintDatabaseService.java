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
package org.sonarsource.sonarlint.core.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverconnection.aicodefix.AiCodeFixRepository;
import org.sonarsource.sonarlint.core.serverconnection.issues.LocalOnlyIssuesRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.SERVER_BRANCHES;
import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.SERVER_DEPENDENCY_RISKS;
import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.SERVER_FINDINGS;

@Component
@Lazy(false)
public class SonarLintDatabaseService {

  private final SonarLintDatabase database;
  private final LocalOnlyIssuesRepository localOnlyIssuesRepository;
  private final AiCodeFixRepository aiCodeFixRepository;
  private final Set<String> initialConnectionIds;

  public SonarLintDatabaseService(SonarLintDatabase database, LocalOnlyIssuesRepository localOnlyIssuesRepository, AiCodeFixRepository aiCodeFixRepository,
    InitializeParams params) {
    this.database = database;
    this.localOnlyIssuesRepository = localOnlyIssuesRepository;
    this.aiCodeFixRepository = aiCodeFixRepository;
    this.initialConnectionIds = Stream.concat(
      params.getSonarQubeConnections().stream().map(SonarQubeConnectionConfigurationDto::getConnectionId),
      params.getSonarCloudConnections().stream().map(SonarCloudConnectionConfigurationDto::getConnectionId))
      .collect(Collectors.toSet());
  }

  public SonarLintDatabase getDatabase() {
    return database;
  }

  @PostConstruct
  public void postConstruct() {
    cleanupNonExistingConnections();
    localOnlyIssuesRepository.purgeIssuesOlderThan(Instant.now().minus(7, ChronoUnit.DAYS));
  }

  private void cleanupNonExistingConnections() {
    aiCodeFixRepository.deleteUnknownConnections(initialConnectionIds);
    // this should be moved to ServerFindingRepository but the current design does not allow it
    database.dsl().deleteFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.CONNECTION_ID.notIn(initialConnectionIds))
      .execute();
    database.dsl().deleteFrom(SERVER_DEPENDENCY_RISKS)
      .where(SERVER_DEPENDENCY_RISKS.CONNECTION_ID.notIn(initialConnectionIds))
      .execute();
    database.dsl().deleteFrom(SERVER_BRANCHES)
      .where(SERVER_BRANCHES.CONNECTION_ID.notIn(initialConnectionIds))
      .execute();
  }

  @PreDestroy
  public void preDestroy() {
    database.shutdown();
  }

}
