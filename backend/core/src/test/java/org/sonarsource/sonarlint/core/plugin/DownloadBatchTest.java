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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.source.PendingDownload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

class DownloadBatchTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @BeforeEach
  void prepare_log_target() {
    SonarLintLogger.get().setTarget(logTester.getLogOutput());
  }

  @Test
  void should_invoke_callback_when_future_completes_normally() {
    var future = new CompletableFuture<Void>();
    var batch = batchWithFutures(Map.of("cpp", future));
    var callbackInvoked = new AtomicBoolean(false);

    batch.start(() -> callbackInvoked.set(true));
    assertThat(callbackInvoked).isFalse();
    future.complete(null);

    await().atMost(1, TimeUnit.SECONDS).untilTrue(callbackInvoked);
  }

  @Test
  void should_not_invoke_callback_when_all_futures_complete_exceptionally() {
    var future = new CompletableFuture<Void>();
    var batch = batchWithFutures(Map.of("cpp", future));
    var callbackInvoked = new AtomicBoolean(false);

    batch.start(() -> callbackInvoked.set(true));
    future.completeExceptionally(new RuntimeException("SSL handshake failed"));

    // Wait briefly to confirm the callback is not triggered even after the future completes
    await().during(200, TimeUnit.MILLISECONDS).atMost(1, TimeUnit.SECONDS)
      .until(() -> !callbackInvoked.get());
  }

  @Test
  void should_invoke_callback_when_at_least_one_future_succeeds() {
    var successFuture = new CompletableFuture<Void>();
    var failureFuture = new CompletableFuture<Void>();
    var batch = batchWithFutures(Map.of("cpp", successFuture, "cs", failureFuture));
    var callbackInvoked = new AtomicBoolean(false);

    batch.start(() -> callbackInvoked.set(true));
    successFuture.complete(null);
    assertThat(callbackInvoked).isFalse();
    failureFuture.completeExceptionally(new RuntimeException("network error"));

    await().atMost(1, TimeUnit.SECONDS).untilTrue(callbackInvoked);
  }

  @Test
  void should_complete_normally_even_when_download_fails() throws Exception {
    var future = new CompletableFuture<Void>();
    var batch = batchWithFutures(Map.of("cpp", future));

    var allDownloads = batch.completion();
    batch.start(() -> {});
    future.completeExceptionally(new RuntimeException("connection refused"));

    // Must not throw — SynchronizationService calls future.get() and expects normal completion
    allDownloads.get(1, TimeUnit.SECONDS);
    assertThat(allDownloads).isCompletedWithValue(null);
  }

  @Test
  void should_complete_empty_batch_without_scheduling_callback() {
    var tasks = new ArrayList<Runnable>();
    var batch = new DownloadBatch(List.of(), tasks::add);
    var callbackCount = new AtomicInteger();
    assertThat(batch.completion()).isCompletedWithValue(null);
    batch.start(callbackCount::incrementAndGet);
    assertThat(callbackCount).hasValue(0);
    assertThat(tasks).isEmpty();
  }

  @Test
  void should_expose_completion_promise_before_start() {
    var future = new CompletableFuture<Void>();
    var batch = batchWithFutures(Map.of("java", future));
    var completion = batch.completion();
    assertThat(completion).isNotDone();
    batch.start(() -> {});
    assertThat(completion).isNotDone();
    future.complete(null);
    assertThat(completion).isCompletedWithValue(null);
  }

  @Test
  void should_start_other_downloads_and_complete_when_supplier_fails() {
    var callbackCount = new AtomicInteger();
    var batch = new DownloadBatch(List.of(
      new PendingDownload("broken", DownloadBatchTest::failToStart),
      new PendingDownload("ok", () -> CompletableFuture.completedFuture(null))), Runnable::run);
    batch.start(callbackCount::incrementAndGet);
    assertThat(batch.completion()).isCompletedWithValue(null);
    assertThat(callbackCount).hasValue(1);
  }

  @Test
  void should_run_callback_on_executor_with_captured_log_target() throws Exception {
    var tasks = new ArrayList<Runnable>();
    var logger = SonarLintLogger.get();
    var output = mock(LogOutput.class);
    logger.setTarget(output);
    var batch = new DownloadBatch(List.of(new PendingDownload("java", () -> CompletableFuture.completedFuture(null))), tasks::add);
    var observed = new AtomicReference<CallbackObservation>();
    batch.start(() -> captureCallback(observed));
    logger.setTarget(null);
    assertThat(observed).hasNullValue();
    assertThat(tasks).hasSize(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var worker = executor.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
      executor.submit(tasks.getFirst()).get(5, TimeUnit.SECONDS);
      var expected = new CallbackObservation(worker, output);
      assertThat(observed).hasValue(expected);
      assertThat(worker).isNotSameAs(Thread.currentThread());
      assertThat(executor.submit(logger::getTargetForCopy).get(5, TimeUnit.SECONDS)).isNull();
    }
    assertThat(batch.completion()).isCompletedWithValue(null);
  }

  @Test
  void should_log_callback_failure_and_complete_normally() throws Exception {
    var tasks = new ArrayList<Runnable>();
    var future = new CompletableFuture<Void>();
    var batch = new DownloadBatch(List.of(new PendingDownload("java", () -> future)), tasks::add);
    batch.start(DownloadBatchTest::failCallback);
    future.complete(null);

    assertThat(tasks).hasSize(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      executor.submit(tasks.getFirst()).get(5, TimeUnit.SECONDS);
      assertThat(executor.submit(SonarLintLogger.get()::getTargetForCopy).get(5, TimeUnit.SECONDS)).isNull();
    }
    assertThat(batch.completion()).isCompletedWithValue(null);
    assertThat(logTester.logs(LogOutput.Level.ERROR)).contains("Could not publish plugin download completion")
      .anySatisfy(error -> assertThat(error).contains("IllegalStateException: callback failed"));
  }

  private static void captureCallback(AtomicReference<CallbackObservation> observed) {
    observed.set(new CallbackObservation(Thread.currentThread(), SonarLintLogger.get().getTargetForCopy()));
  }

  private static CompletableFuture<Void> failToStart() {
    throw new IllegalStateException("cannot start");
  }

  private static void failCallback() {
    throw new IllegalStateException("callback failed");
  }

  private record CallbackObservation(Thread thread, LogOutput target) {
  }

  private static DownloadBatch batchWithFutures(Map<String, CompletableFuture<Void>> futures) {
    return new DownloadBatch(futures.entrySet().stream().map(DownloadBatchTest::pendingDownload).toList(), Runnable::run);
  }
  private static PendingDownload pendingDownload(Map.Entry<String, CompletableFuture<Void>> entry) {
    return new PendingDownload(entry.getKey(), entry::getValue);
  }
}
