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
package org.sonarsource.sonarlint.core.tracking;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.analysis.AnalysisFinishedEvent;
import org.sonarsource.sonarlint.core.analysis.AnalysisStartedEvent;
import org.sonarsource.sonarlint.core.analysis.RawIssueDetectedEvent;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.KnownFinding;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SonarLintBlameResult;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.util.git.GitRepoNotFoundException;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueStorageService;
import org.sonarsource.sonarlint.core.reporting.FindingReportingService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.GetBaseDirParams;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.FindingsSynchronizationService;
import org.sonarsource.sonarlint.core.tracking.matching.IssueMatcher;
import org.sonarsource.sonarlint.core.tracking.matching.LocalOnlyIssueMatchingAttributesMapper;
import org.sonarsource.sonarlint.core.tracking.matching.MatchingSession;
import org.sonarsource.sonarlint.core.tracking.matching.ServerHotspotMatchingAttributesMapper;
import org.sonarsource.sonarlint.core.tracking.matching.ServerIssueMatchingAttributesMapper;
import org.sonarsource.sonarlint.core.tracking.matching.TrackedIssueFindingMatchingAttributeMapper;
import org.springframework.context.event.EventListener;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.sonarsource.sonarlint.core.commons.util.git.GitUtils.blameFile;
import static org.sonarsource.sonarlint.core.commons.util.git.GitUtils.blameFromNativeCommand;
import static org.sonarsource.sonarlint.core.commons.util.git.GitUtils.blameWithFilesGitCommand;

public class TrackingService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final SonarLintRpcClient client;
  private final ConfigurationRepository configurationRepository;
  private final SonarProjectBranchTrackingService branchTrackingService;
  private final PathTranslationService pathTranslationService;
  private final FindingReportingService reportingService;
  private final Map<UUID, MatchingSession> matchingSessionByAnalysisId = new HashMap<>();
  private final KnownFindingsStorageService knownFindingsStorageService;
  private final StorageService storageService;
  private final LocalOnlyIssueRepository localOnlyIssueRepository;
  private final LocalOnlyIssueStorageService localOnlyIssueStorageService;
  private final FindingsSynchronizationService findingsSynchronizationService;

  public TrackingService(SonarLintRpcClient client, ConfigurationRepository configurationRepository, SonarProjectBranchTrackingService branchTrackingService,
    PathTranslationService pathTranslationService, FindingReportingService reportingService, KnownFindingsStorageService knownFindingsStorageService, StorageService storageService,
    LocalOnlyIssueRepository localOnlyIssueRepository, LocalOnlyIssueStorageService localOnlyIssueStorageService, FindingsSynchronizationService findingsSynchronizationService) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.branchTrackingService = branchTrackingService;
    this.pathTranslationService = pathTranslationService;
    this.reportingService = reportingService;
    this.knownFindingsStorageService = knownFindingsStorageService;
    this.storageService = storageService;
    this.localOnlyIssueRepository = localOnlyIssueRepository;
    this.localOnlyIssueStorageService = localOnlyIssueStorageService;
    this.findingsSynchronizationService = findingsSynchronizationService;
  }

  @EventListener
  public void onAnalysisStarted(AnalysisStartedEvent event) {
    if (event.isTrackingEnabled()) {
      var configurationScopeId = event.getConfigurationScopeId();
      var matchingSession = startMatchingSession(configurationScopeId, event.getFileRelativePaths(), event.getFileContentProvider());
      matchingSessionByAnalysisId.put(event.getAnalysisId(), matchingSession);
      reportingService.resetFindingsForFiles(configurationScopeId, event.getFileUris());
      reportingService.initFilesToAnalyze(event.getAnalysisId(), event.getFileUris());
    }
  }

  @EventListener
  public void onIssueDetected(RawIssueDetectedEvent event) {
    var analysisId = event.getAnalysisId();
    var matchingSession = matchingSessionByAnalysisId.get(analysisId);
    if (matchingSession == null) {
      // an issue was detected outside any analysis, this normally shouldn't happen
      return;
    }
    var detectedIssue = event.getDetectedIssue();
    var isSupported = detectedIssue.isInFile();
    if (isSupported) {
      // we don't support global issues for now
      var trackedIssue = matchingSession.matchWithKnownFinding(requireNonNull(detectedIssue.getIdeRelativePath()), detectedIssue);
      reportingService.streamIssue(event.getConfigurationScopeId(), analysisId, trackedIssue);
    }
  }

  @EventListener
  public void onAnalysisFinished(AnalysisFinishedEvent event) {
    var analysisId = event.getAnalysisId();
    var matchingSession = matchingSessionByAnalysisId.remove(analysisId);
    if (matchingSession == null) {
      // a not-started analysis finished, this normally shouldn't happen
      return;
    }
    var configurationScopeId = event.getConfigurationScopeId();
    if (event.shouldFetchServerIssues()) {
      findingsSynchronizationService.refreshServerFindings(configurationScopeId, matchingSession.getRelativePathsInvolved());
    }
    var result = matchWithServerFindings(configurationScopeId, matchingSession);
    reportingService.reportTrackedFindings(configurationScopeId, analysisId, result.issuesToReport, result.hotspotsToReport);
  }

  private MatchingResult matchWithServerFindings(String configurationScopeId, MatchingSession matchingSession) {
    var effectiveBindingOpt = configurationRepository.getEffectiveBinding(configurationScopeId);
    var activeBranchOpt = branchTrackingService.awaitEffectiveSonarProjectBranch(configurationScopeId);
    var translationOpt = pathTranslationService.getOrComputePathTranslation(configurationScopeId);
    var knownFindingsStore = knownFindingsStorageService.get();
    var issuesToReport = matchingSession.getIssuesPerFile();
    var hotspotsToReport = matchingSession.getSecurityHotspotsPerFile();
    if (effectiveBindingOpt.isPresent() && activeBranchOpt.isPresent() && translationOpt.isPresent()) {
      var binding = effectiveBindingOpt.get();
      var activeBranch = activeBranchOpt.get();
      var translation = translationOpt.get();
      issuesToReport = issuesToReport.entrySet().stream().map(e -> {
        var ideRelativePath = e.getKey();
        var serverRelativePath = translation.ideToServerPath(ideRelativePath);
        var serverIssues = storageService.binding(binding).findings().load(activeBranch, serverRelativePath);
        var localOnlyIssues = localOnlyIssueStorageService.get().loadForFile(configurationScopeId, serverRelativePath);
        var matches = matchWithServerIssues(serverRelativePath, serverIssues, localOnlyIssues, e.getValue());
        return Map.entry(ideRelativePath, matches);
      }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      hotspotsToReport = hotspotsToReport.entrySet().stream().map(e -> {
        var ideRelativePath = e.getKey();
        var serverRelativePath = translation.ideToServerPath(ideRelativePath);
        var serverHotspots = storageService.binding(binding).findings().loadHotspots(activeBranch, serverRelativePath);
        var matches = matchWithServerHotspots(serverHotspots, e.getValue());
        return Map.entry(ideRelativePath, matches);
      }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    issuesToReport.forEach((clientRelativePath, trackedIssues) -> storeTrackedIssues(knownFindingsStore, configurationScopeId, clientRelativePath, trackedIssues));
    hotspotsToReport.forEach((clientRelativePath, trackedHotspots) -> storeTrackedSecurityHotspots(knownFindingsStore, configurationScopeId, clientRelativePath, trackedHotspots));
    return new MatchingResult(issuesToReport, hotspotsToReport);
  }

  private static void storeTrackedIssues(XodusKnownFindingsStore knownIssuesStore, String configurationScopeId, Path clientRelativePath, Collection<TrackedIssue> newKnownIssues) {
    knownIssuesStore.storeKnownIssues(configurationScopeId, clientRelativePath,
      newKnownIssues.stream().map(i -> new KnownFinding(i.getId(), i.getServerKey(), i.getTextRangeWithHash(), i.getLineWithHash(), i.getRuleKey(), i.getMessage(),
        i.getIntroductionDate())).collect(Collectors.toList()));
  }

  private static void storeTrackedSecurityHotspots(XodusKnownFindingsStore knownIssuesStore, String configurationScopeId, Path clientRelativePath,
    Collection<TrackedIssue> newKnownSecurityHotspots) {
    knownIssuesStore.storeKnownSecurityHotspots(configurationScopeId, clientRelativePath,
      newKnownSecurityHotspots.stream().map(i -> new KnownFinding(i.getId(), i.getServerKey(), i.getTextRangeWithHash(), i.getLineWithHash(), i.getRuleKey(), i.getMessage(),
        i.getIntroductionDate())).collect(Collectors.toList()));
  }

  private List<TrackedIssue> matchWithServerIssues(Path serverRelativePath, List<ServerIssue<?>> serverIssues,
    List<LocalOnlyIssue> localOnlyIssues, Collection<TrackedIssue> trackedIssues) {
    var serverIssueMatcher = new IssueMatcher<>(new TrackedIssueFindingMatchingAttributeMapper(), new ServerIssueMatchingAttributesMapper());
    var serverMatchingResult = serverIssueMatcher.match(trackedIssues, serverIssues);
    var localIssueMatcher = new IssueMatcher<>(new TrackedIssueFindingMatchingAttributeMapper(), new LocalOnlyIssueMatchingAttributesMapper());
    var localMatchingResult = localIssueMatcher.match(trackedIssues, localOnlyIssues);
    var matches = trackedIssues.stream().map(trackedIssue -> {
      var matchToServer = serverMatchingResult.getMatch(trackedIssue);
      if (matchToServer != null) {
        return updateTrackedIssueWithServerData(trackedIssue, matchToServer);
      } else {
        var matchToLocal = localMatchingResult.getMatch(trackedIssue);
        if (matchToLocal != null) {
          return updateTrackedIssueWithLocalOnlyIssueData(trackedIssue, matchToLocal);
        }
        return trackedIssue;
      }
    }).collect(Collectors.toList());
    localOnlyIssueRepository.save(serverRelativePath,
      matches.stream().filter(issue -> issue.getServerKey() == null).map(issue -> newLocalOnlyIssue(serverRelativePath, issue)).collect(toList()));
    return matches;
  }

  private static List<TrackedIssue> matchWithServerHotspots(Collection<ServerHotspot> serverHotspots, Collection<TrackedIssue> trackedIssues) {
    var serverIssueMatcher = new IssueMatcher<>(new TrackedIssueFindingMatchingAttributeMapper(), new ServerHotspotMatchingAttributesMapper());
    var serverMatchingResult = serverIssueMatcher.match(trackedIssues, serverHotspots);
    return trackedIssues.stream().map(trackedIssue -> {
      var matchToServer = serverMatchingResult.getMatch(trackedIssue);
      if (matchToServer != null) {
        return updateRawHotspotWithServerData(trackedIssue, matchToServer);
      } else {
        return trackedIssue;
      }
    }).collect(Collectors.toList());
  }

  private static LocalOnlyIssue newLocalOnlyIssue(Path serverRelativePath, TrackedIssue issue) {
    return new LocalOnlyIssue(issue.getId(), serverRelativePath, issue.getTextRangeWithHash(), issue.getLineWithHash(),
      issue.getRuleKey(), issue.getMessage(), null);
  }

  private static TrackedIssue updateTrackedIssueWithServerData(TrackedIssue trackedIssue, ServerIssue<?> serverIssue) {
    var serverSeverity = serverIssue.getUserSeverity();
    var severity = serverSeverity != null ? serverSeverity : trackedIssue.getSeverity();
    var impacts = serverIssue.getImpacts().isEmpty() ? trackedIssue.getImpacts() : serverIssue.getImpacts();
    return new TrackedIssue(trackedIssue.getId(), trackedIssue.getMessage(), serverIssue.getCreationDate(),
      serverIssue.isResolved(), severity, serverIssue.getType(), serverIssue.getRuleKey(), trackedIssue.getTextRangeWithHash(),
      trackedIssue.getLineWithHash(), serverIssue.getKey(), impacts, trackedIssue.getFlows(),
      trackedIssue.getQuickFixes(), trackedIssue.getVulnerabilityProbability(), trackedIssue.getHotspotStatus(), trackedIssue.getRuleDescriptionContextKey(),
      trackedIssue.getCleanCodeAttribute(), trackedIssue.getFileUri());
  }

  private static TrackedIssue updateRawHotspotWithServerData(TrackedIssue trackedHotspot, ServerHotspot serverHotspot) {
    return new TrackedIssue(trackedHotspot.getId(), trackedHotspot.getMessage(), serverHotspot.getCreationDate(),
      serverHotspot.getStatus().isResolved(), trackedHotspot.getSeverity(), RuleType.SECURITY_HOTSPOT, trackedHotspot.getRuleKey(),
      trackedHotspot.getTextRangeWithHash(), trackedHotspot.getLineWithHash(),
      serverHotspot.getKey(), trackedHotspot.getImpacts(), trackedHotspot.getFlows(), trackedHotspot.getQuickFixes(),
      serverHotspot.getVulnerabilityProbability(), HotspotStatus.valueOf(serverHotspot.getStatus().name()), trackedHotspot.getRuleDescriptionContextKey(),
      trackedHotspot.getCleanCodeAttribute(), trackedHotspot.getFileUri());
  }

  private static TrackedIssue updateTrackedIssueWithLocalOnlyIssueData(TrackedIssue trackedIssue, LocalOnlyIssue localOnlyIssue) {
    return new TrackedIssue(trackedIssue.getId(), trackedIssue.getMessage(), trackedIssue.getIntroductionDate(),
      localOnlyIssue.getResolution() != null, trackedIssue.getSeverity(), trackedIssue.getType(), trackedIssue.getRuleKey(), trackedIssue.getTextRangeWithHash(),
      trackedIssue.getLineWithHash(), trackedIssue.getServerKey(), trackedIssue.getImpacts(), trackedIssue.getFlows(),
      trackedIssue.getQuickFixes(), trackedIssue.getVulnerabilityProbability(), trackedIssue.getHotspotStatus(), trackedIssue.getRuleDescriptionContextKey(),
      trackedIssue.getCleanCodeAttribute(), trackedIssue.getFileUri());
  }

  private MatchingSession startMatchingSession(String configurationScopeId, Set<Path> fileRelativePaths, UnaryOperator<String> fileContentProvider) {
    var knownFindingsStore = knownFindingsStorageService.get();
    var issuesByRelativePath = fileRelativePaths.stream()
      .collect(toMap(Function.identity(), relativePath -> knownFindingsStore.loadIssuesForFile(configurationScopeId, relativePath)));
    var hotspotsByRelativePath = fileRelativePaths.stream()
      .collect(toMap(Function.identity(), relativePath -> knownFindingsStore.loadSecurityHotspotsForFile(configurationScopeId, relativePath)));
    var introductionDateProvider = getIntroductionDateProvider(configurationScopeId, fileRelativePaths, fileContentProvider);
    var previousFindings = new KnownFindings(issuesByRelativePath, hotspotsByRelativePath);
    return new MatchingSession(previousFindings, introductionDateProvider);
  }

  private IntroductionDateProvider getIntroductionDateProvider(String configurationScopeId, Set<Path> fileRelativePaths, UnaryOperator<String> fileContentProvider) {
    var baseDir = getBaseDir(configurationScopeId);
    if (baseDir != null) {
      try {
        var sonarLintBlameResult2 = blameWithFilesGitCommand(baseDir, fileRelativePaths, fileContentProvider);
        var sonarLintBlameResult = blameFromNativeCommand(baseDir, fileRelativePaths);
        //blameFile(baseDir, fileRelativePaths);
        return (filePath, lineNumbers) -> determineIntroductionDate(filePath, lineNumbers, sonarLintBlameResult);
      } catch (GitRepoNotFoundException e) {
        LOG.info("Git Repository not found for {}. The path {} is not in a Git repository", configurationScopeId, e.getPath());
      } catch (Exception e) {
        LOG.error("Cannot access blame info for " + configurationScopeId, e);
      }
    }
    // we keep the detection date as the introduction date
    return (filePath, lineNumber) -> Instant.now();
  }

  @CheckForNull
  private Path getBaseDir(String configurationScopeId) {
    try {
      return client.getBaseDir(new GetBaseDirParams(configurationScopeId)).join().getBaseDir();
    } catch (Exception e) {
      LOG.error("Error when requesting the base dir", e);
      return null;
    }
  }

  private static Instant determineIntroductionDate(Path path, Collection<Integer> lineNumbers, SonarLintBlameResult sonarLintBlameResult) {
    return sonarLintBlameResult.getLatestChangeDateForLinesInFile(path, lineNumbers)
      .map(Date::toInstant)
      .orElse(Instant.now());
  }

  private static class MatchingResult {
    public final Map<Path, List<TrackedIssue>> issuesToReport;
    public final Map<Path, List<TrackedIssue>> hotspotsToReport;

    public MatchingResult(Map<Path, List<TrackedIssue>> issuesToReport, Map<Path, List<TrackedIssue>> hotspotsToReport) {
      this.issuesToReport = issuesToReport;
      this.hotspotsToReport = hotspotsToReport;
    }
  }
}
