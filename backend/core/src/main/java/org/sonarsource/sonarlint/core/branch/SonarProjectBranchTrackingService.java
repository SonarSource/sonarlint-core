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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.DidChangeMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.SonarProjectBranchesChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

/**
 * This service keep track of the currently matched Sonar project branch for each configuration scope.
 */
@Named
@Singleton
public class SonarProjectBranchTrackingService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final SonarLintRpcClient client;
  private final StorageService storageService;
  private final ConfigurationRepository configurationRepository;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "sonarlint-branch-matcher"));
  private final Map<String, Future<?>> matchingJobPerConfigScopeId = new ConcurrentHashMap<>();
  private final Map<String, CompletableFuture<String>> matchingResultPerConfigScopeId = new ConcurrentHashMap<>();

  public SonarProjectBranchTrackingService(SonarLintRpcClient client, StorageService storageService,
    ConfigurationRepository configurationRepository, ApplicationEventPublisher applicationEventPublisher) {
    this.client = client;
    this.storageService = storageService;
    this.configurationRepository = configurationRepository;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  public Optional<String> awaitEffectiveSonarProjectBranch(String configurationScopeId) {
    var currentConfigScopeId = configurationScopeId;
    do {
      var configurationScope = configurationRepository.getConfigurationScope(currentConfigScopeId);
      if (configurationScope == null) {
        // the scope might have been deleted in the meantime
        break;
      }
      var maybeBranchResult = matchingResultPerConfigScopeId.get(currentConfigScopeId);
      if (maybeBranchResult == null) {
        maybeBranchResult = queueBranchMatching(currentConfigScopeId);
      }
      try {
        var maybeBranch = maybeBranchResult.get();
        if (maybeBranch != null) {
          return Optional.of(maybeBranch);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.debug("Interrupted!", e);
        return Optional.empty();
      } catch (ExecutionException e) {
        LOG.error("Error while matching Sonar project branch for configuration scope '{}'", currentConfigScopeId, e);
        return Optional.empty();
      }
      currentConfigScopeId = configurationScope.getParentId();
    } while (currentConfigScopeId != null);
    return Optional.empty();
  }

  @EventListener
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent event) {
    var removedConfigScopeId = event.getRemovedConfigurationScopeId();
    LOG.debug("Configuration scope '{}' removed, clearing matched branch", removedConfigScopeId);
    cancelAndClear(removedConfigScopeId);
  }

  @EventListener
  public void onConfigurationScopesAdded(ConfigurationScopesAddedEvent event) {
    var configScopeIds = event.getAddedConfigurationScopeIds();
    configScopeIds.forEach(configScopeId -> {
      var effectiveBinding = configurationRepository.getEffectiveBinding(configScopeId);
      if (effectiveBinding.isPresent()) {
        var branchesStorage = storageService.binding(effectiveBinding.get()).branches();
        if (branchesStorage.exists()) {
          LOG.debug("Bound configuration scope '{}' added with an existing storage, queuing matching of the Sonar project branch...", configScopeId);
          queueBranchMatching(configScopeId);
        }
      }
    });
  }

  @EventListener
  public void onBindingChanged(BindingConfigChangedEvent bindingChanged) {
    var configScopeId = bindingChanged.getConfigScopeId();
    if (!bindingChanged.getNewConfig().isBound()) {
      LOG.debug("Configuration scope '{}' unbound, clearing matched branch", configScopeId);
      cancelAndClear(configScopeId);
    } else {
      LOG.debug("Configuration scope '{}' binding changed, queuing matching of the Sonar project branch...", configScopeId);
      queueBranchMatching(configScopeId);
    }
  }

  @EventListener
  public void onSonarProjectBranchChanged(SonarProjectBranchesChangedEvent event) {
    var configScopeIds = configurationRepository.getBoundScopesToConnectionAndSonarProject(event.getConnectionId(), event.getSonarProjectKey());
    configScopeIds.forEach(boundScope -> {
      LOG.debug("Sonar project branch changed for configuration scope '{}', queuing matching of the Sonar project branch...", boundScope.getConfigScopeId());
      queueBranchMatching(boundScope.getConfigScopeId());
    });
  }

  private void cancelAndClear(String configScopeId) {
    var futureJob = matchingJobPerConfigScopeId.remove(configScopeId);
    if (futureJob != null) {
      futureJob.cancel(true);
    }
    clearResult(configScopeId);
  }

  private void clearResult(String configScopeId) {
    synchronized (matchingResultPerConfigScopeId) {
      var existing = matchingResultPerConfigScopeId.remove(configScopeId);
      if (existing != null && !existing.isDone()) {
        existing.complete(null);
      }
    }
  }

  public void didVcsRepositoryChange(String configurationScopeId) {
    LOG.debug("VCS repository changed for configuration scope '{}', queuing matching of the Sonar project branch...", configurationScopeId);
    queueBranchMatching(configurationScopeId);
  }

  private CompletableFuture<String> queueBranchMatching(String configurationScopeId) {
    synchronized (matchingJobPerConfigScopeId) {
      var future = matchingResultPerConfigScopeId.computeIfAbsent(configurationScopeId, k -> new CompletableFuture<>());
      var previousMatchingJob = matchingJobPerConfigScopeId.get(configurationScopeId);
      if (previousMatchingJob != null && previousMatchingJob.cancel(true)) {
        LOG.debug("Cancelled previous matching job for configuration scope '{}'", configurationScopeId);
        try {
          previousMatchingJob.get();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return future;
        } catch (CancellationException expected) {
          LOG.debug("Cancellation successful");
        } catch (ExecutionException e) {
          LOG.debug("Failed to cancel previous matching job for configuration scope '{}'", configurationScopeId, e);
        }
      }
      matchingJobPerConfigScopeId.put(configurationScopeId, executorService.submit(() -> {
        try {
          matchSonarProjectBranch(configurationScopeId);
        } catch (Exception e) {
          LOG.error("Error while matching Sonar project branch for configuration scope '{}'", configurationScopeId, e);
        }
      }));
      return future;
    }
  }

  private void matchSonarProjectBranch(String configurationScopeId) {
    LOG.debug("Matching Sonar project branch for configuration scope '{}'", configurationScopeId);
    var effectiveBindingOpt = configurationRepository.getEffectiveBinding(configurationScopeId);
    if (effectiveBindingOpt.isEmpty()) {
      LOG.debug("No binding for configuration scope '{}'", configurationScopeId);
      clearResult(configurationScopeId);
      return;
    }
    var effectiveBinding = effectiveBindingOpt.get();

    var previousSonarProjectBranch = matchingResultPerConfigScopeId.get(configurationScopeId).getNow(null);
    var branchesStorage = storageService.binding(effectiveBinding).branches();
    if (!branchesStorage.exists()) {
      clearResult(configurationScopeId);
      client.log(new LogParams(LogLevel.INFO, "Cannot match Sonar branch, storage is empty", configurationScopeId));
      return;
    }
    var storedBranches = branchesStorage.read();
    var mainBranchName = storedBranches.getMainBranchName();
    String matchedSonarBranch;
    try {
      matchedSonarBranch = requestClientToMatchSonarProjectBranch(configurationScopeId, mainBranchName, storedBranches.getBranchNames());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }
    if (matchedSonarBranch == null) {
      matchedSonarBranch = mainBranchName;
    }
    if (!matchedSonarBranch.equals(previousSonarProjectBranch)) {
      LOG.debug("Matched Sonar project branch for configuration scope '{}' changed from '{}' to '{}'", configurationScopeId, previousSonarProjectBranch, matchedSonarBranch);
      setMatchedBranchName(configurationScopeId, matchedSonarBranch);
      client.didChangeMatchedSonarProjectBranch(
        new DidChangeMatchedSonarProjectBranchParams(configurationScopeId, matchedSonarBranch));
      applicationEventPublisher.publishEvent(new MatchedSonarProjectBranchChangedEvent(configurationScopeId, matchedSonarBranch));
    } else {
      LOG.debug("Matched Sonar project branch for configuration scope '{}' is still '{}'", configurationScopeId, matchedSonarBranch);
    }
  }

  void setMatchedBranchName(String configScopeId, String newBranchName) {
    synchronized (matchingResultPerConfigScopeId) {
      var previous = matchingResultPerConfigScopeId.get(configScopeId);
      if (previous != null && !previous.isDone()) {
        previous.complete(newBranchName);
      } else {
        matchingResultPerConfigScopeId.put(configScopeId, CompletableFuture.completedFuture(newBranchName));
      }
    }
  }

  @CheckForNull
  private String requestClientToMatchSonarProjectBranch(String configurationScopeId, String mainSonarBranchName, Set<String> allSonarBranchesNames) throws InterruptedException {
    var matchSonarProjectBranchResponseCompletableFuture = client
      .matchSonarProjectBranch(new MatchSonarProjectBranchParams(configurationScopeId, mainSonarBranchName, allSonarBranchesNames));
    try {
      return matchSonarProjectBranchResponseCompletableFuture.get().getMatchedSonarProjectBranch();
    } catch (InterruptedException e) {
      // Cancel the RPC call if it's still running
      matchSonarProjectBranchResponseCompletableFuture.cancel(true);
      LOG.debug("matchSonarProjectBranch interrupted!");
      throw e;
    } catch (ExecutionException e) {
      LOG.warn("Unable to get matched branch from the client", e);
    }
    return null;
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop branch matching executor service in a timely manner");
    }
    matchingResultPerConfigScopeId.values().forEach(future -> future.cancel(true));
  }
}
