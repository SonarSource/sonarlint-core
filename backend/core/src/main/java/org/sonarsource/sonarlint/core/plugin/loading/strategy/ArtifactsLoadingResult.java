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

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;

public record ArtifactsLoadingResult(Set<SonarLanguage> enabledLanguages, Map<String, ResolvedArtifact> resolvedArtifactsByKey) {
  public void whenAllArtifactsDownloaded(Runnable runnable) {
    var pendingDownloads = resolvedArtifactsByKey.values().stream().map(ResolvedArtifact::downloadFuture)
      .filter(Objects::nonNull)
      .toList();
    if (pendingDownloads.isEmpty()) {
      return;
    }
    var completableFutures = pendingDownloads.stream()
      .map(f -> CompletableFuture.runAsync(() -> {
        try {
          f.get();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
          // ignored: download errors are handled elsewhere
        }
      }))
      .toArray(CompletableFuture[]::new);
    var logOutput = SonarLintLogger.get().getTargetForCopy();
    CompletableFuture.allOf(completableFutures).thenRun(() -> {
      SonarLintLogger.get().setTarget(logOutput);
      runnable.run();
    });
  }
}
