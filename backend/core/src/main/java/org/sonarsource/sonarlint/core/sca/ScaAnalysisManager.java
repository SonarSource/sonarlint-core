/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.sca;

import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.util.FailSafeExecutors;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectTrigger;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.CancelDependencyRiskAnalysisResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskAnalysisStatusResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sca.DidChangeDependencyRiskAnalysisStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sca.DidChangeDependencyRiskAnalysisStatusParams.DependencyRiskAnalysisStatus;

public class ScaAnalysisManager {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ScaProjectAnalysisService scaProjectAnalysisService;
  private final SonarLintRpcClient client;
  private final ExecutorService executorService = FailSafeExecutors.newSingleThreadExecutor("sonarlint-sca-analysis");
  private final AtomicReference<RunningAnalysis> runningAnalysis = new AtomicReference<>();

  public ScaAnalysisManager(ScaProjectAnalysisService scaProjectAnalysisService, SonarLintRpcClient client) {
    this.scaProjectAnalysisService = scaProjectAnalysisService;
    this.client = client;
  }

  public AnalyzeDependencyRiskProjectResponse analyzeProject(AnalyzeDependencyRiskProjectParams params) {
    while (true) {
      var currentAnalysis = runningAnalysis.get();
      if (currentAnalysis != null) {
        return handleAlreadyRunningAnalysis(params, currentAnalysis);
      }
      var newAnalysis = new RunningAnalysis(params);
      if (runningAnalysis.compareAndSet(null, newAnalysis)) {
        return runAnalysisAndAwait(newAnalysis);
      }
    }
  }

  public DependencyRiskAnalysisStatusResponse getStatus() {
    var analysis = runningAnalysis.get();
    if (analysis == null) {
      return new DependencyRiskAnalysisStatusResponse(false, null, null, false);
    }
    return new DependencyRiskAnalysisStatusResponse(true, analysis.configurationScopeId(), analysis.trigger(), analysis.isRerunRequested());
  }

  public CancelDependencyRiskAnalysisResponse cancelAnalysis(String configurationScopeId) {
    var analysis = runningAnalysis.get();
    if (analysis == null || (configurationScopeId != null && !analysis.configurationScopeId().equals(configurationScopeId))) {
      return new CancelDependencyRiskAnalysisResponse(false);
    }
    analysis.clearRerunRequested();
    return new CancelDependencyRiskAnalysisResponse(analysis.cancel());
  }

  @PreDestroy
  public void shutdown() {
    var analysis = runningAnalysis.get();
    if (analysis != null) {
      analysis.cancel();
    }
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop SCA analysis executor service in a timely manner");
    }
  }

  private AnalyzeDependencyRiskProjectResponse handleAlreadyRunningAnalysis(AnalyzeDependencyRiskProjectParams params, RunningAnalysis currentAnalysis) {
    if (params.getTrigger() == AnalyzeDependencyRiskProjectTrigger.AUTOMATIC) {
      currentAnalysis.requestRerun(params);
    }
    return currentAnalysis.awaitResult();
  }

  private AnalyzeDependencyRiskProjectResponse runAnalysisAndAwait(RunningAnalysis analysis) {
    analysis.start();
    notifyStatus(analysis, DependencyRiskAnalysisStatus.STARTED, false, null);
    return analysis.awaitResult();
  }

  private void onAnalysisCompleted(RunningAnalysis analysis, Throwable error) {
    if (!runningAnalysis.compareAndSet(analysis, null)) {
      return;
    }
    var status = toStatus(error);
    notifyStatus(analysis, status, analysis.isRerunRequested(), toMessage(error));
    var rerunParams = analysis.consumeRerunParams();
    if (status != DependencyRiskAnalysisStatus.CANCELLED && rerunParams != null) {
      startBackgroundAnalysis(rerunParams);
    }
  }

  private void startBackgroundAnalysis(AnalyzeDependencyRiskProjectParams params) {
    while (runningAnalysis.get() == null) {
      var newAnalysis = new RunningAnalysis(params);
      if (runningAnalysis.compareAndSet(null, newAnalysis)) {
        newAnalysis.start();
        notifyStatus(newAnalysis, DependencyRiskAnalysisStatus.STARTED, false, null);
        return;
      }
    }
  }

  private static DependencyRiskAnalysisStatus toStatus(Throwable error) {
    if (error == null) {
      return DependencyRiskAnalysisStatus.COMPLETED;
    }
    if (isCancellation(error)) {
      return DependencyRiskAnalysisStatus.CANCELLED;
    }
    return DependencyRiskAnalysisStatus.FAILED;
  }

  private static boolean isCancellation(Throwable error) {
    var current = error;
    while (current != null) {
      if (current instanceof CancellationException || current instanceof InterruptedException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static String toMessage(Throwable error) {
    if (error == null || isCancellation(error)) {
      return null;
    }
    var cause = unwrap(error);
    return cause.getMessage();
  }

  private static RuntimeException toRuntimeException(Throwable error) {
    var cause = unwrap(error);
    if (cause instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new CompletionException(cause);
  }

  private static Throwable unwrap(Throwable error) {
    var current = error;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private void notifyStatus(RunningAnalysis analysis, DependencyRiskAnalysisStatus status, boolean rerunRequested, String message) {
    try {
      client.didChangeDependencyRiskAnalysisStatus(new DidChangeDependencyRiskAnalysisStatusParams(analysis.configurationScopeId(), status, analysis.trigger(), rerunRequested, message));
    } catch (Exception e) {
      LOG.warn("Unable to notify client of SCA analysis status change", e);
    }
  }

  private class RunningAnalysis {
    private final AnalyzeDependencyRiskProjectParams params;
    private final CompletableFuture<AnalyzeDependencyRiskProjectResponse> result = new CompletableFuture<>();
    private volatile Future<?> future;
    private volatile AnalyzeDependencyRiskProjectParams rerunParams;
    private volatile boolean started;
    private volatile boolean cancellationRequested;

    private RunningAnalysis(AnalyzeDependencyRiskProjectParams params) {
      this.params = params;
    }

    private void start() {
      future = executorService.submit(() -> {
        started = true;
        Throwable error = null;
        try {
          var response = scaProjectAnalysisService.analyzeProject(params);
          if (cancellationRequested) {
            error = new CancellationException();
            result.completeExceptionally(error);
          } else {
            result.complete(response);
          }
        } catch (Throwable t) {
          error = t;
          result.completeExceptionally(t);
        } finally {
          onAnalysisCompleted(this, error);
        }
      });
      if (future.isCancelled()) {
        var error = new CancellationException();
        result.completeExceptionally(error);
        onAnalysisCompleted(this, error);
      }
    }

    private AnalyzeDependencyRiskProjectResponse awaitResult() {
      try {
        return result.join();
      } catch (CompletionException e) {
        throw toRuntimeException(e);
      }
    }

    private boolean cancel() {
      var currentFuture = future;
      if (currentFuture == null) {
        return false;
      }
      cancellationRequested = true;
      var cancelled = currentFuture.cancel(true);
      if (cancelled && !started) {
        var error = new CancellationException();
        result.completeExceptionally(error);
        onAnalysisCompleted(this, error);
      }
      return cancelled;
    }

    private String configurationScopeId() {
      return params.getConfigurationScopeId();
    }

    private AnalyzeDependencyRiskProjectTrigger trigger() {
      return params.getTrigger();
    }

    private boolean isRerunRequested() {
      return rerunParams != null;
    }

    private void requestRerun(AnalyzeDependencyRiskProjectParams params) {
      rerunParams = params;
    }

    private AnalyzeDependencyRiskProjectParams consumeRerunParams() {
      var params = rerunParams;
      rerunParams = null;
      return params;
    }

    private void clearRerunRequested() {
      rerunParams = null;
    }
  }
}
