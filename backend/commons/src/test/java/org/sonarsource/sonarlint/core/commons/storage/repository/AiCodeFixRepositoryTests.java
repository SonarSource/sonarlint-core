/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabaseMode;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabaseInitParams;
import org.sonarsource.sonarlint.core.commons.storage.model.AiCodeFix;

class AiCodeFixRepositoryTests {

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path temp;

  @Test
  void upsert_and_get_should_persist_to_h2_file_database() {
    // Given a file-based H2 database under a temporary storage root
    var storageRoot = temp.resolve("storage");

    var db = new SonarLintDatabase(new SonarLintDatabaseInitParams(storageRoot, SonarLintDatabaseMode.MEM));
    var aiCodeFixRepo = new AiCodeFixRepository(db);

    var entityToStore = new AiCodeFix(
      "test-connection",
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
    var db2 = new SonarLintDatabase(new SonarLintDatabaseInitParams(storageRoot, SonarLintDatabaseMode.MEM));
    var repo2 = new AiCodeFixRepository(db2);
    // With a different connection id, no settings should be visible
    var loadedOptDifferent = repo2.get("test-connection-2");
    assertThat(loadedOptDifferent).isEmpty();

    // With the same connection id, we should read back exactly what we stored
    var repoSame = new AiCodeFixRepository(db2);
    var loadedOpt = repoSame.get("test-connection");
    assertThat(loadedOpt).isPresent();
    var loaded = loadedOpt.get();

    assertThat(loaded.supportedRules()).containsExactlyInAnyOrder("java:S100", "js:S200");
    assertThat(loaded.organizationEligible()).isTrue();
    assertThat(loaded.enablement()).isEqualTo(AiCodeFix.Enablement.ENABLED_FOR_SOME_PROJECTS);
    assertThat(loaded.enabledProjectKeys()).containsExactlyInAnyOrder("project-a", "project-b");

    db2.shutdown();
  }
}
