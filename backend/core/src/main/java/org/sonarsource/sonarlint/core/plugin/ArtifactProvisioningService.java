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
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ArtifactPlan;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactDownload;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactDownloadCoordinator;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactLocation;

/**
 * Creates and retains artifact provisioning state independently from loaded plugin instances.
 */
public class ArtifactProvisioningService {
  private static final Object STANDALONE_CONTEXT = new Object();

  private final ArtifactDownloadCoordinator downloadCoordinator;
  private final Map<Object, ArtifactProvisioningState> stateByContext = new ConcurrentHashMap<>();

  public ArtifactProvisioningService(ArtifactDownloadCoordinator downloadCoordinator) {
    this.downloadCoordinator = downloadCoordinator;
  }

  public ArtifactProvisioningState provision(@Nullable String connectionId, ArtifactPlan plan) {
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
    stateByContext.put(contextKey(connectionId), state);
    return state;
  }

  public Optional<ArtifactProvisioningState> get(@Nullable String connectionId) {
    return Optional.ofNullable(stateByContext.get(contextKey(connectionId)));
  }

  public void clear(@Nullable String connectionId) {
    stateByContext.remove(contextKey(connectionId));
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

  private static Object contextKey(@Nullable String connectionId) {
    return connectionId == null ? STANDALONE_CONTEXT : connectionId;
  }
}
