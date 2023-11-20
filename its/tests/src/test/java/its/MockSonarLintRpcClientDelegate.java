package its;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.rpc.client.ConfigScopeNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerTaintVulnerabilityChangedOrClosedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerTaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FoundFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.X509CertificateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

public class MockSonarLintRpcClientDelegate implements SonarLintRpcClientDelegate {

  @Override
  public void suggestBinding(Map<String, List<BindingSuggestionDto>> suggestionsByConfigScope) {

  }

  @Override
  public List<FoundFileDto> findFileByNamesInScope(String configScopeId, List<String> filenames, CancelChecker cancelChecker) throws ConfigScopeNotFoundException {
    return List.of();
  }

  @Override
  public void openUrlInBrowser(String url) {

  }

  @Override
  public void showMessage(MessageType type, String text) {

  }

  @Override
  public void log(LogParams params) {

  }

  @Override
  public void showSoonUnsupportedMessage(ShowSoonUnsupportedMessageParams params) {

  }

  @Override
  public void showSmartNotification(ShowSmartNotificationParams params) {

  }

  @Override
  public String getClientDescription() {
    return "";
  }

  @Override
  public void showHotspot(ShowHotspotParams params) {

  }

  @Override
  public void showIssue(ShowIssueParams params) {

  }

  @Override
  public AssistCreatingConnectionResponse assistCreatingConnection(AssistCreatingConnectionParams params, CancelChecker cancelChecker) throws CancellationException {
    throw new CancellationException("Unsupported in ITS");
  }

  @Override
  public AssistBindingResponse assistBinding(AssistBindingParams params, CancelChecker cancelChecker) throws CancellationException {
    throw new CancellationException("Unsupported in ITS");
  }

  @Override
  public void startProgress(StartProgressParams params) throws UnsupportedOperationException {

  }

  @Override
  public void reportProgress(ReportProgressParams params) {

  }

  @Override
  public void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams params) {

  }

  @Override
  public Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) throws ConnectionNotFoundException {
    throw new ConnectionNotFoundException();
  }

  @Override
  public List<ProxyDto> selectProxies(URI uri) {
    return List.of(ProxyDto.NO_PROXY);
  }

  @Override
  public GetProxyPasswordAuthenticationResponse getProxyPasswordAuthentication(String host, int port, String protocol, String prompt, String scheme, URL targetHost) {
    return new GetProxyPasswordAuthenticationResponse("", "");
  }

  @Override
  public boolean checkServerTrusted(List<X509CertificateDto> chain, String authType) {
    return false;
  }

  @Override
  public void didReceiveServerTaintVulnerabilityRaisedEvent(DidReceiveServerTaintVulnerabilityRaisedEvent params) {
  }

  @Override
  public void didReceiveServerTaintVulnerabilityChangedOrClosedEvent(DidReceiveServerTaintVulnerabilityChangedOrClosedEvent params) {
  }

  @Override
  public void didReceiveServerHotspotEvent(DidReceiveServerHotspotEvent params) {

  }

  @Override
  public String matchSonarProjectBranch(String configurationScopeId, String mainBranchName, Set<String> allBranchesNames, CancelChecker cancelChecker)
    throws ConfigScopeNotFoundException {
    return mainBranchName;
  }

  @Override
  public void didChangeMatchedSonarProjectBranch(String configScopeId, String newMatchedBranchName) {

  }

  @Override
  public void didUpdatePlugins(String connectionId) {

  }

  @Override
  public List<String> listAllFilePaths(String configurationScopeId) throws ConfigScopeNotFoundException {
    return List.of();
  }
}
