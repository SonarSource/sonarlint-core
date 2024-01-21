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

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.SmartCancelableLoadingCache;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelChecker;
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
  private final SmartCancelableLoadingCache<String, String> cachedMatchingBranchByConfigScope = new SmartCancelableLoadingCache<>("sonarlint-branch-matcher",
    this::matchSonarProjectBranch, this::afterCachedValueRefreshed);

  public SonarProjectBranchTrackingService(SonarLintRpcClient client, StorageService storageService,
    ConfigurationRepository configurationRepository, ApplicationEventPublisher applicationEventPublisher) {
    this.client = client;
    this.storageService = storageService;
    this.configurationRepository = configurationRepository;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  public Optional<String> awaitEffectiveSonarProjectBranch(String configurationScopeId) {
    return Optional.ofNullable(cachedMatchingBranchByConfigScope.get(configurationScopeId));
  }

  private void afterCachedValueRefreshed(String configScopeId, @Nullable String oldValue, @Nullable String newValue) {
    if (!Objects.equals(newValue, oldValue)) {
      LOG.debug("Matched Sonar project branch for configuration scope '{}' changed from '{}' to '{}'", configScopeId, oldValue, newValue);
      if (newValue != null) {
        client.didChangeMatchedSonarProjectBranch(new DidChangeMatchedSonarProjectBranchParams(configScopeId, newValue));
        applicationEventPublisher.publishEvent(new MatchedSonarProjectBranchChangedEvent(configScopeId, newValue));
      }
    } else {
      LOG.debug("Matched Sonar project branch for configuration scope '{}' is still '{}'", configScopeId, newValue);
    }
  }

  @EventListener
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent event) {
    var removedConfigScopeId = event.getRemovedConfigurationScopeId();
    LOG.debug("Configuration scope '{}' removed, clearing matched branch", removedConfigScopeId);
    cachedMatchingBranchByConfigScope.clear(removedConfigScopeId);
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
          cachedMatchingBranchByConfigScope.refreshAsync(configScopeId);
        }
      }
    });
  }

  @EventListener
  public void onBindingChanged(BindingConfigChangedEvent bindingChanged) {
    var configScopeId = bindingChanged.getConfigScopeId();
    if (!bindingChanged.getNewConfig().isBound()) {
      LOG.debug("Configuration scope '{}' unbound, clearing matched branch", configScopeId);
      cachedMatchingBranchByConfigScope.clear(configScopeId);
    } else {
      LOG.debug("Configuration scope '{}' binding changed, queuing matching of the Sonar project branch...", configScopeId);
      cachedMatchingBranchByConfigScope.refreshAsync(configScopeId);
    }
  }

  @EventListener
  public void onSonarProjectBranchChanged(SonarProjectBranchesChangedEvent event) {
    var configScopeIds = configurationRepository.getBoundScopesToConnectionAndSonarProject(event.getConnectionId(), event.getSonarProjectKey());
    configScopeIds.forEach(boundScope -> {
      LOG.debug("Sonar project branch changed for configuration scope '{}', queuing matching of the Sonar project branch...", boundScope.getConfigScopeId());
      cachedMatchingBranchByConfigScope.refreshAsync(boundScope.getConfigScopeId());
    });
  }

  public void didVcsRepositoryChange(String configScopeId) {
    LOG.debug("VCS repository changed for configuration scope '{}', queuing matching of the Sonar project branch...", configScopeId);
    cachedMatchingBranchByConfigScope.refreshAsync(configScopeId);
  }

  private String matchSonarProjectBranch(String configurationScopeId, SonarLintCancelChecker cancelChecker) {
    LOG.debug("Matching Sonar project branch for configuration scope '{}'", configurationScopeId);
    var effectiveBindingOpt = configurationRepository.getEffectiveBinding(configurationScopeId);
    if (effectiveBindingOpt.isEmpty()) {
      LOG.debug("No binding for configuration scope '{}'", configurationScopeId);
      return null;
    }
    var effectiveBinding = effectiveBindingOpt.get();

    var branchesStorage = storageService.binding(effectiveBinding).branches();
    if (!branchesStorage.exists()) {
      client.log(new LogParams(LogLevel.INFO, "Cannot match Sonar branch, storage is empty", configurationScopeId));
      return null;
    }
    var storedBranches = branchesStorage.read();
    var mainBranchName = storedBranches.getMainBranchName();
    var matchedSonarBranch = requestClientToMatchSonarProjectBranch(configurationScopeId, mainBranchName, storedBranches.getBranchNames(), cancelChecker);
    if (matchedSonarBranch == null) {
      matchedSonarBranch = mainBranchName;
    }
    if (cancelChecker.isCanceled()) {
      throw new CancellationException();
    }
    return matchedSonarBranch;
  }

  @CheckForNull
  private String requestClientToMatchSonarProjectBranch(String configurationScopeId, String mainSonarBranchName, Set<String> allSonarBranchesNames,
    SonarLintCancelChecker cancelChecker) {
    var matchSonarProjectBranchResponseCompletableFuture = client
      .matchSonarProjectBranch(new MatchSonarProjectBranchParams(configurationScopeId, mainSonarBranchName, allSonarBranchesNames));
    cancelChecker.propagateCancelTo(matchSonarProjectBranchResponseCompletableFuture, true);
    try {
      return matchSonarProjectBranchResponseCompletableFuture.join().getMatchedSonarProjectBranch();
    } catch (CancellationException e) {
      throw e;
    } catch (Exception e) {
      LOG.debug("Error while matching Sonar project branch for configuration scope '{}'", configurationScopeId, e);
      return null;
    }
  }

  @PreDestroy
  public void shutdown() {
    cachedMatchingBranchByConfigScope.close();
  }
}
