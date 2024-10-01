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
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.SonarServerEventReceivedEvent;
import org.sonarsource.sonarlint.core.file.FilePathTranslation;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.reporting.FindingReportingService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LocalOnlySecurityHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ServerMatchedSecurityHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotClosedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotRaisedEvent;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.HotspotSynchronizationService;
import org.sonarsource.sonarlint.core.tracking.matching.ClientTrackedFindingMatchingAttributeMapper;
import org.sonarsource.sonarlint.core.tracking.matching.IssueMatcher;
import org.sonarsource.sonarlint.core.tracking.matching.ServerHotspotMatchingAttributesMapper;
import org.springframework.context.event.EventListener;

import static java.util.stream.Collectors.toMap;

@Named
@Singleton
public class SecurityHotspotMatchingService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConfigurationRepository configurationRepository;
  private final StorageService storageService;
  private final FindingReportingService findingReportingService;
  private final ExecutorService executorService;

  public SecurityHotspotMatchingService(ConfigurationRepository configurationRepository, StorageService storageService,  FindingReportingService findingReportingService) {
    this.configurationRepository = configurationRepository;
    this.storageService = storageService;
    this.findingReportingService = findingReportingService;
    this.executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "sonarlint-server-tracking-hotspot-updater"));
  }

  @EventListener
  public void onServerEventReceived(SonarServerEventReceivedEvent event) {
    var connectionId = event.getConnectionId();
    var serverEvent = event.getEvent();
    if (serverEvent instanceof SecurityHotspotChangedEvent) {
      var hotspotChangedEvent = (SecurityHotspotChangedEvent) serverEvent;
      updateStorage(connectionId, hotspotChangedEvent);
      republishPreviouslyRaisedHotspots(connectionId, hotspotChangedEvent);
    } else if (serverEvent instanceof SecurityHotspotClosedEvent) {
      var hotspotClosedEvent = (SecurityHotspotClosedEvent) serverEvent;
      updateStorage(connectionId, hotspotClosedEvent);
      republishPreviouslyRaisedHotspots(connectionId, hotspotClosedEvent);
    } else if (serverEvent instanceof SecurityHotspotRaisedEvent) {
      var hotspotRaisedEvent = (SecurityHotspotRaisedEvent) serverEvent;
      // We could try to match with an existing hotspot. But we don't do it because we don't invest in hotspots right now.
      updateStorage(connectionId, hotspotRaisedEvent);
    }
  }

  private void updateStorage(String connectionId, SecurityHotspotRaisedEvent event) {
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
  }

  private void updateStorage(String connectionId, SecurityHotspotClosedEvent event) {
    var projectKey = event.getProjectKey();
    storageService.connection(connectionId).project(projectKey).findings().deleteHotspot(event.getHotspotKey());
  }

  private void updateStorage(String connectionId, SecurityHotspotChangedEvent event) {
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
  }

  private void republishPreviouslyRaisedHotspots(String connectionId, SecurityHotspotChangedEvent event) {
    var boundScopes = configurationRepository.getBoundScopesToConnectionAndSonarProject(connectionId, event.getProjectKey());
    boundScopes.forEach(scope -> {
      var scopeId = scope.getConfigScopeId();
      findingReportingService.updateAndReportHotspots(scopeId,
        raisedHotspotDto -> changedHotspotUpdater(raisedHotspotDto, event));
    });
  }

  private static RaisedHotspotDto changedHotspotUpdater(RaisedHotspotDto raisedHotspotDto, SecurityHotspotChangedEvent event) {
    if (event.getHotspotKey().equals(raisedHotspotDto.getServerKey())) {
      return raisedHotspotDto.builder().withHotspotStatus(HotspotStatus.valueOf(event.getStatus().name())).buildHotspot();
    }
    return raisedHotspotDto;
  }

  private void republishPreviouslyRaisedHotspots(String connectionId, SecurityHotspotClosedEvent event) {
    var boundScopes = configurationRepository.getBoundScopesToConnectionAndSonarProject(connectionId, event.getProjectKey());
    boundScopes.forEach(scope -> {
      var scopeId = scope.getConfigScopeId();
      findingReportingService.updateAndReportHotspots(scopeId,
        raisedHotspotDto -> closedHotspotUpdater(raisedHotspotDto, event));
    });
  }

  private static RaisedHotspotDto closedHotspotUpdater(RaisedHotspotDto raisedHotspotDto, SecurityHotspotClosedEvent event) {
    if (event.getHotspotKey().equals(raisedHotspotDto.getServerKey())) {
      return null;
    }
    return raisedHotspotDto;
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop hotspot updater executor service in a timely manner");
    }
  }
}
