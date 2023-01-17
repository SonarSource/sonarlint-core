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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.http.HttpConnectionListener;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level.DEBUG;

public class EventStream {
  private static final Integer UNAUTHORIZED = 401;
  private static final Integer FORBIDDEN = 403;
  private static final Integer NOT_FOUND = 404;
  private static final long HEART_BEAT_PERIOD = 60;

  private final ServerApiHelper helper;
  private Consumer<Event> eventConsumer;

  private final ScheduledExecutorService executor;
  private final AtomicReference<HttpClient.AsyncRequest> currentRequest = new AtomicReference<>();
  private final AtomicReference<ScheduledFuture<?>> pendingFuture = new AtomicReference<>();

  public EventStream(ServerApiHelper helper) {
    this(helper, Executors.newScheduledThreadPool(1));
  }

  EventStream(ServerApiHelper helper, ScheduledExecutorService executor) {
    this.helper = helper;
    this.executor = executor;
  }

  public EventStream onEvent(Consumer<Event> eventConsumer) {
    this.eventConsumer = eventConsumer;
    return this;
  }

  public EventStream connect(String wsPath, ClientLogOutput clientLogOutput) {
    return connect(wsPath, clientLogOutput, new Attempt());
  }

  private EventStream connect(String wsPath, ClientLogOutput clientLogOutput, Attempt currentAttempt) {
    clientLogOutput.log("Connecting to server event-stream at '" + wsPath + "'...", DEBUG);
    var eventBuffer = new EventBuffer();
    currentRequest.set(helper.getEventStream(wsPath,
      new HttpConnectionListener() {
        @Override
        public void onConnected() {
          clientLogOutput.log("Connected to server event-stream", DEBUG);
          schedule(() -> connect(wsPath, clientLogOutput), HEART_BEAT_PERIOD * 3);
        }

        @Override
        public void onError(@Nullable Integer responseCode) {
          handleError(wsPath, clientLogOutput, currentAttempt, responseCode);
        }

        @Override
        public void onClosed() {
          pendingFuture.get().cancel(true);
          // reconnect instantly (will also reset attempt parameters)
          clientLogOutput.log("Disconnected from server event-stream, reconnecting now", DEBUG);
          connect(wsPath, clientLogOutput);
        }
      },
      message -> {
        pendingFuture.get().cancel(true);
        eventBuffer.append(message)
          .drainCompleteEvents()
          .forEach(stringEvent -> eventConsumer.accept(EventParser.parse(stringEvent)));
      }));
    return this;
  }

  private void handleError(String wsPath, ClientLogOutput clientLogOutput, Attempt currentAttempt, @Nullable Integer responseCode) {
    if (shouldRetry(responseCode, clientLogOutput)) {
      if (!currentAttempt.isMax()) {
        var retryDelay = currentAttempt.delay;
        clientLogOutput.log("Cannot connect to server event-stream, retrying in " + retryDelay + "s", DEBUG);
        schedule(() -> connect(wsPath, clientLogOutput, currentAttempt.next()), retryDelay);
      } else {
        clientLogOutput.log("Cannot connect to server event-stream, stop retrying", DEBUG);
      }
    }
  }

  private static boolean shouldRetry(@Nullable Integer responseCode, ClientLogOutput clientLogOutput) {
    if (UNAUTHORIZED.equals(responseCode)) {
      clientLogOutput.log("Cannot connect to server event-stream, unauthorized", DEBUG);
      return false;
    }
    if (FORBIDDEN.equals(responseCode)) {
      clientLogOutput.log("Cannot connect to server event-stream, forbidden", DEBUG);
      return false;
    }
    if (NOT_FOUND.equals(responseCode)) {
      // the API is not supported (probably an old SQ or SC)
      clientLogOutput.log("Server events not supported by the server", DEBUG);
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
    if (pendingFuture.get() != null) {
      pendingFuture.get().cancel(true);
    }
    if (currentRequest.get() != null) {
      currentRequest.get().cancel();
    }
    executor.shutdownNow();
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
