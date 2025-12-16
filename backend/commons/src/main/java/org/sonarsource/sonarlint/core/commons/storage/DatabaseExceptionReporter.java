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

import io.sentry.Sentry;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * Central utility for reporting database exceptions to Sentry with relevant context.
 * Includes simple message-hash deduplication (60 min window) to avoid flooding Sentry.
 */
public final class DatabaseExceptionReporter {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  static final String DEDUP_WINDOW_PROPERTY = "sonarlint.internal.db.dedupWindowMs";
  private static final long DEFAULT_DEDUP_WINDOW_MS = 60 * 60 * 1000L; // 60 minutes

  private static final Map<Integer, Long> recentMessageHashes = new ConcurrentHashMap<>();

  private DatabaseExceptionReporter() {
  }

  /**
   * Captures a database exception and reports it to Sentry with contextual tags.
   *
   * @param exception the exception to report
   * @param phase     the phase where the exception occurred (e.g., "startup", "runtime", "shutdown")
   * @param operation the specific operation (e.g., "h2.pool.create", "flyway.migrate", "jooq.execute")
   * @param sql       optional SQL statement that caused the exception
   */
  public static void capture(Throwable exception, String phase, String operation, @Nullable String sql) {
    var message = exception.getMessage();

    if (message != null && isDuplicate(message.hashCode())) {
      LOG.debug("Skipping duplicate database exception report: {} / {}", phase, operation);
      return;
    }

    LOG.debug("Reporting database exception to Sentry: {} / {}", phase, operation);

    Sentry.captureException(exception, scope -> {
      scope.setTag("component", "database");
      scope.setTag("db.phase", phase);
      scope.setTag("db.operation", operation);

      if (exception instanceof SQLException sqlException) {
        var sqlState = sqlException.getSQLState();
        var errorCode = sqlException.getErrorCode();
        if (sqlState != null) {
          scope.setTag("db.sqlState", sqlState);
        }
        scope.setTag("db.errorCode", String.valueOf(errorCode));
      }

      if (sql != null && !sql.isEmpty()) {
        scope.setExtra("db.sql", truncateSql(sql));
      }
    });

    if (message != null) {
      recordException(message.hashCode());
    }
  }

  public static void capture(Throwable exception, String phase, String operation) {
    capture(exception, phase, operation, null);
  }

  private static boolean isDuplicate(int messageHash) {
    var now = System.currentTimeMillis();
    cleanupOldEntries(now);
    var lastReported = recentMessageHashes.get(messageHash);
    return lastReported != null;
  }

  private static void recordException(int messageHash) {
    recentMessageHashes.put(messageHash, System.currentTimeMillis());
  }

  private static void cleanupOldEntries(long now) {
    recentMessageHashes.entrySet().removeIf(entry -> (now - entry.getValue()) > getDedupWindowMs());
  }

  private static long getDedupWindowMs() {
    var property = System.getProperty(DEDUP_WINDOW_PROPERTY);
    if (property != null) {
      try {
        return Long.parseLong(property);
      } catch (NumberFormatException e) {
        // ignore, use default
      }
    }
    return DEFAULT_DEDUP_WINDOW_MS;
  }

  private static String truncateSql(String sql) {
    var maxLength = 1000;
    if (sql.length() <= maxLength) {
      return sql;
    }
    return sql.substring(0, maxLength) + "... [truncated]";
  }

  // Visible for testing
  static void clearRecentExceptions() {
    recentMessageHashes.clear();
  }

  // Visible for testing
  static int getRecentExceptionsCount() {
    return recentMessageHashes.size();
  }
}
