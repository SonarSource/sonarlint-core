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

import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.sql.DataSource;
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
  public static final String STARTUP_LOCK_FILENAME = SQ_IDE_DB_FILENAME + ".startup.lock";
  private static final Duration TURN_TIMEOUT = Duration.ofSeconds(30);
  // A file lock is held by the whole process, so threads of the same process take turns separately
  private static final ReentrantLock TURN_IN_THIS_PROCESS = new ReentrantLock();
  private static final int LOCK_CONTENTION_RETRIES = 3;
  // Reasons given by H2 when several processes run its lock file protocol at the same time. They are not translated.
  private static final List<String> LOCK_RACE_REASONS = List.of("Lock file recently modified", "Another process was faster", "Concurrent update");
  private static final Pattern MVSTORE_ERROR_CODE_PATTERN = Pattern.compile("\\[\\d++\\.\\d++\\.\\d++/(\\d++)]");

  private final Path dbFile;
  private final JdbcConnectionPool dataSource;
  private final DSLContext dsl;

  public SonarLintDatabase(Path storageRoot) {
    this.dbFile = storageRoot.resolve("h2").resolve(SQ_IDE_DB_FILENAME + ".mv.db").toAbsolutePath();
    var baseDir = storageRoot.resolve("h2");
    JdbcConnectionPool ds;
    try {
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
    var reconnecting = new ReconnectingDataSource(ds, baseDir);

    try {
      // Retry here because Flyway's connection retries discard interruption.
      connectInTurn(ds, baseDir);
      var flyway = Flyway.configure()
        .dataSource(reconnecting)
        .locations("classpath:db/migration")
        .defaultSchema("PUBLIC")
        .schemas("PUBLIC")
        .createSchemas(true)
        .baselineOnMigrate(true)
        .failOnMissingLocations(false)
        .load();
      flyway.migrate();

      System.setProperty("org.jooq.no-tips", "true");
      System.setProperty("org.jooq.no-logo", "true");

      var jooqConfig = new DefaultConfiguration()
        .set(reconnecting)
        .set(SQLDialect.H2)
        .set(new Settings().withExecuteLogging(false))
        .set(new DefaultExecuteListenerProvider(new JooqDatabaseExceptionListener()));
      this.dsl = DSL.using(jooqConfig);
    } catch (Exception failure) {
      // Capture cancellation before cleanup can clear the flag, so cancelled startup is never reported.
      var cancelled = Thread.currentThread().isInterrupted() || ExceptionUtils.indexOfType(failure, InterruptedException.class) >= 0;
      disposeAfterFailure(ds, failure, cancelled);
      if (!cancelled) {
        DatabaseExceptionReporter.capture(failure, "startup", "h2.initialize");
        logIfCorrupted(failure);
      }
      throw failure instanceof RuntimeException runtime ? runtime : new IllegalStateException("Failed to initialize H2Database", failure);
    }
  }

  private static void disposeAfterFailure(JdbcConnectionPool pool, Exception failure, boolean cancelled) {
    try {
      pool.dispose();
    } catch (Exception cleanup) {
      if (cleanup != failure) {
        failure.addSuppressed(cleanup);
      }
    } finally {
      // H2 may clear interruption while joining its file-lock watchdog during disposal.
      if (cancelled) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * H2 decides which process owns the database through a protocol on its lock file, and connects the other processes through the owner. When three or more processes
   * run that protocol at the same time, e.g. when several IDE windows are restored together, they disturb each other and some fail. Opening the database one process at
   * a time avoids it. If taking turns is not possible, e.g. on a file system without locks, the database is opened anyway.
   */
  private static void connectInTurn(JdbcConnectionPool pool, Path baseDir) throws SQLException, InterruptedException {
    // Release the logical connection back to the pool, keeping the physical connection open.
    // Always release it on cancellation; do not retry acquisition if close() fails.
    // A new pool has no connection to discard, so a lost connection is not retried
    try (var connection = connectionInTurn(pool, baseDir, 0)) {
      checkNotInterrupted();
    }
    checkNotInterrupted();
  }

  private static Connection connectionInTurn(JdbcConnectionPool pool, Path baseDir, int lostConnectionRetries) throws SQLException, InterruptedException {
    // No timeout needed: another opening in this process only holds it while waiting for its own turn, which times out, and connecting
    TURN_IN_THIS_PROCESS.lockInterruptibly();
    try (var turn = Turn.take(baseDir)) {
      // The turn ends once the database is open
      return connection(pool, lostConnectionRetries);
    } finally {
      TURN_IN_THIS_PROCESS.unlock();
    }
  }

  private static Connection connection(JdbcConnectionPool pool, int lostConnectionRetries) throws SQLException, InterruptedException {
    var lockContentionRetries = 0;
    var discardedConnections = 0;
    while (true) {
      checkNotInterrupted();
      try {
        return pool.getConnection();
      } catch (SQLException failure) {
        if (isLostConnection(failure) && discardedConnections < lostConnectionRetries) {
          // The pool discards a connection that fails, so the next one is either another pooled connection or a new one
          discardedConnections++;
        } else if (isLockContention(failure) && lockContentionRetries < LOCK_CONTENTION_RETRIES) {
          lockContentionRetries++;
          // Another H2 owner may hold the file lock before its AUTO_SERVER is ready.
          Thread.sleep(1000);
        } else {
          throw failure;
        }
      }
    }
  }

  private static void checkNotInterrupted() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("Opening the H2 database was interrupted");
    }
  }

  private static boolean isLockContention(SQLException failure) {
    return failure.getErrorCode() == ErrorCode.DATABASE_ALREADY_OPEN_1
      || (failure.getErrorCode() == ErrorCode.ERROR_OPENING_DATABASE_1 && LOCK_RACE_REASONS.stream().anyMatch(reason -> StringUtils.contains(failure.getMessage(), reason)));
  }

  /**
   * The connection was to another process that served the database and has exited, and could not be reconnected
   */
  private static boolean isLostConnection(SQLException failure) {
    return failure.getErrorCode() == ErrorCode.CONNECTION_BROKEN_1 || failure.getErrorCode() == ErrorCode.DATABASE_CALLED_AT_SHUTDOWN;
  }

  /**
   * The turn of this process to open the database. Taking it is not always possible, the database is then opened anyway.
   */
  private static final class Turn implements AutoCloseable {
    @Nullable
    private final FileChannel startupLock;
    @Nullable
    private final FileLock lock;

    private Turn(@Nullable FileChannel startupLock, @Nullable FileLock lock) {
      this.startupLock = startupLock;
      this.lock = lock;
    }

    static Turn take(Path baseDir) throws InterruptedException {
      var startupLock = openStartupLock(baseDir.resolve(STARTUP_LOCK_FILENAME));
      try {
        return new Turn(startupLock, waitForTurn(startupLock));
      } catch (InterruptedException e) {
        closeQuietly(startupLock);
        throw e;
      }
    }

    @CheckForNull
    private static FileChannel openStartupLock(Path lockFile) {
      try {
        return FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      } catch (IOException e) {
        LOG.debug("Cannot wait for other IDE instances to open the database, opening it anyway: {}", e.getMessage());
        return null;
      }
    }

    @CheckForNull
    private static FileLock waitForTurn(@Nullable FileChannel startupLock) throws InterruptedException {
      if (startupLock == null) {
        return null;
      }
      var deadline = System.nanoTime() + TURN_TIMEOUT.toNanos();
      try {
        var turn = startupLock.tryLock();
        while (turn == null && System.nanoTime() < deadline) {
          Thread.sleep(50);
          turn = startupLock.tryLock();
        }
        if (turn == null) {
          LOG.debug("Timed out waiting for other IDE instances to open the database, opening it anyway");
        }
        return turn;
      } catch (ClosedByInterruptException e) {
        throw new InterruptedException("Opening the H2 database was interrupted");
      } catch (IOException | OverlappingFileLockException e) {
        LOG.debug("Cannot wait for other IDE instances to open the database, opening it anyway: {}", e.toString());
        return null;
      }
    }

    /**
     * Only called once the database is open, or failed to open, so failing to end the turn does not matter
     */
    @Override
    public void close() {
      if (lock != null) {
        try {
          lock.close();
        } catch (IOException e) {
          LOG.debug("Error while releasing the database startup lock: {}", e.getMessage());
        }
      }
      closeQuietly(startupLock);
    }

    private static void closeQuietly(@Nullable FileChannel startupLock) {
      if (startupLock != null) {
        try {
          startupLock.close();
        } catch (IOException e) {
          LOG.debug("Error while releasing the database startup lock: {}", e.getMessage());
        }
      }
    }
  }

  /**
   * When the process that served the database to the others exits, H2 reconnects each pooled connection on its next use, and runs the lock file protocol to replace the
   * owner. As all the processes do it at the same time, some fail like at startup. The pool discards the connection that failed, so get another one in turn instead of
   * failing the request.
   */
  private static final class ReconnectingDataSource implements DataSource {
    private final JdbcConnectionPool pool;
    private final Path baseDir;

    private ReconnectingDataSource(JdbcConnectionPool pool, Path baseDir) {
      this.pool = pool;
      this.baseDir = baseDir;
    }

    @Override
    public Connection getConnection() throws SQLException {
      try {
        return pool.getConnection();
      } catch (SQLException failure) {
        if (!isLostConnection(failure) && !isLockContention(failure)) {
          throw failure;
        }
        LOG.debug("Lost the connection to the database, reconnecting: {}", failure.getMessage());
        return reconnect(failure);
      }
    }

    private Connection reconnect(SQLException failure) throws SQLException {
      try {
        // Each pooled connection may have been lost
        return connectionInTurn(pool, baseDir, pool.getMaxConnections());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        failure.addSuppressed(e);
        throw failure;
      } catch (SQLException retryFailure) {
        retryFailure.addSuppressed(failure);
        throw retryFailure;
      }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return pool.getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() {
      return pool.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) {
      pool.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) {
      pool.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() {
      return pool.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() {
      return pool.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
      return pool.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
      return pool.isWrapperFor(iface);
    }
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
    return ExceptionUtils.getThrowableList(throwable).stream().anyMatch(cause -> isMVStoreCorruption(cause)
      || isFileCorruption(cause)
      || isWrappedMVStoreCorruption(cause));
  }

  private static boolean isMVStoreCorruption(Throwable throwable) {
    return throwable instanceof MVStoreException mvStoreException
      && (isCorruptionCode(mvStoreException.getErrorCode()) || isTruncation(mvStoreException));
  }

  private static boolean isFileCorruption(Throwable throwable) {
    return throwable instanceof SQLException sqlException && sqlException.getErrorCode() == ErrorCode.FILE_CORRUPTED_1;
  }

  private static boolean isWrappedMVStoreCorruption(Throwable throwable) {
    return throwable instanceof SQLException sqlException
      && sqlException.getErrorCode() == ErrorCode.GENERAL_ERROR_1
      && hasMVStoreCorruptionCode(sqlException.getMessage());
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
   * Reading past the end of the file means it is shorter than its headers say, e.g. it was cut short or incompletely synced. Other read failures can happen with a
   * healthy file, e.g. when the thread is interrupted.
   */
  private static boolean isTruncation(MVStoreException mvStoreException) {
    return mvStoreException.getErrorCode() == DataUtils.ERROR_READING_FAILED && ExceptionUtils.indexOfType(mvStoreException, EOFException.class) >= 0;
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
