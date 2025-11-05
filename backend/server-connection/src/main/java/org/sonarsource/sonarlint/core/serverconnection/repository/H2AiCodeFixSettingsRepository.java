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
import java.util.Set;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.serverconnection.AiCodeFixFeatureEnablement;
import org.sonarsource.sonarlint.core.serverconnection.AiCodeFixSettings;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.AI_CODEFIX_SETTINGS;

/**
 * H2-based implementation of AiCodeFixSettingsRepository.
 */
public class H2AiCodeFixSettingsRepository implements AiCodeFixSettingsRepository {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintDatabase database;

  public H2AiCodeFixSettingsRepository(SonarLintDatabase database) {
    this.database = database;
  }

  @Override
  public void store(String connectionId, AiCodeFixSettings settings) {
    var dsl = database.dsl();
    try {
      // Convert Set<String> to String[] array for storage
      var supportedRulesArray = settings.supportedRules().toArray(new String[0]);
      var enabledProjectKeysArray = settings.enabledProjectKeys().toArray(new String[0]);

      int updated = dsl.update(AI_CODEFIX_SETTINGS)
        .set(AI_CODEFIX_SETTINGS.SUPPORTED_RULES, supportedRulesArray)
        .set(AI_CODEFIX_SETTINGS.ORGANIZATION_ELIGIBLE, settings.isOrganizationEligible())
        .set(AI_CODEFIX_SETTINGS.ENABLEMENT, settings.enablement().name())
        .set(AI_CODEFIX_SETTINGS.ENABLED_PROJECT_KEYS, enabledProjectKeysArray)
        .where(AI_CODEFIX_SETTINGS.CONNECTION_ID.eq(connectionId))
        .execute();

      if (updated == 0) {
        dsl.insertInto(AI_CODEFIX_SETTINGS,
          AI_CODEFIX_SETTINGS.CONNECTION_ID,
          AI_CODEFIX_SETTINGS.SUPPORTED_RULES,
          AI_CODEFIX_SETTINGS.ORGANIZATION_ELIGIBLE,
          AI_CODEFIX_SETTINGS.ENABLEMENT,
          AI_CODEFIX_SETTINGS.ENABLED_PROJECT_KEYS)
          .values(connectionId, supportedRulesArray, settings.isOrganizationEligible(), settings.enablement().name(), enabledProjectKeysArray)
          .execute();
      }
    } catch (RuntimeException ex) {
      LOG.debug("Store failed: " + ex.getMessage());
      throw ex;
    }
  }

  @Override
  public Optional<AiCodeFixSettings> read(String connectionId) {
    var rec = database.dsl()
      .select(AI_CODEFIX_SETTINGS.SUPPORTED_RULES, AI_CODEFIX_SETTINGS.ORGANIZATION_ELIGIBLE, AI_CODEFIX_SETTINGS.ENABLEMENT, AI_CODEFIX_SETTINGS.ENABLED_PROJECT_KEYS)
      .from(AI_CODEFIX_SETTINGS)
      .where(AI_CODEFIX_SETTINGS.CONNECTION_ID.eq(connectionId))
      .fetchOne();

    if (rec == null) {
      return Optional.empty();
    }

    var supportedRulesArray = rec.get(AI_CODEFIX_SETTINGS.SUPPORTED_RULES);
    var supportedRules = supportedRulesArray != null && supportedRulesArray.length > 0
      ? Set.copyOf(java.util.Arrays.asList(supportedRulesArray))
      : Set.<String>of();

    var organizationEligible = Boolean.TRUE.equals(rec.get(AI_CODEFIX_SETTINGS.ORGANIZATION_ELIGIBLE));
    var enablement = AiCodeFixFeatureEnablement.valueOf(rec.get(AI_CODEFIX_SETTINGS.ENABLEMENT));

    var enabledProjectKeysArray = rec.get(AI_CODEFIX_SETTINGS.ENABLED_PROJECT_KEYS);
    var enabledProjectKeys = enabledProjectKeysArray != null && enabledProjectKeysArray.length > 0
      ? Set.copyOf(java.util.Arrays.asList(enabledProjectKeysArray))
      : Set.<String>of();

    return Optional.of(new AiCodeFixSettings(supportedRules, organizationEligible, enablement, enabledProjectKeys));
  }
}
