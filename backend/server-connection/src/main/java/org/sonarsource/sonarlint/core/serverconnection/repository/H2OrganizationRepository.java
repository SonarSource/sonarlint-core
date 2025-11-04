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
import java.util.UUID;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.serverconnection.Organization;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.ORGANIZATIONS;

/**
 * H2-based implementation of OrganizationRepository.
 */
public class H2OrganizationRepository implements OrganizationRepository {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintDatabase database;

  public H2OrganizationRepository(SonarLintDatabase database) {
    this.database = database;
  }

  @Override
  public void store(String connectionId, Organization organization) {
    var dsl = database.dsl();
    try {
      int updated = dsl.update(ORGANIZATIONS)
        .set(ORGANIZATIONS.ID, organization.id())
        .set(ORGANIZATIONS.UUIDV4, organization.uuidV4().toString())
        .where(ORGANIZATIONS.CONNECTION_ID.eq(connectionId))
        .execute();
      if (updated == 0) {
        dsl.insertInto(ORGANIZATIONS, ORGANIZATIONS.CONNECTION_ID, ORGANIZATIONS.ID, ORGANIZATIONS.UUIDV4)
          .values(connectionId, organization.id(), organization.uuidV4().toString())
          .execute();
      }
    } catch (RuntimeException ex) {
      LOG.debug("Store failed: " + ex.getMessage());
      throw ex;
    }
  }

  @Override
  public Optional<Organization> read(String connectionId) {
    var rec = database.dsl()
      .select(ORGANIZATIONS.ID, ORGANIZATIONS.UUIDV4)
      .from(ORGANIZATIONS)
      .where(ORGANIZATIONS.CONNECTION_ID.eq(connectionId))
      .fetchOne();
    if (rec == null) {
      return Optional.empty();
    }
    return Optional.of(new Organization(rec.get(ORGANIZATIONS.ID), UUID.fromString(rec.get(ORGANIZATIONS.UUIDV4))));
  }
}
