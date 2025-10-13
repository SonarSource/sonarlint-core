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

import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcConnectionPool;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public final class H2Database {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final JdbcConnectionPool dataSource;
  private final org.jooq.DSLContext dsl;

  @Inject
  public H2Database(StorageInitParams storageInitParams) {
    JdbcConnectionPool ds;
    try {
      Path baseDir = storageInitParams.storageRoot().resolve("h2");
      Files.createDirectories(baseDir);
      Path dbBasePath = baseDir.resolve("sonarlint").toAbsolutePath();
      System.out.println("H2Database dbBasePath: " + dbBasePath);
      String url = "jdbc:h2:file:" + dbBasePath + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
      LOG.debug("Initializing H2Database with URL {}", url);
      ds = JdbcConnectionPool.create(url, "sa", "");
    } catch (Exception e) {
      String fallbackUrl = "jdbc:h2:mem:sonarlint;DB_CLOSE_DELAY=-1";
      LOG.debug("Failed to initialize file-based H2 DB ({}). Falling back to in-memory with URL {}", e.getMessage(), fallbackUrl);
      ds = JdbcConnectionPool.create(fallbackUrl, "sa", "");
    }
    ds.setMaxConnections(10);
    this.dataSource = ds;

    // Run Flyway migrations if available. Do not fail if none are found.
    try {
      Flyway flyway = Flyway.configure()
        .dataSource(this.dataSource)
        .locations("classpath:db/migration")
        .defaultSchema("PUBLIC")
        .schemas("PUBLIC")
        .createSchemas(true)
        .baselineOnMigrate(true)
        .failOnMissingLocations(false)
        .load();
      flyway.migrate();
    } catch (RuntimeException e) {
      LOG.debug("Flyway migration skipped or failed: {}", e.getMessage());
    }

    // Defensive fallback: ensure required tables exist if Flyway didn't create them
    try {
      try (var conn = this.dataSource.getConnection(); var stmt = conn.createStatement()) {
        stmt.executeUpdate(
          "CREATE TABLE IF NOT EXISTS \"AI_CODEFIX_SETTINGS\" (" +
          " \"id\" INTEGER NOT NULL," +
          " \"supported_rules\" CLOB," +
          " \"organization_eligible\" BOOLEAN," +
          " \"enablement\" VARCHAR(64)," +
          " \"enabled_project_keys\" CLOB," +
          " \"updated_at\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
          " CONSTRAINT pk_ai_codefix_settings PRIMARY KEY (\"id\")" +
          ")"
        );
      }
    } catch (Exception ex) {
      LOG.debug("Fallback table creation skipped or failed: {}", ex.getMessage());
    }

    // Debug: verify table existence
    try (var conn = this.dataSource.getConnection(); var stmt = conn.createStatement(); var rs = stmt.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='AI_CODEFIX_SETTINGS'")) {
      if (rs.next()) {
        int c = rs.getInt(1);
        // debug removed
      }
    } catch (Exception ignored) {
    }

    // Initialize jOOQ DSL after migrations
    this.dsl = org.jooq.impl.DSL.using(this.dataSource, org.jooq.SQLDialect.H2);
  }

  public DataSource getDataSource() {
    return dataSource;
  }

  public org.jooq.DSLContext dsl() {
    return dsl;
  }

  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

  public void shutdown() {
    try {
      dataSource.dispose();
      LOG.debug("H2Database disposed");
    } catch (Exception e) {
      LOG.debug("Error while disposing H2Database: {}", e.getMessage());
    }
  }
}
