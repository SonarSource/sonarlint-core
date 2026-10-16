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

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.flywaydb.core.Flyway;
import org.h2.api.ErrorCode;
import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.mvstore.DataUtils;
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
  private static final Pattern MVSTORE_ERROR_CODE_PATTERN = Pattern.compile("\\[\\d++\\.\\d++\\.\\d++/(\\d++)]");

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
      // Another H2 owner may hold the file lock before its AUTO_SERVER is ready.
      .connectRetries(3)
      .connectRetriesInterval(1)
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
      LOG.error("SonarQube for IDE could not read its local database. Close all IDE instances, delete '{}', and restart your IDE. " +
        "Cached server data will be downloaded again when connected.", dbFile);
    }
  }

  static boolean isCorruption(Throwable throwable) {
    return ExceptionUtils.getThrowableList(throwable).stream().anyMatch(t -> (t instanceof MVStoreException mvStoreException && isCorruptionCode(mvStoreException.getErrorCode()))
      || (t instanceof SQLException sqlException && (sqlException.getErrorCode() == ErrorCode.FILE_CORRUPTED_1
        || (sqlException.getErrorCode() == ErrorCode.GENERAL_ERROR_1 && hasMVStoreCorruptionCode(sqlException.getMessage())))));
  }

  /**
   * H2 sometimes only keeps the message of the original {@link MVStoreException}, which ends with "[version/errorCode]"
   */
  private static boolean hasMVStoreCorruptionCode(@Nullable String message) {
    if (!StringUtils.contains(message, MVStoreException.class.getSimpleName())) {
      return false;
    }
    var matcher = MVSTORE_ERROR_CODE_PATTERN.matcher(message);
    while (matcher.find()) {
      if (isCorruptionCode(Integer.parseInt(matcher.group(1)))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Other codes (read/write failures, closed or locked store...) can also happen with a healthy file, e.g. when the disk is full or the thread is interrupted
   */
  private static boolean isCorruptionCode(int mvStoreErrorCode) {
    return mvStoreErrorCode == DataUtils.ERROR_FILE_CORRUPT
      || mvStoreErrorCode == DataUtils.ERROR_CHUNK_NOT_FOUND
      || mvStoreErrorCode == DataUtils.ERROR_BLOCK_NOT_FOUND
      || mvStoreErrorCode == DataUtils.ERROR_TRANSACTION_CORRUPT;
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
