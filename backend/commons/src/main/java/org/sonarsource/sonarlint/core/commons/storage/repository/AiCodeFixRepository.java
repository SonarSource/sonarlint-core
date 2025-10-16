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

import jakarta.inject.Inject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintH2Database;
import org.sonarsource.sonarlint.core.commons.storage.model.AiCodeFix;

import static org.sonarsource.sonarlint.core.commons.storage.generated.Tables.AI_CODEFIX_SETTINGS;

/**
 * Repository for persisting and retrieving AiCodeFix entity using the local H2 database.
 * Stores a single row (id=1) since settings are global for the installation.
 */
public class AiCodeFixRepository {

  private static final int SINGLETON_ID = 1;

  private final SonarLintH2Database database;

  @Inject
  public AiCodeFixRepository(SonarLintH2Database database) {
    this.database = database;
  }

  public Optional<AiCodeFix> get() {
    var rec = database.dsl()
      .select(AI_CODEFIX_SETTINGS.SUPPORTED_RULES, AI_CODEFIX_SETTINGS.ORGANIZATION_ELIGIBLE, AI_CODEFIX_SETTINGS.ENABLEMENT, AI_CODEFIX_SETTINGS.ENABLED_PROJECT_KEYS)
      .from(AI_CODEFIX_SETTINGS)
      .where(AI_CODEFIX_SETTINGS.ID.eq(SINGLETON_ID))
      .fetchOne();
    if (rec == null) {
      return Optional.empty();
    }
    var supportedRules = splitToSet(rec.get(AI_CODEFIX_SETTINGS.SUPPORTED_RULES));
    var organizationEligible = Boolean.TRUE.equals(rec.get(AI_CODEFIX_SETTINGS.ORGANIZATION_ELIGIBLE));
    var enablement = AiCodeFix.Enablement.valueOf(rec.get(AI_CODEFIX_SETTINGS.ENABLEMENT));
    var enabledProjectKeys = splitToSet(rec.get(AI_CODEFIX_SETTINGS.ENABLED_PROJECT_KEYS));
    return Optional.of(new AiCodeFix(supportedRules, organizationEligible, enablement, enabledProjectKeys));
  }

  public void upsert(AiCodeFix entity) {
    var now = Timestamp.from(Instant.now());

    var dsl = database.dsl();
    try {
      int updated = dsl.update(AI_CODEFIX_SETTINGS)
        .set(AI_CODEFIX_SETTINGS.SUPPORTED_RULES, join(entity.getSupportedRules()))
        .set(AI_CODEFIX_SETTINGS.ORGANIZATION_ELIGIBLE, entity.isOrganizationEligible())
        .set(AI_CODEFIX_SETTINGS.ENABLEMENT, entity.getEnablement().name())
        .set(AI_CODEFIX_SETTINGS.ENABLED_PROJECT_KEYS, join(entity.getEnabledProjectKeys()))
        .set(AI_CODEFIX_SETTINGS.UPDATED_AT, now)
        .where(AI_CODEFIX_SETTINGS.ID.eq(SINGLETON_ID))
        .execute();
      if (updated == 0) {
        dsl.insertInto(AI_CODEFIX_SETTINGS,
            AI_CODEFIX_SETTINGS.ID,
            AI_CODEFIX_SETTINGS.SUPPORTED_RULES,
            AI_CODEFIX_SETTINGS.ORGANIZATION_ELIGIBLE,
            AI_CODEFIX_SETTINGS.ENABLEMENT,
            AI_CODEFIX_SETTINGS.ENABLED_PROJECT_KEYS,
            AI_CODEFIX_SETTINGS.UPDATED_AT)
          .values(SINGLETON_ID,
            join(entity.getSupportedRules()),
            entity.isOrganizationEligible(),
            entity.getEnablement().name(),
            join(entity.getEnabledProjectKeys()),
            now)
          .execute();
      }
    } catch (RuntimeException ex) {
      System.out.println("[DEBUG_LOG] upsert failed: " + ex.getMessage());
      throw ex;
    }
  }

  private static String join(Set<String> values) {
    if (values == null || values.isEmpty()) {
      return "";
    }
    return String.join("\n", values);
  }

  private static Set<String> splitToSet(String s) {
    if (s == null || s.isEmpty()) {
      return Set.of();
    }
    // Keep deterministic order for stability
    return new TreeSet<>(Arrays.asList(s.split("\n")));
  }

}
