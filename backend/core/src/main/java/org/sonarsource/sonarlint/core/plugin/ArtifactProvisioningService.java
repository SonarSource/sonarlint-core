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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ArtifactPlan;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactDownload;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactDownloadCoordinator;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactLocation;
import org.sonarsource.sonarlint.core.plugin.source.DownloadBatch;
import org.sonarsource.sonarlint.core.plugin.source.DownloadOutcome;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Creates and retains artifact provisioning state independently from loaded plugin instances.
 */
public class ArtifactProvisioningService {
  private final ArtifactDownloadCoordinator downloadCoordinator;
  private final ApplicationEventPublisher eventPublisher;
  private final Map<PluginContext, CompletableFuture<ArtifactProvisioningState>> stateByContext = new ConcurrentHashMap<>();

  public ArtifactProvisioningService(ArtifactDownloadCoordinator downloadCoordinator, ApplicationEventPublisher eventPublisher) {
    this.downloadCoordinator = downloadCoordinator;
    this.eventPublisher = eventPublisher;
  }

  public ArtifactProvisioningState getOrProvision(PluginContext context, Supplier<ArtifactPlan> planSupplier) {
    var created = new AtomicBoolean();
    var stateFuture = stateByContext.computeIfAbsent(context, ignored -> {
      created.set(true);
      return new CompletableFuture<>();
    });
    if (created.get()) {
      try {
        var provisioning = prepare(planSupplier.get());
        var initialStatusesPublished = new CompletableFuture<Void>();
        // Attach the batch before exposing the state, so synchronous callers cannot miss in-progress downloads.
        // Download result notifications are gated to keep the initial DOWNLOADING status first.
        scheduleDownloads(context, provisioning, initialStatusesPublished);
        // Complete before publishing because standalone status listeners synchronously query the
        // provisioning state again while handling the event.
        stateFuture.complete(provisioning.state());
        try {
          publishStatuses(context, provisioning.state());
        } finally {
          initialStatusesPublished.complete(null);
        }
      } catch (Exception e) {
        if (!stateFuture.isDone()) {
          stateFuture.completeExceptionally(e);
        }
        stateByContext.remove(context, stateFuture);
      }
    }
    return stateFuture.join();
  }

  private Provisioning prepare(ArtifactPlan plan) {
    var downloadsByArtifactKey = remoteDownloads(plan);
    var state = new ArtifactProvisioningState(plan, downloadsByArtifactKey);
    var downloadsToSchedule = new LinkedHashMap<String, ArtifactDownload>();
    downloadsByArtifactKey.forEach((artifactKey, download) -> downloadCoordinator.getPreviousFailure(download.deduplicationKey())
      .ifPresentOrElse(failure -> state.markFailed(artifactKey), () -> downloadsToSchedule.put(download.deduplicationKey(), download)));
    return new Provisioning(state, downloadsToSchedule);
  }

  private void scheduleDownloads(PluginContext context, Provisioning provisioning, CompletableFuture<Void> initialStatusesPublished) {
    if (provisioning.downloadsToSchedule().isEmpty()) {
      return;
    }
    var scheduledBatch = downloadCoordinator.schedule(provisioning.downloadsToSchedule().values());
    var observedOutcomes = new LinkedHashMap<String, CompletableFuture<DownloadOutcome>>();
    scheduledBatch.outcomesByKey().forEach((key, outcome) -> observedOutcomes.put(key, outcome.thenCombine(initialStatusesPublished, (result, ignored) -> {
      provisioning.state().apply(result);
      publishStatuses(context, provisioning.state());
      return result;
    })));
    var observedCompletion = CompletableFuture.allOf(observedOutcomes.values().toArray(CompletableFuture[]::new))
      .thenApply(ignored -> observedOutcomes.values().stream().map(CompletableFuture::join).toList())
      .thenApply(outcomes -> {
        eventPublisher.publishEvent(new PluginsSynchronizedEvent(context.connectionId()));
        return outcomes;
      });
    provisioning.state().setDownloadBatch(new DownloadBatch(Map.copyOf(observedOutcomes), observedCompletion));
  }

  private void publishStatuses(PluginContext context, ArtifactProvisioningState state) {
    eventPublisher.publishEvent(new PluginStatusesChangedEvent(context.connectionId(), PluginStatusResolver.from(state.asLoadingResult())));
  }

  public Optional<ArtifactProvisioningState> get(PluginContext context) {
    return Optional.ofNullable(stateByContext.get(context)).map(CompletableFuture::join);
  }

  public void clear(PluginContext context) {
    stateByContext.remove(context);
  }

  private static Map<String, ArtifactDownload> remoteDownloads(ArtifactPlan plan) {
    var result = new LinkedHashMap<String, ArtifactDownload>();
    plan.selectedArtifacts().forEach((key, artifact) -> {
      if (artifact.location() instanceof ArtifactLocation.Remote remote) {
        result.put(key, remote.download());
      }
    });
    return result;
  }

  private record Provisioning(ArtifactProvisioningState state, Map<String, ArtifactDownload> downloadsToSchedule) {
  }
}
