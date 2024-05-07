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

import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.DtoMapper;
import org.sonarsource.sonarlint.core.analysis.AnalysisFinishedEvent;
import org.sonarsource.sonarlint.core.analysis.RawIssue;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.GitBlameUtils;
import org.sonarsource.sonarlint.core.commons.KnownIssue;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.NewCodeDefinition;
import org.sonarsource.sonarlint.core.commons.SonarLintBlameResult;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.file.FilePathTranslation;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.issue.matching.IssueMatcher;
import org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueStorageService;
import org.sonarsource.sonarlint.core.newcode.NewCodeService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LineWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LocalOnlyIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ServerMatchedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaiseIssuesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rules.RuleDetailsAdapter;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.IssueSynchronizationService;
import org.springframework.context.event.EventListener;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.sonarsource.sonarlint.core.tracking.TextRangeUtils.getLineWithHash;
import static org.sonarsource.sonarlint.core.tracking.TextRangeUtils.getTextRangeWithHash;

@Named
@Singleton
public class IssueMatchingService {
  private static final int FETCH_ALL_ISSUES_THRESHOLD = 10;
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final Duration STANDALONE_NEW_CODE_PERIOD = Duration.of(30, ChronoUnit.DAYS);
  private final ConfigurationRepository configurationRepository;
  private final StorageService storageService;
  private final SonarProjectBranchTrackingService branchTrackingService;
  private final IssueSynchronizationService issueSynchronizationService;
  private final KnownIssuesStorageService knownIssuesStorageService;
  private final LocalOnlyIssueRepository localOnlyIssueRepository;
  private final LocalOnlyIssueStorageService localOnlyIssueStorageService;
  private final NewCodeService newCodeService;
  private final PathTranslationService pathTranslationService;
  private final ExecutorService executorService;
  private final SonarLintRpcClient client;

  public IssueMatchingService(SonarLintRpcClient client, ConfigurationRepository configurationRepository, StorageService storageService,
    SonarProjectBranchTrackingService branchTrackingService, IssueSynchronizationService issueSynchronizationService,
    KnownIssuesStorageService knownIssuesStorageService, LocalOnlyIssueStorageService localOnlyIssueStorageService, LocalOnlyIssueRepository localOnlyIssueRepository,
    NewCodeService newCodeService, PathTranslationService pathTranslationService) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.storageService = storageService;
    this.branchTrackingService = branchTrackingService;
    this.issueSynchronizationService = issueSynchronizationService;
    this.knownIssuesStorageService = knownIssuesStorageService;
    this.localOnlyIssueStorageService = localOnlyIssueStorageService;
    this.localOnlyIssueRepository = localOnlyIssueRepository;
    this.newCodeService = newCodeService;
    this.pathTranslationService = pathTranslationService;
    this.executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "sonarlint-server-tracking-issue-updater"));
  }

  public Map<Path, List<Either<ServerMatchedIssueDto, LocalOnlyIssueDto>>> trackWithServerIssues(String configurationScopeId,
    Map<Path, List<ClientTrackedFindingDto>> clientTrackedIssuesByIdeRelativePath,
    boolean shouldFetchIssuesFromServer, SonarLintCancelMonitor cancelMonitor) {
    var effectiveBindingOpt = configurationRepository.getEffectiveBinding(configurationScopeId);
    var activeBranchOpt = branchTrackingService.awaitEffectiveSonarProjectBranch(configurationScopeId);
    var translationOpt = pathTranslationService.getOrComputePathTranslation(configurationScopeId);
    if (effectiveBindingOpt.isEmpty() || activeBranchOpt.isEmpty() || translationOpt.isEmpty()) {
      return clientTrackedIssuesByIdeRelativePath.entrySet().stream()
        .map(e -> Map.entry(e.getKey(), e.getValue().stream()
          .map(issue -> Either.<ServerMatchedIssueDto, LocalOnlyIssueDto>forRight(
            new LocalOnlyIssueDto(UUID.randomUUID(), null)))
          .collect(Collectors.toList())))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    var binding = effectiveBindingOpt.get();
    var activeBranch = activeBranchOpt.get();
    var translation = translationOpt.get();
    if (shouldFetchIssuesFromServer) {
      refreshServerIssues(cancelMonitor, binding, activeBranch, clientTrackedIssuesByIdeRelativePath, translation);
    }
    var newCodeDefinition = newCodeService.getFullNewCodeDefinition(configurationScopeId)
      .orElse(NewCodeDefinition.withAlwaysNew());
    return clientTrackedIssuesByIdeRelativePath.entrySet().stream().map(e -> {
      var ideRelativePath = e.getKey();
      var serverRelativePath = translation.ideToServerPath(ideRelativePath);
      var serverIssues = storageService.binding(binding).findings().load(activeBranch, serverRelativePath);
      var localOnlyIssues = localOnlyIssueStorageService.get().loadForFile(configurationScopeId, serverRelativePath);
      var matches = matchIssues(serverRelativePath, serverIssues, localOnlyIssues, e.getValue())
        .stream().map(result -> {
          if (result.isLeft()) {
            var serverIssue = result.getLeft();
            var creationDate = serverIssue.getCreationDate().toEpochMilli();
            var isOnNewCode = newCodeDefinition.isOnNewCode(creationDate);
            var userSeverity = serverIssue.getUserSeverity();
            return Either.<ServerMatchedIssueDto, LocalOnlyIssueDto>forLeft(
              new ServerMatchedIssueDto(UUID.randomUUID(), serverIssue.getKey(), creationDate, serverIssue.isResolved(),
                userSeverity != null ? RuleDetailsAdapter.adapt(userSeverity) : null, RuleDetailsAdapter.adapt(serverIssue.getType()), isOnNewCode));
          } else {
            var localOnlyIssue = result.getRight();
            var resolution = localOnlyIssue.getResolution();
            return Either.<ServerMatchedIssueDto, LocalOnlyIssueDto>forRight(
              new LocalOnlyIssueDto(localOnlyIssue.getId(), resolution == null ? null : ResolutionStatus.valueOf(resolution.getStatus().name())));
          }
        }).collect(Collectors.toList());
      return Map.entry(ideRelativePath, matches);
    }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @EventListener
  public void trackAnalysedIssues(AnalysisFinishedEvent event) {
    if (event.isTrackingEnabled()) {
      processEvent(event);
    }
  }

  private void processEvent(AnalysisFinishedEvent event) {
    var configurationScopeId = event.getConfigurationScopeId();
    var allIssues = event.getIssues();
    var nonHotspotIssues = allIssues.stream().filter(issue -> !issue.getRuleType().equals(org.sonarsource.sonarlint.core.commons.RuleType.SECURITY_HOTSPOT)).collect(toList());
    if (nonHotspotIssues.isEmpty()) {
      return;
    }
    var effectiveBindingOpt = configurationRepository.getEffectiveBinding(configurationScopeId);
    var activeBranchOpt = branchTrackingService.awaitEffectiveSonarProjectBranch(configurationScopeId);
    var translationOpt = pathTranslationService.getOrComputePathTranslation(configurationScopeId);
    var rawIssuesByIdeRelativePath = nonHotspotIssues.stream().filter(it -> Objects.nonNull(it.getIdeRelativePath()))
      .collect(Collectors.groupingBy(RawIssue::getIdeRelativePath, mapping(Function.identity(), toList())));
    Map<Path, List<TrackedIssue>> newIssues;
    var knownIssuesStore = knownIssuesStorageService.get();
    if (effectiveBindingOpt.isEmpty() || activeBranchOpt.isEmpty() || translationOpt.isEmpty()) {
      newIssues = rawIssuesByIdeRelativePath.entrySet().stream()
        .map(e -> Map.entry(e.getKey(), e.getValue().stream()
          .map(issue -> new TrackedIssue(UUID.randomUUID(), issue.getMessage(), null, false,
            issue.getSeverity(), issue.getRuleType(), issue.getRuleKey(), true,
            getTextRangeWithHash(issue.getTextRange(), issue.getClientInputFile()),
            getLineWithHash(issue.getTextRange(), issue.getClientInputFile()), null,
            issue.getImpacts(), issue.getFlows(), issue.getQuickFixes(), issue.getVulnerabilityProbability(),
            issue.getRuleDescriptionContextKey(), issue.getCleanCodeAttribute(), issue.getFileUri()))
          .collect(toList())))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      var updatedIssues = newIssues.entrySet().stream()
        .collect(toMap(Map.Entry::getKey, e -> {
          var previouslyKnownIssues = knownIssuesStore.loadForFile(configurationScopeId, e.getKey());
          if (previouslyKnownIssues == null || previouslyKnownIssues.isEmpty()) {
            return e.getValue();
          }
          var localIssueMatcher = new IssueMatcher<>(new KnownIssueMatchingAttributesMapper(), new TrackedIssueFindingMatchingAttributeMapper());
          var localMatchingResult = localIssueMatcher.match(previouslyKnownIssues, e.getValue());
          var matchedAndUnmatchedIssues = localMatchingResult.getMatchedLefts().entrySet().stream()
            .map(matchedEntry -> updateTrackedIssueWithPreviousTrackingData(matchedEntry.getKey(), matchedEntry.getValue())).collect(Collectors.toCollection(ArrayList::new));
          List<TrackedIssue> unmatchedIssues =
            StreamSupport.stream(localMatchingResult.getUnmatchedRights().spliterator(), false).collect(Collectors.toList());
          matchedAndUnmatchedIssues.addAll(unmatchedIssues);
          return matchedAndUnmatchedIssues;
        }));

      updatedIssues = setIntroductionDateAndNewCode(updatedIssues, true);

      updatedIssues.forEach((clientRelativePath, trackedIssues) -> storeTrackedIssues(knownIssuesStore, configurationScopeId, clientRelativePath, trackedIssues));
      var issuesToRaise = getIssuesToRaise(updatedIssues);
      client.raiseIssues(new RaiseIssuesParams(configurationScopeId, issuesToRaise, false, event.getAnalysisId()));
      return;
    }

    var binding = effectiveBindingOpt.get();
    var activeBranch = activeBranchOpt.get();
    var translation = translationOpt.get();
    if (event.isShouldFetchServerIssues()) {
      var issuesByPath = rawIssuesByIdeRelativePath.entrySet().stream().collect(toMap(Map.Entry::getKey,
        e -> e.getValue().stream().map(IssueMatchingService::toClientTrackedIssue).collect(toList())));
      refreshServerIssues(new SonarLintCancelMonitor(), binding, activeBranch, issuesByPath, translation);
    }
    var newCodeDefinition = newCodeService.getFullNewCodeDefinition(configurationScopeId)
      .orElse(NewCodeDefinition.withAlwaysNew());
    newIssues = rawIssuesByIdeRelativePath.entrySet().stream().map(e -> {
      var ideRelativePath = e.getKey();
      var serverRelativePath = translation.ideToServerPath(ideRelativePath);
      var serverIssues = storageService.binding(binding).findings().load(activeBranch, serverRelativePath);
      var localOnlyIssues = localOnlyIssueStorageService.get().loadForFile(configurationScopeId, serverRelativePath);
      var rawIssues = e.getValue();
      var previouslyKnownIssues = knownIssuesStore.loadForFile(configurationScopeId, e.getKey());
      List<TrackedIssue> allTrackedIssues;
      if (previouslyKnownIssues != null && !previouslyKnownIssues.isEmpty()) {
        var localIssueMatcher = new IssueMatcher<>(new KnownIssueMatchingAttributesMapper(), new RawIssueFindingMatchingAttributeMapper());
        var localMatchingResult = localIssueMatcher.match(previouslyKnownIssues, rawIssues);
        var newTrackedIssues = StreamSupport.stream(localMatchingResult.getUnmatchedRights().spliterator(), false)
          .map(IssueMapper::toTrackedIssue).collect(toSet());
        var updatedMatchedIssues = localMatchingResult.getMatchedLefts().entrySet().stream()
          .map(entry -> updateTrackedIssueWithRawIssueData(entry.getKey(), entry.getValue())).collect(toList());
        allTrackedIssues = new ArrayList<>(newTrackedIssues);
        allTrackedIssues.addAll(updatedMatchedIssues);
      } else {
        allTrackedIssues = rawIssues.stream()
          .map(IssueMapper::toTrackedIssue).collect(Collectors.toList());
      }
      var matches = newMatchIssues(serverRelativePath, serverIssues, localOnlyIssues, allTrackedIssues, newCodeDefinition);
      return Map.entry(ideRelativePath, matches);
    }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    newIssues = setIntroductionDateAndNewCode(newIssues, false);

    newIssues.forEach((clientRelativePath, trackedIssues) -> storeTrackedIssues(knownIssuesStore, configurationScopeId, clientRelativePath, trackedIssues));
    var issuesToRaise = getIssuesToRaise(newIssues);
    client.raiseIssues(new RaiseIssuesParams(configurationScopeId, issuesToRaise, false, event.getAnalysisId()));
  }

  private static Map<Path, List<TrackedIssue>> setIntroductionDateAndNewCode(Map<Path, List<TrackedIssue>> issueMap, boolean isStandalone) {
    var thresholdDate = Instant.now().minus(STANDALONE_NEW_CODE_PERIOD);
    var issuesByFileToBlame = getIssuesByFileToBlame(issueMap);
    var sonarLintBlameResultOpt = getSonarLintBlameResult(issuesByFileToBlame);

    return issueMap.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> e.getValue().stream().map(trackedIssue -> {
      var introductionDate = Optional.ofNullable(trackedIssue.getIntroductionDate())
        .orElse(sonarLintBlameResultOpt
          .map(sonarLintBlameResult -> determineIntroductionDate(e.getKey(), trackedIssue, sonarLintBlameResult))
          .orElse(Instant.now()));
      var isOnNewCode = isStandalone ? introductionDate.isAfter(thresholdDate) : trackedIssue.isOnNewCode();
      return copyIssueWithAdditionalValues(trackedIssue, introductionDate, isOnNewCode);
    }).collect(toList())));
  }

  private static Map<Path, List<TrackedIssue>> getIssuesByFileToBlame(Map<Path, List<TrackedIssue>> issueMap) {
    var fileToBeBlamedIssuesMap = issueMap.entrySet().stream()
      .collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
        .filter(liveFinding -> Objects.isNull(liveFinding.getIntroductionDate()))
        .collect(toList())));

    fileToBeBlamedIssuesMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    return fileToBeBlamedIssuesMap;
  }

  private static Path findBaseDir(Map.Entry<Path, List<TrackedIssue>> issueEntry) {
    var issue = issueEntry.getValue().get(0);
    var path = Path.of(issue.getFileUri());
    var relativeDepth = issueEntry.getKey().getNameCount();
    for (var i = 0; i < relativeDepth; i++) {
      path = path.getParent();
    }
    return path;
  }

  private static TrackedIssue copyIssueWithAdditionalValues(TrackedIssue trackedIssue, Instant introductionDate, boolean isOnNewCode) {
    return new TrackedIssue(trackedIssue.getId(), trackedIssue.getMessage(), introductionDate,
      trackedIssue.isResolved(), trackedIssue.getSeverity(), trackedIssue.getType(), trackedIssue.getRuleKey(),
      isOnNewCode, trackedIssue.getTextRangeWithHash(),
      trackedIssue.getLineWithHash(), trackedIssue.getServerKey(), trackedIssue.getImpacts(), trackedIssue.getFlows(),
      trackedIssue.getQuickFixes(), trackedIssue.getVulnerabilityProbability(), trackedIssue.getRuleDescriptionContextKey(),
      trackedIssue.getCleanCodeAttribute(), trackedIssue.getFileUri());
  }

  private static Instant determineIntroductionDate(Path path, TrackedIssue trackedIssue, SonarLintBlameResult sonarLintBlameResult) {
    return sonarLintBlameResult.getLatestChangeDateForLinesInFile(path, trackedIssue.getLineNumbers())
        .map(Date::toInstant)
        .orElse(Instant.now());
  }

  private static Optional<SonarLintBlameResult> getSonarLintBlameResult(Map<Path, List<TrackedIssue>> issueMap) {
    if (issueMap.isEmpty()) {
      return Optional.empty();
    }
    try {
      var baseDir = findBaseDir(issueMap.entrySet().iterator().next());
      return Optional.of(GitBlameUtils.blameWithFilesGitCommand(baseDir, issueMap.keySet()));
    } catch (Exception e) {
      LOG.debug("Change dates of found issues couldn't fetch from git. Introduction dates for new issues are setting as current time", e);
      return Optional.empty();
    }
  }

  public static Map<URI, List<RaisedIssueDto>> getIssuesToRaise(Map<Path, List<TrackedIssue>> updatedIssues) {
    return updatedIssues.values().stream().flatMap(Collection::stream)
      .collect(groupingBy(TrackedIssue::getFileUri, Collectors.mapping(DtoMapper::toRaisedIssueDto, Collectors.toList())));
  }

  private static void storeTrackedIssues(XodusKnownIssuesStore knownIssuesStore, String configurationScopeId, Path clientRelativePath, List<TrackedIssue> newKnownIssues) {
    knownIssuesStore.storeKnownIssues(configurationScopeId, clientRelativePath,
      newKnownIssues.stream().map(i -> new KnownIssue(i.getId(), i.getServerKey(), i.getTextRangeWithHash(), i.getLineWithHash(), i.getRuleKey(), i.getMessage(),
        i.getIntroductionDate())).collect(Collectors.toList()));
  }

  static ClientTrackedFindingDto toClientTrackedIssue(RawIssue issue) {
    return new ClientTrackedFindingDto(UUID.randomUUID(), null,
      TextRangeUtils.toTextRangeWithHashDto(issue.getTextRange(), issue.getClientInputFile()),
      TextRangeUtils.getLineWithHashDto(issue.getTextRange(), issue.getClientInputFile()), issue.getRuleKey(), issue.getMessage());
  }

  private void refreshServerIssues(SonarLintCancelMonitor cancelMonitor, Binding binding, String activeBranch,
    Map<Path, List<ClientTrackedFindingDto>> clientTrackedIssuesByIdeRelativePath, FilePathTranslation translation) {
    var serverFileRelativePaths = clientTrackedIssuesByIdeRelativePath.keySet()
      .stream().map(translation::serverToIdePath).collect(Collectors.toSet());
    var downloadAllIssuesAtOnce = serverFileRelativePaths.size() > FETCH_ALL_ISSUES_THRESHOLD;
    var fetchTasks = new LinkedList<CompletableFuture<?>>();
    if (downloadAllIssuesAtOnce) {
      fetchTasks.add(CompletableFuture.runAsync(() -> issueSynchronizationService.fetchProjectIssues(binding, activeBranch, cancelMonitor), executorService));
    } else {
      fetchTasks.addAll(serverFileRelativePaths.stream()
        .map(serverFileRelativePath -> CompletableFuture.runAsync(() -> issueSynchronizationService
          .fetchFileIssues(binding, serverFileRelativePath, activeBranch, cancelMonitor), executorService))
        .collect(Collectors.toList()));
    }
    CompletableFuture.allOf(fetchTasks.toArray(new CompletableFuture[0])).join();
  }

  private List<TrackedIssue> newMatchIssues(Path serverRelativePath, List<ServerIssue<?>> serverIssues,
    List<LocalOnlyIssue> localOnlyIssues, List<TrackedIssue> trackedIssues, NewCodeDefinition newCodeDefinition) {
    var serverIssueMatcher = new IssueMatcher<>(new TrackedIssueFindingMatchingAttributeMapper(), new ServerIssueMatchingAttributesMapper());
    var serverMatchingResult = serverIssueMatcher.match(trackedIssues, serverIssues);
    var localIssueMatcher = new IssueMatcher<>(new TrackedIssueFindingMatchingAttributeMapper(), new LocalOnlyIssueMatchingAttributesMapper());
    var localMatchingResult = localIssueMatcher.match(trackedIssues, localOnlyIssues);
    var matches = trackedIssues.stream().map(trackedIssue -> {
      var matchToServer = serverMatchingResult.getMatch(trackedIssue);
      if (matchToServer != null) {
        return updateTrackedIssueWithServerData(trackedIssue, matchToServer, newCodeDefinition);
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

  private static TrackedIssue updateTrackedIssueWithServerData(TrackedIssue trackedIssue, ServerIssue<?> serverIssue, NewCodeDefinition newCodeDefinition) {
    var serverSeverity = serverIssue.getUserSeverity();
    var severity = serverSeverity != null ? serverSeverity : trackedIssue.getSeverity();
    return new TrackedIssue(trackedIssue.getId(), trackedIssue.getMessage(), serverIssue.getCreationDate(),
      serverIssue.isResolved(), severity, serverIssue.getType(), serverIssue.getRuleKey(),
      newCodeDefinition.isOnNewCode(serverIssue.getCreationDate().toEpochMilli()), trackedIssue.getTextRangeWithHash(),
      trackedIssue.getLineWithHash(), serverIssue.getKey(), trackedIssue.getImpacts(), trackedIssue.getFlows(),
      trackedIssue.getQuickFixes(), trackedIssue.getVulnerabilityProbability(), trackedIssue.getRuleDescriptionContextKey(),
      trackedIssue.getCleanCodeAttribute(), trackedIssue.getFileUri());
  }

  private static TrackedIssue updateTrackedIssueWithRawIssueData(KnownIssue knownIssue, RawIssue rawIssue) {
    return new TrackedIssue(knownIssue.getId(), knownIssue.getMessage(), knownIssue.getIntroductionDate(),
      false, rawIssue.getSeverity(), rawIssue.getRuleType(), knownIssue.getRuleKey(),
      true,
      TextRangeUtils.getTextRangeWithHash(rawIssue.getTextRange(), rawIssue.getClientInputFile()),
      TextRangeUtils.getLineWithHash(rawIssue.getTextRange(), rawIssue.getClientInputFile()), knownIssue.getServerKey(),
      rawIssue.getImpacts(), rawIssue.getFlows(), rawIssue.getQuickFixes(), rawIssue.getVulnerabilityProbability(),
      rawIssue.getRuleDescriptionContextKey(), rawIssue.getCleanCodeAttribute(), rawIssue.getFileUri());
  }

  private static TrackedIssue updateTrackedIssueWithPreviousTrackingData(KnownIssue oldIssue, TrackedIssue newIssue) {
    return new TrackedIssue(oldIssue.getId(), newIssue.getMessage(), oldIssue.getIntroductionDate(),
      newIssue.isResolved(), newIssue.getSeverity(), newIssue.getType(), newIssue.getRuleKey(),
      newIssue.isOnNewCode(), newIssue.getTextRangeWithHash(),
      newIssue.getLineWithHash(), newIssue.getServerKey(), newIssue.getImpacts(), newIssue.getFlows(),
      newIssue.getQuickFixes(), newIssue.getVulnerabilityProbability(), newIssue.getRuleDescriptionContextKey(),
      newIssue.getCleanCodeAttribute(), newIssue.getFileUri());
  }

  private static TrackedIssue updateTrackedIssueWithLocalOnlyIssueData(TrackedIssue trackedIssue, LocalOnlyIssue localOnlyIssue) {
    return new TrackedIssue(trackedIssue.getId(), trackedIssue.getMessage(), trackedIssue.getIntroductionDate(),
      localOnlyIssue.getResolution() != null, trackedIssue.getSeverity(), trackedIssue.getType(), trackedIssue.getRuleKey(),
      true, trackedIssue.getTextRangeWithHash(),
      trackedIssue.getLineWithHash(), trackedIssue.getServerKey(), trackedIssue.getImpacts(), trackedIssue.getFlows(),
      trackedIssue.getQuickFixes(), trackedIssue.getVulnerabilityProbability(), trackedIssue.getRuleDescriptionContextKey(),
      trackedIssue.getCleanCodeAttribute(), trackedIssue.getFileUri());
  }

  private List<Either<ServerIssue<?>, LocalOnlyIssue>> matchIssues(Path serverRelativePath, List<ServerIssue<?>> serverIssues,
    List<LocalOnlyIssue> localOnlyIssues, List<ClientTrackedFindingDto> clientTrackedIssues) {
    var serverIssueMatcher = new IssueMatcher<>(new ClientTrackedFindingMatchingAttributeMapper(), new ServerIssueMatchingAttributesMapper());
    var serverMatchingResult = serverIssueMatcher.match(clientTrackedIssues, serverIssues);
    var localIssueMatcher = new IssueMatcher<>(new ClientTrackedFindingMatchingAttributeMapper(), new LocalOnlyIssueMatchingAttributesMapper());
    var localMatchingResult = localIssueMatcher.match(clientTrackedIssues, localOnlyIssues);
    var matches = clientTrackedIssues.stream().<Either<ServerIssue<?>, LocalOnlyIssue>>map(clientTrackedIssue -> {
      var matchToServer = serverMatchingResult.getMatch(clientTrackedIssue);
      if (matchToServer != null) {
        return Either.forLeft(matchToServer);
      } else {
        var matchToLocal = localMatchingResult.getMatch(clientTrackedIssue);
        return Either.forRight(Objects.requireNonNullElseGet(matchToLocal, () -> newLocalOnlyIssue(serverRelativePath, clientTrackedIssue)));
      }
    }).collect(Collectors.toList());
    var localOnlyIssuesMatched = matches.stream().filter(Either::isRight).map(Either::getRight).collect(Collectors.toList());
    localOnlyIssueRepository.save(serverRelativePath, localOnlyIssuesMatched);
    return matches;
  }

  @NotNull
  private static LocalOnlyIssue newLocalOnlyIssue(Path serverRelativePath, ClientTrackedFindingDto clientTrackedIssue) {
    return new LocalOnlyIssue(UUID.randomUUID(), serverRelativePath, adapt(clientTrackedIssue.getTextRangeWithHash()), adapt(clientTrackedIssue.getLineWithHash()),
      clientTrackedIssue.getRuleKey(), clientTrackedIssue.getMessage(), null);
  }

  @NotNull
  private static LocalOnlyIssue newLocalOnlyIssue(Path serverRelativePath, TrackedIssue issue) {
    return new LocalOnlyIssue(issue.getId(), serverRelativePath, issue.getTextRangeWithHash(), issue.getLineWithHash(), issue.getRuleKey(), issue.getMessage(), null);
  }

  @CheckForNull
  private static TextRangeWithHash adapt(@Nullable TextRangeWithHashDto textRange) {
    return textRange == null ? null
      : new TextRangeWithHash(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset(), textRange.getHash());
  }

  @CheckForNull
  private static LineWithHash adapt(@Nullable LineWithHashDto line) {
    return line == null ? null : new LineWithHash(line.getNumber(), line.getHash());
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop issue updater executor service in a timely manner");
    }
  }

}
