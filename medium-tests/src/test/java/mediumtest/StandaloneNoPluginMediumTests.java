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
package mediumtest;

import java.nio.file.Path;
import java.util.List;
import mediumtest.fixtures.SonarLintBackendFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static testutils.AnalysisUtils.analyzeAndGetIssuesByFile;
import static testutils.AnalysisUtils.createFile;

class StandaloneNoPluginMediumTests {
  private static final String CONFIGURATION_SCOPE_ID = "configScopeId";

  @TempDir
  private Path baseDir;
  private SonarLintTestRpcServer backend;
  private SonarLintBackendFixture.FakeSonarLintRpcClient client;
  private Path filePath;

  @BeforeEach
  void prepare() {
    filePath = createFile(baseDir, "foo.js", "function foo() {var x;}");
    client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, baseDir,
        List.of(new ClientFileDto(filePath.toUri(), baseDir.relativize(filePath), CONFIGURATION_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    backend = newBackend().withTelemetryEnabled().build(client);
  }

  @AfterEach
  void stop() {
    backend.shutdown().join();
  }

  @Test
  void dont_fail_and_detect_language_even_if_no_plugin() {
    var issuesByFile = analyzeAndGetIssuesByFile(backend, client, CONFIGURATION_SCOPE_ID, filePath.toUri());

    assertThat(issuesByFile).isEmpty();
    assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"analyzers\":{\"js\":{\"analysisCount\":1,");
  }
}
