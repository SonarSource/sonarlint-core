/*
 * SonarLint Core - Test Utils
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.test.utils;

import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueStorageService;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.impl.SonarLintRpcServerImpl;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAssistedIdeRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.BindingRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.SonarProjectBranchRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.ConfigurationRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.dogfooding.DogfoodingRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.FileRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.flightrecorder.FlightRecordingRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.IssueRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.labs.IdeLabsRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.log.LogRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.NewCodeRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.progress.TaskProgressRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.AiCodeFixRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RulesRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityTrackingRpcService;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.telemetry.LocalDateAdapter;
import org.sonarsource.sonarlint.core.telemetry.LocalDateTimeAdapter;
import org.sonarsource.sonarlint.core.telemetry.OffsetDateTimeAdapter;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorage;

import static java.util.Objects.requireNonNull;

public class SonarLintTestRpcServer implements SonarLintRpcServer {
  private final SonarLintRpcServer serverUsingRpc;
  private final SonarLintRpcServerImpl serverUsingJava;
  @org.jetbrains.annotations.NotNull
  private final BackendJsonRpcLauncher serverLauncher;
  private final ClientJsonRpcLauncher clientLauncher;
  private Path userHome;
  private Path workDir;
  private Path storageRoot;
  private String productKey;

  public SonarLintTestRpcServer(BackendJsonRpcLauncher serverLauncher, ClientJsonRpcLauncher clientLauncher) {
    this.serverUsingRpc = clientLauncher.getServerProxy();
    this.serverUsingJava = serverLauncher.getServer();
    this.serverLauncher = serverLauncher;
    this.clientLauncher = clientLauncher;
  }

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    this.productKey = params.getTelemetryConstantAttributes().getProductKey();
    this.userHome = Path.of(requireNonNull(params.getSonarlintUserHome()));
    this.workDir = requireNonNull(params.getWorkDir());
    this.storageRoot = requireNonNull(params.getStorageRoot());
    return serverUsingRpc.initialize(params);
  }

  @Override
  public ConnectionRpcService getConnectionService() {
    return serverUsingRpc.getConnectionService();
  }

  @Override
  public ConfigurationRpcService getConfigurationService() {
    return serverUsingRpc.getConfigurationService();
  }

  @Override
  public FileRpcService getFileService() {
    return serverUsingRpc.getFileService();
  }

  @Override
  public RulesRpcService getRulesService() {
    return serverUsingRpc.getRulesService();
  }

  @Override
  public BindingRpcService getBindingService() {
    return serverUsingRpc.getBindingService();
  }

  @Override
  public HotspotRpcService getHotspotService() {
    return serverUsingRpc.getHotspotService();
  }

  @Override
  public TelemetryRpcService getTelemetryService() {
    return serverUsingRpc.getTelemetryService();
  }

  @Override
  public AnalysisRpcService getAnalysisService() {
    return serverUsingRpc.getAnalysisService();
  }

  @Override
  public SonarProjectBranchRpcService getSonarProjectBranchService() {
    return serverUsingRpc.getSonarProjectBranchService();
  }

  @Override
  public IssueRpcService getIssueService() {
    return serverUsingRpc.getIssueService();
  }

  @Override
  public DependencyRiskRpcService getDependencyRiskService() {
    return serverUsingRpc.getDependencyRiskService();
  }

  @Override
  public NewCodeRpcService getNewCodeService() {
    return serverUsingRpc.getNewCodeService();
  }

  @Override
  public TaintVulnerabilityTrackingRpcService getTaintVulnerabilityTrackingService() {
    return serverUsingRpc.getTaintVulnerabilityTrackingService();
  }

  @Override
  public DogfoodingRpcService getDogfoodingService() {
    return serverUsingRpc.getDogfoodingService();
  }

  @Override
  public AiCodeFixRpcService getAiCodeFixRpcService() {
    return serverUsingRpc.getAiCodeFixRpcService();
  }

  @Override
  public TaskProgressRpcService getTaskProgressRpcService() {
    return serverUsingRpc.getTaskProgressRpcService();
  }

  @Override
  public FlightRecordingRpcService getFlightRecordingService() {
    return serverUsingJava.getFlightRecordingService();
  }

  @Override
  public AiAssistedIdeRpcService getAiAssistedIdeRpcService() {
    return serverUsingJava.getAiAssistedIdeRpcService();
  }

  @Override
  public LogRpcService getLogService() {
    return serverUsingRpc.getLogService();
  }

  @Override
  public IdeLabsRpcService getIdeLabsService() {
    return serverUsingRpc.getIdeLabsService();
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
    return userHome.resolve("telemetry").resolve(productKey).resolve("usage");
  }

  public TelemetryLocalStorage telemetryFileContent() {
    try {
      return readTelemetryFile(telemetryFilePath());
    } catch (IOException e) {
      // use this exception type to allow retries with awaitility's untilAsserted method
      throw new AssertionError("Failed to read telemetry file", e);
    }
  }

  private static TelemetryLocalStorage readTelemetryFile(Path path) throws IOException {
    var fileContent = Files.readString(path, StandardCharsets.UTF_8);
    var decoded = new String(Base64.getDecoder().decode(fileContent), StandardCharsets.UTF_8);
    var gson = new GsonBuilder()
      .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter().nullSafe())
      .registerTypeAdapter(LocalDate.class, new LocalDateAdapter().nullSafe())
      .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter().nullSafe())
      .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
      .create();
    return gson.fromJson(decoded, TelemetryLocalStorage.class);
  }

  public LocalOnlyIssueStorageService getLocalOnlyIssueStorageService() {
    return serverUsingJava.getLocalOnlyIssueStorageService();
  }

  public StorageService getIssueStorageService() {
    return serverUsingJava.getIssueStorageService();
  }

  public SonarLintDatabase getSonarLintDatabase() {
    return serverUsingJava.getDatabase();
  }

  @Override
  public CompletableFuture<Void> shutdown() {
    try {
      serverUsingRpc.shutdown().get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    } finally {
      try {
        serverLauncher.close();
      } catch (Exception e) {
        e.printStackTrace(System.err);
      }
      try {
        clientLauncher.close();
      } catch (Exception e) {
        e.printStackTrace(System.err);
      }
      FileUtils.deleteQuietly(workDir.toFile());
      FileUtils.deleteQuietly(userHome.toFile());
      FileUtils.deleteQuietly(storageRoot.toFile());
    }
    return CompletableFuture.completedFuture(null);
  }

  public int getEmbeddedServerPort() {
    return serverUsingJava.getEmbeddedServerPort();
  }
}
