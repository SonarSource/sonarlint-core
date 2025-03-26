/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource SA
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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import mediumtest.analysis.sensor.WaitingCancellationSensor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static mediumtest.analysis.sensor.WaitingCancellationSensor.CANCELLATION_FILE_PATH_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.RequestCancelled;
import static org.sonarsource.sonarlint.core.analysis.AnalysisQueue.ANALYSIS_EXPIRATION_DELAY_PROPERTY_NAME;
import static org.sonarsource.sonarlint.core.test.utils.plugins.SonarPluginBuilder.newSonarPlugin;
import static utils.AnalysisUtils.createFile;

class AnalysisCancellationMediumTests {

  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";
  private static final String CONNECTION_ID = "connectionId";

  @AfterEach
  void init_property() {
    System.clearProperty(ANALYSIS_EXPIRATION_DELAY_PROPERTY_NAME);
  }

  @SonarLintTest
  void it_should_cancel_progress_monitor_when_analysis_is_canceled(SonarLintTestHarness harness, @TempDir Path baseDir) throws InterruptedException {
    var filePath = createFile(baseDir, "pom.xml", "");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false,
        null, filePath, null, null, true)))
      .build();
    var plugin = newSonarPlugin("xml")
      .withSensor(WaitingCancellationSensor.class)
      .generate(baseDir);
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPlugin(plugin)
      .withEnabledLanguageInStandaloneMode(Language.XML)
      .start(client);
    var cancelationFilePath = baseDir.resolve("cancellation.result");
    var future = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(fileUri),
        Map.of(CANCELLATION_FILE_PATH_PROPERTY_NAME, cancelationFilePath.toString()), false, System.currentTimeMillis()));
    Thread.sleep(500);

    future.cancel(false);

    assertThat(future.isCancelled()).isTrue();
    await().atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(cancelationFilePath).hasContent("CANCELED"));
  }

  @SonarLintTest
  void it_should_cancel_analysis_when_not_ready_for_a_while(SonarLintTestHarness harness, @TempDir Path baseDir) throws InterruptedException {
    System.setProperty(ANALYSIS_EXPIRATION_DELAY_PROPERTY_NAME, "PT0S");
    var filePath = createFile(baseDir, "pom.xml", "");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false,
        null, filePath, null, null, true)))
      .build();
    var server = harness.newFakeSonarQubeServer()
      .withPlugin(TestPlugin.XML)
      .withProject("projectKey")
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage
        .withPlugins(TestPlugin.XML)
        .withProject("projectKey2", project -> project.withMainBranch("main")))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, "projectKey")
      .withBoundConfigScope("otherConfigScope", CONNECTION_ID, "projectKey2")
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .start(client);
    // this first analysis will never be ready
    var firstAnalysisFuture = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(fileUri), Map.of(), false, System.currentTimeMillis()));
    Thread.sleep(500);

    // trigger analysis queue cleanup by posting a new analysis
    backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams("otherConfigScope", UUID.randomUUID(), List.of(fileUri), Map.of(), false, System.currentTimeMillis()));

    assertThat(firstAnalysisFuture)
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode)
      .isEqualTo(RequestCancelled.getValue());
  }

  @SonarLintTest
  void it_should_cancel_pending_analyses_when_closing_a_configuration_scope(SonarLintTestHarness harness, @TempDir Path baseDir) throws InterruptedException {
    System.setProperty(ANALYSIS_EXPIRATION_DELAY_PROPERTY_NAME, "PT0S");
    var filePath = createFile(baseDir, "pom.xml", "");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false,
        null, filePath, null, null, true)))
      .build();
    var server = harness.newFakeSonarQubeServer()
      .withPlugin(TestPlugin.XML)
      .withProject("projectKey")
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .start(client);

    var future = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(fileUri), Map.of(), false, System.currentTimeMillis()));
    Thread.sleep(500);

    backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams(CONFIG_SCOPE_ID));

    assertThat(future)
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableThat()
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .asInstanceOf(InstanceOfAssertFactories.type(ResponseErrorException.class))
      .extracting(ResponseErrorException::getResponseError)
      .extracting(ResponseError::getCode)
      .isEqualTo(RequestCancelled.getValue());
  }
}
