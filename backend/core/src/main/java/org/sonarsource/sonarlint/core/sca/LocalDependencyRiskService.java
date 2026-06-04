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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.storage.UpdateSummary;
import org.sonarsource.sonarlint.core.storage.StorageService;

public class LocalDependencyRiskService {
  private final ConcurrentHashMap<String, LocalDependencyRiskAnalysis> analysesByConfigurationScopeId = new ConcurrentHashMap<>();
  private final StorageService storageService;
  private final DependencyRiskDtoMapper dependencyRiskDtoMapper;

  public LocalDependencyRiskService(StorageService storageService, DependencyRiskDtoMapper dependencyRiskDtoMapper) {
    this.storageService = storageService;
    this.dependencyRiskDtoMapper = dependencyRiskDtoMapper;
  }

  public List<DependencyRiskDto> listAll(String configurationScopeId, Binding binding, String branchName) {
    return mergeWithLocalAnalysis(configurationScopeId, loadServerRisks(binding, branchName));
  }

  public List<DependencyRiskDto> mergeWithLocalAnalysis(String configurationScopeId, List<DependencyRiskDto> serverRisks) {
    var localAnalysis = analysesByConfigurationScopeId.get(configurationScopeId);
    return localAnalysis == null ? serverRisks : merge(serverRisks, localAnalysis);
  }

  public LocalDependencyRiskUpdate updateLocalAnalysisAndComputeUpdate(String configurationScopeId, AnalyzeProjectResponse localAnalysis,
    Binding binding, String branchName) {
    var newLocalAnalysis = toLocalDependencyRiskAnalysis(localAnalysis);
    var serverRisks = loadServerRisks(binding, branchName);
    var previousLocalAnalysis = analysesByConfigurationScopeId.put(configurationScopeId, newLocalAnalysis);
    var newRisks = merge(serverRisks, newLocalAnalysis);
    var closedLocalOnlyRiskIds = previousLocalAnalysis == null ? Set.<UUID>of() : previousLocalAnalysis.localOnlyRiskIds(serverRisks);
    var changes = computeChanges(closedLocalOnlyRiskIds, newRisks);
    return new LocalDependencyRiskUpdate(newRisks, changes.closedRiskIds(), changes.addedRisks(), changes.updatedRisks());
  }

  public DependencyRiskChange computeServerSynchronizationUpdate(String configurationScopeId, UpdateSummary<ServerDependencyRisk> summary) {
    var deletedRiskIds = summary.deletedItemIds();
    var addedServerRisks = summary.addedItems().stream()
      .map(dependencyRiskDtoMapper::toDto)
      .toList();
    var updatedServerRisks = summary.updatedItems().stream()
      .map(dependencyRiskDtoMapper::toDto)
      .toList();
    var localAnalysis = analysesByConfigurationScopeId.get(configurationScopeId);
    if (localAnalysis == null) {
      return new DependencyRiskChange(deletedRiskIds, addedServerRisks, updatedServerRisks);
    }

    var deletedRisksStillFoundLocally = localAnalysis.localRisksMatchingIds(deletedRiskIds).stream()
      .map(LocalDependencyRisk::dto)
      .toList();
    var locallyFoundDeletedRiskIds = deletedRisksStillFoundLocally.stream()
      .map(DependencyRiskDto::getId)
      .collect(Collectors.toSet());
    var closedRiskIds = deletedRiskIds.stream()
      .filter(deletedRiskId -> !locallyFoundDeletedRiskIds.contains(deletedRiskId))
      .collect(Collectors.toSet());
    var updatedRisksWithLocalOnlyTransitions = new ArrayList<>(enrichServerRisks(updatedServerRisks, localAnalysis));
    updatedRisksWithLocalOnlyTransitions.addAll(deletedRisksStillFoundLocally);

    return new DependencyRiskChange(closedRiskIds, enrichServerRisks(addedServerRisks, localAnalysis), updatedRisksWithLocalOnlyTransitions);
  }

  public List<DependencyRiskDto> enrichServerRisksWithLocalAnalysis(String configurationScopeId, List<ServerDependencyRisk> serverRisks) {
    var serverRiskDtos = serverRisks.stream()
      .map(dependencyRiskDtoMapper::toDto)
      .toList();
    var localAnalysis = analysesByConfigurationScopeId.get(configurationScopeId);
    return localAnalysis == null ? serverRiskDtos : enrichServerRisks(serverRiskDtos, localAnalysis);
  }

  public void remove(String configurationScopeId) {
    analysesByConfigurationScopeId.remove(configurationScopeId);
  }

  private static DependencyRiskChange computeChanges(Set<UUID> closedRiskIds, List<DependencyRiskDto> newRisks) {
    return new DependencyRiskChange(
      closedRiskIds,
      newRisks.stream()
        .filter(risk -> risk.getPresence() == DependencyRiskDto.Presence.LOCAL_ONLY)
        .toList(),
      newRisks.stream()
        .filter(risk -> risk.getPresence() == DependencyRiskDto.Presence.SERVER_AND_LOCAL)
        .toList());
  }

  private List<DependencyRiskDto> loadServerRisks(Binding binding, String branchName) {
    return storageService.binding(binding).findings().loadDependencyRisks(branchName)
      .stream()
      .map(dependencyRiskDtoMapper::toDto)
      .toList();
  }

  private LocalDependencyRiskAnalysis toLocalDependencyRiskAnalysis(AnalyzeProjectResponse localAnalysis) {
    var risks = new ArrayList<LocalDependencyRisk>();
    for (var release : localAnalysis.releases()) {
      for (var issue : release.issues()) {
        risks.add(new LocalDependencyRisk(release, issue, dependencyRiskDtoMapper.toLocalOnlyDto(release, issue)));
      }
    }
    return new LocalDependencyRiskAnalysis(List.copyOf(risks));
  }

  private List<DependencyRiskDto> merge(List<DependencyRiskDto> serverRisks, LocalDependencyRiskAnalysis localAnalysis) {
    var serverRiskById = new HashMap<UUID, DependencyRiskDto>();
    serverRisks.stream()
      .forEach(risk -> serverRiskById.put(risk.getId(), risk));

    var merged = new ArrayList<DependencyRiskDto>();
    var matchedServerIds = new HashSet<UUID>();

    for (var localRisk : localAnalysis.risks()) {
      var serverRisk = serverRiskById.get(localRisk.dto().getId());
      if (serverRisk != null) {
        merged.add(dependencyRiskDtoMapper.enrichServerDto(serverRisk, localRisk.release(), localRisk.issue()));
        matchedServerIds.add(serverRisk.getId());
      } else {
        merged.add(localRisk.dto());
      }
    }

    for (var serverRisk : serverRisks) {
      if (!matchedServerIds.contains(serverRisk.getId())) {
        merged.add(serverRisk);
      }
    }
    return merged;
  }

  private List<DependencyRiskDto> enrichServerRisks(List<DependencyRiskDto> serverRisks, LocalDependencyRiskAnalysis localAnalysis) {
    var serverRiskById = new HashMap<UUID, DependencyRiskDto>();
    serverRisks.stream()
      .forEach(risk -> serverRiskById.put(risk.getId(), risk));

    var enrichedServerRisksById = new HashMap<UUID, DependencyRiskDto>();
    for (var localRisk : localAnalysis.risks()) {
      var serverRisk = serverRiskById.get(localRisk.dto().getId());
      if (serverRisk != null) {
        enrichedServerRisksById.put(serverRisk.getId(), dependencyRiskDtoMapper.enrichServerDto(serverRisk, localRisk.release(), localRisk.issue()));
      }
    }

    return serverRisks.stream()
      .map(serverRisk -> enrichedServerRisksById.getOrDefault(serverRisk.getId(), serverRisk))
      .toList();
  }

  public record DependencyRiskChange(Set<UUID> closedRiskIds, List<DependencyRiskDto> addedRisks, List<DependencyRiskDto> updatedRisks) {
    public boolean hasChanges() {
      return !closedRiskIds.isEmpty() || !addedRisks.isEmpty() || !updatedRisks.isEmpty();
    }
  }

  public record LocalDependencyRiskUpdate(List<DependencyRiskDto> newRisks, Set<UUID> closedRiskIds, List<DependencyRiskDto> addedRisks,
    List<DependencyRiskDto> updatedRisks) {
    public boolean hasChanges() {
      return !closedRiskIds.isEmpty() || !addedRisks.isEmpty() || !updatedRisks.isEmpty();
    }
  }

  private record LocalDependencyRiskAnalysis(List<LocalDependencyRisk> risks) {
    Set<UUID> localOnlyRiskIds(List<DependencyRiskDto> serverRisks) {
      var serverRiskIds = serverRisks.stream()
        .map(DependencyRiskDto::getId)
        .collect(Collectors.toSet());
      return risks.stream()
        .map(LocalDependencyRisk::dto)
        .map(DependencyRiskDto::getId)
        .filter(id -> !serverRiskIds.contains(id))
        .collect(Collectors.toSet());
    }

    List<LocalDependencyRisk> localRisksMatchingIds(Set<UUID> riskIds) {
      if (riskIds.isEmpty()) {
        return List.of();
      }
      return risks.stream()
        .filter(risk -> riskIds.contains(risk.dto().getId()))
        .toList();
    }
  }

  private record LocalDependencyRisk(AnalyzeProjectRelease release, AnalyzeProjectIssue issue, DependencyRiskDto dto) {
  }
}
