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
package org.sonarsource.sonarlint.core.sync;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.file.FilePathTranslation;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;

public class FindingsSynchronizationService {
  private static final int FETCH_ALL_ISSUES_THRESHOLD = 10;
  private final ConfigurationRepository configurationRepository;
  private final SonarProjectBranchTrackingService branchTrackingService;
  private final PathTranslationService pathTranslationService;
  private final IssueSynchronizationService issueSynchronizationService;
  private final HotspotSynchronizationService hotspotSynchronizationService;
  private final ExecutorService issueUpdaterExecutorService;

  public FindingsSynchronizationService(ConfigurationRepository configurationRepository, SonarProjectBranchTrackingService branchTrackingService,
    PathTranslationService pathTranslationService, IssueSynchronizationService issueSynchronizationService, HotspotSynchronizationService hotspotSynchronizationService) {
    this.configurationRepository = configurationRepository;
    this.branchTrackingService = branchTrackingService;
    this.pathTranslationService = pathTranslationService;
    this.issueSynchronizationService = issueSynchronizationService;
    this.hotspotSynchronizationService = hotspotSynchronizationService;
    this.issueUpdaterExecutorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "sonarlint-server-tracking-issue-updater"));
  }

  public void refreshServerFindings(String configurationScopeId, Set<Path> pathsToRefresh) {
    var effectiveBindingOpt = configurationRepository.getEffectiveBinding(configurationScopeId);
    var activeBranchOpt = branchTrackingService.awaitEffectiveSonarProjectBranch(configurationScopeId);
    var translationOpt = pathTranslationService.getOrComputePathTranslation(configurationScopeId);
    if (effectiveBindingOpt.isPresent() && activeBranchOpt.isPresent() && translationOpt.isPresent()) {
      var binding = effectiveBindingOpt.get();
      var activeBranch = activeBranchOpt.get();
      var translation = translationOpt.get();
      var cancelMonitor = new SonarLintCancelMonitor();
      refreshServerIssues(cancelMonitor, binding, activeBranch, pathsToRefresh, translation);
      refreshServerSecurityHotspots(cancelMonitor, binding, activeBranch, pathsToRefresh, translationOpt.get());
    }
  }

  private void refreshServerIssues(SonarLintCancelMonitor cancelMonitor, Binding binding, String activeBranch,
    Set<Path> pathsInvolved, FilePathTranslation translation) {
    var serverFileRelativePaths = pathsInvolved.stream().map(translation::ideToServerPath).collect(Collectors.toSet());
    var downloadAllIssuesAtOnce = serverFileRelativePaths.size() > FETCH_ALL_ISSUES_THRESHOLD;
    var fetchTasks = new LinkedList<CompletableFuture<?>>();
    if (downloadAllIssuesAtOnce) {
      fetchTasks.add(CompletableFuture.runAsync(() -> issueSynchronizationService.fetchProjectIssues(binding, activeBranch, cancelMonitor), issueUpdaterExecutorService));
    } else {
      fetchTasks.addAll(serverFileRelativePaths.stream()
        .map(serverFileRelativePath -> CompletableFuture.runAsync(() -> issueSynchronizationService
          .fetchFileIssues(binding, serverFileRelativePath, activeBranch, cancelMonitor), issueUpdaterExecutorService))
        .collect(Collectors.toList()));
    }
    CompletableFuture.allOf(fetchTasks.toArray(new CompletableFuture[0])).join();
  }

  private void refreshServerSecurityHotspots(SonarLintCancelMonitor cancelMonitor, Binding binding, String activeBranch,
    Set<Path> pathsInvolved, FilePathTranslation translation) {
    var serverFileRelativePaths = pathsInvolved.stream().map(translation::ideToServerPath).collect(Collectors.toSet());
    var downloadAllSecurityHotspotsAtOnce = serverFileRelativePaths.size() > FETCH_ALL_ISSUES_THRESHOLD;
    var fetchTasks = new LinkedList<CompletableFuture<?>>();
    if (downloadAllSecurityHotspotsAtOnce) {
      fetchTasks.add(CompletableFuture.runAsync(() -> hotspotSynchronizationService.fetchProjectHotspots(binding, activeBranch, cancelMonitor), issueUpdaterExecutorService));
    } else {
      fetchTasks.addAll(serverFileRelativePaths.stream()
        .map(serverFileRelativePath -> CompletableFuture
          .runAsync(() -> hotspotSynchronizationService.fetchFileHotspots(binding, activeBranch, serverFileRelativePath, cancelMonitor), issueUpdaterExecutorService))
        .collect(Collectors.toList()));
    }
    CompletableFuture.allOf(fetchTasks.toArray(new CompletableFuture[0])).join();
  }
}
