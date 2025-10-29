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
package org.sonarsource.sonarlint.core.commons.testutils;

import java.sql.SQLException;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;

public class H2Utils {

  public static void insertRecords(SonarLintDatabase sonarLintDatabase, int recCount) throws SQLException {
    var insertSQL = "INSERT INTO TEST_TABLE (VAL) VALUES (?)";
    var countSQL = "SELECT COUNT(*) FROM TEST_TABLE";
    try (var connection = sonarLintDatabase.getConnection()) {
      try (var preparedInsertStatement = connection.prepareStatement(insertSQL);
           var preparedCountStatement = connection.prepareStatement(countSQL)) {
        for (int i = 1; i <= recCount; i++) {
          preparedInsertStatement.setString(1, "External Record " + i);
          preparedInsertStatement.executeUpdate();
          var countResultSet = preparedCountStatement.executeQuery();
          if (i % 10 == 0) {
            System.out.println("Process " + ProcessHandle.current().pid() + " - Inserting record");
            if (countResultSet.next()) {
              var totalCount = countResultSet.getLong(1);
              System.out.println("Inserted " + i + " records, total: " + totalCount);
            }
          }
        }
      }
      System.out.println("Process " + ProcessHandle.current().pid() + " - Releasing connection");
    }
  }

  public static void ensureTestTableExists(SonarLintDatabase sonarLintDatabase) throws SQLException {
    try (var connection = sonarLintDatabase.getConnection()) {
      System.out.println("Process " + ProcessHandle.current().pid() + " - Ensuring test table exists");
      try (var statement = connection.createStatement()) {
        statement.execute("CREATE TABLE IF NOT EXISTS TEST_TABLE (VAL VARCHAR(255))");
      }
      System.out.println("Process " + ProcessHandle.current().pid() + " - Ensuring test table is empty");
      try (var statement = connection.createStatement()) {
        statement.execute("DELETE FROM TEST_TABLE");
      }
    }
  }

}
