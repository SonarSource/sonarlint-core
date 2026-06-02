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
package org.sonarsource.sonarlint.core.sca;

import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectIssue;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectRelease;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;

public class DependencyRiskMerger {
  private final DependencyRiskDtoMapper dependencyRiskDtoMapper;

  public DependencyRiskMerger(DependencyRiskDtoMapper dependencyRiskDtoMapper) {
    this.dependencyRiskDtoMapper = dependencyRiskDtoMapper;
  }

  public List<DependencyRiskDto> merge(List<DependencyRiskDto> serverRisks, AnalyzeProjectResponse localAnalysis) {
    return mergeRisks(serverRisks, localAnalysis.releases());
  }

  public List<DependencyRiskDto> enrichServerRisks(List<DependencyRiskDto> serverRisks, AnalyzeProjectResponse localAnalysis) {
    return enrichServerRisks(serverRisks, localAnalysis.releases());
  }

  public DependencyRiskDelta computeDelta(List<DependencyRiskDto> newRisks) {
    return new DependencyRiskDelta(
      newRisks.stream()
        .filter(risk -> risk.getPresence() == DependencyRiskDto.Presence.LOCAL_ONLY)
        .toList(),
      newRisks.stream()
        .filter(risk -> risk.getPresence() == DependencyRiskDto.Presence.SERVER_AND_LOCAL)
        .toList());
  }

  private List<DependencyRiskDto> mergeRisks(List<DependencyRiskDto> serverRisks, List<AnalyzeProjectRelease> localReleases) {
    var serverRiskById = new HashMap<UUID, DependencyRiskDto>();
    serverRisks.stream()
      .filter(risk -> risk.getId() != null)
      .forEach(risk -> serverRiskById.put(risk.getId(), risk));

    var merged = new ArrayList<DependencyRiskDto>();
    var matchedServerIds = new HashSet<UUID>();

    for (var release : localReleases) {
      for (var issue : release.issues()) {
        var serverRisk = lookupServerRisk(serverRiskById, issue);
        if (serverRisk != null) {
          merged.add(dependencyRiskDtoMapper.enrichServerDto(serverRisk, release, issue));
          matchedServerIds.add(serverRisk.getId());
        } else {
          dependencyRiskDtoMapper.toLocalOnlyDto(release, issue).ifPresent(merged::add);
        }
      }
    }

    for (var serverRisk : serverRisks) {
      if (!matchedServerIds.contains(serverRisk.getId())) {
        merged.add(serverRisk);
      }
    }
    return merged;
  }

  private List<DependencyRiskDto> enrichServerRisks(List<DependencyRiskDto> serverRisks, List<AnalyzeProjectRelease> localReleases) {
    var serverRiskById = new HashMap<UUID, DependencyRiskDto>();
    serverRisks.stream()
      .filter(risk -> risk.getId() != null)
      .forEach(risk -> serverRiskById.put(risk.getId(), risk));

    var enrichedServerRisksById = new HashMap<UUID, DependencyRiskDto>();
    for (var release : localReleases) {
      for (var issue : release.issues()) {
        var serverRisk = lookupServerRisk(serverRiskById, issue);
        if (serverRisk != null) {
          enrichedServerRisksById.put(serverRisk.getId(), dependencyRiskDtoMapper.enrichServerDto(serverRisk, release, issue));
        }
      }
    }

    return serverRisks.stream()
      .map(serverRisk -> enrichedServerRisksById.getOrDefault(serverRisk.getId(), serverRisk))
      .toList();
  }

  @Nullable
  private static DependencyRiskDto lookupServerRisk(HashMap<UUID, DependencyRiskDto> byId, AnalyzeProjectIssue issue) {
    var key = issue.key();
    if (key == null || key.isBlank()) {
      return null;
    }
    try {
      return byId.get(UUID.fromString(key));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static class DependencyRiskDelta {
    private final List<DependencyRiskDto> addedRisks;
    private final List<DependencyRiskDto> updatedRisks;

    private DependencyRiskDelta(List<DependencyRiskDto> addedRisks, List<DependencyRiskDto> updatedRisks) {
      this.addedRisks = addedRisks;
      this.updatedRisks = updatedRisks;
    }

    public List<DependencyRiskDto> addedRisks() {
      return addedRisks;
    }

    public List<DependencyRiskDto> updatedRisks() {
      return updatedRisks;
    }
  }
}
