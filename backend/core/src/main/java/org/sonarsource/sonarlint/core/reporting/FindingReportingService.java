/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.AnalysisReportedIssuesEvent;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.NewCodeDefinition;
import org.sonarsource.sonarlint.core.mode.SeverityModeService;
import org.sonarsource.sonarlint.core.newcode.NewCodeService;
import org.sonarsource.sonarlint.core.remediation.aicodefix.AiCodeFixFeature;
import org.sonarsource.sonarlint.core.remediation.aicodefix.AiCodeFixService;
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
import org.springframework.context.ApplicationEventPublisher;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.sonarsource.sonarlint.core.DtoMapper.toRaisedHotspotDto;
import static org.sonarsource.sonarlint.core.DtoMapper.toRaisedIssueDto;

public class FindingReportingService {
  public static final Duration STREAMING_INTERVAL = Duration.ofMillis(300);

  private final SonarLintRpcClient client;
  private final ConfigurationRepository configurationRepository;
  private final NewCodeService newCodeService;
  private final SeverityModeService severityModeService;
  private final AiCodeFixService aiCodeFixService;
  private final PreviouslyRaisedFindingsRepository previouslyRaisedFindingsRepository;
  private final Map<URI, Collection<TrackedIssue>> issuesPerFileUri = new ConcurrentHashMap<>();
  private final Map<URI, Collection<TrackedIssue>> securityHotspotsPerFileUri = new ConcurrentHashMap<>();
  private final Map<String, Alarm> streamingTriggeringAlarmByConfigScopeId = new ConcurrentHashMap<>();
  private final Map<UUID, Set<URI>> filesPerAnalysis = new ConcurrentHashMap<>();
  private final ApplicationEventPublisher eventPublisher;

  public FindingReportingService(SonarLintRpcClient client, ConfigurationRepository configurationRepository, NewCodeService newCodeService, SeverityModeService severityModeService,
    AiCodeFixService aiCodeFixService, PreviouslyRaisedFindingsRepository previouslyRaisedFindingsRepository, ApplicationEventPublisher eventPublisher) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.newCodeService = newCodeService;
    this.severityModeService = severityModeService;
    this.aiCodeFixService = aiCodeFixService;
    this.previouslyRaisedFindingsRepository = previouslyRaisedFindingsRepository;
    this.eventPublisher = eventPublisher;
  }

  public void resetFindingsForFiles(String configurationScopeId, Set<URI> files) {
    files.forEach(fileUri -> {
      resetFindingsForFile(issuesPerFileUri, fileUri);
      resetFindingsForFile(securityHotspotsPerFileUri, fileUri);
    });
    previouslyRaisedFindingsRepository.resetFindingsCache(configurationScopeId, files);
  }

  public void initFilesToAnalyze(UUID analysisId, Set<URI> files) {
    filesPerAnalysis.computeIfAbsent(analysisId, k -> new HashSet<>()).addAll(files);
  }

  private static void resetFindingsForFile(Map<URI, Collection<TrackedIssue>> findingsMap, URI fileUri) {
    findingsMap.computeIfPresent(fileUri, (k, v) -> List.of());
  }

  public void streamIssue(String configurationScopeId, UUID analysisId, TrackedIssue trackedIssue) {
    // Cache is cleared on new analysis, but it's possible that 2 analyses almost start at the same time.
    // In this case, same issues will be reported twice for the same file during the streaming, which will be sent to the client.
    // A quick workaround is to replace the existing issue with the duplicated one (which should be the most up-to-date).
    // Ideally, we should be able to cancel the previous analysis if it's not relevant.
    if (trackedIssue.isSecurityHotspot()) {
      insertTrackedIssue(securityHotspotsPerFileUri, trackedIssue);
    } else {
      insertTrackedIssue(issuesPerFileUri, trackedIssue);
    }
    getStreamingDebounceAlarm(configurationScopeId, analysisId).schedule();
  }

  private static void insertTrackedIssue(Map<URI, Collection<TrackedIssue>> map, TrackedIssue trackedIssue) {
    map.compute(trackedIssue.getFileUri(), (fileUri, fileFindings) -> {
      // make sure to return an immutable list as it might be iterated over in parallel
      if (fileFindings == null) {
        return List.of(trackedIssue);
      }
      var newIssues = new ArrayList<>(fileFindings);
      newIssues.removeIf(i -> i.getId().equals(trackedIssue.getId()));
      newIssues.add(trackedIssue);
      return List.copyOf(newIssues);
    });
  }

  private void triggerStreaming(String configurationScopeId, UUID analysisId) {
    var effectiveBinding = configurationRepository.getEffectiveBinding(configurationScopeId);
    var connectionId = effectiveBinding.map(Binding::connectionId).orElse(null);
    var newCodeDefinition = newCodeService.getFullNewCodeDefinition(configurationScopeId).orElseGet(NewCodeDefinition::withAlwaysNew);
    var isMQRMode = severityModeService.isMQRModeForConnection(connectionId);
    var aiCodeFixFeature = effectiveBinding.flatMap(aiCodeFixService::getFeature);
    var issuesToRaise = issuesPerFileUri.entrySet().stream()
      .filter(e -> filesPerAnalysis.get(analysisId).contains(e.getKey()))
      .map(e -> Map.entry(e.getKey(),
        e.getValue().stream().map(issue -> toRaisedIssueDto(issue, newCodeDefinition, isMQRMode, aiCodeFixFeature.map(feature -> feature.isFixable(issue)).orElse(false)))
          .toList()))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    var hotspotsToRaise = securityHotspotsPerFileUri.entrySet().stream()
      .filter(e -> filesPerAnalysis.get(analysisId).contains(e.getKey()))
      .map(e -> Map.entry(e.getKey(), e.getValue().stream().map(issue -> toRaisedHotspotDto(issue, newCodeDefinition, isMQRMode)).toList()))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    updateRaisedFindingsCacheAndNotifyClient(configurationScopeId, analysisId, issuesToRaise, hotspotsToRaise, true);
  }

  public void reportTrackedFindings(String configurationScopeId, UUID analysisId, Map<Path, List<TrackedIssue>> issuesToReport, Map<Path, List<TrackedIssue>> hotspotsToReport) {
    // stop streaming now, we will raise all issues one last time from this method
    stopStreaming(configurationScopeId);
    var effectiveBinding = configurationRepository.getEffectiveBinding(configurationScopeId);
    var connectionId = effectiveBinding.map(Binding::connectionId).orElse(null);
    var newCodeDefinition = newCodeService.getFullNewCodeDefinition(configurationScopeId).orElseGet(NewCodeDefinition::withAlwaysNew);
    var isMQRMode = severityModeService.isMQRModeForConnection(connectionId);
    var aiCodeFixFeature = effectiveBinding.flatMap(aiCodeFixService::getFeature);
    var issuesToRaise = getIssuesToRaise(issuesToReport, newCodeDefinition, isMQRMode, aiCodeFixFeature);
    this.eventPublisher.publishEvent(new AnalysisReportedIssuesEvent(issuesToRaise.values().stream().flatMap(List::stream).toList()));
    var hotspotsToRaise = getHotspotsToRaise(hotspotsToReport, newCodeDefinition, isMQRMode);
    updateRaisedFindingsCacheAndNotifyClient(configurationScopeId, analysisId, issuesToRaise, hotspotsToRaise, false);
    filesPerAnalysis.remove(analysisId);
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

  private static Map<URI, List<RaisedIssueDto>> getIssuesToRaise(Map<Path, List<TrackedIssue>> updatedIssues, NewCodeDefinition newCodeDefinition, boolean isMQRMode,
    Optional<AiCodeFixFeature> aiCodeFixFeature) {
    return updatedIssues.values().stream().flatMap(Collection::stream)
      .collect(groupingBy(TrackedIssue::getFileUri,
        Collectors.mapping(issue -> toRaisedIssueDto(issue, newCodeDefinition, isMQRMode, aiCodeFixFeature.map(feature -> feature.isFixable(issue)).orElse(false)),
          Collectors.toList())));
  }

  private static Map<URI, List<RaisedHotspotDto>> getHotspotsToRaise(Map<Path, List<TrackedIssue>> hotspots, NewCodeDefinition newCodeDefinition, boolean isMQRMode) {
    return hotspots.values().stream().flatMap(Collection::stream)
      .collect(groupingBy(TrackedIssue::getFileUri, Collectors.mapping(hotspot -> toRaisedHotspotDto(hotspot, newCodeDefinition, isMQRMode), Collectors.toList())));
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
        .toList();
      updatedFindings.put(uri, updatedFindingsForFile);
    });
    return updatedFindings;
  }

  @CheckForNull
  public RaisedIssueDto findReportedIssue(UUID issueId, NewCodeDefinition newCodeDefinition, boolean isMQRMode, Optional<AiCodeFixFeature> aiCodeFixFeature) {
    for (var findingsForFile : issuesPerFileUri.values()) {
      var optFinding = findingsForFile.stream().filter(issue -> issue.getId().equals(issueId)).findFirst();
      if (optFinding.isPresent()) {
        return toRaisedIssueDto(optFinding.get(), newCodeDefinition, isMQRMode, aiCodeFixFeature.map(feature -> feature.isFixable(optFinding.get())).orElse(false));
      }
    }
    return null;
  }

  @CheckForNull
  public RaisedHotspotDto findReportedHotspot(UUID hotspotId, NewCodeDefinition newCodeDefinition, boolean isMQRMode) {
    for (var findingsForFile : securityHotspotsPerFileUri.values()) {
      var optFinding = findingsForFile.stream().filter(hotspot -> hotspot.getId().equals(hotspotId)).findFirst();
      if (optFinding.isPresent()) {
        return toRaisedHotspotDto(optFinding.get(), newCodeDefinition, isMQRMode);
      }
    }
    return null;
  }
}
