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
package org.sonarsource.sonarlint.core.commons.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcConnectionPool;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.AI_CODEFIX_SETTINGS;

public final class SonarLintDatabase {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final String SQ_IDE_DB_FILENAME = "sq-ide";

  private final JdbcConnectionPool dataSource;
  private final DSLContext dsl;

  public SonarLintDatabase(Path storageRoot) {
    JdbcConnectionPool ds;
    try {
      var baseDir = storageRoot.resolve("h2");
      deleteLegacyDatabase(baseDir);
      Files.createDirectories(baseDir);
      var dbBasePath = baseDir.resolve(SQ_IDE_DB_FILENAME).toAbsolutePath();
      var url = "jdbc:h2:" + dbBasePath + ";AUTO_SERVER=TRUE";
      // Ensure H2 AUTO_SERVER binds and advertises loopback to allow local cross-process connections reliably
      var bindAddressProperty = "h2.bindAddress";
      if (StringUtils.isEmpty(System.getProperty(bindAddressProperty))) {
        System.setProperty(bindAddressProperty, "127.0.0.1");
      }
      LOG.debug("Initializing H2Database with URL {}", url);
      ds = JdbcConnectionPool.create(url, "sa", "");
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize H2Database", e);
    }
    this.dataSource = ds;

    var flyway = Flyway.configure()
      .dataSource(this.dataSource)
      .locations("classpath:db/migration")
      .defaultSchema("PUBLIC")
      .schemas("PUBLIC")
      .createSchemas(true)
      .baselineOnMigrate(true)
      .failOnMissingLocations(false)
      .load();
    // this might throw but it's fine. If migrations fail, we want to fail starting the backend
    flyway.migrate();

    System.setProperty("org.jooq.no-tips", "true");
    System.setProperty("org.jooq.no-logo", "true");
    this.dsl = DSL.using(this.dataSource, SQLDialect.H2);
  }

  private static void deleteLegacyDatabase(Path baseDir) {
    // see SLCORE-1847
    var legacyDb = baseDir.resolve("sonarlint");
    if (Files.exists(legacyDb)) {
      FileUtils.deleteQuietly(legacyDb.toFile());
    }
  }

  public DSLContext dsl() {
    return dsl;
  }

  public void withTransaction(Consumer<Configuration> transaction) {
    dsl.transaction(transaction::accept);
  }

  public void shutdown() {
    try {
      dataSource.dispose();
      LOG.debug("H2Database disposed");
    } catch (Exception e) {
      LOG.debug("Error while disposing H2Database: {}", e.getMessage());
    }
  }

  public void cleanupNonExistingConnections(Set<String> existingConnectionIds) {
    dsl.deleteFrom(AI_CODEFIX_SETTINGS)
      .where(AI_CODEFIX_SETTINGS.CONNECTION_ID.notIn(existingConnectionIds))
      .execute();
  }
}
