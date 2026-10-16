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

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import javax.annotation.Nullable;
import org.h2.api.ErrorCode;

final class SqlExceptions {
  private SqlExceptions() {
  }

  static List<Throwable> chain(Throwable failure) {
    var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    var pending = new ArrayDeque<Throwable>();
    var result = new ArrayList<Throwable>();
    pending.add(failure);
    while (!pending.isEmpty()) {
      var current = pending.removeFirst();
      if (!seen.add(current)) {
        continue;
      }
      result.add(current);
      if (current.getCause() != null) {
        pending.addLast(current.getCause());
      }
      if (current instanceof SQLException sql && sql.getNextException() != null) {
        pending.addLast(sql.getNextException());
      }
    }
    return result;
  }

  static boolean isContention(Throwable failure) {
    return chain(failure).stream().anyMatch(t -> t instanceof SQLException sql && sql.getErrorCode() == ErrorCode.DATABASE_ALREADY_OPEN_1);
  }

  static boolean isCancellation(Throwable failure) {
    return Thread.currentThread().isInterrupted() || chain(failure).stream().anyMatch(InterruptedException.class::isInstance);
  }

  @Nullable
  static SQLException representative(Throwable failure) {
    SQLException first = null;
    SQLException firstWithCode = null;
    for (var current : chain(failure)) {
      if (current instanceof SQLException sql) {
        if (sql.getErrorCode() == ErrorCode.DATABASE_ALREADY_OPEN_1) {
          return sql;
        }
        if (first == null) {
          first = sql;
        }
        if (firstWithCode == null && sql.getErrorCode() != 0) {
          firstWithCode = sql;
        }
      }
    }
    return firstWithCode != null ? firstWithCode : first;
  }
}
