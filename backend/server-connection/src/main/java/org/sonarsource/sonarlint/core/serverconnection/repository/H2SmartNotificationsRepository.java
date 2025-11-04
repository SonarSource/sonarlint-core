/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.PROJECTS;

/**
 * H2-based implementation of SmartNotificationsRepository.
 */
public class H2SmartNotificationsRepository implements SmartNotificationsRepository {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintDatabase database;

  public H2SmartNotificationsRepository(SonarLintDatabase database) {
    this.database = database;
  }

  @Override
  public void store(String connectionId, String projectKey, Long lastEventPolling) {
    var dsl = database.dsl();
    try {
      var lastPollTime = lastEventPolling != null ? LocalDateTime.ofInstant(Instant.ofEpochMilli(lastEventPolling), ZoneId.systemDefault()) : null;
      int updated = dsl.update(PROJECTS)
        .set(PROJECTS.LAST_SMART_NOTIFICATION_POLL_DATE, lastPollTime)
        .where(PROJECTS.CONNECTION_ID.eq(connectionId))
        .and(PROJECTS.PROJECT_KEY.eq(projectKey))
        .execute();
      if (updated == 0) {
        dsl.insertInto(PROJECTS,
          PROJECTS.CONNECTION_ID,
          PROJECTS.PROJECT_KEY,
          PROJECTS.LAST_SMART_NOTIFICATION_POLL_DATE)
          .values(connectionId, projectKey, lastPollTime)
          .execute();
      }
    } catch (RuntimeException ex) {
      LOG.debug("Store failed: " + ex.getMessage());
      throw ex;
    }
  }

  @Override
  public Optional<Long> readLastEventPolling(String connectionId, String projectKey) {
    var rec = database.dsl()
      .select(PROJECTS.LAST_SMART_NOTIFICATION_POLL_DATE)
      .from(PROJECTS)
      .where(PROJECTS.CONNECTION_ID.eq(connectionId))
      .and(PROJECTS.PROJECT_KEY.eq(projectKey))
      .fetchOne();
    return Optional.ofNullable(rec)
      .map(r -> r.get(PROJECTS.LAST_SMART_NOTIFICATION_POLL_DATE))
      .map(ldt -> ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
  }
}
