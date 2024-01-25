/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.commons;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.stubbing.Answer;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SmartCancelableLoadingCacheTests {

  public static final String ANOTHER_VALUE = "anotherValue";
  @RegisterExtension
  private static SonarLintLogTester logTester = new SonarLintLogTester(true);

  public static final String A_VALUE = "aValue";
  public static final String A_KEY = "aKey";
  public static final String ANOTHER_KEY = "anotherKey";
  private final SmartCancelableLoadingCache.Listener<String, String> listener = mock(SmartCancelableLoadingCache.Listener.class);
  private final BiFunction<String, SonarLintCancelMonitor, String> computer = mock(BiFunction.class);
  private final SmartCancelableLoadingCache<String, String> underTest = new SmartCancelableLoadingCache<>("test", computer, listener);

  @AfterEach
  void close() {
    underTest.close();
  }

  @Test
  void should_cache_value_and_notify_listener_once() {
    when(computer.apply(eq(A_KEY), any(SonarLintCancelMonitor.class))).thenReturn(A_VALUE);

    assertThat(underTest.get(A_KEY)).isEqualTo(A_VALUE);
    assertThat(underTest.get(A_KEY)).isEqualTo(A_VALUE);
    assertThat(underTest.get(A_KEY)).isEqualTo(A_VALUE);

    verify(listener).afterCachedValueRefreshed(A_KEY, null, A_VALUE);
    verify(computer, times(1)).apply(eq(A_KEY), any());
  }

  @Test
  void should_wait_for_long_computation() {
    when(computer.apply(eq(A_KEY), any(SonarLintCancelMonitor.class))).thenAnswer(invocation -> {
      Thread.sleep(100);
      return A_VALUE;
    });

    assertThat(underTest.get(A_KEY)).isEqualTo(A_VALUE);
  }

  @Test
  void should_throw_if_failure_while_loading() {
    when(computer.apply(eq(A_KEY), any(SonarLintCancelMonitor.class))).thenThrow(new RuntimeException("boom"));

    assertThrows(RuntimeException.class, () -> underTest.get(A_KEY));
    assertThrows(RuntimeException.class, () -> underTest.get(A_KEY));
    assertThrows(RuntimeException.class, () -> underTest.get(A_KEY));

    verify(computer, times(1)).apply(eq(A_KEY), any());
    verifyNoInteractions(listener);
  }

  @Test
  void should_refresh_value() {
    when(computer.apply(eq(A_KEY), any(SonarLintCancelMonitor.class)))
      .thenReturn(A_VALUE)
      .thenReturn(ANOTHER_VALUE);

    assertThat(underTest.get(A_KEY)).isEqualTo(A_VALUE);
    verify(listener).afterCachedValueRefreshed(A_KEY, null, A_VALUE);

    underTest.refreshAsync(A_KEY);

    verify(listener, timeout(1000)).afterCachedValueRefreshed(A_KEY, A_VALUE, ANOTHER_VALUE);
    assertThat(underTest.get(A_KEY)).isEqualTo(ANOTHER_VALUE);

    verify(computer, times(2)).apply(eq(A_KEY), any());
  }

  @Test
  void should_cancel_previous_computation_on_refresh() throws InterruptedException {
    var firstComputationStarted = new CountDownLatch(1);
    var cancelled = new AtomicBoolean();
    when(computer.apply(eq(A_KEY), any(SonarLintCancelMonitor.class)))
      .thenAnswer(waitingForCancellation(firstComputationStarted, cancelled))
      .thenReturn(ANOTHER_VALUE);

    // Queue a first computation
    underTest.refreshAsync(A_KEY);
    firstComputationStarted.await();

    // Queue a second computation
    underTest.refreshAsync(A_KEY);

    assertThat(underTest.get(A_KEY)).isEqualTo(ANOTHER_VALUE);
    assertThat(cancelled.get()).isTrue();

    verify(computer, times(2)).apply(eq(A_KEY), any());
  }

  @Test
  void should_cancel_previous_computation_on_clear() throws InterruptedException {
    var firstComputationStarted = new CountDownLatch(1);
    var cancelled = new AtomicBoolean();
    when(computer.apply(eq(A_KEY), any(SonarLintCancelMonitor.class)))
      .thenAnswer(waitingForCancellation(firstComputationStarted, cancelled));

    // Queue a first computation
    underTest.refreshAsync(A_KEY);
    firstComputationStarted.await();

    underTest.clear(A_KEY);

    await().untilAsserted(() -> assertThat(cancelled.get()).isTrue());

    verify(computer, times(1)).apply(eq(A_KEY), any());
  }

  @Test
  void should_cancel_all_previous_computation_on_close() throws InterruptedException {
    var key1ComputationStarted = new CountDownLatch(1);
    var cancelledKey1 = new AtomicBoolean();
    when(computer.apply(eq(A_KEY), any(SonarLintCancelMonitor.class)))
      .thenAnswer(waitingForCancellation(key1ComputationStarted, cancelledKey1));

    // Queue a computation of key1
    underTest.refreshAsync(A_KEY);
    key1ComputationStarted.await();

    var key2ComputationStarted = new CountDownLatch(1);
    var cancelledKey2 = new AtomicBoolean();
    when(computer.apply(eq(ANOTHER_KEY), any(SonarLintCancelMonitor.class)))
      .thenAnswer(waitingForCancellation(key2ComputationStarted, cancelledKey2));

    // Queue a computation of key2, that will only start after computation of key1 because the executor service is single threaded
    underTest.refreshAsync(ANOTHER_KEY);

    underTest.close();

    await().untilAsserted(() -> assertThat(cancelledKey1.get()).isTrue());
    // Second computation was cancelled early, because calling the computer
    await().untilAsserted(() -> assertThat(key2ComputationStarted.getCount()).isEqualTo(1));

    verify(computer, times(1)).apply(eq(A_KEY), any());
    verifyNoMoreInteractions(computer);
  }

  @Test
  void previously_queued_get_should_receive_latest_value_on_cancellation() throws InterruptedException {
    var firstComputationStarted = new CountDownLatch(1);
    var cancelled = new AtomicBoolean();
    when(computer.apply(eq(A_KEY), any(SonarLintCancelMonitor.class)))
      .thenAnswer(waitingForCancellation(firstComputationStarted, cancelled))
      .thenReturn(ANOTHER_VALUE);

    // Queue a first computation
    AtomicReference<String> value = new AtomicReference<>();
    var t = new Thread(() -> {
      value.set(underTest.get(A_KEY));
    });
    t.start();
    firstComputationStarted.await();

    // Queue a second computation
    underTest.refreshAsync(A_KEY);

    assertThat(underTest.get(A_KEY)).isEqualTo(ANOTHER_VALUE);
    t.join();
    assertThat(value.get()).isEqualTo(ANOTHER_VALUE);
    assertThat(cancelled.get()).isTrue();
  }


  @Test
  void should_notify_once_in_case_of_cancellation() throws InterruptedException {
    var firstComputationStarted = new CountDownLatch(1);
    when(computer.apply(eq(A_KEY), any(SonarLintCancelMonitor.class)))
      .thenAnswer(waitingForCancellation(firstComputationStarted, null))
      .thenReturn(ANOTHER_VALUE);

    underTest.refreshAsync(A_KEY);
    firstComputationStarted.await();

    underTest.refreshAsync(A_KEY);

    verify(listener, timeout(1000).times(1)).afterCachedValueRefreshed(A_KEY, null, ANOTHER_VALUE);
    verifyNoMoreInteractions(listener);
  }


  private static Answer<String> waitingForCancellation(CountDownLatch startedLatch, @Nullable AtomicBoolean wasCancelled) {
    return invocation -> {
      var cancelChecker = (SonarLintCancelMonitor) invocation.getArgument(1);
      startedLatch.countDown();
      while (!cancelChecker.isCanceled()) {
        Thread.sleep(100);
      }
      if (wasCancelled != null) {
        wasCancelled.set(true);
      }
      throw new CancellationException();
    };
  }

}