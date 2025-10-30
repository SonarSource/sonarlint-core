/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.storage.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.commons.storage.model.AiCodeFix;

import static org.sonarsource.sonarlint.core.commons.storage.generated.Tables.AI_CODEFIX_SETTINGS;

/**
 * Repository for persisting and retrieving AiCodeFix entity using the local H2 database.
 * Settings are stored per server connection, addressed by connectionId.
 */
public class AiCodeFixRepository {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintDatabase database;

  public AiCodeFixRepository(SonarLintDatabase database) {
    this.database = database;
  }

  public Optional<AiCodeFix> get(String connectionId) {
    var rec = database.dsl()
      .select(AI_CODEFIX_SETTINGS.SUPPORTED_RULES, AI_CODEFIX_SETTINGS.ORGANIZATION_ELIGIBLE, AI_CODEFIX_SETTINGS.ENABLEMENT, AI_CODEFIX_SETTINGS.ENABLED_PROJECT_KEYS)
      .from(AI_CODEFIX_SETTINGS)
      .where(AI_CODEFIX_SETTINGS.CONNECTION_ID.eq(connectionId))
      .fetchOne();
    if (rec == null) {
      return Optional.empty();
    }
    var supportedRules = rec.get(AI_CODEFIX_SETTINGS.SUPPORTED_RULES);
    var organizationEligible = Boolean.TRUE.equals(rec.get(AI_CODEFIX_SETTINGS.ORGANIZATION_ELIGIBLE));
    var enablement = AiCodeFix.Enablement.valueOf(rec.get(AI_CODEFIX_SETTINGS.ENABLEMENT));
    var enabledProjectKeys = rec.get(AI_CODEFIX_SETTINGS.ENABLED_PROJECT_KEYS);
    return Optional.of(new AiCodeFix(connectionId, supportedRules, organizationEligible, enablement, enabledProjectKeys));
  }

  public void upsert(AiCodeFix entity) {
    var now = Timestamp.from(Instant.now());
    // use connection ID from Connection Table when it will be created
    int rowId = entity.connectionId().hashCode();

    var dsl = database.dsl();
    try {
      int updated = dsl.update(AI_CODEFIX_SETTINGS)
        .set(AI_CODEFIX_SETTINGS.SUPPORTED_RULES, entity.supportedRules())
        .set(AI_CODEFIX_SETTINGS.ORGANIZATION_ELIGIBLE, entity.organizationEligible())
        .set(AI_CODEFIX_SETTINGS.ENABLEMENT, entity.enablement().name())
        .set(AI_CODEFIX_SETTINGS.ENABLED_PROJECT_KEYS, entity.enabledProjectKeys())
        .set(AI_CODEFIX_SETTINGS.UPDATED_AT, now)
        .where(AI_CODEFIX_SETTINGS.CONNECTION_ID.eq(entity.connectionId()))
        .execute();
      if (updated == 0) {
        dsl.insertInto(AI_CODEFIX_SETTINGS,
            AI_CODEFIX_SETTINGS.ID,
            AI_CODEFIX_SETTINGS.CONNECTION_ID,
            AI_CODEFIX_SETTINGS.SUPPORTED_RULES,
            AI_CODEFIX_SETTINGS.ORGANIZATION_ELIGIBLE,
            AI_CODEFIX_SETTINGS.ENABLEMENT,
            AI_CODEFIX_SETTINGS.ENABLED_PROJECT_KEYS,
            AI_CODEFIX_SETTINGS.UPDATED_AT)
          .values(rowId,
            entity.connectionId(),
            entity.supportedRules(),
            entity.organizationEligible(),
            entity.enablement().name(),
            entity.enabledProjectKeys(),
            now)
          .execute();
      }
    } catch (RuntimeException ex) {
      LOG.debug("Upsert failed: " + ex.getMessage());
      throw ex;
    }
  }

}
