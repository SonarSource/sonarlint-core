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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintH2Database;
import org.sonarsource.sonarlint.core.commons.storage.StorageInitParams;
import org.sonarsource.sonarlint.core.commons.storage.model.AiCodeFix;

class AiCodeFixRepositoryTest {

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path temp;

  @Test
  void upsert_and_get_should_persist_to_h2_file_database() {
    // Given a file-based H2 database under a temporary storage root
    var storageRoot = temp.resolve("storage");

    var db = new SonarLintH2Database(new StorageInitParams(storageRoot));
    var aiCodeFixRepo = new AiCodeFixRepository(db);

    var entityToStore = new AiCodeFix(
      Set.of("java:S100", "js:S200"),
      true,
      AiCodeFix.Enablement.ENABLED_FOR_SOME_PROJECTS,
      Set.of("project-a", "project-b")
    );

    // When we upsert the entity
    aiCodeFixRepo.upsert(entityToStore);

    // And shutdown the first DB to force closing connections
    db.shutdown();

    // Create a new repository with a fresh DB instance pointing to the same storage root
    var db2 = new SonarLintH2Database(new StorageInitParams(storageRoot));
    var repo2 = new AiCodeFixRepository(db2);

    // Then we can read back exactly what we stored
    var loadedOpt = repo2.get();
    assertThat(loadedOpt).isPresent();
    var loaded = loadedOpt.get();

    assertThat(loaded.getSupportedRules()).containsExactlyInAnyOrder("java:S100", "js:S200");
    assertThat(loaded.isOrganizationEligible()).isTrue();
    assertThat(loaded.getEnablement()).isEqualTo(AiCodeFix.Enablement.ENABLED_FOR_SOME_PROJECTS);
    assertThat(loaded.getEnabledProjectKeys()).containsExactlyInAnyOrder("project-a", "project-b");

    db2.shutdown();
  }
}
