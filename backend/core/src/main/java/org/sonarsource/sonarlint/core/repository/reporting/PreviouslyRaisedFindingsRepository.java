/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.repository.reporting;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;

public class PreviouslyRaisedFindingsRepository {
  private final Map<String, Map<URI, List<RaisedIssueDto>>> previouslyRaisedIssuesByScopeId = new ConcurrentHashMap<>();
  private final Map<String, Map<URI, List<RaisedHotspotDto>>> previouslyRaisedHotspotsByScopeId = new ConcurrentHashMap<>();

  public Map<URI, List<RaisedIssueDto>> replaceIssuesForFiles(String scopeId, Map<URI, List<RaisedIssueDto>> raisedIssues) {
    return addOrReplaceFindings(scopeId, raisedIssues, previouslyRaisedIssuesByScopeId);
  }

  public Map<URI, List<RaisedHotspotDto>> replaceHotspotsForFiles(String scopeId, Map<URI, List<RaisedHotspotDto>> raisedHotpots) {
    return addOrReplaceFindings(scopeId, raisedHotpots, previouslyRaisedHotspotsByScopeId);
  }

  private static <F extends RaisedFindingDto> Map<URI, List<F>> addOrReplaceFindings(String scopeId, Map<URI, List<F>> raisedFindings,
    Map<String, Map<URI, List<F>>> previouslyRaisedFindingsByScopeId) {
    var findingsPerFile = previouslyRaisedFindingsByScopeId.computeIfAbsent(scopeId, k -> new ConcurrentHashMap<>());
    findingsPerFile.putAll(raisedFindings);
    return findingsPerFile;
  }

  public Map<URI, List<RaisedIssueDto>> getRaisedIssuesForScope(String scopeId) {
    return previouslyRaisedIssuesByScopeId.getOrDefault(scopeId, Map.of());
  }

  public Map<URI, List<RaisedHotspotDto>> getRaisedHotspotsForScope(String scopeId) {
    return previouslyRaisedHotspotsByScopeId.getOrDefault(scopeId, Map.of());
  }

  public void resetFindingsCache(String scopeId, Set<URI> files) {
    resetCacheForFindings(scopeId, files, previouslyRaisedIssuesByScopeId);
    resetCacheForFindings(scopeId, files, previouslyRaisedHotspotsByScopeId);
  }

  private static <F extends RaisedFindingDto> void resetCacheForFindings(String scopeId, Set<URI> files, Map<String, Map<URI, List<F>>> cache) {
    Map<URI, List<F>> blankCache = files.stream().collect(Collectors.toMap(Function.identity(), e -> new ArrayList<>()));
    cache.compute(scopeId, (file, issues) -> blankCache);
  }

  public Optional<RaisedIssue> findRaisedIssueById(UUID issueId) {
    return previouslyRaisedIssuesByScopeId.values().stream()
      .flatMap(issuesByUri -> issuesByUri.entrySet().stream()
        .flatMap(entry -> entry.getValue().stream().filter(issue -> issue.getId().equals(issueId)).findFirst().map(issue -> new RaisedIssue(entry.getKey(), issue)).stream()))
      .findFirst();
  }
}
