/*
 * SonarLint Core - ITs - Tests
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
package its;

import its.utils.AnalysisUtils;
import its.utils.PluginLocator;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;

import static its.StandaloneTests.IT_CLIENT_INFO;
import static its.StandaloneTests.IT_TELEMETRY_ATTRIBUTES;
import static its.utils.AnalysisUtils.analyzeFile;
import static its.utils.AnalysisUtils.analyzeUntilIssuesAreRaised;
import static its.utils.AnalysisUtils.registerFiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.CS;

class StandaloneCSharpTests {
  private static final String CONFIG_SCOPE_ID = "my-ide-csharp-project";

  @TempDir
  private static Path sonarUserHome;

  private static SonarLintRpcServer backend;
  private static MockSonarLintRpcClientDelegate client;

  @BeforeAll
  static void prepare() throws IOException {
    var clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    var serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);
    client = new MockSonarLintRpcClientDelegate();
    new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, client);
    backend = clientLauncher.getServerProxy();
    try {
      backend.initialize(
        new InitializeParams(IT_CLIENT_INFO, IT_TELEMETRY_ATTRIBUTES, HttpConfigurationDto.defaultConfig(), null, Set.of(),
          sonarUserHome.resolve("storage"),
          sonarUserHome.resolve("work"),
          Set.of(PluginLocator.getOmnisharpPluginPath()), Collections.emptyMap(),
          Set.of(CS), Collections.emptySet(), Collections.emptySet(), Collections.emptyList(), Collections.emptyList(), sonarUserHome.toString(), Map.of(),
          false, null, false, null))
        .get();
      backend.getConfigurationService()
        .didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, false, CONFIG_SCOPE_ID, null))));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot initialize the backend", e);
    }
  }

  @AfterAll
  static void stop() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void shouldRaiseOnlyNonEnterpriseIssuesOnACSharpProject() throws IOException {
    var baseDir = Path.of("projects", "sample-csharp").toRealPath();
    registerFiles(backend, CONFIG_SCOPE_ID, baseDir, "Foo.cs", "Enterprise.cs", "Foo.csproj", "Foo.sln");

    var fooIssues = analyzeUntilIssuesAreRaised(backend, client, CONFIG_SCOPE_ID, baseDir.resolve("Foo.cs"));
    var enterpriseIssues = analyzeFile(backend, client, CONFIG_SCOPE_ID, baseDir.resolve("Enterprise.cs"));

    assertThat(fooIssues)
      .extracting(RaisedIssueDto::getRuleKey, AnalysisUtils::startLine)
      .containsOnly(
        tuple("csharpsquid:S3903", 1),
        tuple("csharpsquid:S2325", 2),
        tuple("csharpsquid:S1481", 3));
    assertThat(enterpriseIssues)
      .extracting(RaisedIssueDto::getRuleKey)
      .isNotEmpty()
      .doesNotContain("csharpsquid:S2259");
  }
}
