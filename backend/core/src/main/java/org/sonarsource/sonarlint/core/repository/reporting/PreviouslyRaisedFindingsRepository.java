/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;

@Named
@Singleton
public class PreviouslyRaisedFindingsRepository {

  private final Map<String, Map<URI, List<RaisedIssueDto>>> previouslyRaisedIssuesByScopeId = new ConcurrentHashMap<>();
  private final Map<String, Map<URI, List<RaisedHotspotDto>>> previouslyRaisedHotspotsByScopeId = new ConcurrentHashMap<>();

  public void addOrReplaceIssues(String scopeId, Map<URI, List<RaisedIssueDto>> raisedIssues) {
    previouslyRaisedIssuesByScopeId.put(scopeId, raisedIssues);
  }

  public Map<URI, List<RaisedIssueDto>> getRaisedIssuesForScope(String scopeId) {
    return previouslyRaisedIssuesByScopeId.getOrDefault(scopeId, Map.of());
  }

  public void addOrReplaceHotspots(String scopeId, Map<URI, List<RaisedHotspotDto>> raisedHotpots) {
    previouslyRaisedHotspotsByScopeId.put(scopeId, raisedHotpots);
  }

  public Map<URI, List<RaisedHotspotDto>> getRaisedHotspotsForScope(String scopeId) {
    return previouslyRaisedHotspotsByScopeId.getOrDefault(scopeId, Map.of());
  }

}
