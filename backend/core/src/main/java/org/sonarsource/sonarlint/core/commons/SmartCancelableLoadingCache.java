package org.sonarsource.sonarlint.core.commons;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * Extend {@link com.github.benmanes.caffeine.cache.AsyncLoadingCache} to support cancellation.
 */
public class SmartCancelableLoadingCache<K, V> implements AutoCloseable {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ExecutorService executorService;
  private final String threadName;
  private final AsyncLoadingCache<K, V> caffeineCache;
  private final Listener<K, V> listener;

  public interface Listener<K, V> {

    void afterCachedValueRefreshed(K key, @Nullable V oldValue, @Nullable V newValue);

  }

  public SmartCancelableLoadingCache(String threadName, BiFunction<K, FutureCancelChecker, V> valueComputer, Listener<K, V> listener) {
    this.executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, threadName));
    this.threadName = threadName;
    this.caffeineCache = Caffeine.newBuilder()
      .executor(executorService)
      .buildAsync(new AsyncCacheLoader<>() {
        @Override
        public CompletableFuture<? extends V> asyncLoad(K key, Executor executor) throws Exception {
          return asyncLoadWithFailsafe(key, null, executor);
        }

        @Override
        public CompletableFuture<? extends V> asyncReload(K key, V oldValue, Executor executor) throws Exception {
          return asyncLoadWithFailsafe(key, oldValue, executor);
        }

        private CompletableFuture<V> asyncLoadWithFailsafe(K key, @Nullable V oldValue, Executor executor) {
          CompletableFuture<FutureCancelChecker> start = new CompletableFuture<>();
          CompletableFuture<V> result = start.thenApplyAsync(cancelChecker -> valueComputer.apply(key, cancelChecker), executor);
          start.complete(new FutureCancelChecker(result));
          result.whenCompleteAsync((newValue, error) -> {
            if (error instanceof CancellationException) {
              return;
            }
            listener.afterCachedValueRefreshed(key, oldValue, newValue);
          }, executorService);
          return result;
        }
      });
    this.listener = listener;
  }

  public static class FutureCancelChecker implements CancelChecker {

    private final CompletableFuture<?> future;

    public FutureCancelChecker(CompletableFuture<?> future) {
      this.future = future;
    }

    @Override
    public void checkCanceled() {
      if (future.isCancelled()) {
        throw new CancellationException();
      }
    }

    public void propagateCancelTo(CompletableFuture<?> downstreamFuture, boolean mayInterruptIfRunning) {
      future.whenComplete((value, error) -> {
        if (error instanceof CancellationException || future.isCancelled()) {
          downstreamFuture.cancel(mayInterruptIfRunning);
        }
      });
    }

    @Override
    public boolean isCanceled() {
      return future.isCancelled();
    }

  }

  /**
   * Clear the cached value for this key. Attempt to cancel the computation if it is still running.
   */
  public void clear(K key) {
    invalidate(key);
  }

  /**
   * Clear the cached value for this key. Attempt to cancel the ongoing computation if it is still running.
   */
  public void invalidate(K key) {
    CompletableFuture<?> cachedFuture;
    synchronized (caffeineCache) {
      cachedFuture = caffeineCache.getIfPresent(key);
      var oldValue = caffeineCache.synchronous().getIfPresent(key);
      caffeineCache.synchronous().invalidate(key);
      listener.afterCachedValueRefreshed(key, oldValue, null);
    }
    if (cachedFuture != null) {
      cachedFuture.cancel(false);
    }
  }

  /**
   * Force a new computation for this key. Ensure {@link Listener#afterCachedValueRefreshed(Object, Object, Object)} is called.
   */
  public void refreshAsync(K key) {
    CompletableFuture<?> previousFuture;
    synchronized (caffeineCache) {
      previousFuture = caffeineCache.getIfPresent(key);
      caffeineCache.synchronous().refresh(key);
    }
    if (previousFuture != null) {
      previousFuture.cancel(false);
    }

  }

  public V get(K key) {
    return caffeineCache.synchronous().get(key);
  }

  @Override
  public void close() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop " + threadName + " executor service in a timely manner");
    }
  }

}
