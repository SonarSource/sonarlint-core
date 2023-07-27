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
package mediumtest.fixtures;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;

import static java.util.Objects.requireNonNull;

public class SonarLintTestBackend extends SonarLintBackendImpl {
  private Path userHome;
  private Path workDir;
  private Path telemetryFilePath;

  public SonarLintTestBackend(SonarLintClient client) {
    super(client);
  }

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    this.userHome = Path.of(requireNonNull(params.getSonarlintUserHome()));
    this.workDir = requireNonNull(params.getWorkDir());
    this.telemetryFilePath = TelemetryPathManager.getPath(userHome, params.getClientInfo().getTelemetryProductKey());
    return super.initialize(params);
  }

  public Path getWorkDir() {
    return workDir;
  }

  public Path getUserHome() {
    return userHome;
  }

  public Path telemetryFilePath() {
    return telemetryFilePath;
  }

  @Override
  public CompletableFuture<Void> shutdown() {
    return super.shutdown().whenComplete((v, t) -> {
      FileUtils.deleteQuietly(workDir.toFile());
      FileUtils.deleteQuietly(userHome.toFile());
    });
  }
}
