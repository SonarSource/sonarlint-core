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
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcConnectionPool;
import org.jooq.DSLContext;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public final class SonarLintH2Database {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final JdbcConnectionPool dataSource;
  private final DSLContext dsl;

  @Inject
  public SonarLintH2Database(StorageInitParams storageInitParams) {
    JdbcConnectionPool ds;
    try {
      var baseDir = storageInitParams.storageRoot().resolve("h2");
      Files.createDirectories(baseDir);
      var dbBasePath = baseDir.resolve("sonarlint").toAbsolutePath();
      var url = "jdbc:h2:file:" + dbBasePath + ";AUTO_SERVER=TRUE";
      LOG.debug("Initializing H2Database with URL {}", url);
      ds = JdbcConnectionPool.create(url, "sa", "");
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize H2Database", e);
    }
    ds.setMaxConnections(10);
    this.dataSource = ds;

    // Run Flyway migrations if available. Do not fail if none is found.
    try {
      var flyway = Flyway.configure()
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

    // Initialize jOOQ DSL after migrations
    this.dsl = org.jooq.impl.DSL.using(this.dataSource, org.jooq.SQLDialect.H2);
  }

  public DataSource getDataSource() {
    return dataSource;
  }

  public DSLContext dsl() {
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
