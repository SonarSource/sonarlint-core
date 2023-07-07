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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.IssueTrackingService;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.LocallyTrackedIssueDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.NotTrackedIssueDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackWithServerIssuesResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackedIssueDto;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;
import org.sonarsource.sonarlint.core.issuetracking.Tracker;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.vcs.ActiveSonarProjectBranchRepository;
import org.sonarsource.sonarlint.core.serverconnection.StorageService;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.sync.SynchronizationServiceImpl;

import static java.util.Objects.requireNonNull;
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
  private final ExecutorService executorService;

  public IssueTrackingServiceImpl(ConfigurationRepository configurationRepository, StorageService storageService,
    ActiveSonarProjectBranchRepository activeSonarProjectBranchRepository, SynchronizationServiceImpl synchronizationService) {
    this.configurationRepository = configurationRepository;
    this.storageService = storageService;
    this.activeSonarProjectBranchRepository = activeSonarProjectBranchRepository;
    this.synchronizationService = synchronizationService;
    this.executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "sonarlint-server-tracking-issue-updater"));
  }

  @Override
  public CompletableFuture<TrackWithServerIssuesResponse> trackWithServerIssues(TrackWithServerIssuesParams params) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      var effectiveBindingOpt = configurationRepository.getEffectiveBinding(params.getConfigurationScopeId());
      var activeBranchOpt = activeSonarProjectBranchRepository.getActiveSonarProjectBranch(params.getConfigurationScopeId());
      if (effectiveBindingOpt.isEmpty() || activeBranchOpt.isEmpty()) {
        return new TrackWithServerIssuesResponse(params.getLocallyTrackedIssuesByServerRelativePath().entrySet().stream()
          .map(e -> Map.entry(e.getKey(),
            e.getValue().stream().<Either<TrackedIssueDto, NotTrackedIssueDto>>map(issue -> Either.forRight(new NotTrackedIssueDto())).collect(Collectors.toList())))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
      }
      var binding = effectiveBindingOpt.get();
      var activeBranch = activeBranchOpt.get();
      if (params.shouldFetchIssuesFromServer()) {
        refreshServerIssues(cancelChecker, binding, activeBranch, params);
      }
      var rawIssuesByServerRelativePath = params.getLocallyTrackedIssuesByServerRelativePath();
      return new TrackWithServerIssuesResponse(rawIssuesByServerRelativePath.entrySet().stream().map(e -> {
        var serverIssues = storageService.binding(binding).findings().load(activeBranch, e.getKey());
        var rawIssues = e.getValue();
        var rawIssueTrackables = toTrackables(rawIssues);
        var trackedIssues = getTrackedIssues(serverIssues, rawIssueTrackables);
        return Map.entry(e.getKey(), trackedIssues);
      }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    });
  }

  private void refreshServerIssues(CancelChecker cancelChecker, Binding binding, String activeBranch, TrackWithServerIssuesParams params) {
    var serverFileRelativePaths = params.getLocallyTrackedIssuesByServerRelativePath().keySet();
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

  @NotNull
  private static List<Either<TrackedIssueDto, NotTrackedIssueDto>> getTrackedIssues(List<ServerIssue> serverIssues,
    Collection<LocallyTrackedIssueTrackable> locallyTrackedIssueTrackables) {
    var tracker = new Tracker<>();
    var trackingResult = tracker.track(() -> new ArrayList<>(locallyTrackedIssueTrackables), () -> toServerIssueTrackables(serverIssues));
    return locallyTrackedIssueTrackables.stream().<Either<TrackedIssueDto, NotTrackedIssueDto>>map(locallyTrackedIssueTrackable -> {
      var match = trackingResult.getMatch(locallyTrackedIssueTrackable);
      if (match != null) {
        return Either.forLeft(new TrackedIssueDto(match.getServerIssueKey(), requireNonNull(match.getCreationDate()), match.isResolved(), match.getSeverity(), match.getType()));
      } else {
        return Either.forRight(new NotTrackedIssueDto());
      }
    }).collect(Collectors.toList());
  }

  private static Collection<LocallyTrackedIssueTrackable> toTrackables(List<LocallyTrackedIssueDto> locallyTrackedIssue) {
    return locallyTrackedIssue.stream().map(LocallyTrackedIssueTrackable::new).collect(Collectors.toList());
  }

  private static Collection<Trackable> toServerIssueTrackables(List<ServerIssue> serverIssues) {
    return serverIssues.stream().map(ServerIssueTrackable::new).collect(Collectors.toList());
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop binding suggestions executor service in a timely manner");
    }
  }
}
