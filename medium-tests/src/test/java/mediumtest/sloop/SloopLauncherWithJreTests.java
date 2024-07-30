/*
 * SonarLint Core - Medium Tests
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
package mediumtest.sloop;

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
import org.sonarsource.sonarlint.core.rpc.client.Sloop;
import org.sonarsource.sonarlint.core.rpc.client.SloopLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import testutils.PluginLocator;

import static mediumtest.sloop.UnArchiveUtils.unarchiveDistribution;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.PHP;

class SloopLauncherWithJreTests {
  @TempDir
  private static Path sonarUserHome;

  @TempDir
  private static Path unarchiveTmpDir;

  private static Sloop sloop;
  private static SonarLintRpcServer server;

  private static SloopLauncherTests.DummySonarLintRpcClient client;

  @BeforeAll
  static void setup() {
    var sloopDistPath = SystemUtils.IS_OS_WINDOWS ? SloopDistLocator.getWindowsDistPath() : SloopDistLocator.getLinux64DistPath();
    var jrePath = SystemUtils.IS_OS_WINDOWS ? JreLocator.getWindowsJrePath() : JreLocator.getLinuxJrePath();
    var sloopOutDirPath = unarchiveSloop(sloopDistPath);
    client = new SloopLauncherTests.DummySonarLintRpcClient();
    var sloopLauncher = new SloopLauncher(client);
    sloop = sloopLauncher.start(sloopOutDirPath.toAbsolutePath(), jrePath.toAbsolutePath());
    server = sloop.getRpcServer();
  }

  @AfterAll
  static void tearDown() throws ExecutionException, InterruptedException {
    sloop.shutdown().get();
    var exitCode = sloop.onExit().join();
    assertThat(exitCode).isZero();
  }

  @Test
  void test_all_rules_returns() {
    var telemetryInitDto = new TelemetryClientConstantAttributesDto("SonarLint ITs", "SonarLint ITs",
      "1.2.3", "4.5.6", Collections.emptyMap());
    var clientInfo = new ClientConstantInfoDto("clientName", "integrationTests", 0);
    var featureFlags = new FeatureFlagsDto(false, false, false, false, false, false, false, false, false, false);

    server.initialize(new InitializeParams(clientInfo, telemetryInitDto, HttpConfigurationDto.defaultConfig(), null, featureFlags, sonarUserHome.resolve("storage"), sonarUserHome.resolve("workDir"),
    Set.of(PluginLocator.getPhpPluginPath().toAbsolutePath()), Collections.emptyMap(), Set.of(PHP), Collections.emptySet(), Collections.emptySet(), Collections.emptyList(),
      Collections.emptyList(), sonarUserHome.toString(), Map.of(), false, null, false)).join();

    var result = server.getRulesService().listAllStandaloneRulesDefinitions().join();
    assertThat(result.getRulesByKey()).hasSize(219);
    var expectedJreLog = "Using JRE from " + (SystemUtils.IS_OS_WINDOWS ? JreLocator.getWindowsJrePath() : JreLocator.getLinuxJrePath());
    assertThat(client.getLogs()).extracting(LogParams::getMessage).contains(expectedJreLog);
  }

  @NotNull
  private static Path unarchiveSloop(Path sloopDistPath) {
    var sloopOutDirPath = unarchiveTmpDir.resolve("sloopDistOut");
    unarchiveDistribution(sloopDistPath.toString(), sloopOutDirPath);
    return sloopOutDirPath;
  }
}
