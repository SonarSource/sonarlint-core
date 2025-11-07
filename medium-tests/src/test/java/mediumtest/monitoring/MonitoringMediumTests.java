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
package mediumtest.monitoring;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.monitoring.DogfoodEnvironmentDetectionService;
import org.sonarsource.sonarlint.core.commons.monitoring.MonitoringService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import utils.TestPlugin;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.MONITORING;
import static org.sonarsource.sonarlint.core.test.utils.plugins.SonarPluginBuilder.newSonarPlugin;
import static utils.AnalysisUtils.analyzeFileAndGetIssues;
import static utils.AnalysisUtils.createFile;

@ExtendWith(SystemStubsExtension.class)
class MonitoringMediumTests {
  private static final String CONFIGURATION_SCOPE_ID = "configScopeId";
  private WireMockServer sentryServer;

  @SystemStub
  private EnvironmentVariables environmentVariables;

  @BeforeEach
  void setup() {
    sentryServer = new WireMockServer(wireMockConfig().dynamicPort());
    sentryServer.start();
    System.setProperty(MonitoringService.DSN_PROPERTY, createValidSentryDsn(sentryServer));
    System.setProperty(MonitoringService.TRACES_SAMPLE_RATE_PROPERTY, "1");
    environmentVariables.set(DogfoodEnvironmentDetectionService.SONARSOURCE_DOGFOODING_ENV_VAR_KEY, "1");
    setupSentryStubs();
  }

  @AfterEach
  void tearDown() {
    sentryServer.stop();
  }

  private String createValidSentryDsn(WireMockServer server) {
    return "http://fake-public-key@localhost:" + server.port() + "/12345";
  }

  private void setupSentryStubs() {
    // Stub the Sentry project endpoint (where events are sent)
    sentryServer.stubFor(post(urlPathMatching("/api/\\d+/store/"))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"id\": \"event-id-12345\"}")));

    // Stub the Sentry envelope endpoint (used for transactions, etc.)
    sentryServer.stubFor(post(urlPathMatching("/api/\\d+/envelope/"))
      .willReturn(aResponse()
        .withStatus(200)));
  }

  @SonarLintTest
  void simplePhpWithMonitoring(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var inputFile = createFile(baseDir, "foo.php", """
      <?php
      function writeMsg($fname) {
          $i = 0; // NOSONAR
          echo "Hello world!";
      }
      ?>
      """);

    var client = harness.newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)))
      .build();

    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PHP)
      .withBackendCapability(MONITORING)
      .start(client);

    var issues = analyzeFileAndGetIssues(inputFile.toUri(), client, backend, CONFIGURATION_SCOPE_ID);

    assertThat(issues).extracting(RaisedIssueDto::getRuleKey, i -> i.getTextRange().getStartLine()).contains(tuple("php:S1172", 2));

    // The mock Sentry server receives 1 event for the analysis trace
    await().atMost(1, TimeUnit.SECONDS).untilAsserted(() ->  assertThat(sentryServer.getAllServeEvents()).hasSize(1));
  }

  @SonarLintTest
  void analysisErrorsWithTracing(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var content = """
      <?php
      function writeMsg($fname) {
          echo "Hello world!;
      }
      ?>""";
    var inputFile = createFile(baseDir, "foo.php", content);

    var client = harness.newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIGURATION_SCOPE_ID, false, null, inputFile, null, null, true)))
      .build();

    var throwingPluginPath = newSonarPlugin("php")
      .withSensor(ThrowingPhpSensor.class)
      .generate(baseDir);

    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPlugin(throwingPluginPath)
      .withEnabledLanguageInStandaloneMode(Language.PHP)
      .withBackendCapability(MONITORING)
      .start(client);

    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(CONFIGURATION_SCOPE_ID, analysisId, List.of(inputFile.toUri()), Map.of(), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().during(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIGURATION_SCOPE_ID)).isEmpty());
    // The mock Sentry server receives 2 events: one for the trace, one for the exception
    await().atMost(1, TimeUnit.SECONDS).untilAsserted(() ->  assertThat(sentryServer.getAllServeEvents()).hasSize(2));
    assertThat(sentryServer.getAllServeEvents())
      .extracting(e -> e.getRequest().getBodyAsString())
      // Server name should be removed from events
      .noneMatch(m -> m.contains("server_name"));
  }

  @SonarLintTest
  void it_should_not_capture_silenced_exception(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var content = """
      [3, 1, 4, 1, 5, 9]
      result = set(sorted(data))
      
      result = set(sordata))
      """;
    var newContent = """
      [3, 1, 4, 1, 5, 9]
      result = set(sorted(data))
      
      result = set(sordata))
      """;
    var filePath = createFile(baseDir, "invalid.py", content);
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIGURATION_SCOPE_ID, false, null, filePath, content, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIGURATION_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .withBackendCapability(MONITORING)
      .start(client);

    var updatedFile = new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIGURATION_SCOPE_ID, false, null, filePath, newContent, Language.PYTHON, true);
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(), List.of(updatedFile), List.of()));

    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Error processing file event"));
    await().atLeast(100, TimeUnit.MILLISECONDS).untilAsserted(() ->  assertThat(sentryServer.getAllServeEvents()).isEmpty());
  }
}
