/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.tracking;

import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.NewCodeDefinition;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.file.FilePathTranslation;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.issue.matching.IssueMatcher;
import org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueStorageService;
import org.sonarsource.sonarlint.core.newcode.NewCodeService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LineWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LocalOnlyIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ServerMatchedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rules.RuleDetailsAdapter;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.IssueSynchronizationService;
import org.sonarsource.sonarlint.core.utils.FutureUtils;

import static org.sonarsource.sonarlint.core.utils.FutureUtils.waitForTasks;

@Named
@Singleton
public class IssueMatchingService {
  private static final int FETCH_ALL_ISSUES_THRESHOLD = 10;
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConfigurationRepository configurationRepository;
  private final StorageService storageService;
  private final SonarProjectBranchTrackingService branchTrackingService;
  private final IssueSynchronizationService issueSynchronizationService;
  private final LocalOnlyIssueRepository localOnlyIssueRepository;
  private final LocalOnlyIssueStorageService localOnlyIssueStorageService;
  private final NewCodeService newCodeService;
  private final PathTranslationService pathTranslationService;
  private final ExecutorService executorService;

  public IssueMatchingService(ConfigurationRepository configurationRepository, StorageService storageService,
    SonarProjectBranchTrackingService branchTrackingService, IssueSynchronizationService issueSynchronizationService,
    LocalOnlyIssueStorageService localOnlyIssueStorageService, LocalOnlyIssueRepository localOnlyIssueRepository,
    NewCodeService newCodeService, PathTranslationService pathTranslationService) {
    this.configurationRepository = configurationRepository;
    this.storageService = storageService;
    this.branchTrackingService = branchTrackingService;
    this.issueSynchronizationService = issueSynchronizationService;
    this.localOnlyIssueRepository = localOnlyIssueRepository;
    this.localOnlyIssueStorageService = localOnlyIssueStorageService;
    this.newCodeService = newCodeService;
    this.pathTranslationService = pathTranslationService;
    this.executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "sonarlint-server-tracking-issue-updater"));
  }

  public Map<Path, List<Either<ServerMatchedIssueDto, LocalOnlyIssueDto>>> trackWithServerIssues(String configurationScopeId,
    Map<Path, List<ClientTrackedFindingDto>> clientTrackedIssuesByIdeRelativePath,
    boolean shouldFetchIssuesFromServer, CancelChecker cancelChecker) {
    var effectiveBindingOpt = configurationRepository.getEffectiveBinding(configurationScopeId);
    var activeBranchOpt = branchTrackingService.awaitEffectiveSonarProjectBranch(configurationScopeId);
    var translationOpt = pathTranslationService.getOrComputePathTranslation(configurationScopeId);
    if (effectiveBindingOpt.isEmpty() || activeBranchOpt.isEmpty() || translationOpt.isEmpty()) {
      return clientTrackedIssuesByIdeRelativePath.entrySet().stream()
        .map(e -> Map.entry(e.getKey(), e.getValue().stream()
          .map(issue -> Either.<ServerMatchedIssueDto, LocalOnlyIssueDto>forRight(
            new LocalOnlyIssueDto(UUID.randomUUID(), null))).collect(Collectors.toList())))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    var binding = effectiveBindingOpt.get();
    var activeBranch = activeBranchOpt.get();
    var translation = translationOpt.get();
    if (shouldFetchIssuesFromServer) {
      refreshServerIssues(cancelChecker, binding, activeBranch, clientTrackedIssuesByIdeRelativePath, translation);
    }
    var newCodeDefinition = newCodeService.getFullNewCodeDefinition(configurationScopeId)
      .orElse(NewCodeDefinition.withAlwaysNew());
    return clientTrackedIssuesByIdeRelativePath.entrySet().stream().map(e -> {
      var serverRelativePath = translation.ideToServerPath(e.getKey());
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
      return Map.entry(serverRelativePath, matches);
    }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private void refreshServerIssues(CancelChecker cancelChecker, Binding binding, String activeBranch,
    Map<Path, List<ClientTrackedFindingDto>> clientTrackedIssuesByIdeRelativePath, FilePathTranslation translation) {
    var serverFileRelativePaths = clientTrackedIssuesByIdeRelativePath.keySet()
      .stream().map(translation::serverToIdePath).collect(Collectors.toSet());
    var downloadAllIssuesAtOnce = serverFileRelativePaths.size() > FETCH_ALL_ISSUES_THRESHOLD;
    var fetchTasks = new LinkedList<Future<?>>();
    if (downloadAllIssuesAtOnce) {
      fetchTasks.add(executorService.submit(() -> issueSynchronizationService.fetchProjectIssues(binding, activeBranch)));
    } else {
      fetchTasks.addAll(serverFileRelativePaths.stream()
        .map(serverFileRelativePath -> executorService.submit(() -> issueSynchronizationService.fetchFileIssues(binding, serverFileRelativePath, activeBranch)))
        .collect(Collectors.toList()));
    }
    var waitForTasksTask = executorService.submit(() -> waitForTasks(cancelChecker, fetchTasks, "Wait for server issues", Duration.ofSeconds(20)));
    FutureUtils.waitForTask(cancelChecker, waitForTasksTask, "Wait for server issues (global timeout)", Duration.ofSeconds(60));
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
