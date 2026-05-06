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
package org.sonarsource.sonarlint.core.plugin.loading.strategy;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ArtifactsLoadingResultTest {

  @Test
  void whenAllArtifactsDownloaded_invokes_callback_when_future_completes_normally() {
    var future = new CompletableFuture<Void>();
    var result = resultWithFutures(Map.of("cpp", future));
    var callbackInvoked = new AtomicBoolean(false);

    result.whenAllArtifactsDownloaded(() -> callbackInvoked.set(true));
    future.complete(null);

    await().atMost(1, TimeUnit.SECONDS).untilTrue(callbackInvoked);
  }

  /**
   * Regression test for the infinite plugin reload loop.
   *
   * The bug: BinariesArtifactSource.downloadAndFireEvent() swallowed exceptions, so the
   * download CompletableFuture always completed normally — even on failure.
   * whenAllArtifactsDownloaded therefore always fired, publishing PluginsSynchronizedEvent,
   * which triggered ResetPluginsCommand -> cache clear -> new download -> infinite loop.
   *
   * The fix: BinariesArtifactSource now rethrows on failure, completing the future exceptionally.
   * whenAllArtifactsDownloaded skips the callback when all downloads failed.
   */
  @Test
  void whenAllArtifactsDownloaded_does_not_invoke_callback_when_all_futures_complete_exceptionally() {
    var future = new CompletableFuture<Void>();
    var result = resultWithFutures(Map.of("cpp", future));
    var callbackInvoked = new AtomicBoolean(false);

    result.whenAllArtifactsDownloaded(() -> callbackInvoked.set(true));
    future.completeExceptionally(new RuntimeException("SSL handshake failed"));

    // Wait briefly to confirm the callback is not triggered even after the future completes
    await().during(200, TimeUnit.MILLISECONDS).atMost(1, TimeUnit.SECONDS)
      .until(() -> !callbackInvoked.get());
  }

  @Test
  void whenAllArtifactsDownloaded_invokes_callback_when_at_least_one_future_succeeds() {
    var successFuture = new CompletableFuture<Void>();
    var failureFuture = new CompletableFuture<Void>();
    var result = resultWithFutures(Map.of("cpp", successFuture, "cs", failureFuture));
    var callbackInvoked = new AtomicBoolean(false);

    result.whenAllArtifactsDownloaded(() -> callbackInvoked.set(true));
    failureFuture.completeExceptionally(new RuntimeException("network error"));
    successFuture.complete(null);

    await().atMost(1, TimeUnit.SECONDS).untilTrue(callbackInvoked);
  }

  @Test
  void getAllDownloadsFuture_completes_normally_even_when_download_fails() throws Exception {
    var future = new CompletableFuture<Void>();
    var result = resultWithFutures(Map.of("cpp", future));

    var allDownloads = result.getAllDownloadsFuture().orElseThrow();
    future.completeExceptionally(new RuntimeException("connection refused"));

    // Must not throw — SynchronizationService calls future.get() and expects normal completion
    allDownloads.get(1, TimeUnit.SECONDS);
    assertThat(allDownloads).isCompletedWithValue(null);
  }

  private static ArtifactsLoadingResult resultWithFutures(Map<String, CompletableFuture<Void>> futures) {
    var artifacts = new HashMap<String, ResolvedArtifact>();
    futures.forEach((key, future) -> artifacts.put(key, new ResolvedArtifact(ArtifactState.DOWNLOADING, null, null, null, future)));
    return new ArtifactsLoadingResult(EnumSet.of(SonarLanguage.CPP), artifacts);
  }
}
