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
package org.sonarsource.sonarlint.core.plugin.source;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactDownloadCoordinatorTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private final ExecutorService executor = Executors.newFixedThreadPool(2);
  private final ArtifactDownloadCoordinator coordinator = new ArtifactDownloadCoordinator(executor);

  @AfterEach
  void stopExecutor() {
    executor.shutdownNow();
  }

  @Test
  void should_deduplicate_concurrent_downloads_with_the_same_source_key() throws Exception {
    var started = new CountDownLatch(1);
    var proceed = new CountDownLatch(1);
    var invocationCount = new AtomicInteger();
    var first = download("shared-key", () -> {
      invocationCount.incrementAndGet();
      started.countDown();
      assertThat(proceed.await(5, TimeUnit.SECONDS)).isTrue();
      return local("first.jar");
    });
    var duplicate = download("shared-key", () -> {
      invocationCount.incrementAndGet();
      return local("duplicate.jar");
    });

    var firstBatch = coordinator.schedule(List.of(first));
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    var duplicateBatch = coordinator.schedule(List.of(duplicate));
    proceed.countDown();

    assertThat(firstBatch.completion().get(5, TimeUnit.SECONDS)).singleElement().isInstanceOf(DownloadOutcome.Success.class);
    assertThat(duplicateBatch.completion().get(5, TimeUnit.SECONDS)).singleElement().isInstanceOf(DownloadOutcome.Success.class);
    assertThat(invocationCount).hasValue(1);
  }

  @Test
  void should_retain_failures_until_explicitly_cleared() throws Exception {
    var invocationCount = new AtomicInteger();
    var failing = download("artifact-key", () -> {
      invocationCount.incrementAndGet();
      throw new IllegalStateException("download failed");
    });

    var firstOutcome = coordinator.schedule(List.of(failing)).completion().get(5, TimeUnit.SECONDS);
    var retainedOutcome = coordinator.schedule(List.of(download("artifact-key", () -> {
      invocationCount.incrementAndGet();
      return local("unexpected.jar");
    }))).completion().get(5, TimeUnit.SECONDS);

    assertThat(firstOutcome).singleElement().isInstanceOf(DownloadOutcome.Failure.class);
    assertThat(retainedOutcome).singleElement().isInstanceOf(DownloadOutcome.Failure.class);
    assertThat(coordinator.getPreviousFailure("artifact-key")).isPresent();
    assertThat(invocationCount).hasValue(1);
    assertThat(logTester.logs()).contains("Failed to download artifact");

    coordinator.clearFailure("artifact-key");
    var retryOutcome = coordinator.schedule(List.of(download("artifact-key", () -> {
      invocationCount.incrementAndGet();
      return local("retry.jar");
    }))).completion().get(5, TimeUnit.SECONDS);

    assertThat(retryOutcome).singleElement().isInstanceOf(DownloadOutcome.Success.class);
    assertThat(coordinator.getPreviousFailure("artifact-key")).isEmpty();
    assertThat(invocationCount).hasValue(2);
  }

  @Test
  void should_keep_the_calling_log_context_while_notifying_completion() throws Exception {
    var started = new CountDownLatch(1);
    var proceed = new CountDownLatch(1);
    var batch = coordinator.schedule(List.of(download("artifact-key", () -> {
      started.countDown();
      assertThat(proceed.await(5, TimeUnit.SECONDS)).isTrue();
      return local("artifact.jar");
    })));
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    var observed = batch.outcomesByKey().get("artifact-key").thenApply(outcome -> {
      SonarLintLogger.get().debug("Download completion observed");
      return outcome;
    });

    proceed.countDown();

    assertThat(observed.get(5, TimeUnit.SECONDS)).isInstanceOf(DownloadOutcome.Success.class);
    assertThat(logTester.logs()).contains("Download completion observed");
  }

  private static ArtifactDownload download(String key, DownloadAction action) {
    return new ArtifactDownload() {
      @Override
      public String deduplicationKey() {
        return key;
      }

      @Override
      public ArtifactLocation.Local download() throws Exception {
        return action.run();
      }
    };
  }

  private static ArtifactLocation.Local local(String path) {
    return new ArtifactLocation.Local(Path.of(path), ArtifactOrigin.ON_DEMAND, null);
  }

  private interface DownloadAction {
    ArtifactLocation.Local run() throws Exception;
  }
}
