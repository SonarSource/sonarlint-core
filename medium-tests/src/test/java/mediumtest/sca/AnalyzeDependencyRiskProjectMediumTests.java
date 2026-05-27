/*
 * SonarLint Core - Medium Tests
 * Copyright (C) SonarSource Sàrl
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
package mediumtest.sca;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.serverapi.features.Feature;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@ExtendWith(SystemStubsExtension.class)
class AnalyzeDependencyRiskProjectMediumTests {

  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";
  private static final String PROJECT_KEY = "projectKey";

  @SystemStub
  SystemProperties systemProperties;

  @SonarLintTest
  void it_should_fail_when_no_base_directory_is_available(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var throwable = catchThrowable(() -> analyzeProject(backend));

    assertThat(throwable)
      .isInstanceOf(CompletionException.class)
      .hasCauseInstanceOf(ResponseErrorException.class);
    var responseErrorException = (ResponseErrorException) throwable.getCause();
    assertThat(responseErrorException.getResponseError().getCode()).isEqualTo(SonarLintRpcErrorCode.INVALID_ARGUMENT);
    assertThat(responseErrorException.getResponseError().getMessage()).isEqualTo("No base directory is available for configuration scope 'configScopeId'");
  }

  @SonarLintTest
  void it_should_cleanup_sca_work_directory_when_scanner_execution_fails(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var pom = Files.writeString(baseDir.resolve("pom.xml"), "<project />");
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(pom.toUri(), baseDir.relativize(pom), CONFIG_SCOPE_ID, false, null, pom, null, null, true)))
      .build();
    var server = harness.newFakeSonarQubeServer()
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage
        .withServerFeature(Feature.SCA)
        .withServerVersion("2025.4"))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start(client);
    systemProperties.set("sonarlint.sca.cliOverrideLocation", baseDir.toString());

    var throwable = catchThrowable(() -> analyzeProject(backend));

    assertThat(throwable)
      .isInstanceOf(CompletionException.class)
      .hasCauseInstanceOf(ResponseErrorException.class);
    var responseErrorException = (ResponseErrorException) throwable.getCause();
    assertThat(responseErrorException.getResponseError().getCode()).isEqualTo(SonarLintRpcErrorCode.INVALID_ARGUMENT);
    assertThat(hasScaAnalysisWorkDir(backend)).isFalse();
  }

  private static void analyzeProject(SonarLintTestRpcServer backend) {
    backend.getDependencyRiskService().analyzeProject(new AnalyzeDependencyRiskProjectParams(CONFIG_SCOPE_ID)).join();
  }

  private static boolean hasScaAnalysisWorkDir(SonarLintTestRpcServer backend) throws IOException {
    var scaWorkDir = backend.getWorkDir().resolve("sca-scanner/work");
    if (!Files.isDirectory(scaWorkDir)) {
      return false;
    }
    try (Stream<Path> children = Files.list(scaWorkDir)) {
      return children.anyMatch(path -> path.getFileName().toString().startsWith("analyze-project-"));
    }
  }
}
