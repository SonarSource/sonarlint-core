/*
 * SonarLint Core - Medium Tests
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
package mediumtest.fixtures;

import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueStorageService;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.impl.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintBackend;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.BindingService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.SonarProjectBranchService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.ConfigurationService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.IssueService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.NewCodeService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RulesService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.IssueTrackingService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.SecurityHotspotMatchingService;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;

import static java.util.Objects.requireNonNull;

public class SonarLintTestBackend implements SonarLintBackend {
  private final SonarLintBackend serverUsingRpc;
  private final PipedOutputStream clientToServerOutputStream;
  private final PipedOutputStream serverToClientOutputStream;
  private final SonarLintBackendImpl serverUsingJava;
  @org.jetbrains.annotations.NotNull
  private final BackendJsonRpcLauncher serverLauncher;
  private final ClientJsonRpcLauncher clientLauncher;
  private Path userHome;
  private Path workDir;
  private Path telemetryFilePath;
  private Path storageRoot;

  public SonarLintTestBackend(PipedOutputStream clientToServerOutputStream, PipedOutputStream serverToClientOutputStream, BackendJsonRpcLauncher serverLauncher, ClientJsonRpcLauncher clientLauncher) {
    this.serverUsingRpc = clientLauncher.getServerProxy();
    this.clientToServerOutputStream = clientToServerOutputStream;
    this.serverToClientOutputStream = serverToClientOutputStream;
    this.serverUsingJava = serverLauncher.getJavaImpl();
    this.serverLauncher = serverLauncher;
    this.clientLauncher = clientLauncher;
  }

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    this.userHome = Path.of(requireNonNull(params.getSonarlintUserHome()));
    this.workDir = requireNonNull(params.getWorkDir());
    this.storageRoot = requireNonNull(params.getStorageRoot());
    this.telemetryFilePath = TelemetryPathManager.getPath(userHome, params.getClientInfo().getTelemetryProductKey());
    return serverUsingRpc.initialize(params);
  }

  @Override
  public ConnectionService getConnectionService() {
    return serverUsingRpc.getConnectionService();
  }

  @Override
  public ConfigurationService getConfigurationService() {
    return serverUsingRpc.getConfigurationService();
  }

  @Override
  public RulesService getRulesService() {
    return serverUsingRpc.getRulesService();
  }

  @Override
  public BindingService getBindingService() {
    return serverUsingRpc.getBindingService();
  }

  @Override
  public HotspotService getHotspotService() {
    return serverUsingRpc.getHotspotService();
  }

  @Override
  public TelemetryService getTelemetryService() {
    return serverUsingRpc.getTelemetryService();
  }

  @Override
  public AnalysisService getAnalysisService() {
    return serverUsingRpc.getAnalysisService();
  }

  @Override
  public SonarProjectBranchService getSonarProjectBranchService() {
    return serverUsingRpc.getSonarProjectBranchService();
  }

  @Override
  public IssueService getIssueService() {
    return serverUsingRpc.getIssueService();
  }

  @Override
  public IssueTrackingService getIssueTrackingService() {
    return serverUsingRpc.getIssueTrackingService();
  }

  @Override
  public SecurityHotspotMatchingService getSecurityHotspotMatchingService() {
    return serverUsingRpc.getSecurityHotspotMatchingService();
  }

  @Override
  public NewCodeService getNewCodeService() {
    return serverUsingRpc.getNewCodeService();
  }

  public Path getWorkDir() {
    return workDir;
  }

  public Path getUserHome() {
    return userHome;
  }

  public Path getStorageRoot() {
    return storageRoot;
  }

  public Path telemetryFilePath() {
    return telemetryFilePath;
  }

  public LocalOnlyIssueStorageService getLocalOnlyIssueStorageService() {
    return serverUsingJava.getLocalOnlyIssueStorageService();
  }

  public StorageService getIssueStorageService() {
    return serverUsingJava.getIssueStorageService();
  }

  @Override
  public CompletableFuture<Void> shutdown() {
    try {
      serverUsingRpc.shutdown().get(10, TimeUnit.SECONDS);
      serverLauncher.close();
      clientLauncher.close();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    } finally {
      FileUtils.deleteQuietly(workDir.toFile());
      FileUtils.deleteQuietly(userHome.toFile());
    }
    return CompletableFuture.completedFuture(null);
  }

  public int getEmbeddedServerPort() {
    return serverUsingJava.getEmbeddedServerPort();
  }

  public HttpClient getHttpClientNoAuth() {
    return serverUsingJava.getHttpClientNoAuth();
  }

  public HttpClient getHttpClient(String connectionId) {
    return serverUsingJava.getHttpClient(connectionId);
  }


}
