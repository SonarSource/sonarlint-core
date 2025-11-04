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

import java.util.Set;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBranches;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.PROJECT_BRANCHES;

/**
 * H2-based implementation of ProjectBranchesRepository.
 */
public class H2ProjectBranchesRepository implements ProjectBranchesRepository {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintDatabase database;

  public H2ProjectBranchesRepository(SonarLintDatabase database) {
    this.database = database;
  }

  @Override
  public boolean exists(String connectionId, String projectKey) {
    var rec = database.dsl()
      .select(PROJECT_BRANCHES.NAME)
      .from(PROJECT_BRANCHES)
      .where(PROJECT_BRANCHES.CONNECTION_ID.eq(connectionId))
      .and(PROJECT_BRANCHES.PROJECT_KEY.eq(projectKey))
      .fetchAny();
    return rec != null;
  }

  @Override
  public void store(String connectionId, String projectKey, ProjectBranches projectBranches) {
    var dsl = database.dsl();
    try {
      // Delete existing branches for this project
      dsl.deleteFrom(PROJECT_BRANCHES)
        .where(PROJECT_BRANCHES.CONNECTION_ID.eq(connectionId))
        .and(PROJECT_BRANCHES.PROJECT_KEY.eq(projectKey))
        .execute();

      // Insert all branches
      for (var branchName : projectBranches.getBranchNames()) {
        var isDefault = branchName.equals(projectBranches.getMainBranchName());
        dsl.insertInto(PROJECT_BRANCHES, PROJECT_BRANCHES.CONNECTION_ID, PROJECT_BRANCHES.PROJECT_KEY, PROJECT_BRANCHES.NAME, PROJECT_BRANCHES.IS_DEFAULT)
          .values(connectionId, projectKey, branchName, isDefault)
          .execute();
      }
    } catch (RuntimeException ex) {
      LOG.debug("Store failed: " + ex.getMessage());
      throw ex;
    }
  }

  @Override
  public ProjectBranches read(String connectionId, String projectKey) {
    var records = database.dsl()
      .select(PROJECT_BRANCHES.NAME, PROJECT_BRANCHES.IS_DEFAULT)
      .from(PROJECT_BRANCHES)
      .where(PROJECT_BRANCHES.CONNECTION_ID.eq(connectionId))
      .and(PROJECT_BRANCHES.PROJECT_KEY.eq(projectKey))
      .fetch();

    if (records.isEmpty()) {
      return new ProjectBranches(Set.of(), null);
    }

    var branchNames = records.stream()
      .map(rec -> rec.get(PROJECT_BRANCHES.NAME))
      .collect(java.util.stream.Collectors.toSet());

    var mainBranchName = records.stream()
      .filter(rec -> Boolean.TRUE.equals(rec.get(PROJECT_BRANCHES.IS_DEFAULT)))
      .map(rec -> rec.get(PROJECT_BRANCHES.NAME))
      .findFirst()
      .orElse(null);

    return new ProjectBranches(branchNames, mainBranchName);
  }
}
