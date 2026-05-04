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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactsLoadingResultTest {

  /**
   * Regression test for the infinite plugin reload loop.
   *
   * The bug: BinariesArtifactSource.downloadAndFireEvent() swallowed exceptions, so the
   * download CompletableFuture always completed normally — even on failure.
   * whenAllArtifactsDownloaded therefore always fired, publishing PluginsSynchronizedEvent,
   * which triggered ResetPluginsCommand -> cache clear -> new download -> infinite loop.
   *
   * This test simulates the buggy scenario (future completes normally despite a failed download)
   * and documents that the callback IS triggered — which is what caused the loop.
   * The actual fix is in BinariesArtifactSource (rethrowing the exception), which is tested
   * by BinariesArtifactSourceTest#load_should_fire_failed_event_on_async_download_error.
   */
  @Test
  void whenAllArtifactsDownloaded_invokes_callback_when_future_completes_normally() {
    var future = new CompletableFuture<Void>();
    var result = resultWithFuture(future);
    var callbackInvoked = new AtomicBoolean(false);

    result.whenAllArtifactsDownloaded(() -> callbackInvoked.set(true));
    future.complete(null);

    assertThat(callbackInvoked).isTrue();
  }

  @Test
  void whenAllArtifactsDownloaded_does_not_invoke_callback_when_future_completes_exceptionally() {
    var future = new CompletableFuture<Void>();
    var result = resultWithFuture(future);
    var callbackInvoked = new AtomicBoolean(false);

    result.whenAllArtifactsDownloaded(() -> callbackInvoked.set(true));
    future.completeExceptionally(new RuntimeException("SSL handshake failed"));

    assertThat(callbackInvoked).isFalse();
  }

  private static ArtifactsLoadingResult resultWithFuture(CompletableFuture<Void> future) {
    var artifact = new ResolvedArtifact(ArtifactState.DOWNLOADING, null, null, null, future);
    return new ArtifactsLoadingResult(EnumSet.of(SonarLanguage.CPP), Map.of("cpp", artifact));
  }
}
