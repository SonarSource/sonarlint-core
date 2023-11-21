/*
 * SonarLint Core - ITs - Tests
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
package its;

import its.utils.JreLocator;
import its.utils.PluginLocator;
import its.utils.SloopDistLocator;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.client.SloopLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryConstantAttributesDto;

import static its.utils.UnArchiveUtils.unarchiveDistribution;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.GO;

class SloopLauncherWithJreTests {
  @TempDir
  private static Path sonarUserHome;

  @TempDir
  private static Path unarchiveTmpDir;

  private static SonarLintRpcServer server;

  private static SloopLauncherTests.DummySonarLintRpcClient client;

  @BeforeAll
  static void setup() {
    var sloopDistPath = SystemUtils.IS_OS_WINDOWS ? SloopDistLocator.getWindowsDistPath() : SloopDistLocator.getLinux64DistPath();
    var jrePath = SystemUtils.IS_OS_WINDOWS ? JreLocator.getWindowsJrePath() : JreLocator.getLinuxJrePath();
    var sloopOutDirPath = unarchiveSloop(sloopDistPath);
    client = new SloopLauncherTests.DummySonarLintRpcClient();
    server = SloopLauncher.startSonarLintRpcServerWithJre(sloopOutDirPath.toAbsolutePath().toString(), client, jrePath.toAbsolutePath().toString());
  }

  @AfterAll
  static void tearDown() throws ExecutionException, InterruptedException {
    server.shutdown().get();
    var exitCode = SloopLauncher.waitFor();
    assertThat(exitCode).isZero();
  }

  @Test
  void test_all_rules_returns() throws Exception {
    var telemetryInitDto = new TelemetryConstantAttributesDto("SonarLint ITs", "SonarLint ITs",
      "1.2.3", "4.5.6", "linux", "x64", Collections.emptyMap());
    var clientInfo = new ClientConstantInfoDto("clientName", "integrationTests");
    var featureFlags = new FeatureFlagsDto(false, false, false, false, false, false, false, false);

    server.initialize(new InitializeParams(clientInfo, telemetryInitDto, featureFlags, sonarUserHome.resolve("storage"), sonarUserHome.resolve("workDir"),
      Set.of(PluginLocator.getGoPluginPath().toAbsolutePath()), Collections.emptyMap(), Set.of(GO), Collections.emptySet(), Collections.emptyList(),
      Collections.emptyList(), sonarUserHome.toString(), Map.of(), false)).get();

    var result = server.getRulesService().listAllStandaloneRulesDefinitions().get();
    assertThat(result.getRulesByKey()).hasSize(36);
    var expectedJreLog = "Using JRE from " + (SystemUtils.IS_OS_WINDOWS ? JreLocator.getWindowsJrePath() : JreLocator.getLinuxJrePath());
    assertThat(client.getLogs()).anyMatch(logParams -> logParams.getMessage().equals(expectedJreLog));
  }

  @NotNull
  private static Path unarchiveSloop(Path sloopDistPath) {
    var sloopOutDirPath = unarchiveTmpDir.resolve("sloopDistOut");
    unarchiveDistribution(sloopDistPath.toString(), sloopOutDirPath);
    return sloopOutDirPath;
  }
}
