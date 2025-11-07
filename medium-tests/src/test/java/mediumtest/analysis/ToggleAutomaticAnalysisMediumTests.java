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
package mediumtest.analysis;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.embedded.server.ToggleAutomaticAnalysisRequestHandler;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.EMBEDDED_SERVER;

class ToggleAutomaticAnalysisMediumTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester(true);

  private final Gson gson = new Gson();

  @SonarLintTest
  void it_should_enable_automatic_analysis(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var backend = harness.newBackend()
      .withBackendCapability(EMBEDDED_SERVER)
      .start();

    var response = executePostAutomaticAnalysisEnablementRequest(backend, "enabled=true");
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(response.statusCode()).isEqualTo(200));
  }

  @SonarLintTest
  void it_should_disable_automatic_analysis(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var backend = harness.newBackend()
      .withBackendCapability(EMBEDDED_SERVER)
      .start();

    var response = executePostAutomaticAnalysisEnablementRequest(backend, "enabled=false");
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(response.statusCode()).isEqualTo(200));
  }

  @SonarLintTest
  void it_should_return_bad_request_for_missing_parameter(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var backend = harness.newBackend()
      .withBackendCapability(EMBEDDED_SERVER)
      .start();

    var response = executePostAutomaticAnalysisEnablementRequest(backend, "invalid=param");
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(response.statusCode()).isEqualTo(400));
    var errorMessage = gson.fromJson(response.body(), ToggleAutomaticAnalysisRequestHandler.ErrorMessage.class);
    assertThat(errorMessage.message()).isEqualTo("Missing 'enabled' query parameter");
  }

  @SonarLintTest
  void it_should_return_bad_request_for_get_request(SonarLintTestHarness harness) throws IOException, InterruptedException {
    var backend = harness.newBackend()
      .withBackendCapability(EMBEDDED_SERVER)
      .start();

    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> 
      assertThat(backend.getEmbeddedServerPort()).isGreaterThan(0));

    var response = executeGetAutomaticAnalysisEnablementRequest(backend);
    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(response.statusCode()).isEqualTo(400));
  }

  private HttpResponse<String> executeGetAutomaticAnalysisEnablementRequest(SonarLintTestRpcServer backend) throws IOException, InterruptedException {
    return HttpClient.newHttpClient().send(
      executeToggleAutomaticAnalysisRequest(backend, null).GET().build(),
      HttpResponse.BodyHandlers.ofString()
    );
  }

  private HttpResponse<String> executePostAutomaticAnalysisEnablementRequest(SonarLintTestRpcServer backend, String queryParams) throws IOException, InterruptedException {
    return HttpClient.newHttpClient().send(
      executeToggleAutomaticAnalysisRequest(backend, queryParams).POST(HttpRequest.BodyPublishers.noBody()).build(),
      HttpResponse.BodyHandlers.ofString()
    );
  }

  private HttpRequest.Builder executeToggleAutomaticAnalysisRequest(SonarLintTestRpcServer backend, String queryParams) {
    var uri = "http://localhost:" + backend.getEmbeddedServerPort() + "/sonarlint/api/analysis/automatic/config" + (queryParams != null && !queryParams.isEmpty() ? ("?" + queryParams) : "");
    return HttpRequest.newBuilder()
      .uri(URI.create(uri))
      .header("Origin", "http://localhost");
  }

}
