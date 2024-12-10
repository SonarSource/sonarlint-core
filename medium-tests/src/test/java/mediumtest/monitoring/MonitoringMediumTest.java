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
package mediumtest.monitoring;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintBackendFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static testutils.AnalysisUtils.analyzeFileAndGetIssues;
import static testutils.AnalysisUtils.createFile;

class MonitoringMediumTest {

  private static SonarLintBackendFixture.FakeSonarLintRpcClient client;

  private static final String A_JAVA_FILE_PATH = "Foo.java";
  private static final String CONFIGURATION_SCOPE_ID = "configScopeId";
  private static final List<String> logs = new CopyOnWriteArrayList<>();
  private static SonarLintTestRpcServer backend;
  // commercial plugins might not be available
  // (if you pass -Dcommercial to maven, a profile will be activated that downloads the commercial plugins)
  private static final boolean COMMERCIAL_ENABLED = System.getProperty("commercial") != null;

  @BeforeAll
  static void checkDogfoodingVariableSet() {
    Assumptions.assumeThat(System.getenv("SONARSOURCE_DOGFOODING"))
      .withFailMessage("Dogfooding environment variable is not set, skipping tests")
      .isEqualTo("1");
  }

  @AfterAll
  static void stop() throws ExecutionException, InterruptedException {
    Thread.sleep(5_000);
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @AfterEach
  void cleanup() {
    client.cleanRaisedIssues();
  }

  @Test
  void shouldPostMonitoringPingToSentry(@TempDir Path baseDir) throws Throwable {
    var content = "function foo() {\n"
      + "  let x;\n"
      + "  let y; //NOSONAR\n"
      + "}";
    var inputFile = createFile(baseDir, "foo.js", content);

    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();

    backend = newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVASCRIPT)
      .build(client);

    analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);
  }
}
