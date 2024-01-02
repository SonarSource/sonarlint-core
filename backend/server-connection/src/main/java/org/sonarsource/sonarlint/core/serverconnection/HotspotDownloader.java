/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots;

import static java.util.function.Predicate.not;

public class HotspotDownloader {

  private final Set<Language> enabledLanguages;

  public HotspotDownloader(Set<Language> enabledLanguages) {
    this.enabledLanguages = enabledLanguages;
  }

  /**
   * Fetch all hotspots of the project with specified key, using new SQ 10.1 api/issues/pull
   *
   * @param projectKey project key
   * @param branchName name of the branch.
   * @return List of hotspots. It can be empty but never null.
   */
  public PullResult downloadFromPull(HotspotApi hotspotApi, String projectKey, String branchName, Optional<Instant> lastSync) {
    var apiResult = hotspotApi.pullHotspots(projectKey, branchName, enabledLanguages, lastSync.map(Instant::toEpochMilli).orElse(null));
    var changedHotspots = apiResult.getHotspots()
      .stream()
      .filter(not(Hotspots.HotspotLite::getClosed))
      .map(HotspotDownloader::convertLiteHotspot)
      .collect(Collectors.toList());
    var closedIssueKeys = apiResult.getHotspots()
      .stream()
      .filter(Hotspots.HotspotLite::getClosed)
      .map(Hotspots.HotspotLite::getKey)
      .collect(Collectors.toSet());

    return new PullResult(Instant.ofEpochMilli(apiResult.getTimestamp().getQueryTimestamp()), changedHotspots, closedIssueKeys);
  }

  private static ServerHotspot convertLiteHotspot(Hotspots.HotspotLite liteHotspotFromWs) {
    var creationDate = Instant.ofEpochMilli(liteHotspotFromWs.getCreationDate());
    return new ServerHotspot(
      liteHotspotFromWs.getKey(),
      liteHotspotFromWs.getRuleKey(),
      liteHotspotFromWs.getMessage(),
      Path.of(liteHotspotFromWs.getFilePath()),
      toServerHotspotTextRange(liteHotspotFromWs.getTextRange()),
      creationDate,
      fromHotspotLite(liteHotspotFromWs),
      VulnerabilityProbability.valueOf(liteHotspotFromWs.getVulnerabilityProbability()),
      liteHotspotFromWs.getAssignee()
    );
  }

  private static HotspotReviewStatus fromHotspotLite(Hotspots.HotspotLite hotspot) {
    var status = hotspot.getStatus();
    var resolution = hotspot.hasResolution() ? hotspot.getResolution() : null;
    return HotspotReviewStatus.fromStatusAndResolution(status, resolution);
  }

  private static TextRangeWithHash toServerHotspotTextRange(Hotspots.TextRange textRange) {
    return new TextRangeWithHash(
      textRange.getStartLine(),
      textRange.getStartLineOffset(),
      textRange.getEndLine(),
      textRange.getEndLineOffset(),
      textRange.getHash()
    );
  }

  public static class PullResult {
    private final Instant queryTimestamp;
    private final List<ServerHotspot> changedHotspots;
    private final Set<String> closedHotspotKeys;

    public PullResult(Instant queryTimestamp, List<ServerHotspot> changedHotspots, Set<String> closedHotspotKeys) {
      this.queryTimestamp = queryTimestamp;
      this.changedHotspots = changedHotspots;
      this.closedHotspotKeys = closedHotspotKeys;
    }

    public Instant getQueryTimestamp() {
      return queryTimestamp;
    }

    public List<ServerHotspot> getChangedHotspots() {
      return changedHotspots;
    }

    public Set<String> getClosedHotspotKeys() {
      return closedHotspotKeys;
    }
  }

}
