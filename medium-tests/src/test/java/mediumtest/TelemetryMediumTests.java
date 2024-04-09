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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddQuickFixAppliedForRuleParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddReportedRulesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisDoneOnSingleLanguageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.DevNotificationsClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.telemetry.InternalDebug;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorageManager;
import org.sonarsource.sonarlint.core.telemetry.TelemetrySpringConfig;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Collections.emptyMap;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.telemetry.TelemetrySpringConfig.PROPERTY_TELEMETRY_ENDPOINT;

class TelemetryMediumTests {

  @RegisterExtension
  static WireMockExtension telemetryEndpointMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private SonarLintTestRpcServer backend;
  private boolean oldDebugValue;

  @BeforeAll
  static void mockTelemetryEndpoint() {
    System.setProperty("sonarlint.internal.nodejs.forcedPath", "/path/to/nodeJS");
    System.setProperty("sonarlint.internal.nodejs.forcedVersion", "v3.1.4");
    telemetryEndpointMock.stubFor(post("/sonarlint-telemetry").willReturn(aResponse().withStatus(200)));
  }

  @AfterAll
  static void clearTelemetryEndpoint() {
    System.clearProperty(PROPERTY_TELEMETRY_ENDPOINT);
    System.clearProperty("sonarlint.internal.telemetry.initialDelay");
    System.clearProperty("sonarlint.internal.nodejs.forcedPath");
    System.clearProperty("sonarlint.internal.nodejs.forcedVersion");
  }

  @BeforeEach
  void saveInternalDebugFlag() {
    this.oldDebugValue = InternalDebug.isEnabled();
    InternalDebug.setEnabled(true);
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    InternalDebug.setEnabled(oldDebugValue);
  }

  @Test
  void it_should_not_create_telemetry_file_if_telemetry_disabled_by_system_property() throws ExecutionException, InterruptedException {
    backend = newBackend()
      .withSonarQubeConnection("connectionId", "http://localhost:12345", storage -> storage.withProject("projectKey", project -> project.withMainBranch("master")))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build();

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();

    this.backend.getHotspotService().openHotspotInBrowser(new OpenHotspotInBrowserParams("scopeId", "ab12ef45"));
    assertThat(backend.telemetryFilePath()).doesNotExist();
  }

  @Test
  void it_should_not_enable_telemetry_if_disabled_by_system_property() throws ExecutionException, InterruptedException {
    System.setProperty("sonarlint.internal.telemetry.initialDelay", "0");

    var fakeClient = newFakeClient()
      .build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", "http://localhost:12345", storage -> storage.withProject("projectKey",
        project -> project.withMainBranch("master")))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build(fakeClient);

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();
    backend.getTelemetryService().enableTelemetry();

    await().untilAsserted(() -> assertThat(fakeClient.getLogs()).extracting(LogParams::getMessage).contains(("Telemetry was disabled on " +
      "server startup. Ignoring client request.")));
    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();
  }

  @Test
  void it_should_create_telemetry_file_if_telemetry_enabled() {
    System.setProperty("sonarlint.internal.telemetry.initialDelay", "0");

    var fakeClient = newFakeClient()
      .build();

    backend = newBackend()
      .withSonarQubeConnection("connectionId", "http://localhost:12345", storage -> storage.withProject("projectKey", project -> project.withMainBranch("master")))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withTelemetryEnabled(telemetryEndpointMock.baseUrl() + "/sonarlint-telemetry")
      .build(fakeClient);

    this.backend.getHotspotService().openHotspotInBrowser(new OpenHotspotInBrowserParams("scopeId", "ab12ef45"));
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).isNotEmptyFile());

    await().untilAsserted(() -> telemetryEndpointMock.verify(postRequestedFor(urlEqualTo("/sonarlint-telemetry"))
      .withRequestBody(equalToJson("{\n" +
        "  \"sonarlint_version\" : \"1.2.3\",\n" +
        "  \"sonarlint_product\" : \"mediumTests\",\n" +
        "  \"ide_version\" : \"4.5.6\",\n" +
        "  \"platform\" : \"" + SystemUtils.OS_NAME + "\",\n" +
        "  \"architecture\" : \"" + SystemUtils.OS_ARCH + "\",\n" +
        "  \"nodejs\" : \"3.1.4\",\n" +
        "  \"connected_mode_used\" : false,\n" +
        "  \"connected_mode_sonarcloud\" : false\n" +
        "}", true, true))));

    System.clearProperty("sonarlint.internal.telemetry.initialDelay");
  }

  @Test
  void it_should_consider_telemetry_status_in_file() throws ExecutionException, InterruptedException {
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .build();

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isTrue();
    assertThat(backend.telemetryFilePath()).isNotEmptyFile();

    // Emulate another process has disabled telemetry
    var telemetryLocalStorageManager = new TelemetryLocalStorageManager(backend.telemetryFilePath());
    telemetryLocalStorageManager.tryUpdateAtomically(data -> data.setEnabled(false));

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();
  }

  @Test
  void it_should_ping_telemetry_endpoint() throws ExecutionException, InterruptedException {
    System.setProperty("sonarlint.internal.telemetry.initialDelay", "0");
    var fakeClient = newFakeClient().build();
    when(fakeClient.getTelemetryLiveAttributes()).thenReturn(new TelemetryClientLiveAttributesResponse(emptyMap()));

    backend = newBackend()
      .withTelemetryEnabled(telemetryEndpointMock.baseUrl() + "/sonarlint-telemetry")
      .build(fakeClient);

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isTrue();

    telemetryEndpointMock.verify(postRequestedFor(urlEqualTo("/sonarlint-telemetry"))
      .withRequestBody(equalToJson("{\n" +
        "  \"sonarlint_version\" : \"1.2.3\",\n" +
        "  \"sonarlint_product\" : \"mediumTests\",\n" +
        "  \"ide_version\" : \"4.5.6\",\n" +
        "  \"platform\" : \"" + SystemUtils.OS_NAME + "\",\n" +
        "  \"architecture\" : \"" + SystemUtils.OS_ARCH + "\",\n" +
        "  \"nodejs\" : \"3.1.4\",\n" +
        "  \"connected_mode_used\" : false,\n" +
        "  \"connected_mode_sonarcloud\" : false\n" +
        "}", true, true)));
    System.clearProperty("sonarlint.internal.telemetry.initialDelay");
  }

  @Test
  void it_should_disable_telemetry() throws ExecutionException, InterruptedException {
    System.setProperty("sonarlint.internal.telemetry.initialDelay", "0");

    var fakeClient = newFakeClient().build();
    when(fakeClient.getTelemetryLiveAttributes()).thenReturn(new TelemetryClientLiveAttributesResponse(emptyMap()));

    backend = newBackend()
      .withTelemetryEnabled(telemetryEndpointMock.baseUrl() + "/sonarlint-telemetry")
      .build(fakeClient);

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isTrue();

    telemetryEndpointMock.verify(postRequestedFor(urlEqualTo("/sonarlint-telemetry"))
      .withRequestBody(equalToJson("{\n" +
        "  \"sonarlint_version\" : \"1.2.3\",\n" +
        "  \"sonarlint_product\" : \"mediumTests\",\n" +
        "  \"ide_version\" : \"4.5.6\",\n" +
        "  \"platform\" : \"" + SystemUtils.OS_NAME + "\",\n" +
        "  \"architecture\" : \"" + SystemUtils.OS_ARCH + "\",\n" +
        "  \"nodejs\" : \"3.1.4\",\n" +
        "  \"connected_mode_used\" : false,\n" +
        "  \"connected_mode_sonarcloud\" : false\n" +
        "}", true, true)));

    backend.getTelemetryService().disableTelemetry();

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();
    System.clearProperty("sonarlint.internal.telemetry.initialDelay");
  }

  @Test
  void it_should_enable_disabled_telemetry() throws ExecutionException, InterruptedException {
    System.setProperty("sonarlint.internal.telemetry.initialDelay", "0");

    var fakeClient = newFakeClient().build();
    when(fakeClient.getTelemetryLiveAttributes()).thenReturn(new TelemetryClientLiveAttributesResponse(emptyMap()));

    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .build(fakeClient);

    backend.getTelemetryService().disableTelemetry();
    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();

    backend.getTelemetryService().enableTelemetry();
    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isTrue();
    System.clearProperty("sonarlint.internal.telemetry.initialDelay");
  }

  @Test
  void it_should_not_crash_when_cannot_build_payload() {
    var fakeClient = newFakeClient().build();
    when(fakeClient.getTelemetryLiveAttributes()).thenThrow(new IllegalStateException("Unexpected error"));
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build(fakeClient);
    assertThat(telemetryEndpointMock.getAllServeEvents()).isEmpty();
  }

  @Test
  void failed_upload_payload_should_log_if_debug() {
    System.setProperty("sonarlint.internal.telemetry.initialDelay", "0");
    var originalValue = InternalDebug.isEnabled();
    InternalDebug.setEnabled(true);

    var fakeClient = newFakeClient().build();
    when(fakeClient.getTelemetryLiveAttributes()).thenThrow(new IllegalStateException("Unexpected error"));
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .build(fakeClient);

    assertThat(telemetryEndpointMock.getAllServeEvents()).isEmpty();
    await().untilAsserted(() -> assertThat(fakeClient.getLogs()).extracting(LogParams::getMessage).contains(("Failed to fetch telemetry payload")));
    InternalDebug.setEnabled(originalValue);
    System.clearProperty("sonarlint.internal.telemetry.initialDelay");
  }

  @Test
  void should_increment_numDays_on_analysis_once_per_day() {
    setupClientAndBackend();

    backend.getTelemetryService().analysisDoneOnMultipleFiles();
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"numUseDays\":1"));

    backend.getTelemetryService().analysisDoneOnMultipleFiles();
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"numUseDays\":1"));
  }

  @Test
  void it_should_accumulate_investigated_taint_vulnerabilities() {
    setupClientAndBackend();

    backend.getTelemetryService().taintVulnerabilitiesInvestigatedLocally();
    backend.getTelemetryService().taintVulnerabilitiesInvestigatedRemotely();
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains(
      "\"taintVulnerabilitiesInvestigatedLocallyCount\":1,\"taintVulnerabilitiesInvestigatedRemotelyCount\":1"));

    backend.getTelemetryService().taintVulnerabilitiesInvestigatedLocally();
    backend.getTelemetryService().taintVulnerabilitiesInvestigatedLocally();
    backend.getTelemetryService().taintVulnerabilitiesInvestigatedRemotely();
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains(
      "\"taintVulnerabilitiesInvestigatedLocallyCount\":3,\"taintVulnerabilitiesInvestigatedRemotelyCount\":2"));
  }

  @Test
  void it_should_accumulate_clicked_dev_notifications() {
    setupClientAndBackend();
    var notificationEvent = "myNotification";

    backend.getTelemetryService().devNotificationsClicked(new DevNotificationsClickedParams(notificationEvent));
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains(
      "\"notificationsCountersByEventType\":{\"" + notificationEvent + "\":{\"devNotificationsCount\":0,\"devNotificationsClicked\":1}}"));

    backend.getTelemetryService().devNotificationsClicked(new DevNotificationsClickedParams(notificationEvent));
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains(
      "\"notificationsCountersByEventType\":{\"" + notificationEvent + "\":{\"devNotificationsCount\":0,\"devNotificationsClicked\":2}}"));
  }

  @Test
  void it_should_record_helpAndFeedbackLinkClicked() {
    setupClientAndBackend();

    backend.getTelemetryService().helpAndFeedbackLinkClicked(new HelpAndFeedbackClickedParams("itemId"));
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains(
      "\"helpAndFeedbackLinkClickedCount\":{\"itemId\":{\"helpAndFeedbackLinkClickedCount\":1}}"));
  }

  @Test
  void it_should_record_addQuickFixAppliedForRule() {
    setupClientAndBackend();

    backend.getTelemetryService().addQuickFixAppliedForRule(new AddQuickFixAppliedForRuleParams("ruleKey"));
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"quickFixesApplied\":[\"ruleKey\"]"));
  }

  @Test
  void it_should_record_addReportedRules() {
    setupClientAndBackend();

    backend.getTelemetryService().addReportedRules(new AddReportedRulesParams(Set.of("ruleA")));
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"raisedIssuesRules\":[\"ruleA\"]"));
  }

  @Test
  void it_should_record_taintVulnerabilitiesInvestigatedRemotely() {
    setupClientAndBackend();

    backend.getTelemetryService().taintVulnerabilitiesInvestigatedRemotely();
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"taintVulnerabilitiesInvestigatedRemotelyCount\":1"));
  }

  @Test
  void it_should_record_taintVulnerabilitiesInvestigatedLocally() {
    setupClientAndBackend();

    backend.getTelemetryService().taintVulnerabilitiesInvestigatedLocally();
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"taintVulnerabilitiesInvestigatedLocallyCount\":1"));
  }

  @Test
  void it_should_record_analysisDoneOnSingleLanguage() {
    setupClientAndBackend();

    backend.getTelemetryService().analysisDoneOnSingleLanguage(new AnalysisDoneOnSingleLanguageParams(Language.JAVA, 1000));
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"analyzers" +
      "\":{\"java\":{\"analysisCount\":1,\"frequencies\":{\"0-300\":0,\"300-500\":0,\"500-1000\":0,\"1000-2000\":1,\"2000-4000\":0,\"4000+\":0}}"));
  }

  @Test
  void it_should_record_analysisDoneOnMultipleFiles() {
    setupClientAndBackend();

    backend.getTelemetryService().analysisDoneOnMultipleFiles();
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"numUseDays\":1"));
  }

  @Test
  void it_should_record_addedManualBindings() {
    setupClientAndBackend();

    backend.getTelemetryService().addedManualBindings();
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"manualAddedBindingsCount\":1"));
  }

  @Test
  void it_should_record_addedImportedBindings() {
    setupClientAndBackend();

    backend.getTelemetryService().addedImportedBindings();
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"importedAddedBindingsCount\":1"));
  }

  @Test
  void it_should_record_addedAutomaticBindings() {
    setupClientAndBackend();

    backend.getTelemetryService().addedAutomaticBindings();
    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"autoAddedBindingsCount\":1"));
  }

  private void setupClientAndBackend() {
    var fakeClient = newFakeClient().build();

    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .build(fakeClient);
  }

}
