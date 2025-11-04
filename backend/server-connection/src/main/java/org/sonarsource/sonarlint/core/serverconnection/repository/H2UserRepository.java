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

import java.util.Optional;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.USERS;

/**
 * H2-based implementation of UserRepository.
 */
public class H2UserRepository implements UserRepository {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintDatabase database;

  public H2UserRepository(SonarLintDatabase database) {
    this.database = database;
  }

  @Override
  public void store(String connectionId, String userId) {
    var dsl = database.dsl();
    try {
      int updated = dsl.update(USERS)
        .set(USERS.ID, userId)
        .where(USERS.CONNECTION_ID.eq(connectionId))
        .execute();
      if (updated == 0) {
        dsl.insertInto(USERS, USERS.CONNECTION_ID, USERS.ID)
          .values(connectionId, userId)
          .execute();
      }
    } catch (RuntimeException ex) {
      LOG.debug("Store failed: " + ex.getMessage());
      throw ex;
    }
  }

  @Override
  public Optional<String> read(String connectionId) {
    var rec = database.dsl()
      .select(USERS.ID)
      .from(USERS)
      .where(USERS.CONNECTION_ID.eq(connectionId))
      .fetchOne();
    if (rec == null || rec.get(USERS.ID) == null) {
      return Optional.empty();
    }
    return Optional.of(rec.get(USERS.ID));
  }
}
