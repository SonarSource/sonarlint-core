/*
 * SonarLint Core - Commons
 * Copyright (C) SonarSource Sàrl
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

import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.flywaydb.core.Flyway;
import org.h2.api.ErrorCode;
import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.mvstore.MVStoreException;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public final class SonarLintDatabase {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static final String SQ_IDE_DB_FILENAME = "sq-ide";

  private final Path dbFile;
  private final JdbcConnectionPool dataSource;
  private final DSLContext dsl;

  public SonarLintDatabase(Path storageRoot) {
    this.dbFile = storageRoot.resolve("h2").resolve(SQ_IDE_DB_FILENAME + ".mv.db").toAbsolutePath();
    JdbcConnectionPool ds;
    try {
      var baseDir = storageRoot.resolve("h2");
      deleteLegacyDatabase(baseDir);
      Files.createDirectories(baseDir);
      var dbBasePath = baseDir.toRealPath().resolve(SQ_IDE_DB_FILENAME).toAbsolutePath();
      var url = "jdbc:h2:" + dbBasePath + ";AUTO_SERVER=TRUE";
      // Ensure H2 AUTO_SERVER binds and advertises loopback to allow local cross-process connections reliably
      var bindAddressProperty = "h2.bindAddress";
      if (StringUtils.isEmpty(System.getProperty(bindAddressProperty))) {
        System.setProperty(bindAddressProperty, "127.0.0.1");
      }
      LOG.debug("Initializing H2Database with URL {}", url);
      ds = JdbcConnectionPool.create(url, "sa", "");
    } catch (Exception e) {
      DatabaseExceptionReporter.capture(e, "startup", "h2.pool.create");
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
    try {
      flyway.migrate();
    } catch (Exception e) {
      // We are catching the exception for Sentry and rethrowing here to fail starting the backend
      DatabaseExceptionReporter.capture(e, "startup", "flyway.migrate");
      logIfCorrupted(e);
      throw e;
    }

    System.setProperty("org.jooq.no-tips", "true");
    System.setProperty("org.jooq.no-logo", "true");

    var jooqConfig = new DefaultConfiguration()
      .set(this.dataSource)
      .set(SQLDialect.H2)
      .set(new Settings().withExecuteLogging(false))
      .set(new DefaultExecuteListenerProvider(new JooqDatabaseExceptionListener()));
    this.dsl = DSL.using(jooqConfig);
  }

  /**
   * Tell users how to recover when the failure is caused by a corrupted database file
   */
  public void logIfCorrupted(Throwable throwable) {
    if (isCorruption(throwable)) {
      LOG.error("The local database seems to be corrupted. We recommend closing all IDEs, then deleting the H2 database used by SonarQube for IDE: {}", dbFile);
    }
  }

  static boolean isCorruption(Throwable throwable) {
    var causes = ExceptionUtils.getThrowableList(throwable);
    // An interrupted thread closes the file channel, which H2 reports as a read failure although the file is fine
    if (causes.stream().anyMatch(t -> t instanceof ClosedByInterruptException || t instanceof InterruptedException)) {
      return false;
    }
    return causes.stream().anyMatch(t -> t instanceof MVStoreException
      || (t instanceof SQLException sqlException && (sqlException.getErrorCode() == ErrorCode.FILE_CORRUPTED_1
        || (sqlException.getErrorCode() == ErrorCode.GENERAL_ERROR_1 && StringUtils.contains(sqlException.getMessage(), MVStoreException.class.getSimpleName())))));
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

  public void shutdown() {
    try {
      dataSource.dispose();
      LOG.debug("H2Database disposed");
    } catch (Exception e) {
      DatabaseExceptionReporter.capture(e, "shutdown", "h2.pool.dispose");
      LOG.debug("Error while disposing H2Database: {}", e.getMessage());
    }
  }
}
