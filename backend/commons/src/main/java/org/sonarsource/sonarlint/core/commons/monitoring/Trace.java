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
package org.sonarsource.sonarlint.core.commons.monitoring;

import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SpanStatus;

public class Trace {

  private final ITransaction transaction;

  private Trace(ITransaction transaction) {
    this.transaction = transaction;
  }

  static Trace begin(String name, String operation) {
    return new Trace(Sentry.startTransaction(name, operation));
  }

  public void setData(String key, Object value) {
    this.transaction.setData(key, value);
  }

  public void setThrowable(Throwable throwable) {
    this.transaction.setThrowable(throwable);
  }

  public Span startChild(String task, String operation) {
    return new Span(this.transaction.startChild(task, operation));
  }

  public void finishExceptionally(Throwable throwable) {
    this.transaction.setThrowable(throwable);
    this.transaction.setStatus(SpanStatus.INTERNAL_ERROR);
    this.transaction.finish();
  }

  public void finishSuccessfully() {
    this.transaction.setStatus(SpanStatus.OK);
    this.transaction.finish();
  }
}
