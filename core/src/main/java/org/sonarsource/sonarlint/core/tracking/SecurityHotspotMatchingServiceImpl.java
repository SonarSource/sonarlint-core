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
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.LocalOnlySecurityHotspotDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.MatchWithServerSecurityHotspotsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.MatchWithServerSecurityHotspotsResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.SecurityHotspotMatchingService;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.ServerMatchedSecurityHotspotDto;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;
import org.sonarsource.sonarlint.core.issuetracking.Tracker;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.vcs.ActiveSonarProjectBranchRepository;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.SynchronizationServiceImpl;

import static org.sonarsource.sonarlint.core.utils.FutureUtils.waitForTask;
import static org.sonarsource.sonarlint.core.utils.FutureUtils.waitForTasks;

@Named
@Singleton
public class SecurityHotspotMatchingServiceImpl implements SecurityHotspotMatchingService {
  private static final int FETCH_ALL_SECURITY_HOTSPOTS_THRESHOLD = 10;
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConfigurationRepository configurationRepository;
  private final StorageService storageService;
  private final ActiveSonarProjectBranchRepository activeSonarProjectBranchRepository;
  private final SynchronizationServiceImpl synchronizationService;
  private final ExecutorService executorService;

  public SecurityHotspotMatchingServiceImpl(ConfigurationRepository configurationRepository, StorageService storageService,
    ActiveSonarProjectBranchRepository activeSonarProjectBranchRepository, SynchronizationServiceImpl synchronizationService) {
    this.configurationRepository = configurationRepository;
    this.storageService = storageService;
    this.activeSonarProjectBranchRepository = activeSonarProjectBranchRepository;
    this.synchronizationService = synchronizationService;
    this.executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "sonarlint-server-tracking-hotspot-updater"));
  }

  @Override
  public CompletableFuture<MatchWithServerSecurityHotspotsResponse> matchWithServerSecurityHotspots(MatchWithServerSecurityHotspotsParams params) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      var configurationScopeId = params.getConfigurationScopeId();
      var effectiveBindingOpt = configurationRepository.getEffectiveBinding(configurationScopeId);
      var activeBranchOpt = activeSonarProjectBranchRepository.getActiveSonarProjectBranch(configurationScopeId);
      if (effectiveBindingOpt.isEmpty() || activeBranchOpt.isEmpty()) {
        return new MatchWithServerSecurityHotspotsResponse(params.getClientTrackedHotspotsByServerRelativePath().entrySet().stream()
          .map(e -> Map.entry(e.getKey(), e.getValue().stream()
            .<Either<ServerMatchedSecurityHotspotDto, LocalOnlySecurityHotspotDto>>map(issue -> Either.forRight(new LocalOnlySecurityHotspotDto(UUID.randomUUID())))
            .collect(Collectors.toList())))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
      }
      var binding = effectiveBindingOpt.get();
      var activeBranch = activeBranchOpt.get();
      if (params.shouldFetchHotspotsFromServer()) {
        refreshServerSecurityHotspots(cancelChecker, binding, activeBranch, params);
      }
      var newCodeDefinition = storageService.binding(binding).newCodeDefinition().read();
      var clientTrackedIssuesByServerRelativePath = params.getClientTrackedHotspotsByServerRelativePath();
      return new MatchWithServerSecurityHotspotsResponse(clientTrackedIssuesByServerRelativePath.entrySet().stream().map(e -> {
        var serverRelativePath = e.getKey();
        var serverHotspots = storageService.binding(binding).findings().loadHotspots(activeBranch, serverRelativePath);
        var clientHotspotTrackables = toTrackables(e.getValue());
        var matches = matchSecurityHotspots(serverHotspots, clientHotspotTrackables)
          .stream().<Either<ServerMatchedSecurityHotspotDto, LocalOnlySecurityHotspotDto>>map(result -> {
            if (result.isLeft()) {
              var serverSecurityHotspot = result.getLeft();
              var creationDate = serverSecurityHotspot.getCreationDate();
              var isOnNewCode = newCodeDefinition.map(definition -> definition.isOnNewCode(creationDate.toEpochMilli())).orElse(true);
              return Either.forLeft(new ServerMatchedSecurityHotspotDto(UUID.randomUUID(), serverSecurityHotspot.getKey(), creationDate.toEpochMilli(),
                HotspotStatus.valueOf(serverSecurityHotspot.getStatus().name()), isOnNewCode));
            } else {
              return Either.forRight(new LocalOnlySecurityHotspotDto(result.getRight().getId()));
            }
          }).collect(Collectors.toList());
        return Map.entry(serverRelativePath, matches);
      }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    });
  }

  private void refreshServerSecurityHotspots(CancelChecker cancelChecker, Binding binding, String activeBranch, MatchWithServerSecurityHotspotsParams params) {
    var serverFileRelativePaths = params.getClientTrackedHotspotsByServerRelativePath().keySet();
    var downloadAllSecurityHotspotsAtOnce = serverFileRelativePaths.size() > FETCH_ALL_SECURITY_HOTSPOTS_THRESHOLD;
    var fetchTasks = new LinkedList<Future<?>>();
    if (downloadAllSecurityHotspotsAtOnce) {
      fetchTasks.add(executorService.submit(() -> synchronizationService.fetchProjectHotspots(binding, activeBranch)));
    } else {
      fetchTasks.addAll(serverFileRelativePaths.stream()
        .map(serverFileRelativePath -> executorService.submit(() -> synchronizationService.fetchFileHotspots(binding, activeBranch, serverFileRelativePath)))
        .collect(Collectors.toList()));
    }
    var waitForTasksTask = executorService.submit(() -> waitForTasks(cancelChecker, fetchTasks, "Wait for server hotspots", Duration.ofSeconds(20)));
    waitForTask(cancelChecker, waitForTasksTask, "Wait for server hotspots (global timeout)", Duration.ofSeconds(60));
  }

  private static List<Either<ServerHotspot, LocalOnlySecurityHotspot>> matchSecurityHotspots(Collection<ServerHotspot> serverHotspots,
    Collection<ClientTrackedFindingTrackable> clientTrackedHotspotTrackables) {
    var tracker = new Tracker<>();
    var trackingResult = tracker.track(() -> new ArrayList<>(clientTrackedHotspotTrackables), () -> toServerHotspotTrackables(serverHotspots));
    return clientTrackedHotspotTrackables.stream().<Either<ServerHotspot, LocalOnlySecurityHotspot>>map(clientTrackedFindingTrackable -> {
      var match = trackingResult.getMatch(clientTrackedFindingTrackable);
      if (match != null) {
        return Either.forLeft(((ServerHotspotTrackable) match).getServerHotspot());
      } else {
        return Either.forRight(new LocalOnlySecurityHotspot(UUID.randomUUID()));
      }
    }).collect(Collectors.toList());
  }

  private static Collection<ClientTrackedFindingTrackable> toTrackables(List<ClientTrackedFindingDto> clientTrackedFindings) {
    return clientTrackedFindings.stream().map(ClientTrackedFindingTrackable::new).collect(Collectors.toList());
  }

  private static Collection<Trackable> toServerHotspotTrackables(Collection<ServerHotspot> serverHotspots) {
    return serverHotspots.stream().map(ServerHotspotTrackable::new).collect(Collectors.toList());
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop hotspot updater executor service in a timely manner");
    }
  }
}
