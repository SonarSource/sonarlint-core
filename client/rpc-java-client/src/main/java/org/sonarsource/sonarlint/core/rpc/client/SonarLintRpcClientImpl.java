/*
 * SonarLint Core - RPC Java Client
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
package org.sonarsource.sonarlint.core.rpc.client;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.DidChangeAnalysisReadinessParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.DidDetectSecretParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.DidRaiseIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.GetInferredAnalysisPropertiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.GetInferredAnalysisPropertiesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.GetFileExclusionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.GetFileExclusionsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.NoBindingSuggestionFoundParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.DidChangeMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SuggestConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.GetBaseDirParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.GetBaseDirResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListFilesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaiseHotspotsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.CheckServerTrustedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.CheckServerTrustedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.info.GetClientLiveInfoResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaiseIssuesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidSkipLoadingPluginParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.promotion.PromoteExtraEnabledLanguagesInConnectedModeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.taint.vulnerability.DidChangeTaintVulnerabilitiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;

/**
 * Implementation of {@link SonarLintRpcClient} that delegates to {@link SonarLintRpcClientDelegate} in order to simplify Java clients and avoid
 * leaking too many RPC-specific concept in each Java IDE.
 * In particular, this class attempt to:
 * <ul>
 *   <li>Hide the fact that RPC is asynchronous (don't let clients manipulate completable futures)</li>
 *   <li>Hide cancellation except if there is a functional need</li>
 *   <li>Convert Java exceptions to RPC error messages</li>
 * </ul>
 */
public class SonarLintRpcClientImpl implements SonarLintRpcClient {

  private final SonarLintRpcClientDelegate delegate;
  private final Executor requestsExecutor;
  private final Executor requestAndNotificationsSequentialExecutor;

  public SonarLintRpcClientImpl(SonarLintRpcClientDelegate delegate, Executor requestsExecutor, Executor requestAndNotificationsSequentialExecutor) {
    this.delegate = delegate;
    this.requestsExecutor = requestsExecutor;
    this.requestAndNotificationsSequentialExecutor = requestAndNotificationsSequentialExecutor;
  }

  protected <R> CompletableFuture<R> requestAsync(Function<CancelChecker, R> code) {
    CompletableFuture<CancelChecker> start = new CompletableFuture<>();
    // First we schedule the processing of the request on the sequential executor, to maintain ordering of notifications, requests, responses,
    // and cancellations
    var sequentialFuture = start.thenApplyAsync(cancelChecker -> {
      // We can maybe cancel early
      cancelChecker.checkCanceled();
      return cancelChecker;
    }, requestAndNotificationsSequentialExecutor);
    // Then requests are processed asynchronously to not block the processing of notifications, responses and cancellations
    var requestFuture = sequentialFuture.thenApplyAsync(cancelChecker -> {
      cancelChecker.checkCanceled();
      return code.apply(cancelChecker);
    }, requestsExecutor);
    start.complete(new CompletableFutures.FutureCancelChecker(requestFuture));
    return requestFuture;
  }

  protected CompletableFuture<Void> runAsync(Consumer<CancelChecker> code) {
    CompletableFuture<CancelChecker> start = new CompletableFuture<>();
    // First we schedule the processing of the request on the sequential executor, to maintain ordering of notifications, requests, responses,
    // and cancellations
    var sequentialFuture = start.thenApplyAsync(cancelChecker -> {
      // We can maybe cancel early
      cancelChecker.checkCanceled();
      return cancelChecker;
    }, requestAndNotificationsSequentialExecutor);
    // Then requests are processed asynchronously to not block the processing of notifications, responses and cancellations
    var requestFuture = sequentialFuture.<Void>thenApplyAsync(cancelChecker -> {
      cancelChecker.checkCanceled();
      code.accept(cancelChecker);
      return null;
    }, requestsExecutor);
    start.complete(new CompletableFutures.FutureCancelChecker(requestFuture));
    return requestFuture;
  }

  protected void notify(Runnable code) {
    requestAndNotificationsSequentialExecutor.execute(() -> {
      try {
        code.run();
      } catch (Throwable throwable) {
        logClientSideError("Error when handling a notification", throwable);
      }
    });
  }

  /**
   * Client errors don't need to go over RPC, and can instead directly go through the delegate.
   */
  void logClientSideError(String message, Throwable throwable) {
    delegate.log(new LogParams(LogLevel.ERROR, message, null, stackTraceToString(throwable), Instant.now()));
  }

  private static String stackTraceToString(Throwable t) {
    var stringWriter = new StringWriter();
    var printWriter = new PrintWriter(stringWriter);
    t.printStackTrace(printWriter);
    return stringWriter.toString();
  }

  @Override
  public void suggestBinding(SuggestBindingParams params) {
    notify(() -> delegate.suggestBinding(params.getSuggestions()));
  }

  @Override
  public void suggestConnection(SuggestConnectionParams params) {
    notify(() -> delegate.suggestConnection(params.getSuggestionsByConfigScopeId()));
  }

  @Override
  public void openUrlInBrowser(OpenUrlInBrowserParams params) {
    notify(() -> {
      try {
        delegate.openUrlInBrowser(new URL(params.getUrl()));
      } catch (MalformedURLException e) {
        throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Not a valid URL: " + params.getUrl(), params.getUrl()));
      }
    });
  }

  @Override
  public void showMessage(ShowMessageParams params) {
    notify(() -> delegate.showMessage(params.getType(), params.getText()));
  }

  @Override
  public void log(LogParams params) {
    notify(() -> delegate.log(params));
  }

  @Override
  public void showSoonUnsupportedMessage(ShowSoonUnsupportedMessageParams params) {
    notify(() -> delegate.showSoonUnsupportedMessage(params));
  }

  @Override
  public void showSmartNotification(ShowSmartNotificationParams params) {
    notify(() -> delegate.showSmartNotification(params));
  }

  @Override
  public CompletableFuture<GetClientLiveInfoResponse> getClientLiveInfo() {
    return requestAsync(cancelChecker -> new GetClientLiveInfoResponse(delegate.getClientLiveDescription()));
  }

  @Override
  public void showHotspot(ShowHotspotParams params) {
    notify(() -> delegate.showHotspot(params.getConfigurationScopeId(), params.getHotspotDetails()));
  }

  @Override
  public void showIssue(ShowIssueParams params) {
    notify(() -> delegate.showIssue(params.getConfigurationScopeId(), params.getIssueDetails()));
  }

  @Override
  public CompletableFuture<AssistCreatingConnectionResponse> assistCreatingConnection(AssistCreatingConnectionParams params) {
    return requestAsync(cancelChecker -> delegate.assistCreatingConnection(params, new SonarLintCancelChecker(cancelChecker)));
  }

  @Override
  public CompletableFuture<AssistBindingResponse> assistBinding(AssistBindingParams params) {
    return requestAsync(cancelChecker -> delegate.assistBinding(params, new SonarLintCancelChecker(cancelChecker)));
  }

  @Override
  public CompletableFuture<Void> startProgress(StartProgressParams params) {
    return runAsync(cancelChecker -> {
      try {
        delegate.startProgress(params);
      } catch (UnsupportedOperationException e) {
        throw new ResponseErrorException(new ResponseError(SonarLintRpcErrorCode.PROGRESS_CREATION_FAILED, e.getMessage(), null));
      }
    });
  }

  @Override
  public void reportProgress(ReportProgressParams params) {
    notify(() -> delegate.reportProgress(params));
  }

  @Override
  public void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams params) {
    notify(() -> delegate.didSynchronizeConfigurationScopes(params.getConfigurationScopeIds()));
  }

  @Override
  public CompletableFuture<GetCredentialsResponse> getCredentials(GetCredentialsParams params) {
    return requestAsync(cancelChecker -> {
      try {
        return new GetCredentialsResponse(delegate.getCredentials(params.getConnectionId()));
      } catch (ConnectionNotFoundException e) {
        throw new ResponseErrorException(
          new ResponseError(SonarLintRpcErrorCode.CONNECTION_NOT_FOUND, "Unknown connection: " + params.getConnectionId(), params.getConnectionId()));
      }
    });
  }

  @Override
  public CompletableFuture<TelemetryClientLiveAttributesResponse> getTelemetryLiveAttributes() {
    return requestAsync(cancelChecker -> delegate.getTelemetryLiveAttributes());
  }

  @Override
  public CompletableFuture<SelectProxiesResponse> selectProxies(SelectProxiesParams params) {
    return requestAsync(cancelChecker -> new SelectProxiesResponse(delegate.selectProxies(params.getUri())));
  }

  @Override
  public CompletableFuture<GetProxyPasswordAuthenticationResponse> getProxyPasswordAuthentication(GetProxyPasswordAuthenticationParams params) {
    return requestAsync(cancelChecker -> delegate.getProxyPasswordAuthentication(params.getHost(), params.getPort(), params.getProtocol(), params.getPrompt(), params.getScheme(),
      params.getTargetHost()));
  }

  @Override
  public CompletableFuture<CheckServerTrustedResponse> checkServerTrusted(CheckServerTrustedParams params) {
    return requestAsync(cancelChecker -> new CheckServerTrustedResponse(delegate.checkServerTrusted(params.getChain(), params.getAuthType())));
  }

  @Override
  public void didReceiveServerHotspotEvent(DidReceiveServerHotspotEvent params) {
    notify(() -> delegate.didReceiveServerHotspotEvent(params));
  }

  @Override
  public CompletableFuture<MatchSonarProjectBranchResponse> matchSonarProjectBranch(MatchSonarProjectBranchParams params) {
    return requestAsync(cancelChecker -> {
      try {
        return new MatchSonarProjectBranchResponse(
          delegate.matchSonarProjectBranch(params.getConfigurationScopeId(), params.getMainSonarBranchName(),
            params.getAllSonarBranchesNames(), new SonarLintCancelChecker(cancelChecker)));
      } catch (ConfigScopeNotFoundException e) {
        throw configScopeNotFoundError(params.getConfigurationScopeId());
      }
    });
  }

  @Override
  public void didChangeMatchedSonarProjectBranch(DidChangeMatchedSonarProjectBranchParams params) {
    notify(() -> delegate.didChangeMatchedSonarProjectBranch(params.getConfigScopeId(), params.getNewMatchedBranchName()));
  }

  @Override
  public CompletableFuture<GetBaseDirResponse> getBaseDir(GetBaseDirParams params) {
    return requestAsync(cancelChecker -> {
      try {
        return new GetBaseDirResponse(delegate.getBaseDir(params.getConfigurationScopeId()));
      } catch (ConfigScopeNotFoundException e) {
        throw configScopeNotFoundError(params.getConfigurationScopeId());
      }
    });
  }

  @Override
  public CompletableFuture<ListFilesResponse> listFiles(ListFilesParams params) {
    return requestAsync(cancelChecker -> {
      try {
        return new ListFilesResponse(delegate.listFiles(params.getConfigScopeId()));
      } catch (ConfigScopeNotFoundException e) {
        throw configScopeNotFoundError(params.getConfigScopeId());
      }
    });
  }

  private static ResponseErrorException configScopeNotFoundError(String configScopeId) {
    return new ResponseErrorException(
      new ResponseError(SonarLintRpcErrorCode.CONFIG_SCOPE_NOT_FOUND, "Unknown config scope: " + configScopeId, configScopeId));
  }

  @Override
  public void didChangeTaintVulnerabilities(DidChangeTaintVulnerabilitiesParams params) {
    notify(() -> delegate.didChangeTaintVulnerabilities(params.getConfigurationScopeId(), params.getClosedTaintVulnerabilityIds(), params.getAddedTaintVulnerabilities(),
      params.getUpdatedTaintVulnerabilities()));
  }

  @Override
  public void noBindingSuggestionFound(NoBindingSuggestionFoundParams params) {
    notify(() -> delegate.noBindingSuggestionFound(params.getProjectKey()));
  }

  public void didChangeAnalysisReadiness(DidChangeAnalysisReadinessParams params) {
    notify(() -> delegate.didChangeAnalysisReadiness(params.getConfigurationScopeIds(), params.areReadyForAnalysis()));
  }

  @Override
  public void didRaiseIssue(DidRaiseIssueParams params) {
    notify(() -> delegate.didRaiseIssue(params.getConfigurationScopeId(), params.getAnalysisId(), params.getRawIssue()));
  }

  @Override
  public void raiseIssues(RaiseIssuesParams params) {
    notify(() -> delegate.raiseIssues(params.getConfigurationScopeId(), params.getIssuesByFileUri(), params.isIntermediatePublication(), params.getAnalysisId()));
  }

  @Override
  public void raiseHotspots(RaiseHotspotsParams params) {
    notify(() -> delegate.raiseHotspots(params.getConfigurationScopeId(), params.getIssuesByFileUri(), params.isIntermediatePublication(), params.getAnalysisId()));
  }

  @Override
  public void didSkipLoadingPlugin(DidSkipLoadingPluginParams params) {
    notify(() -> delegate.didSkipLoadingPlugin(params.getConfigurationScopeId(), params.getLanguage(), params.getReason(), params.getMinVersion(), params.getCurrentVersion()));
  }

  @Override
  public void didDetectSecret(DidDetectSecretParams params) {
    notify(() -> delegate.didDetectSecret(params.getConfigurationScopeId()));
  }

  @Override
  public void promoteExtraEnabledLanguagesInConnectedMode(PromoteExtraEnabledLanguagesInConnectedModeParams params) {
    notify(() -> delegate.promoteExtraEnabledLanguagesInConnectedMode(params.getConfigurationScopeId(), params.getLanguagesToPromote()));
  }

  @Override
  public CompletableFuture<GetInferredAnalysisPropertiesResponse> getInferredAnalysisProperties(GetInferredAnalysisPropertiesParams params) {
    return requestAsync(cancelChecker -> {
      try {
        return new GetInferredAnalysisPropertiesResponse(delegate.getInferredAnalysisProperties(params.getConfigurationScopeId(), params.getFilePathsToAnalyze()));
      } catch (ConfigScopeNotFoundException e) {
        throw configScopeNotFoundError(params.getConfigurationScopeId());
      }
    });
  }

  @Override
  public CompletableFuture<GetFileExclusionsResponse> getFileExclusions(GetFileExclusionsParams params) {
    return requestAsync(cancelChecker -> {
      try {
        return new GetFileExclusionsResponse(delegate.getFileExclusions(params.getConfigurationScopeId()));
      } catch (ConfigScopeNotFoundException e) {
        throw configScopeNotFoundError(params.getConfigurationScopeId());
      }
    });
  }
}
