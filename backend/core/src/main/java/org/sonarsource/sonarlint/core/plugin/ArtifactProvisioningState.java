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
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ArtifactsLoadingResult;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactDownload;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactLocation;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.source.DownloadBatch;
import org.sonarsource.sonarlint.core.plugin.source.DownloadOutcome;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;

/**
 * The current artifact provisioning snapshot for one standalone or connected context.
 */
public class ArtifactProvisioningState {
  private final ArtifactPlan plan;
  private final Map<String, ResolvedArtifact> resolvedArtifactsByKey = new ConcurrentHashMap<>();
  private final Map<String, ArtifactDownload> remoteDownloadsByArtifactKey;
  @Nullable
  private volatile DownloadBatch downloadBatch;

  public ArtifactProvisioningState(ArtifactPlan plan, Map<String, ArtifactDownload> remoteDownloadsByArtifactKey) {
    this.plan = plan;
    this.remoteDownloadsByArtifactKey = Map.copyOf(remoteDownloadsByArtifactKey);
    plan.selectedArtifacts().forEach((key, artifact) -> {
      if (artifact.location() instanceof ArtifactLocation.Local local) {
        resolvedArtifactsByKey.put(key, resolved(local));
      } else {
        resolvedArtifactsByKey.put(key, downloading());
      }
    });
    plan.unavailableArtifacts().forEach((key, state) -> resolvedArtifactsByKey.put(key, unresolved(state)));
  }

  public void markFailed(String artifactKey) {
    resolvedArtifactsByKey.put(artifactKey, unresolved(ArtifactState.FAILED));
  }

  public void apply(DownloadOutcome outcome) {
    remoteDownloadsByArtifactKey.forEach((artifactKey, download) -> {
      if (download.deduplicationKey().equals(outcome.download().deduplicationKey())) {
        if (outcome instanceof DownloadOutcome.Success success) {
          resolvedArtifactsByKey.put(artifactKey, resolved(success.location()));
        } else {
          resolvedArtifactsByKey.put(artifactKey, unresolved(ArtifactState.FAILED));
        }
      }
    });
  }

  public Map<String, ResolvedArtifact> resolvedArtifactsByKey() {
    return Map.copyOf(new LinkedHashMap<>(resolvedArtifactsByKey));
  }

  public ArtifactsLoadingResult asLoadingResult() {
    return new ArtifactsLoadingResult(plan.enabledLanguages(), resolvedArtifactsByKey());
  }

  public Optional<DownloadBatch> downloadBatch() {
    return Optional.ofNullable(downloadBatch);
  }

  public void setDownloadBatch(DownloadBatch downloadBatch) {
    this.downloadBatch = downloadBatch;
  }

  private static ResolvedArtifact resolved(ArtifactLocation.Local local) {
    var state = local.origin() == ArtifactOrigin.SONARQUBE_SERVER || local.origin() == ArtifactOrigin.SONARQUBE_CLOUD
      ? ArtifactState.SYNCED
      : ArtifactState.ACTIVE;
    return new ResolvedArtifact(state, local.path(), local.origin(), local.version());
  }

  private static ResolvedArtifact downloading() {
    return unresolved(ArtifactState.DOWNLOADING);
  }

  private static ResolvedArtifact unresolved(ArtifactState state) {
    return new ResolvedArtifact(state, null, null, null);
  }
}
