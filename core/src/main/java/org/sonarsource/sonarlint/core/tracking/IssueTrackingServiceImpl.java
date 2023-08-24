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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.ClientTrackedIssueDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.IssueTrackingService;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.LineWithHashDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.LocalOnlyIssueDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.ServerMatchedIssueDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackWithServerIssuesResponse;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;
import org.sonarsource.sonarlint.core.issuetracking.Tracker;
import org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueStorageService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.vcs.ActiveSonarProjectBranchRepository;
import org.sonarsource.sonarlint.core.serverconnection.StorageService;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.sync.SynchronizationServiceImpl;

import static org.sonarsource.sonarlint.core.utils.FutureUtils.waitForTask;
import static org.sonarsource.sonarlint.core.utils.FutureUtils.waitForTasks;

@Named
@Singleton
public class IssueTrackingServiceImpl implements IssueTrackingService {
  private static final int FETCH_ALL_ISSUES_THRESHOLD = 10;
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConfigurationRepository configurationRepository;
  private final StorageService storageService;
  private final ActiveSonarProjectBranchRepository activeSonarProjectBranchRepository;
  private final SynchronizationServiceImpl synchronizationService;
  private final LocalOnlyIssueRepository localOnlyIssueRepository;
  private final LocalOnlyIssueStorageService localOnlyIssueStorageService;
  private final ExecutorService executorService;

  public IssueTrackingServiceImpl(ConfigurationRepository configurationRepository, StorageService storageService,
    ActiveSonarProjectBranchRepository activeSonarProjectBranchRepository, SynchronizationServiceImpl synchronizationService,
    LocalOnlyIssueStorageService localOnlyIssueStorageService, LocalOnlyIssueRepository localOnlyIssueRepository) {
    this.configurationRepository = configurationRepository;
    this.storageService = storageService;
    this.activeSonarProjectBranchRepository = activeSonarProjectBranchRepository;
    this.synchronizationService = synchronizationService;
    this.localOnlyIssueRepository = localOnlyIssueRepository;
    this.localOnlyIssueStorageService = localOnlyIssueStorageService;
    this.executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "sonarlint-server-tracking-issue-updater"));
  }

  @Override
  public CompletableFuture<TrackWithServerIssuesResponse> trackWithServerIssues(TrackWithServerIssuesParams params) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      var effectiveBindingOpt = configurationRepository.getEffectiveBinding(params.getConfigurationScopeId());
      var activeBranchOpt = activeSonarProjectBranchRepository.getActiveSonarProjectBranch(params.getConfigurationScopeId());
      if (effectiveBindingOpt.isEmpty() || activeBranchOpt.isEmpty()) {
        return new TrackWithServerIssuesResponse(params.getClientTrackedIssuesByServerRelativePath().entrySet().stream()
          .map(e -> Map.entry(e.getKey(), e.getValue().stream()
            .<Either<ServerMatchedIssueDto, LocalOnlyIssueDto>>map(issue -> Either.forRight(new LocalOnlyIssueDto(UUID.randomUUID(), null))).collect(Collectors.toList())))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
      }
      var binding = effectiveBindingOpt.get();
      var activeBranch = activeBranchOpt.get();
      if (params.shouldFetchIssuesFromServer()) {
        refreshServerIssues(cancelChecker, binding, activeBranch, params);
      }
      var clientTrackedIssuesByServerRelativePath = params.getClientTrackedIssuesByServerRelativePath();
      return new TrackWithServerIssuesResponse(clientTrackedIssuesByServerRelativePath.entrySet().stream().map(e -> {
        var serverRelativePath = e.getKey();
        var serverIssues = storageService.binding(binding).findings().load(activeBranch, serverRelativePath);
        var localOnlyIssues = localOnlyIssueStorageService.get().loadForFile(params.getConfigurationScopeId(), serverRelativePath);
        var clientIssueTrackables = toTrackables(e.getValue());
        var matches = matchIssues(serverRelativePath, serverIssues, localOnlyIssues, clientIssueTrackables)
          .stream().<Either<ServerMatchedIssueDto, LocalOnlyIssueDto>>map(result -> {
            if (result.isLeft()) {
              var serverIssue = result.getLeft();
              return Either.forLeft(new ServerMatchedIssueDto(UUID.randomUUID(), serverIssue.getKey(), serverIssue.getCreationDate().toEpochMilli(), serverIssue.isResolved(),
                serverIssue.getUserSeverity(), serverIssue.getType()));
            } else {
              var localOnlyIssue = result.getRight();
              var resolution = localOnlyIssue.getResolution();
              return Either.forRight(new LocalOnlyIssueDto(localOnlyIssue.getId(), resolution == null ? null : ResolutionStatus.valueOf(resolution.getStatus().name())));
            }
          }).collect(Collectors.toList());
        return Map.entry(serverRelativePath, matches);
      }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    });
  }

  private void refreshServerIssues(CancelChecker cancelChecker, Binding binding, String activeBranch, TrackWithServerIssuesParams params) {
    var serverFileRelativePaths = params.getClientTrackedIssuesByServerRelativePath().keySet();
    var downloadAllIssuesAtOnce = serverFileRelativePaths.size() > FETCH_ALL_ISSUES_THRESHOLD;
    var fetchTasks = new LinkedList<Future<?>>();
    if (downloadAllIssuesAtOnce) {
      fetchTasks.add(executorService.submit(() -> synchronizationService.fetchProjectIssues(binding, activeBranch)));
    } else {
      fetchTasks.addAll(serverFileRelativePaths.stream()
        .map(serverFileRelativePath -> executorService.submit(() -> synchronizationService.fetchFileIssues(binding, serverFileRelativePath, activeBranch)))
        .collect(Collectors.toList()));
    }
    var waitForTasksTask = executorService.submit(() -> waitForTasks(cancelChecker, fetchTasks, "Wait for server issues", Duration.ofSeconds(20)));
    waitForTask(cancelChecker, waitForTasksTask, "Wait for server issues (global timeout)", Duration.ofSeconds(60));
  }

  private List<Either<ServerIssue, LocalOnlyIssue>> matchIssues(String serverRelativePath, List<ServerIssue> serverIssues,
    List<LocalOnlyIssue> localOnlyIssues, Collection<ClientTrackedIssueTrackable> clientTrackedIssueTrackables) {
    var tracker = new Tracker<>();
    var trackingResult = tracker.track(() -> new ArrayList<>(clientTrackedIssueTrackables),
      () -> mergeTrackables(toServerIssueTrackables(serverIssues), toLocalOnlyIssueTrackables(localOnlyIssues)));
    var matches = clientTrackedIssueTrackables.stream().<Either<ServerIssue, LocalOnlyIssue>>map(clientTrackedIssueTrackable -> {
      var match = trackingResult.getMatch(clientTrackedIssueTrackable);
      if (match != null) {
        if (match.getServerIssueKey() != null) {
          return Either.forLeft(((ServerIssueTrackable) match).getServerIssue());
        } else {
          return Either.forRight(((LocalOnlyIssueTrackable) match).getLocalOnlyIssue());
        }
      } else {
        var clientTrackedIssue = clientTrackedIssueTrackable.getClientTrackedIssue();
        return Either
          .forRight(new LocalOnlyIssue(UUID.randomUUID(), serverRelativePath, adapt(clientTrackedIssue.getTextRangeWithHash()), adapt(clientTrackedIssue.getLineWithHash()),
            clientTrackedIssue.getRuleKey(), clientTrackedIssue.getMessage(), null));
      }
    }).collect(Collectors.toList());
    var localOnlyIssuesMatched = matches.stream().filter(Either::isRight).map(Either::getRight).collect(Collectors.toList());
    localOnlyIssueRepository.save(serverRelativePath, localOnlyIssuesMatched);
    return matches;
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

  private static Collection<ClientTrackedIssueTrackable> toTrackables(List<ClientTrackedIssueDto> clientTrackedIssue) {
    return clientTrackedIssue.stream().map(ClientTrackedIssueTrackable::new).collect(Collectors.toList());
  }

  private static Collection<Trackable> mergeTrackables(Collection<Trackable> serverTrackables, Collection<Trackable> localOnlyTrackables) {
    return Stream.of(serverTrackables, localOnlyTrackables).flatMap(Collection::stream).collect(Collectors.toList());
  }

  private static Collection<Trackable> toServerIssueTrackables(List<ServerIssue> serverIssues) {
    return serverIssues.stream().map(ServerIssueTrackable::new).collect(Collectors.toList());
  }

  private static Collection<Trackable> toLocalOnlyIssueTrackables(List<LocalOnlyIssue> localOnlyIssues) {
    return localOnlyIssues.stream().map(LocalOnlyIssueTrackable::new).collect(Collectors.toList());
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop binding suggestions executor service in a timely manner");
    }
  }
}
