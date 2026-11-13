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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.source.PendingDownload;

public class DownloadBatch {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final List<PendingDownload> pending;
  private final Executor callbackExecutor;
  private final Optional<CompletableFuture<Void>> completion;
  private final Map<String, CompletableFuture<Void>> started = new ConcurrentHashMap<>();
  private final AtomicBoolean startCalled = new AtomicBoolean();

  DownloadBatch(List<PendingDownload> pending, Executor callbackExecutor) {
    this.pending = List.copyOf(pending);
    this.callbackExecutor = callbackExecutor;
    this.completion = pending.isEmpty() ? Optional.empty() : Optional.of(new CompletableFuture<>());
  }

  public Optional<CompletableFuture<Void>> completion() {
    return completion;
  }

  public boolean hasFailed(String artifactKey) {
    var future = started.get(artifactKey);
    return future != null && future.isCompletedExceptionally();
  }

  void start(Runnable onSomeDownloadSucceeded) {
    if (!startCalled.compareAndSet(false, true) || pending.isEmpty()) {
      return;
    }
    var logOutput = LOG.getTargetForCopy();
    var futures = pending.stream().map(this::startDownload).toList();
    var normalized = futures.stream().map(f -> f.exceptionally(e -> null)).toArray(CompletableFuture[]::new);
    var allEnded = CompletableFuture.allOf(normalized);
    allEnded.whenComplete((unused, error) -> completion.orElseThrow().complete(null));
    allEnded.thenRunAsync(() -> {
      var previousLogOutput = LOG.getTargetForCopy();
      LOG.setTarget(logOutput);
      try {
        if (futures.stream().anyMatch(f -> !f.isCompletedExceptionally())) {
          onSomeDownloadSucceeded.run();
        }
      } finally {
        LOG.setTarget(previousLogOutput);
      }
    }, callbackExecutor);
  }

  private CompletableFuture<Void> startDownload(PendingDownload download) {
    CompletableFuture<Void> future;
    try {
      future = download.start().get();
    } catch (Exception e) {
      LOG.error("Could not start download for '{}'", download.artifactKey(), e);
      future = CompletableFuture.failedFuture(e);
    }
    started.put(download.artifactKey(), future);
    return future;
  }
}
