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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ArtifactPlan;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactDownload;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactDownloadCoordinator;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactLocation;

/**
 * Creates and retains artifact provisioning state independently from loaded plugin instances.
 */
public class ArtifactProvisioningService {
  private final ArtifactDownloadCoordinator downloadCoordinator;
  private final Map<PluginContext, ArtifactProvisioningState> stateByContext = new ConcurrentHashMap<>();

  public ArtifactProvisioningService(ArtifactDownloadCoordinator downloadCoordinator) {
    this.downloadCoordinator = downloadCoordinator;
  }

  public ArtifactProvisioningState getOrProvision(PluginContext context, Supplier<ArtifactPlan> planSupplier) {
    return stateByContext.computeIfAbsent(context, ignored -> provision(planSupplier.get()));
  }

  private ArtifactProvisioningState provision(ArtifactPlan plan) {
    var downloadsByArtifactKey = remoteDownloads(plan);
    var state = new ArtifactProvisioningState(plan, downloadsByArtifactKey);
    var downloadsToSchedule = new LinkedHashMap<String, ArtifactDownload>();
    downloadsByArtifactKey.forEach((artifactKey, download) -> downloadCoordinator.getPreviousFailure(download.deduplicationKey())
      .ifPresentOrElse(failure -> state.markFailed(artifactKey), () -> downloadsToSchedule.put(download.deduplicationKey(), download)));
    if (!downloadsToSchedule.isEmpty()) {
      var batch = downloadCoordinator.schedule(downloadsToSchedule.values());
      state.setDownloadBatch(batch);
      batch.outcomesByKey().values().forEach(outcome -> outcome.thenAccept(state::apply));
    }
    return state;
  }

  public Optional<ArtifactProvisioningState> get(PluginContext context) {
    return Optional.ofNullable(stateByContext.get(context));
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
}
