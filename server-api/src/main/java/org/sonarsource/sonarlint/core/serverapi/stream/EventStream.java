/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.stream;

import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpConnectionListener;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

import static java.util.concurrent.TimeUnit.SECONDS;

public class EventStream {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final Integer UNAUTHORIZED = 401;
  private static final Integer FORBIDDEN = 403;
  private static final Integer NOT_FOUND = 404;
  private static final long HEART_BEAT_PERIOD = 60;

  private final ServerApiHelper helper;
  private final ScheduledExecutorService executor;
  private final AtomicReference<HttpClient.AsyncRequest> currentRequest = new AtomicReference<>();
  private final AtomicReference<ScheduledFuture<?>> pendingFuture = new AtomicReference<>();
  private final Consumer<Event> eventConsumer;

  public EventStream(ServerApiHelper helper, Consumer<Event> eventConsumer) {
    this(helper, eventConsumer, Executors.newScheduledThreadPool(1));
  }

  EventStream(ServerApiHelper helper, Consumer<Event> eventConsumer, ScheduledExecutorService executor) {
    this.helper = helper;
    this.eventConsumer = eventConsumer;
    this.executor = executor;
  }

  public EventStream connect(String wsPath) {
    return connect(wsPath, new Attempt());
  }

  private EventStream connect(String wsPath, Attempt currentAttempt) {
    LOG.debug("Connecting to server event-stream at '" + wsPath + "'...");
    var eventBuffer = new EventBuffer();
    currentRequest.set(helper.getEventStream(wsPath,
      new HttpConnectionListener() {
        @Override
        public void onConnected() {
          LOG.debug("Connected to server event-stream");
          schedule(() -> connect(wsPath), HEART_BEAT_PERIOD * 3);
        }

        @Override
        public void onError(@Nullable Integer responseCode) {
          handleError(wsPath, currentAttempt, responseCode);
        }

        @Override
        public void onClosed() {
          cancelPendingFutureIfAny();
          // reconnect instantly (will also reset attempt parameters)
          LOG.debug("Disconnected from server event-stream, reconnecting now");
          connect(wsPath);
        }
      },
      message -> {
        cancelPendingFutureIfAny();
        eventBuffer.append(message)
          .drainCompleteEvents()
          .forEach(stringEvent -> eventConsumer.accept(EventParser.parse(stringEvent)));
      }));
    return this;
  }

  private void handleError(String wsPath, Attempt currentAttempt, @Nullable Integer responseCode) {
    if (shouldRetry(responseCode)) {
      if (!currentAttempt.isMax()) {
        var retryDelay = currentAttempt.delay;
        var msgBuilder = new StringBuilder();
        msgBuilder.append("Cannot connect to server event-stream");
        if (responseCode != null) {
          msgBuilder.append(" (").append(responseCode).append(")");
        }
        msgBuilder.append(", retrying in ").append(retryDelay).append("s");
        LOG.debug(msgBuilder.toString());
        schedule(() -> connect(wsPath, currentAttempt.next()), retryDelay);
      } else {
        LOG.debug("Cannot connect to server event-stream, stop retrying");
      }
    }
  }

  private static boolean shouldRetry(@Nullable Integer responseCode) {
    if (UNAUTHORIZED.equals(responseCode)) {
      LOG.debug("Cannot connect to server event-stream, unauthorized");
      return false;
    }
    if (FORBIDDEN.equals(responseCode)) {
      LOG.debug("Cannot connect to server event-stream, forbidden");
      return false;
    }
    if (NOT_FOUND.equals(responseCode)) {
      // the API is not supported (probably an old SQ or SC)
      LOG.debug("Server events not supported by the server");
      return false;
    }
    return true;
  }

  private void schedule(Runnable task, long delayInSeconds) {
    if (!executor.isShutdown()) {
      pendingFuture.set(executor.schedule(task, delayInSeconds, SECONDS));
    }
  }

  public void close() {
    cancelPendingFutureIfAny();
    var currentRequestOrNull = currentRequest.getAndSet(null);
    if (currentRequestOrNull != null) {
      currentRequestOrNull.cancel();
    }
    if (!MoreExecutors.shutdownAndAwaitTermination(executor, 5, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop event stream executor service in a timely manner");
    }
  }

  private void cancelPendingFutureIfAny() {
    var pendingFutureOrNull = pendingFuture.getAndSet(null);
    if (pendingFutureOrNull != null) {
      pendingFutureOrNull.cancel(true);
    }
  }

  private static class Attempt {
    private static final int DEFAULT_DELAY_S = 60;
    private static final int BACK_OFF_MULTIPLIER = 2;
    private static final int MAX_ATTEMPTS = 10;

    private final long delay;
    private final int attemptNumber;

    public Attempt() {
      this(DEFAULT_DELAY_S, 1);
    }

    public Attempt(long delay, int attemptNumber) {
      this.delay = delay;
      this.attemptNumber = attemptNumber;
    }

    public Attempt next() {
      return new Attempt(delay * BACK_OFF_MULTIPLIER, attemptNumber + 1);
    }

    public boolean isMax() {
      return attemptNumber == MAX_ATTEMPTS;
    }
  }
}
