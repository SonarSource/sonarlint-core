/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import java.net.URL;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.OpenDependencyRiskInBrowserParams;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

class OpenDependencyRiskInBrowserMediumTests {
  static final String CONNECTION_ID = "connectionId";
  static final String SCOPE_ID = "scopeId";
  static final String PROJECT_KEY = "projectKey";
  static final UUID DEPENDENCY_KEY = UUID.randomUUID();
  static final String BRANCH_NAME = "master";

  @SonarLintTest
  void it_should_open_dependency_risk_in_sonarqube(SonarLintTestHarness harness) throws IOException {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, "http://localhost:12345", storage -> storage.withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME)))
      .withBoundConfigScope(SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withTelemetryEnabled()
      .start(fakeClient);

    backend.getDependencyRiskService().openDependencyRiskInBrowser(new OpenDependencyRiskInBrowserParams(
      SCOPE_ID, DEPENDENCY_KEY)).join();

    var expectedUrl = String.format("http://localhost:12345/dependency-risks/%s/what?id=%s&branch=%s",
      urlEncode(DEPENDENCY_KEY.toString()), urlEncode(PROJECT_KEY), urlEncode(BRANCH_NAME));

    verify(fakeClient, timeout(5000)).openUrlInBrowser(new URL(expectedUrl));
    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getDependencyRiskInvestigatedRemotelyCount()).isEqualTo(1));
  }

  @SonarLintTest
  void it_should_not_open_dependency_risk_if_unbound(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(SCOPE_ID)
      .start(fakeClient);

    var result = backend.getDependencyRiskService().openDependencyRiskInBrowser(new OpenDependencyRiskInBrowserParams(
      SCOPE_ID, DEPENDENCY_KEY));

    assertThat(result).failsWithin(Duration.ofSeconds(2)).withThrowableOfType(ExecutionException.class)
      .withMessage("org.eclipse.lsp4j.jsonrpc.ResponseErrorException: Configuration scope 'scopeId' is not bound properly, unable to open dependency risk");
    verify(fakeClient, timeout(5000).times(0)).openUrlInBrowser(any(URL.class));
  }
}
