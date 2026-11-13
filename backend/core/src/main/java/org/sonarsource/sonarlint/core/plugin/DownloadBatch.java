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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.source.PendingDownload;

public class DownloadBatch {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final List<PendingDownload> pending;
  private final Executor callbackExecutor;
  private final CompletableFuture<Void> completion;

  DownloadBatch(List<PendingDownload> pending, Executor callbackExecutor) {
    this.pending = List.copyOf(pending);
    this.callbackExecutor = callbackExecutor;
    this.completion = pending.isEmpty() ? CompletableFuture.completedFuture(null) : new CompletableFuture<>();
  }

  public CompletableFuture<Void> completion() {
    return completion;
  }

  void start(Runnable onSomeDownloadSucceeded) {
    if (pending.isEmpty()) {
      return;
    }
    var logOutput = LOG.getTargetForCopy();
    var futures = pending.stream().map(DownloadBatch::startDownload).toList();
    var normalized = futures.stream().map(f -> f.exceptionally(e -> null)).toArray(CompletableFuture[]::new);
    var allEnded = CompletableFuture.allOf(normalized);
    allEnded.thenRun(this::completeNormally);
    allEnded.thenRunAsync(() -> runCallback(logOutput, futures, onSomeDownloadSucceeded), callbackExecutor)
      .exceptionallyAsync(error -> logCallbackFailure(logOutput, error), callbackExecutor);
  }

  private void completeNormally() {
    completion.complete(null);
  }

  private static void runCallback(LogOutput logOutput, List<CompletableFuture<Void>> futures, Runnable onSomeDownloadSucceeded) {
    LOG.setTarget(logOutput);
    try {
      if (futures.stream().anyMatch(f -> !f.isCompletedExceptionally())) {
        onSomeDownloadSucceeded.run();
      }
    } finally {
      LOG.setTarget(null);
    }
  }

  private static Void logCallbackFailure(LogOutput logOutput, Throwable error) {
    LOG.setTarget(logOutput);
    try {
      LOG.error("Could not publish plugin download completion", error);
      return null;
    } finally {
      LOG.setTarget(null);
    }
  }

  private static CompletableFuture<Void> startDownload(PendingDownload download) {
    try {
      return download.starter().get();
    } catch (Exception e) {
      LOG.error("Could not start download for '{}'", download.artifactKey(), e);
      return CompletableFuture.failedFuture(e);
    }
  }
}
