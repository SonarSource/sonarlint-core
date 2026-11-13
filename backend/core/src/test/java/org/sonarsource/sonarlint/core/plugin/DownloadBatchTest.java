/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.source.PendingDownload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DownloadBatchTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void start_invokes_callback_when_future_completes_normally() {
    var future = new CompletableFuture<Void>();
    var result = resultWithFutures(Map.of("cpp", future));
    var callbackInvoked = new AtomicBoolean(false);

    result.start(() -> callbackInvoked.set(true));
    assertThat(callbackInvoked).isFalse();
    future.complete(null);

    await().atMost(1, TimeUnit.SECONDS).untilTrue(callbackInvoked);
  }

  @Test
  void start_does_not_invoke_callback_when_all_futures_complete_exceptionally() {
    var future = new CompletableFuture<Void>();
    var result = resultWithFutures(Map.of("cpp", future));
    var callbackInvoked = new AtomicBoolean(false);

    result.start(() -> callbackInvoked.set(true));
    future.completeExceptionally(new RuntimeException("SSL handshake failed"));

    // Wait briefly to confirm the callback is not triggered even after the future completes
    await().during(200, TimeUnit.MILLISECONDS).atMost(1, TimeUnit.SECONDS)
      .until(() -> !callbackInvoked.get());
  }

  @Test
  void start_invokes_callback_when_at_least_one_future_succeeds() {
    var successFuture = new CompletableFuture<Void>();
    var failureFuture = new CompletableFuture<Void>();
    var result = resultWithFutures(Map.of("cpp", successFuture, "cs", failureFuture));
    var callbackInvoked = new AtomicBoolean(false);

    result.start(() -> callbackInvoked.set(true));
    successFuture.complete(null);
    assertThat(callbackInvoked).isFalse();
    failureFuture.completeExceptionally(new RuntimeException("network error"));

    await().atMost(1, TimeUnit.SECONDS).untilTrue(callbackInvoked);
  }

  @Test
  void completion_finishes_normally_even_when_download_fails() throws Exception {
    var future = new CompletableFuture<Void>();
    var result = resultWithFutures(Map.of("cpp", future));

    var allDownloads = result.completion().orElseThrow();
    result.start(() -> {});
    future.completeExceptionally(new RuntimeException("connection refused"));

    // Must not throw — SynchronizationService calls future.get() and expects normal completion
    allDownloads.get(1, TimeUnit.SECONDS);
    assertThat(allDownloads).isCompletedWithValue(null);
  }

  @Test
  void empty_batch_has_no_completion_or_callback() {
    var batch = new DownloadBatch(List.of(), Runnable::run);
    var callbackCount = new AtomicInteger();
    assertThat(batch.completion()).isEmpty();
    batch.start(callbackCount::incrementAndGet);
    assertThat(callbackCount).hasValue(0);
  }

  @Test
  void promise_exists_before_start_and_start_is_idempotent() {
    var calls = new AtomicInteger();
    var future = new CompletableFuture<Void>();
    var batch = new DownloadBatch(List.of(new PendingDownload("java", () -> {
      calls.incrementAndGet();
      return future;
    })), Runnable::run);
    var completion = batch.completion().orElseThrow();
    assertThat(completion).isNotDone();
    batch.start(() -> {});
    batch.start(() -> { throw new AssertionError("second callback"); });
    assertThat(calls).hasValue(1);
    assertThat(completion).isNotDone();
    future.complete(null);
    assertThat(completion).isCompletedWithValue(null);
  }

  @Test
  void supplier_failure_does_not_prevent_other_downloads_or_completion() {
    var callbackCount = new AtomicInteger();
    var batch = new DownloadBatch(List.of(
      new PendingDownload("broken", () -> { throw new IllegalStateException("cannot start"); }),
      new PendingDownload("ok", () -> CompletableFuture.completedFuture(null))), Runnable::run);
    assertThat(batch.hasFailed("broken")).isFalse();
    batch.start(callbackCount::incrementAndGet);
    assertThat(batch.hasFailed("broken")).isTrue();
    assertThat(batch.hasFailed("ok")).isFalse();
    assertThat(batch.hasFailed("unknown")).isFalse();
    assertThat(batch.completion().orElseThrow()).isCompletedWithValue(null);
    assertThat(callbackCount).hasValue(1);
  }

  @Test
  void callback_is_dispatched_separately_from_completion_and_restores_log_target() throws Exception {
    var tasks = new ArrayList<Runnable>();
    var logger = SonarLintLogger.get();
    var output = org.mockito.Mockito.mock(LogOutput.class);
    logger.setTarget(output);
    var batch = new DownloadBatch(List.of(new PendingDownload("java", () -> CompletableFuture.completedFuture(null))), tasks::add);
    var callbackThread = new AtomicReference<Thread>();
    var observedTarget = new AtomicReference<LogOutput>();
    batch.start(() -> {
      callbackThread.set(Thread.currentThread());
      observedTarget.set(logger.getTargetForCopy());
    });
    logger.setTarget(null);
    assertThat(callbackThread).hasNullValue();
    assertThat(batch.completion().orElseThrow()).isCompletedWithValue(null);
    assertThat(tasks).hasSize(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var previousTarget = org.mockito.Mockito.mock(LogOutput.class);
      executor.submit(() -> logger.setTarget(previousTarget)).get(5, TimeUnit.SECONDS);
      executor.submit(tasks.getFirst()).get(5, TimeUnit.SECONDS);
      assertThat(observedTarget).hasValue(output);
      assertThat(executor.submit(logger::getTargetForCopy).get(5, TimeUnit.SECONDS)).isSameAs(previousTarget);
    }
    assertThat(callbackThread.get()).isNotSameAs(Thread.currentThread());
  }

  private static DownloadBatch resultWithFutures(Map<String, CompletableFuture<Void>> futures) {
    return new DownloadBatch(futures.entrySet().stream().map(e -> new PendingDownload(e.getKey(), e::getValue)).toList(), Runnable::run);
  }
}
