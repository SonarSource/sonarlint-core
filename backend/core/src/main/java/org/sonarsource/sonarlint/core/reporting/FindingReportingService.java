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
package org.sonarsource.sonarlint.core.reporting;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.NewCodeDefinition;
import org.sonarsource.sonarlint.core.newcode.NewCodeService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.reporting.PreviouslyRaisedFindingsRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaiseHotspotsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaiseIssuesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.tracking.TrackedIssue;
import org.sonarsource.sonarlint.core.tracking.streaming.Alarm;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.sonarsource.sonarlint.core.DtoMapper.toRaisedHotspotDto;
import static org.sonarsource.sonarlint.core.DtoMapper.toRaisedIssueDto;

public class FindingReportingService {
  public static final Duration STREAMING_INTERVAL = Duration.ofMillis(300);

  private final SonarLintRpcClient client;
  private final ConfigurationRepository configurationRepository;
  private final NewCodeService newCodeService;
  private final PreviouslyRaisedFindingsRepository previouslyRaisedFindingsRepository;
  private final Map<String, Map<URI, Collection<TrackedIssue>>> issuesPerFileUriPerConfigScope = new ConcurrentHashMap<>();
  private final Map<String, Map<URI, Collection<TrackedIssue>>> securityHotspotsPerFileUriPerConfigScope = new ConcurrentHashMap<>();
  private final Map<String, Alarm> streamingTriggeringAlarmByConfigScopeId = new ConcurrentHashMap<>();

  public FindingReportingService(SonarLintRpcClient client, ConfigurationRepository configurationRepository, NewCodeService newCodeService,
    PreviouslyRaisedFindingsRepository previouslyRaisedFindingsRepository) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.newCodeService = newCodeService;
    this.previouslyRaisedFindingsRepository = previouslyRaisedFindingsRepository;
  }

  public void resetFindingsForFiles(String configurationScopeId, Set<URI> files) {
    files.forEach(fileUri -> {
      resetFindingsForFile(issuesPerFileUriPerConfigScope, fileUri, configurationScopeId);
      resetFindingsForFile(securityHotspotsPerFileUriPerConfigScope, fileUri, configurationScopeId);
    });
    previouslyRaisedFindingsRepository.resetFindingsCache(configurationScopeId, files);
  }

  private static void resetFindingsForFile(Map<String, Map<URI, Collection<TrackedIssue>>> findingsMap, URI fileUri, String scopeId) {
    findingsMap.computeIfAbsent(scopeId, k -> new ConcurrentHashMap<>()).computeIfAbsent(fileUri, k -> new ArrayList<>()).clear();
  }

  public void streamIssue(String configurationScopeId, UUID analysisId, TrackedIssue trackedIssue) {
    if (trackedIssue.isSecurityHotspot()) {
      securityHotspotsPerFileUriPerConfigScope.computeIfAbsent(configurationScopeId, k -> new ConcurrentHashMap<>()).computeIfAbsent(trackedIssue.getFileUri(), k -> new ArrayList<>()).add(trackedIssue);
    } else {
      issuesPerFileUriPerConfigScope.computeIfAbsent(configurationScopeId, k -> new ConcurrentHashMap<>()).computeIfAbsent(trackedIssue.getFileUri(), k -> new ArrayList<>()).add(trackedIssue);
    }
    getStreamingDebounceAlarm(configurationScopeId, analysisId).schedule();
  }

  private void triggerStreaming(String configurationScopeId, UUID analysisId) {
    var newCodeDefinition = newCodeService.getFullNewCodeDefinition(configurationScopeId).orElseGet(NewCodeDefinition::withAlwaysNew);
    var issuesToRaise = issuesPerFileUriPerConfigScope.getOrDefault(configurationScopeId, new ConcurrentHashMap<>()).entrySet().stream()
      .map(e -> Map.entry(e.getKey(), e.getValue().stream().map(issue -> toRaisedIssueDto(issue, newCodeDefinition)).collect(toList())))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    var hotspotsToRaise = securityHotspotsPerFileUriPerConfigScope.getOrDefault(configurationScopeId, new ConcurrentHashMap<>()).entrySet().stream()
      .map(e -> Map.entry(e.getKey(), e.getValue().stream().map(issue -> toRaisedHotspotDto(issue, newCodeDefinition)).collect(toList())))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    updateRaisedFindingsCacheAndNotifyClient(configurationScopeId, analysisId, issuesToRaise, hotspotsToRaise, true);
  }

  public void reportTrackedFindings(String configurationScopeId, UUID analysisId, Map<Path, List<TrackedIssue>> issuesToReport, Map<Path, List<TrackedIssue>> hotspotsToReport) {
    // stop streaming now, we will raise all issues one last time from this method
    stopStreaming(configurationScopeId);
    var newCodeDefinition = newCodeService.getFullNewCodeDefinition(configurationScopeId).orElseGet(NewCodeDefinition::withAlwaysNew);
    var issuesToRaise = getIssuesToRaise(issuesToReport, newCodeDefinition);
    var hotspotsToRaise = getHotspotsToRaise(hotspotsToReport, newCodeDefinition);
    updateRaisedFindingsCacheAndNotifyClient(configurationScopeId, analysisId, issuesToRaise, hotspotsToRaise, false);
  }

  private synchronized void updateRaisedFindingsCacheAndNotifyClient(String configurationScopeId, @Nullable UUID analysisId, Map<URI, List<RaisedIssueDto>> updatedIssues,
    Map<URI, List<RaisedHotspotDto>> updatedHotspots, boolean isIntermediatePublication) {
    var issuesToRaise = previouslyRaisedFindingsRepository.replaceIssuesForFiles(configurationScopeId, updatedIssues);
    client.raiseIssues(new RaiseIssuesParams(configurationScopeId, issuesToRaise, isIntermediatePublication, analysisId));
    var effectiveBindingOpt = configurationRepository.getEffectiveBinding(configurationScopeId);
    if (effectiveBindingOpt.isPresent()) {
      // security hotspots are only supported in connected mode
      var hotspotsToRaise = previouslyRaisedFindingsRepository.replaceHotspotsForFiles(configurationScopeId, updatedHotspots);
      client.raiseHotspots(new RaiseHotspotsParams(configurationScopeId, hotspotsToRaise, isIntermediatePublication, analysisId));
    }
  }

  private void stopStreaming(String configurationScopeId) {
    var alarm = removeStreamingDebounceAlarmIfExists(configurationScopeId);
    if (alarm != null) {
      alarm.shutdownNow();
    }
  }

  private Alarm getStreamingDebounceAlarm(String configurationScopeId, UUID analysisId) {
    return streamingTriggeringAlarmByConfigScopeId.computeIfAbsent(configurationScopeId,
      id -> new Alarm("sonarlint-finding-streamer", STREAMING_INTERVAL, () -> triggerStreaming(configurationScopeId, analysisId)));
  }

  private Alarm removeStreamingDebounceAlarmIfExists(String configurationScopeId) {
    return streamingTriggeringAlarmByConfigScopeId.remove(configurationScopeId);
  }

  private static Map<URI, List<RaisedIssueDto>> getIssuesToRaise(Map<Path, List<TrackedIssue>> updatedIssues, NewCodeDefinition newCodeDefinition) {
    return updatedIssues.values().stream().flatMap(Collection::stream)
      .collect(groupingBy(TrackedIssue::getFileUri, Collectors.mapping(issue -> toRaisedIssueDto(issue, newCodeDefinition), Collectors.toList())));
  }

  private static Map<URI, List<RaisedHotspotDto>> getHotspotsToRaise(Map<Path, List<TrackedIssue>> hotspots, NewCodeDefinition newCodeDefinition) {
    return hotspots.values().stream().flatMap(Collection::stream)
      .collect(groupingBy(TrackedIssue::getFileUri, Collectors.mapping(hotspot -> toRaisedHotspotDto(hotspot, newCodeDefinition), Collectors.toList())));
  }

  public void updateAndReportIssues(String configurationScopeId, UnaryOperator<RaisedIssueDto> issueUpdater) {
    updateAndReportFindings(configurationScopeId, UnaryOperator.identity(), issueUpdater);
  }

  public void updateAndReportHotspots(String configurationScopeId, UnaryOperator<RaisedHotspotDto> hotspotUpdater) {
    updateAndReportFindings(configurationScopeId, hotspotUpdater, UnaryOperator.identity());
  }

  public void updateAndReportFindings(String configurationScopeId, UnaryOperator<RaisedHotspotDto> hotspotUpdater, UnaryOperator<RaisedIssueDto> issueUpdater) {
    var updatedHotspots = updateFindings(hotspotUpdater, previouslyRaisedFindingsRepository.getRaisedHotspotsForScope(configurationScopeId));
    var updatedIssues = updateFindings(issueUpdater, previouslyRaisedFindingsRepository.getRaisedIssuesForScope(configurationScopeId));
    updateRaisedFindingsCacheAndNotifyClient(configurationScopeId, null, updatedIssues, updatedHotspots, false);
  }

  private static <F extends RaisedFindingDto> Map<URI, List<F>> updateFindings(UnaryOperator<F> findingUpdater, Map<URI, List<F>> previouslyRaisedFindings) {
    Map<URI, List<F>> updatedFindings = new HashMap<>();
    previouslyRaisedFindings.forEach((uri, finding) -> {
      var updatedFindingsForFile = finding.stream()
        .map(findingUpdater)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
      updatedFindings.put(uri, updatedFindingsForFile);
    });
    return updatedFindings;
  }
}
