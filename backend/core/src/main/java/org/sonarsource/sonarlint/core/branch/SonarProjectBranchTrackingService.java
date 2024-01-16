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

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.util.concurrent.MoreExecutors;
import dev.failsafe.ExecutionContext;
import dev.failsafe.Failsafe;
import dev.failsafe.Fallback;
import dev.failsafe.RetryPolicy;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
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
  private final AsyncLoadingCache<String, String> cachedMatchingBranchByConfigScope = Caffeine.newBuilder()
    .executor(executorService)
    .buildAsync(new AsyncCacheLoader<>() {
      @Override
      public CompletableFuture<? extends String> asyncLoad(String key, Executor executor) throws Exception {
        return asyncLoadWithFailsafe(key, null, executor);
      }

      @Override
      public CompletableFuture<? extends String> asyncReload(String key, String oldValue, Executor executor) throws Exception {
        return asyncLoadWithFailsafe(key, oldValue, executor);
      }

      private CompletableFuture<String> asyncLoadWithFailsafe(String key, @Nullable String oldValue, Executor executor) {
        var currentThreadOutput = SonarLintLogger.getTargetForCopy();
        return Failsafe.none().with(executor)
          .onFailure(e -> LOG.error("Error while matching Sonar project branch for configuration scope '" + key + "'", e.getException()))
          .getAsync(ctx -> {
          SonarLintLogger.setTarget(currentThreadOutput);
          return matchSonarProjectBranch(key, oldValue, ctx);
        });
      }
    });

  public SonarProjectBranchTrackingService(SonarLintRpcClient client, StorageService storageService,
    ConfigurationRepository configurationRepository, ApplicationEventPublisher applicationEventPublisher) {
    this.client = client;
    this.storageService = storageService;
    this.configurationRepository = configurationRepository;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  public Optional<String> awaitEffectiveSonarProjectBranch(String configurationScopeId) {
    var currentThreadOutput = SonarLintLogger.getTargetForCopy();
    var retryOnCancelPolicy = RetryPolicy.<String>builder()
      .handle(CancellationException.class)
      .withMaxRetries(3)
      .onRetry(e -> {
        SonarLintLogger.setTarget(currentThreadOutput);
        LOG.debug("Retrying to compute paths translation for config scope '{}'", configurationScopeId, e);
      })
      .build();
    return Optional.ofNullable(
      Failsafe.with(Fallback.of((String) null), retryOnCancelPolicy)
        .onFailure(e -> LOG.error("Error while matching Sonar project branch for configuration scope '" + configurationScopeId + "'", e.getException()))
        .getStageAsync(ctx ->  {
          SonarLintLogger.setTarget(currentThreadOutput);
          return cachedMatchingBranchByConfigScope.get(configurationScopeId);
        }).join());
  }

  @EventListener
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent event) {
    var removedConfigScopeId = event.getRemovedConfigurationScopeId();
    LOG.debug("Configuration scope '{}' removed, clearing matched branch", removedConfigScopeId);
    cancelAndInvalidate(removedConfigScopeId);
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
          cancelAndRefresh(configScopeId);
        }
      }
    });
  }

  @EventListener
  public void onBindingChanged(BindingConfigChangedEvent bindingChanged) {
    var configScopeId = bindingChanged.getConfigScopeId();
    if (!bindingChanged.getNewConfig().isBound()) {
      LOG.debug("Configuration scope '{}' unbound, clearing matched branch", configScopeId);
      cancelAndInvalidate(configScopeId);
    } else {
      LOG.debug("Configuration scope '{}' binding changed, queuing matching of the Sonar project branch...", configScopeId);
      cancelAndRefresh(configScopeId);
    }
  }

  @EventListener
  public void onSonarProjectBranchChanged(SonarProjectBranchesChangedEvent event) {
    var configScopeIds = configurationRepository.getBoundScopesToConnectionAndSonarProject(event.getConnectionId(), event.getSonarProjectKey());
    configScopeIds.forEach(boundScope -> {
      LOG.debug("Sonar project branch changed for configuration scope '{}', queuing matching of the Sonar project branch...", boundScope.getConfigScopeId());
      cancelAndRefresh(boundScope.getConfigScopeId());
    });
  }

  private void cancelAndInvalidate(String configScopeId) {
    CompletableFuture<?> cachedFuture;
    synchronized (cachedMatchingBranchByConfigScope) {
      cachedFuture = cachedMatchingBranchByConfigScope.getIfPresent(configScopeId);
      cachedMatchingBranchByConfigScope.synchronous().invalidate(configScopeId);
    }
    if (cachedFuture != null) {
      cachedFuture.cancel(false);
    }
  }

  private void cancelAndRefresh(String configScopeId) {
    CompletableFuture<?> cachedFuture;
    synchronized (cachedMatchingBranchByConfigScope) {
      cachedFuture = cachedMatchingBranchByConfigScope.getIfPresent(configScopeId);
      cachedMatchingBranchByConfigScope.synchronous().refresh(configScopeId);
    }
    if (cachedFuture != null) {
      cachedFuture.cancel(false);
    }
  }

  public void didVcsRepositoryChange(String configScopeId) {
    LOG.debug("VCS repository changed for configuration scope '{}', queuing matching of the Sonar project branch...", configScopeId);
    cancelAndRefresh(configScopeId);
  }

  private String matchSonarProjectBranch(String configurationScopeId, @Nullable String oldValue, ExecutionContext<String> ctx) {
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
    var matchedSonarBranch = requestClientToMatchSonarProjectBranch(configurationScopeId, mainBranchName, storedBranches.getBranchNames(), ctx);
    if (matchedSonarBranch == null) {
      matchedSonarBranch = mainBranchName;
    }
    if (!matchedSonarBranch.equals(oldValue)) {
      client.didChangeMatchedSonarProjectBranch(
        new DidChangeMatchedSonarProjectBranchParams(configurationScopeId, matchedSonarBranch));
      applicationEventPublisher.publishEvent(new MatchedSonarProjectBranchChangedEvent(configurationScopeId, matchedSonarBranch));
      LOG.debug("Matched Sonar project branch for configuration scope '{}' changed from '{}' to '{}'", configurationScopeId, oldValue, matchedSonarBranch);
    } else {
      LOG.debug("Matched Sonar project branch for configuration scope '{}' is still '{}'", configurationScopeId, matchedSonarBranch);
    }
    return matchedSonarBranch;
  }

  @CheckForNull
  private String requestClientToMatchSonarProjectBranch(String configurationScopeId, String mainSonarBranchName, Set<String> allSonarBranchesNames, ExecutionContext<?> ctx) {
    var matchSonarProjectBranchResponseCompletableFuture = client
      .matchSonarProjectBranch(new MatchSonarProjectBranchParams(configurationScopeId, mainSonarBranchName, allSonarBranchesNames));
    try {
      ctx.onCancel(() -> {
        if (!matchSonarProjectBranchResponseCompletableFuture.isDone()) {
          System.out.println("Cancelling matchSonarProjectBranch");
          matchSonarProjectBranchResponseCompletableFuture.cancel(true);
        }
      });
      matchSonarProjectBranchResponseCompletableFuture.thenAccept(r -> ctx.onCancel(null));
      return matchSonarProjectBranchResponseCompletableFuture.get().getMatchedSonarProjectBranch();
    } catch (InterruptedException e) {
      // Cancel the RPC call if it's still running
      matchSonarProjectBranchResponseCompletableFuture.cancel(true);
      LOG.debug("matchSonarProjectBranch interrupted!");
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      LOG.warn("Unable to get matched branch from the client", e);
    } finally {
      ctx.onCancel(null);
    }
    return null;
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop branch matching executor service in a timely manner");
    }
  }
}
