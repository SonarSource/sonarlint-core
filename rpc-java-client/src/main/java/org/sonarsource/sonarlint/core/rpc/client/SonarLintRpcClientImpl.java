/*
 * SonarLint Core - RPC Java Client
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
package org.sonarsource.sonarlint.core.rpc.client;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.DidChangeMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerTaintVulnerabilityChangedOrClosedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerTaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListAllFilePathsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListAllFilePathsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.CheckServerTrustedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.CheckServerTrustedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.info.GetClientInfoResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidUpdatePluginsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryLiveAttributesResponse;

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
  private final ExecutorService requestsExecutor;
  private final ExecutorService requestAndNotificationsSequentialExecutor;

  public SonarLintRpcClientImpl(SonarLintRpcClientDelegate delegate, ExecutorService requestsExecutor, ExecutorService requestAndNotificationsSequentialExecutor) {
    this.delegate = delegate;
    this.requestsExecutor = requestsExecutor;
    this.requestAndNotificationsSequentialExecutor = requestAndNotificationsSequentialExecutor;
  }

  private <R> CompletableFuture<R> requestAsync(Function<CancelChecker, R> code) {
    return CompletableFutures.computeAsync(requestAndNotificationsSequentialExecutor, cancelChecker -> {
      var wrapper = new CancelCheckerWrapper(cancelChecker);
      wrapper.checkCanceled();
      return wrapper;
    }).thenApplyAsync(cancelChecker -> {
      cancelChecker.checkCanceled();
      return code.apply(cancelChecker);
    }, requestsExecutor);
  }

  private CompletableFuture<Void> runAsync(Consumer<CancelChecker> code) {
    return CompletableFutures.computeAsync(requestAndNotificationsSequentialExecutor, cancelChecker -> {
      var wrapper = new CancelCheckerWrapper(cancelChecker);
      wrapper.checkCanceled();
      return wrapper;
    }).thenApplyAsync(cancelChecker -> {
      cancelChecker.checkCanceled();
      code.accept(cancelChecker);
      return null;
    }, requestsExecutor);
  }

  protected void notify(Runnable code) {
    requestAndNotificationsSequentialExecutor.submit(code);
  }

  @Override
  public void suggestBinding(SuggestBindingParams params) {
    notify(() -> delegate.suggestBinding(params.getSuggestions()));
  }

  @Override
  public CompletableFuture<FindFileByNamesInScopeResponse> findFileByNamesInScope(FindFileByNamesInScopeParams params) {
    return requestAsync(cancelChecker -> new FindFileByNamesInScopeResponse(delegate.findFileByNamesInScope(params.getConfigScopeId(), params.getFilenames(), cancelChecker)));
  }

  @Override
  public void openUrlInBrowser(OpenUrlInBrowserParams params) {
    notify(() -> delegate.openUrlInBrowser(params.getUrl()));
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
  public CompletableFuture<GetClientInfoResponse> getClientInfo() {
    return requestAsync(delegate::getClientInfo);
  }

  @Override
  public void showHotspot(ShowHotspotParams params) {
    notify(() -> delegate.showHotspot(params));
  }

  @Override
  public void showIssue(ShowIssueParams params) {
    notify(() -> delegate.showIssue(params));
  }

  @Override
  public CompletableFuture<AssistCreatingConnectionResponse> assistCreatingConnection(AssistCreatingConnectionParams params) {
    return requestAsync(cancelChecker -> delegate.assistCreatingConnection(params, cancelChecker));
  }

  @Override
  public CompletableFuture<AssistBindingResponse> assistBinding(AssistBindingParams params) {
    return requestAsync(cancelChecker -> delegate.assistBinding(params, cancelChecker));
  }

  @Override
  public CompletableFuture<Void> startProgress(StartProgressParams params) {
    return runAsync(cancelChecker -> delegate.startProgress(params, cancelChecker));
  }

  @Override
  public void reportProgress(ReportProgressParams params) {
    notify(() -> delegate.reportProgress(params));
  }

  @Override
  public void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams params) {
    notify(() -> delegate.didSynchronizeConfigurationScopes(params));
  }

  @Override
  public CompletableFuture<GetCredentialsResponse> getCredentials(GetCredentialsParams params) {
    return requestAsync(cancelChecker -> delegate.getCredentials(params, cancelChecker));
  }

  @Override
  public CompletableFuture<TelemetryLiveAttributesResponse> getTelemetryLiveAttributes() {
    return requestAsync(cancelChecker -> delegate.getTelemetryLiveAttributes());
  }

  @Override
  public CompletableFuture<SelectProxiesResponse> selectProxies(SelectProxiesParams params) {
    return requestAsync(cancelChecker -> delegate.selectProxies(params, cancelChecker));
  }

  @Override
  public CompletableFuture<GetProxyPasswordAuthenticationResponse> getProxyPasswordAuthentication(GetProxyPasswordAuthenticationParams params) {
    return requestAsync(cancelChecker -> delegate.getProxyPasswordAuthentication(params, cancelChecker));
  }

  @Override
  public CompletableFuture<CheckServerTrustedResponse> checkServerTrusted(CheckServerTrustedParams params) {
    return requestAsync(cancelChecker -> delegate.checkServerTrusted(params, cancelChecker));
  }

  @Override
  public void didReceiveServerTaintVulnerabilityRaisedEvent(DidReceiveServerTaintVulnerabilityRaisedEvent params) {
    notify(() -> delegate.didReceiveServerTaintVulnerabilityRaisedEvent(params));
  }

  @Override
  public void didReceiveServerTaintVulnerabilityChangedOrClosedEvent(DidReceiveServerTaintVulnerabilityChangedOrClosedEvent params) {
    notify(() -> delegate.didReceiveServerTaintVulnerabilityChangedOrClosedEvent(params));
  }

  @Override
  public void didReceiveServerHotspotEvent(DidReceiveServerHotspotEvent params) {
    notify(() -> delegate.didReceiveServerHotspotEvent(params));
  }

  @Override
  public CompletableFuture<MatchSonarProjectBranchResponse> matchSonarProjectBranch(MatchSonarProjectBranchParams params) {
    return requestAsync(cancelChecker -> delegate.matchSonarProjectBranch(params, cancelChecker));
  }

  @Override
  public void didChangeMatchedSonarProjectBranch(DidChangeMatchedSonarProjectBranchParams params) {
    notify(() -> delegate.didChangeMatchedSonarProjectBranch(params));
  }

  @Override
  public void didUpdatePlugins(DidUpdatePluginsParams params) {
    notify(() -> delegate.didUpdatePlugins(params));
  }

  @Override
  public CompletableFuture<ListAllFilePathsResponse> listAllFilePaths(ListAllFilePathsParams params) {
    return requestAsync(cancelChecker -> delegate.listAllFilePaths(params));
  }

  private class CancelCheckerWrapper implements CancelChecker {

    private final CancelChecker wrapped;

    private CancelCheckerWrapper(CancelChecker wrapped) {
      this.wrapped = wrapped;
    }

    @Override
    public void checkCanceled() {
      wrapped.checkCanceled();
      if (requestsExecutor.isShutdown()) {
        throw new CancellationException("Client is shutting down");
      }
    }
  }
}
