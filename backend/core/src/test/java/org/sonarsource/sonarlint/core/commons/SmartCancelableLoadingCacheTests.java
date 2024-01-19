package org.sonarsource.sonarlint.core.commons;

import dev.failsafe.ExecutionContext;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.stubbing.Answer;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SmartCancelableLoadingCacheTests {

  public static final String ANOTHER_VALUE = "anotherValue";
  @RegisterExtension
  private static SonarLintLogTester logTester = new SonarLintLogTester(true);

  public static final String A_VALUE = "aValue";
  public static final String A_KEY = "aKey";
  private SmartCancelableLoadingCache.Listener<String, String> listener = mock(SmartCancelableLoadingCache.Listener.class);
  private BiFunction<String, SmartCancelableLoadingCache.FutureCancelChecker, String> computer = mock(BiFunction.class);
  private SmartCancelableLoadingCache<String, String> underTest = new SmartCancelableLoadingCache<>("test", computer, listener);

  @Test
  void should_cache_value_and_notify_listener_once() {
    when(computer.apply(any(), any())).thenReturn(A_VALUE);

    assertThat(underTest.get(A_KEY)).isEqualTo(A_VALUE);
    assertThat(underTest.get(A_KEY)).isEqualTo(A_VALUE);
    assertThat(underTest.get(A_KEY)).isEqualTo(A_VALUE);

    verify(listener).afterCachedValueRefreshed(A_KEY, null, A_VALUE);
    verify(computer, times(1)).apply(eq(A_KEY), any());
  }

  @Test
  void should_wait_for_long_computation() {
    when(computer.apply(any(), any())).thenAnswer(invocation -> {
      Thread.sleep(100);
      return A_VALUE;
    });

    assertThat(underTest.get(A_KEY)).isEqualTo(A_VALUE);
  }

  @Test
  void should_throw_if_failure_while_loading() {
    when(computer.apply(any(), any())).thenThrow(new RuntimeException("boom"));

    assertThrows(RuntimeException.class, () -> underTest.get(A_KEY));
    assertThrows(RuntimeException.class, () -> underTest.get(A_KEY));
    assertThrows(RuntimeException.class, () -> underTest.get(A_KEY));

    verify(computer, times(1)).apply(eq(A_KEY), any());
  }

  @Test
  void should_refresh_value() {
    when(computer.apply(any(), any()))
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
    when(computer.apply(any(), any()))
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
  void previously_queued_get_should_receive_latest_value_on_cancellation() throws InterruptedException {
    var firstComputationStarted = new CountDownLatch(1);
    var cancelled = new AtomicBoolean();
    when(computer.apply(any(), any()))
      .thenAnswer(waitingForCancellation(firstComputationStarted, cancelled))
      .thenReturn(ANOTHER_VALUE);

    // Queue a first computation
    AtomicReference<String> value = new AtomicReference<>();
    new Thread(() -> {
      value.set(underTest.get(A_KEY));
    }).start();
    firstComputationStarted.await();

    // Queue a second computation
    underTest.refreshAsync(A_KEY);

    assertThat(underTest.get(A_KEY)).isEqualTo(ANOTHER_VALUE);
    assertThat(value.get()).isEqualTo(ANOTHER_VALUE);
  }


  @Test
  void should_notify_once_in_case_of_cancellation() throws InterruptedException {
    var firstComputationStarted = new CountDownLatch(1);
    when(computer.apply(any(), any()))
      .thenAnswer(waitingForCancellation(firstComputationStarted, null))
      .thenReturn(ANOTHER_VALUE);

    underTest.refreshAsync(A_KEY);
    firstComputationStarted.await();

    underTest.refreshAsync(A_KEY);

    verify(listener, timeout(1000).times(1)).afterCachedValueRefreshed(A_KEY, null, ANOTHER_VALUE);
    verifyNoMoreInteractions(listener);
  }


  private static Answer<String> waitingForCancellation(CountDownLatch startedLatch, @Nullable AtomicBoolean wasCancelled) {
    return (Answer<String>) invocation -> {
      var cancelChecker = (SmartCancelableLoadingCache.FutureCancelChecker) invocation.getArgument(1);
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