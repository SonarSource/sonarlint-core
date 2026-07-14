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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Owns asynchronous artifact download execution and cross-context deduplication.
 */
public class ArtifactDownloadCoordinator {
  private final ExecutorService executor;
  private final Map<String, CompletableFuture<DownloadOutcome>> inProgressByKey = new ConcurrentHashMap<>();
  private final Map<String, DownloadOutcome.Failure> failuresByKey = new ConcurrentHashMap<>();

  public ArtifactDownloadCoordinator(@Qualifier("pluginDownloadExecutor") ExecutorService executor) {
    this.executor = executor;
  }

  public DownloadBatch schedule(Collection<ArtifactDownload> downloads) {
    var outcomes = new LinkedHashMap<String, CompletableFuture<DownloadOutcome>>();
    for (var download : downloads) {
      outcomes.computeIfAbsent(download.deduplicationKey(), key -> schedule(download));
    }
    var allCompleted = CompletableFuture.allOf(outcomes.values().toArray(CompletableFuture[]::new))
      .thenApply(ignored -> outcomes.values().stream().map(CompletableFuture::join).toList());
    return new DownloadBatch(Map.copyOf(outcomes), allCompleted);
  }

  public Optional<DownloadOutcome.Failure> getPreviousFailure(String deduplicationKey) {
    return Optional.ofNullable(failuresByKey.get(deduplicationKey));
  }

  public void clearFailure(String deduplicationKey) {
    failuresByKey.remove(deduplicationKey);
  }

  private CompletableFuture<DownloadOutcome> schedule(ArtifactDownload download) {
    var key = download.deduplicationKey();
    var previousFailure = failuresByKey.get(key);
    if (previousFailure != null) {
      return CompletableFuture.completedFuture(previousFailure);
    }
    var future = inProgressByKey.computeIfAbsent(key, ignored -> start(download));
    return future.whenComplete((outcome, error) -> inProgressByKey.remove(key, future));
  }

  private CompletableFuture<DownloadOutcome> start(ArtifactDownload download) {
    var logOutput = SonarLintLogger.get().getTargetForCopy();
    return CompletableFuture.supplyAsync(() -> {
      SonarLintLogger.get().setTarget(logOutput);
      try {
        var outcome = new DownloadOutcome.Success(download, download.download());
        failuresByKey.remove(download.deduplicationKey());
        return outcome;
      } catch (Exception e) {
        var outcome = new DownloadOutcome.Failure(download, e);
        failuresByKey.put(download.deduplicationKey(), outcome);
        return outcome;
      } finally {
        SonarLintLogger.get().setTarget(null);
      }
    }, executor);
  }
}
