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
package org.sonarsource.sonarlint.core.commons.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

class SonarLintDatabaseAutoServerTest {

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  java.nio.file.Path tempDir;

  @Test
  void auto_server_allows_second_simultaneous_connection() throws Exception {
    var init = new SonarLintDatabaseInitParams(tempDir, SonarLintDatabaseMode.FILE, true);

    // First DB instance opens the same file DB and creates a table + a row
    var db1 = new SonarLintDatabase(init);
    try (var c1 = db1.getConnection(); var st1 = c1.createStatement()) {
      st1.execute("CREATE TABLE IF NOT EXISTS T(ID INT PRIMARY KEY, VAL VARCHAR(255))");
      st1.executeUpdate("MERGE INTO T (ID, VAL) KEY(ID) VALUES (1, 'from-db1')");
    }

    // Second DB instance, same storage root, should be able to open concurrently
    var db2 = new SonarLintDatabase(init);
    try (var c2 = db2.getConnection(); var st2 = c2.createStatement()) {
      try (ResultSet rs = st2.executeQuery("SELECT VAL FROM T WHERE ID=1")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("from-db1");
      }
      // Write from the second connection
      st2.executeUpdate("MERGE INTO T (ID, VAL) KEY(ID) VALUES (2, 'from-db2')");
    }

    // Verify the first DB instance can see the change written by the second
    try (var c1b = db1.getConnection(); var ps = c1b.prepareStatement("SELECT COUNT(*) FROM T"); ResultSet rs = ps.executeQuery()) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1)).isEqualTo(2);
    }

    db2.shutdown();
    db1.shutdown();
  }
}
