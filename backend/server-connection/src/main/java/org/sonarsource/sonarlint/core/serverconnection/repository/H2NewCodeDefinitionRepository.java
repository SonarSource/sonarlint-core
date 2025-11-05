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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.sonarsource.sonarlint.core.commons.NewCodeDefinition;
import org.sonarsource.sonarlint.core.commons.NewCodeMode;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.NEW_CODE_DEFINITIONS;

public class H2NewCodeDefinitionRepository implements NewCodeDefinitionRepository {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintDatabase database;

  public H2NewCodeDefinitionRepository(SonarLintDatabase database) {
    this.database = database;
  }

  @Override
  public void store(String connectionId, String projectKey, NewCodeDefinition newCodeDefinition) {
    var dsl = database.dsl();
    try {
      var mode = newCodeDefinition.getMode().name();
      Integer days = null;
      LocalDate thresholdDate = null;
      String version = null;
      String referenceBranch = null;

      if (newCodeDefinition.getMode() == NewCodeMode.NUMBER_OF_DAYS) {
        var numberOfDays = (NewCodeDefinition.NewCodeNumberOfDaysWithDate) newCodeDefinition;
        days = numberOfDays.getDays();
        thresholdDate = LocalDate.ofInstant(numberOfDays.getThresholdDate(), ZoneId.systemDefault());
      } else if (newCodeDefinition.getMode() == NewCodeMode.PREVIOUS_VERSION) {
        var previousVersion = (NewCodeDefinition.NewCodePreviousVersion) newCodeDefinition;
        thresholdDate = LocalDate.ofInstant(previousVersion.getThresholdDate(), ZoneId.systemDefault());
        version = previousVersion.getVersion();
      } else if (newCodeDefinition.getMode() == NewCodeMode.REFERENCE_BRANCH) {
        var referenceBranchDef = (NewCodeDefinition.NewCodeReferenceBranch) newCodeDefinition;
        referenceBranch = referenceBranchDef.getBranchName();
      } else {
        // SPECIFIC_ANALYSIS or other modes with date
        thresholdDate = LocalDate.ofInstant(newCodeDefinition.getThresholdDate(), ZoneId.systemDefault());
      }

      int updated = dsl.update(NEW_CODE_DEFINITIONS)
        .set(NEW_CODE_DEFINITIONS.MODE, mode)
        .set(NEW_CODE_DEFINITIONS.DAYS, days)
        .set(NEW_CODE_DEFINITIONS.THRESHOLD_DATE, thresholdDate)
        .set(NEW_CODE_DEFINITIONS.VERSION, version)
        .set(NEW_CODE_DEFINITIONS.REFERENCE_BRANCH, referenceBranch)
        .where(NEW_CODE_DEFINITIONS.CONNECTION_ID.eq(connectionId))
        .and(NEW_CODE_DEFINITIONS.PROJECT_KEY.eq(projectKey))
        .execute();

      if (updated == 0) {
        dsl.insertInto(NEW_CODE_DEFINITIONS,
          NEW_CODE_DEFINITIONS.CONNECTION_ID,
          NEW_CODE_DEFINITIONS.PROJECT_KEY,
          NEW_CODE_DEFINITIONS.MODE,
          NEW_CODE_DEFINITIONS.DAYS,
          NEW_CODE_DEFINITIONS.THRESHOLD_DATE,
          NEW_CODE_DEFINITIONS.VERSION,
          NEW_CODE_DEFINITIONS.REFERENCE_BRANCH)
          .values(connectionId, projectKey, mode, days, thresholdDate, version, referenceBranch)
          .execute();
      }
    } catch (RuntimeException ex) {
      LOG.debug("Store failed: " + ex.getMessage());
      throw ex;
    }
  }

  @Override
  public Optional<NewCodeDefinition> read(String connectionId, String projectKey) {
    var rec = database.dsl()
      .select(NEW_CODE_DEFINITIONS.MODE, NEW_CODE_DEFINITIONS.DAYS, NEW_CODE_DEFINITIONS.THRESHOLD_DATE, NEW_CODE_DEFINITIONS.VERSION, NEW_CODE_DEFINITIONS.REFERENCE_BRANCH)
      .from(NEW_CODE_DEFINITIONS)
      .where(NEW_CODE_DEFINITIONS.CONNECTION_ID.eq(connectionId))
      .and(NEW_CODE_DEFINITIONS.PROJECT_KEY.eq(projectKey))
      .fetchOne();

    if (rec == null) {
      return Optional.empty();
    }

    var mode = NewCodeMode.valueOf(rec.get(NEW_CODE_DEFINITIONS.MODE));
    var thresholdDate = rec.get(NEW_CODE_DEFINITIONS.THRESHOLD_DATE);
    var thresholdDateMillis = thresholdDate != null ? thresholdDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0L;
    var days = rec.get(NEW_CODE_DEFINITIONS.DAYS);
    var version = rec.get(NEW_CODE_DEFINITIONS.VERSION);
    var referenceBranch = rec.get(NEW_CODE_DEFINITIONS.REFERENCE_BRANCH);

    return switch (mode) {
      case NUMBER_OF_DAYS -> Optional.of(NewCodeDefinition.withNumberOfDaysWithDate(days != null ? days : 0, thresholdDateMillis));
      case PREVIOUS_VERSION -> Optional.of(NewCodeDefinition.withPreviousVersion(thresholdDateMillis, version));
      case REFERENCE_BRANCH -> Optional.of(NewCodeDefinition.withReferenceBranch(referenceBranch));
      case SPECIFIC_ANALYSIS -> Optional.of(NewCodeDefinition.withSpecificAnalysis(thresholdDateMillis));
      default -> Optional.empty();
    };
  }
}
