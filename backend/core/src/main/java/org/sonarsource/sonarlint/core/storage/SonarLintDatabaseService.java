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
package org.sonarsource.sonarlint.core.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;

public class SonarLintDatabaseService {

  private final SonarLintDatabase database;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;

  public SonarLintDatabaseService(SonarLintDatabase database, ConnectionConfigurationRepository connectionConfigurationRepository) {
    this.database = database;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
  }

  public SonarLintDatabase getDatabase() {
    return database;
  }

  @PostConstruct
  public void postConstruct() {
    var connectionIds = connectionConfigurationRepository.getConnectionsById().keySet();
    database.cleanupNonExistingConnections(connectionIds);
  }

  @PreDestroy
  public void preDestroy() {
    database.shutdown();
  }

}
