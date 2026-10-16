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

import java.io.ByteArrayInputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;
import org.h2.engine.Constants;
import org.h2.jdbcx.JdbcConnectionPool;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/** Establishes the pooled AUTO_SERVER connection before Flyway can start any migration. */
final class H2DatabaseStartup {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(10);
  private static final long INITIAL_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
  private static final long MAX_DELAY_NANOS = TimeUnit.SECONDS.toNanos(1);
  private static final int MAX_LOCK_BYTES = 16 * 1024;
  private static final Set<String> LOCK_METHODS = Set.of("file", "socket", "fs", "no");

  private final JdbcConnectionPool pool;
  private final Path lockFile;
  private final LongSupplier nanoTime;
  private final Sleeper sleeper;

  H2DatabaseStartup(JdbcConnectionPool pool, Path lockFile) {
    this(pool, lockFile, System::nanoTime, TimeUnit.NANOSECONDS::sleep);
  }

  H2DatabaseStartup(JdbcConnectionPool pool, Path lockFile, LongSupplier nanoTime, Sleeper sleeper) {
    this.pool = pool;
    this.lockFile = lockFile;
    this.nanoTime = nanoTime;
    this.sleeper = sleeper;
  }

  void connect() {
    var started = nanoTime.getAsLong();
    var attempts = 0;
    var delay = INITIAL_DELAY_NANOS;
    Exception lastFailure = null;
    LockObservation firstLock = null;
    LockObservation finalLock = null;
    while (true) {
      checkInterrupted();
      if (lastFailure != null && nanoTime.getAsLong() - started >= WINDOW_NANOS) {
        throw terminal(lastFailure, started, attempts, firstLock, finalLock);
      }
      Connection connection;
      attempts++;
      try {
        connection = pool.getConnection();
      } catch (Exception failure) {
        if (SqlExceptions.isCancellation(failure)) {
          throw cancelled(failure);
        }
        finalLock = observeLock(lockFile);
        if (firstLock == null) {
          firstLock = finalLock;
        }
        var remaining = WINDOW_NANOS - (nanoTime.getAsLong() - started);
        if (!SqlExceptions.isContention(failure) || remaining <= 0) {
          throw terminal(failure, started, attempts, firstLock, finalLock);
        }
        lastFailure = failure;
        debug("H2 startup contention after attempt {}; retrying connection acquisition", attempts);
        checkInterrupted();
        remaining = WINDOW_NANOS - (nanoTime.getAsLong() - started);
        if (remaining <= 0) {
          throw terminal(failure, started, attempts, firstLock, finalLock);
        }
        try {
          sleeper.sleep(Math.min(delay, remaining));
        } catch (InterruptedException interrupted) {
          throw cancelled(interrupted);
        }
        delay = Math.min(delay * 2, MAX_DELAY_NANOS);
        continue;
      }

      // Returning this logical connection keeps the physical connection alive in the pool.
      if (Thread.currentThread().isInterrupted()) {
        var interrupted = new InterruptedException("H2 database startup interrupted");
        try {
          connection.close();
        } catch (Exception cleanup) {
          interrupted.addSuppressed(cleanup);
        }
        throw cancelled(interrupted);
      }
      try {
        connection.close();
      } catch (Exception failure) {
        if (SqlExceptions.isCancellation(failure)) {
          throw cancelled(failure);
        }
        finalLock = observeLock(lockFile);
        throw terminal(failure, started, attempts, firstLock == null ? finalLock : firstLock, finalLock);
      }
      checkInterrupted();
      if (attempts > 1) {
        debug("H2 startup contention recovered after {} attempts in {} ms", attempts, elapsedMillis(started));
      }
      return;
    }
  }

  private RuntimeException terminal(Exception failure, long started, int attempts, @Nullable LockObservation firstLock, @Nullable LockObservation finalLock) {
    if (SqlExceptions.isCancellation(failure)) {
      return cancelled(failure);
    }
    // Diagnostic and reporting failures must never replace the database failure.
    try {
      DatabaseExceptionReporter.captureStartup(failure, new Diagnostics(Constants.FULL_VERSION, attempts, elapsedMillis(started), firstLock, finalLock));
    } catch (Exception ignored) {
      // Best effort; do not attach diagnostic exceptions that could contain sensitive paths or values.
    }
    if (SqlExceptions.isContention(failure)) {
      try {
        LOG.error("SonarQube for IDE could not open its local database because it is in use. Close other IDE instances and SonarQube for IDE backend processes, then restart your IDE.");
      } catch (Exception ignored) {
        // Preserve the original startup failure if logging is unavailable.
      }
    }
    return propagate(failure);
  }

  private static void debug(String message, Object... arguments) {
    try {
      LOG.debug(message, arguments);
    } catch (Exception ignored) {
      // Startup must not depend on diagnostic logging being available.
    }
  }

  private long elapsedMillis(long started) {
    return TimeUnit.NANOSECONDS.toMillis(Math.max(0, nanoTime.getAsLong() - started));
  }

  private static void checkInterrupted() {
    if (Thread.currentThread().isInterrupted()) {
      throw cancelled(new InterruptedException("H2 database startup interrupted"));
    }
  }

  private static RuntimeException cancelled(Exception failure) {
    Thread.currentThread().interrupt();
    return propagate(failure);
  }

  private static RuntimeException propagate(Exception failure) {
    return failure instanceof RuntimeException runtime ? runtime : new IllegalStateException("Failed to initialize H2Database", failure);
  }

  static LockObservation observeLock(Path lockFile) {
    try {
      var attributes = Files.readAttributes(lockFile, BasicFileAttributes.class);
      var age = Math.max(0, System.currentTimeMillis() - attributes.lastModifiedTime().toMillis());
      if (!Files.isReadable(lockFile)) {
        return new LockObservation("unreadable", true, false, null, null, age);
      }
      byte[] contents;
      try (var input = Files.newInputStream(lockFile)) {
        contents = input.readNBytes(MAX_LOCK_BYTES + 1);
      }
      if (contents.length > MAX_LOCK_BYTES) {
        return new LockObservation("unavailable", true, true, null, null, age);
      }
      var properties = new Properties();
      try {
        properties.load(new ByteArrayInputStream(contents));
      } catch (IllegalArgumentException malformed) {
        return new LockObservation("malformed", true, true, null, null, age);
      }
      var method = properties.getProperty("method");
      if (method == null) {
        return new LockObservation("malformed", true, true, null, properties.containsKey("server"), age);
      }
      return new LockObservation("readable", true, true, LOCK_METHODS.contains(method) ? method : "other", properties.containsKey("server"), age);
    } catch (NoSuchFileException missing) {
      return new LockObservation("missing", false, null, null, null, null);
    } catch (AccessDeniedException unreadable) {
      return new LockObservation("unreadable", null, false, null, null, null);
    } catch (Exception unavailable) {
      return new LockObservation("unavailable", null, null, null, null, null);
    }
  }

  @FunctionalInterface
  interface Sleeper {
    void sleep(long nanos) throws InterruptedException;
  }

  record Diagnostics(String h2Version, int attempts, long elapsedMillis, @Nullable LockObservation firstLock, @Nullable LockObservation finalLock) {
  }

  record LockObservation(String state, @Nullable Boolean present, @Nullable Boolean readable, @Nullable String method, @Nullable Boolean serverPresent,
    @Nullable Long ageMillis) {
  }
}
