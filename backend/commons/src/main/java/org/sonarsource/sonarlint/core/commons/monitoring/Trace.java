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
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class Trace {

  private final ITransaction transaction;

  private Trace(ITransaction transaction) {
    this.transaction = transaction;
  }

  static Trace begin(String name, String operation) {
    return new Trace(Sentry.startTransaction(name, operation));
  }

  public static <T> T startChild(@Nullable Trace trace, String task, @Nullable String description, Supplier<T> operation) {
    if (trace == null) {
      return operation.get();
    }
    var span = new Span(trace.transaction.startChild(task, description));
    try {
      var result = operation.get();
      span.finishSuccessfully();
      return result;
    } catch (Exception exception) {
      span.finishExceptionally(exception);
      throw exception;
    }
  }

  public static void startChild(@Nullable Trace trace, String task, @Nullable String description, Runnable operation) {
    if (trace == null) {
      operation.run();
      return;
    }
    var span = new Span(trace.transaction.startChild(task, description));
    try {
      operation.run();
      span.finishSuccessfully();
    } catch (Exception exception) {
      span.finishExceptionally(exception);
      throw exception;
    }
  }

  public static void startChildren(@Nullable Trace trace, @Nullable String description, Step... steps) {
    if (trace == null) {
      Stream.of(steps).forEach(Step::execute);
      return;
    }
    Stream.of(steps).forEach(step -> step.executeTransaction(trace.transaction,  description));
  }

  public void setData(String key, Object value) {
    this.transaction.setData(key, value);
  }

  public void setThrowable(Throwable throwable) {
    this.transaction.setThrowable(throwable);
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
