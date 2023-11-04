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
package org.sonarsource.sonarlint.core.branch;

import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.MatchedSonarProjectBranchChangedEvent;
import org.sonarsource.sonarlint.core.repository.branch.MatchedSonarProjectBranchRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidChangeActiveSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidVcsRepositoryChangeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.DidChangeMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.SonarProjectBranches;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

@Named
@Singleton
public class SonarProjectBranchTrackingService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final SonarLintRpcClient client;
  private final StorageService storageService;
  private final MatchedSonarProjectBranchRepository matchedSonarProjectBranchRepository;
  private final ConfigurationRepository configurationRepository;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final ExecutorService executorService;

  public SonarProjectBranchTrackingService(SonarLintRpcClient client, StorageService storageService, MatchedSonarProjectBranchRepository matchedSonarProjectBranchRepository,
                                           ConfigurationRepository configurationRepository,
                                           ApplicationEventPublisher applicationEventPublisher) {
    this.client = client;
    this.storageService = storageService;
    this.matchedSonarProjectBranchRepository = matchedSonarProjectBranchRepository;
    this.configurationRepository = configurationRepository;
    this.applicationEventPublisher = applicationEventPublisher;
    this.executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "sonarlint-branch-matcher"));
  }

  public void didChangeMatchedSonarProjectBranch(DidChangeActiveSonarProjectBranchParams params) {
    var newMatchedBranchName = params.getNewActiveBranchName();
    var configScopeId = params.getConfigScopeId();
    var previousBranchName = matchedSonarProjectBranchRepository.setMatchedBranchName(configScopeId, params.getNewActiveBranchName());
    if (!newMatchedBranchName.equals(previousBranchName)) {
      applicationEventPublisher.publishEvent(new MatchedSonarProjectBranchChangedEvent(configScopeId, newMatchedBranchName));
    }
  }

  public Optional<String> getEffectiveMatchedSonarProjectBranch(String configurationScopeId) {
    var currentConfigScopeId = configurationScopeId;
    do {
      var configurationScope = configurationRepository.getConfigurationScope(currentConfigScopeId);
      if (configurationScope == null) {
        // the scope might have been deleted in the meantime
        break;
      }
      var maybeBranch = matchedSonarProjectBranchRepository.getMatchedBranch(currentConfigScopeId);
      if (maybeBranch.isPresent()) {
        return maybeBranch;
      }
      currentConfigScopeId = configurationScope.getParentId();
    } while (currentConfigScopeId != null);
    return Optional.empty();
  }

  @EventListener
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent removedEvent) {
    var currentConfigScopeId = removedEvent.getRemovedConfigurationScopeId();
    matchedSonarProjectBranchRepository.clearMatchedBranch(currentConfigScopeId);
  }

  @EventListener
  public void onBindingChanged(BindingConfigChangedEvent bindingChanged) {
    var configScopeId = bindingChanged.getConfigScopeId();
    matchedSonarProjectBranchRepository.clearMatchedBranch(configScopeId);
    // a new matching will be triggered after this new binding has been synchronized
  }

  public void didVcsRepositoryChange(DidVcsRepositoryChangeParams params) {
    matchSonarProjectBranchAsync(params.getConfigurationScopeId());
  }

  private void matchSonarProjectBranchAsync(String configurationScopeId) {
    executorService.submit(() -> matchSonarProjectBranch(configurationScopeId));
  }

  // call this method when server branches changed during sync
  private void matchSonarProjectBranch(String configurationScopeId) {
    configurationRepository.getEffectiveBinding(configurationScopeId).ifPresent(binding -> {
      var previousSonarProjectBranch = matchedSonarProjectBranchRepository.getMatchedBranch(configurationScopeId).orElse(null);
      var branchesStorage = storageService.binding(binding).branches();
      if (!branchesStorage.exists()) {
        client.log(new LogParams(LogLevel.INFO, "Cannot match Sonar branch, storage is empty", configurationScopeId));
        return;
      }
      var storedBranches = branchesStorage.read();
      var sonarProjectBranches = new SonarProjectBranches(storedBranches.getMainBranchName(), storedBranches.getBranchNames());
      var matchedSonarBranch = requestClientToMatchSonarProjectBranch(configurationScopeId, sonarProjectBranches);
      if (matchedSonarBranch == null) {
        matchedSonarBranch = sonarProjectBranches.getMainBranchName();
      }
      if (!matchedSonarBranch.equals(previousSonarProjectBranch)) {
        matchedSonarProjectBranchRepository.setMatchedBranchName(configurationScopeId, matchedSonarBranch);
        client.didChangeMatchedSonarProjectBranch(
          new DidChangeMatchedSonarProjectBranchParams(configurationScopeId, matchedSonarBranch));
        applicationEventPublisher.publishEvent(new MatchedSonarProjectBranchChangedEvent(configurationScopeId, matchedSonarBranch));
      }
    });
  }

  @CheckForNull
  private String requestClientToMatchSonarProjectBranch(String configurationScopeId, SonarProjectBranches sonarProjectBranches) {
    try {
      return client.matchSonarProjectBranch(new MatchSonarProjectBranchParams(configurationScopeId, sonarProjectBranches))
        .get().getMatchedSonarProjectBranch();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted!", e);
    } catch (ExecutionException e) {
      LOG.warn("Unable to get matched branch from the client", e);
    }
    return null;
  }

  public GetMatchedSonarProjectBranchResponse getMatchedSonarProjectBranch(GetMatchedSonarProjectBranchParams params) {
    return new GetMatchedSonarProjectBranchResponse(matchedSonarProjectBranchRepository.getMatchedBranch(params.getConfigurationScopeId()).orElse(null));
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop branch matching executor service in a timely manner");
    }
  }
}
