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
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.SonarServerEventReceivedEvent;
import org.sonarsource.sonarlint.core.file.FilePathTranslation;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.issue.matching.IssueMatcher;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LocalOnlySecurityHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ServerMatchedSecurityHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotClosedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotRaisedEvent;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.HotspotSynchronizationService;
import org.springframework.context.event.EventListener;

@Named
@Singleton
public class SecurityHotspotMatchingService {
  private static final int FETCH_ALL_SECURITY_HOTSPOTS_THRESHOLD = 10;
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final SonarLintRpcClient client;
  private final ConfigurationRepository configurationRepository;
  private final StorageService storageService;
  private final SonarProjectBranchTrackingService branchTrackingService;
  private final HotspotSynchronizationService hotspotSynchronizationService;
  private final PathTranslationService pathTranslationService;
  private final ExecutorService executorService;

  public SecurityHotspotMatchingService(SonarLintRpcClient client, ConfigurationRepository configurationRepository, StorageService storageService,
    SonarProjectBranchTrackingService branchTrackingService, HotspotSynchronizationService hotspotSynchronizationService,
    PathTranslationService pathTranslationService) {
    this.client = client;
    this.configurationRepository = configurationRepository;
    this.storageService = storageService;
    this.branchTrackingService = branchTrackingService;
    this.hotspotSynchronizationService = hotspotSynchronizationService;
    this.pathTranslationService = pathTranslationService;
    this.executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "sonarlint-server-tracking-hotspot-updater"));
  }

  public Map<Path, List<Either<ServerMatchedSecurityHotspotDto, LocalOnlySecurityHotspotDto>>> matchWithServerSecurityHotspots(String configurationScopeId,
    Map<Path, List<ClientTrackedFindingDto>> clientTrackedHotspotsByIdeRelativePath, boolean shouldFetchHotspotsFromServer, SonarLintCancelMonitor cancelMonitor) {
    var effectiveBindingOpt = configurationRepository.getEffectiveBinding(configurationScopeId);
    var activeBranchOpt = branchTrackingService.awaitEffectiveSonarProjectBranch(configurationScopeId);
    var translationOpt = pathTranslationService.getOrComputePathTranslation(configurationScopeId);
    if (effectiveBindingOpt.isEmpty() || activeBranchOpt.isEmpty() || translationOpt.isEmpty()) {
      return clientTrackedHotspotsByIdeRelativePath.entrySet().stream()
        .map(e -> Map.entry(e.getKey(), e.getValue().stream()
          .map(issue -> Either.<ServerMatchedSecurityHotspotDto, LocalOnlySecurityHotspotDto>forRight(
            new LocalOnlySecurityHotspotDto(UUID.randomUUID())))
          .collect(Collectors.toList())))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    var binding = effectiveBindingOpt.get();
    var activeBranch = activeBranchOpt.get();
    if (shouldFetchHotspotsFromServer) {
      refreshServerSecurityHotspots(cancelMonitor, binding, activeBranch, clientTrackedHotspotsByIdeRelativePath, translationOpt.get());
    }
    var newCodeDefinition = storageService.binding(binding).newCodeDefinition().read();
    return clientTrackedHotspotsByIdeRelativePath.entrySet().stream().map(e -> {
      var serverRelativePath = e.getKey();
      var serverHotspots = storageService.binding(binding).findings().loadHotspots(activeBranch, serverRelativePath);
      var matches = matchSecurityHotspots(serverHotspots, e.getValue())
        .stream().map(result -> {
          if (result.isLeft()) {
            var serverSecurityHotspot = result.getLeft();
            var creationDate = serverSecurityHotspot.getCreationDate();
            var isOnNewCode = newCodeDefinition.map(definition -> definition.isOnNewCode(creationDate.toEpochMilli())).orElse(true);
            return Either.<ServerMatchedSecurityHotspotDto, LocalOnlySecurityHotspotDto>forLeft(
              new ServerMatchedSecurityHotspotDto(UUID.randomUUID(), serverSecurityHotspot.getKey(), creationDate.toEpochMilli(),
                HotspotStatus.valueOf(serverSecurityHotspot.getStatus().name()), isOnNewCode));
          } else {
            return Either.<ServerMatchedSecurityHotspotDto, LocalOnlySecurityHotspotDto>forRight(new LocalOnlySecurityHotspotDto(result.getRight().getId()));
          }
        }).collect(Collectors.toList());
      return Map.entry(serverRelativePath, matches);
    }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private void refreshServerSecurityHotspots(SonarLintCancelMonitor cancelMonitor, Binding binding, String activeBranch,
    Map<Path, List<ClientTrackedFindingDto>> clientTrackedHotspotsByIdeRelativePath, FilePathTranslation translation) {
    var serverFileRelativePaths = clientTrackedHotspotsByIdeRelativePath.keySet()
      .stream().map(translation::ideToServerPath).collect(Collectors.toSet());
    var downloadAllSecurityHotspotsAtOnce = serverFileRelativePaths.size() > FETCH_ALL_SECURITY_HOTSPOTS_THRESHOLD;
    var fetchTasks = new LinkedList<CompletableFuture<?>>();
    if (downloadAllSecurityHotspotsAtOnce) {
      fetchTasks.add(CompletableFuture.runAsync(() -> hotspotSynchronizationService.fetchProjectHotspots(binding, activeBranch, cancelMonitor), executorService));
    } else {
      fetchTasks.addAll(serverFileRelativePaths.stream()
        .map(serverFileRelativePath ->
          CompletableFuture.runAsync(() -> hotspotSynchronizationService.fetchFileHotspots(binding, activeBranch, serverFileRelativePath, cancelMonitor), executorService))
        .collect(Collectors.toList()));
    }
    CompletableFuture.allOf(fetchTasks.toArray(new CompletableFuture[0])).join();
  }

  private static List<Either<ServerHotspot, LocalOnlySecurityHotspot>> matchSecurityHotspots(Collection<ServerHotspot> serverHotspots,
    List<ClientTrackedFindingDto> clientTrackedHotspots) {
    var matcher = new IssueMatcher<>(new ClientTrackedFindingMatchingAttributeMapper(), new ServerHotspotMatchingAttributesMapper());
    var matchingResult = matcher.match(clientTrackedHotspots, serverHotspots);
    return clientTrackedHotspots.stream().<Either<ServerHotspot, LocalOnlySecurityHotspot>>map(clientTrackedHotspot -> {
      var match = matchingResult.getMatch(clientTrackedHotspot);
      if (match != null) {
        return Either.forLeft(match);
      } else {
        return Either.forRight(new LocalOnlySecurityHotspot(UUID.randomUUID()));
      }
    }).collect(Collectors.toList());
  }

  @EventListener
  public void onServerEventReceived(SonarServerEventReceivedEvent event) {
    var connectionId = event.getConnectionId();
    var serverEvent = event.getEvent();
    if (serverEvent instanceof SecurityHotspotChangedEvent) {
      updateStorageAndNotifyClient(connectionId, (SecurityHotspotChangedEvent) serverEvent);
    } else if (serverEvent instanceof SecurityHotspotClosedEvent) {
      updateStorageAndNotifyClient(connectionId, (SecurityHotspotClosedEvent) serverEvent);
    } else if (serverEvent instanceof SecurityHotspotRaisedEvent) {
      updateStorageAndNotifyClient(connectionId, (SecurityHotspotRaisedEvent) serverEvent);
    }
  }

  private void updateStorageAndNotifyClient(String connectionId, SecurityHotspotRaisedEvent event) {
    var hotspot = new ServerHotspot(
      event.getHotspotKey(),
      event.getRuleKey(),
      event.getMainLocation().getMessage(),
      event.getMainLocation().getFilePath(),
      TaintVulnerabilityTrackingService.adapt(event.getMainLocation().getTextRange()),
      event.getCreationDate(),
      event.getStatus(),
      event.getVulnerabilityProbability(),
      null);
    var projectKey = event.getProjectKey();
    storageService.connection(connectionId).project(projectKey).findings().insert(event.getBranch(), hotspot);
    client.didReceiveServerHotspotEvent(new DidReceiveServerHotspotEvent(connectionId, projectKey, event.getFilePath()));
  }

  private void updateStorageAndNotifyClient(String connectionId, SecurityHotspotClosedEvent event) {
    var projectKey = event.getProjectKey();
    storageService.connection(connectionId).project(projectKey).findings().deleteHotspot(event.getHotspotKey());
    client.didReceiveServerHotspotEvent(new DidReceiveServerHotspotEvent(connectionId, projectKey, event.getFilePath()));
  }

  private void updateStorageAndNotifyClient(String connectionId, SecurityHotspotChangedEvent event) {
    var projectKey = event.getProjectKey();
    storageService.connection(connectionId).project(projectKey).findings().updateHotspot(event.getHotspotKey(), hotspot -> {
      var status = event.getStatus();
      if (status != null) {
        hotspot.setStatus(status);
      }
      var assignee = event.getAssignee();
      if (assignee != null) {
        hotspot.setAssignee(assignee);
      }
    });
    client.didReceiveServerHotspotEvent(new DidReceiveServerHotspotEvent(connectionId, projectKey, event.getFilePath()));
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop hotspot updater executor service in a timely manner");
    }
  }
}
