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
package mediumtest;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryMigrationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddQuickFixAppliedForRuleParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddReportedRulesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisDoneOnSingleLanguageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingTriggeredParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.DevNotificationsClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FindingsFilteredParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionResolvedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.McpTransportMode;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.McpTransportModeUsedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ToolCalledParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.telemetry.InternalDebug;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorage;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorageManager;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.telemetry.TelemetrySpringConfig.PROPERTY_TELEMETRY_ENDPOINT;
import static utils.AnalysisUtils.analyzeFileAndGetIssue;
import static utils.AnalysisUtils.analyzeFileAndGetIssues;

class TelemetryMediumTests {

  @RegisterExtension
  static WireMockExtension telemetryEndpointMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

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
  void tearDown() {
    InternalDebug.setEnabled(oldDebugValue);
  }

  @SonarLintTest
  void it_should_not_create_telemetry_file_if_telemetry_disabled_by_system_property(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", "http://localhost:12345", storage -> storage.withProject("projectKey", project -> project.withMainBranch("master")))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .start();

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();

    backend.getHotspotService().openHotspotInBrowser(new OpenHotspotInBrowserParams("scopeId", "ab12ef45"));
    assertThat(backend.telemetryFilePath()).doesNotExist();
  }

  @SonarLintTest
  void it_should_not_enable_telemetry_if_disabled_by_system_property(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    System.setProperty("sonarlint.internal.telemetry.initialDelay", "0");

    var fakeClient = harness.newFakeClient()
      .build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", "http://localhost:12345", storage -> storage.withProject("projectKey",
        project -> project.withMainBranch("master")))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .start(fakeClient);

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();
    backend.getTelemetryService().enableTelemetry();

    await().untilAsserted(() -> assertThat(fakeClient.getLogs()).extracting(LogParams::getMessage).contains(("Telemetry was disabled on " +
      "server startup. Ignoring client request.")));
    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();
  }

  @SonarLintTest
  void it_should_create_telemetry_file_if_telemetry_enabled(SonarLintTestHarness harness) {
    System.setProperty("sonarlint.internal.telemetry.initialDelay", "0");

    var fakeClient = harness.newFakeClient()
      .build();

    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", "http://localhost:12345", storage -> storage.withProject("projectKey", project -> project.withMainBranch("master")))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withTelemetryEnabled(telemetryEndpointMock.baseUrl() + "/sonarlint-telemetry")
      .withEnabledLanguageInStandaloneMode(Language.JS)
      .start(fakeClient);

    backend.getHotspotService().openHotspotInBrowser(new OpenHotspotInBrowserParams("scopeId", "ab12ef45"));
    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().openHotspotInBrowserCount()).isEqualTo(1));

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

  @SonarLintTest
  void it_should_consider_telemetry_status_in_file(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .start();

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isTrue();
    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().enabled()).isTrue());

    // Emulate another process has disabled telemetry
    var telemetryLocalStorageManager = new TelemetryLocalStorageManager(backend.telemetryFilePath(), mock(InitializeParams.class));
    telemetryLocalStorageManager.tryUpdateAtomically(data -> data.setEnabled(false));

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();
    assertThat(backend.telemetryFileContent().enabled()).isFalse();
  }

  @SonarLintTest
  void it_should_ping_telemetry_endpoint(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    System.setProperty("sonarlint.internal.telemetry.initialDelay", "0");
    var fakeClient = harness.newFakeClient().build();
    when(fakeClient.getTelemetryLiveAttributes()).thenReturn(new TelemetryClientLiveAttributesResponse(emptyMap()));

    var backend = harness.newBackend()
      .withTelemetryEnabled(telemetryEndpointMock.baseUrl() + "/sonarlint-telemetry")
      .withEnabledLanguageInStandaloneMode(Language.JS)
      .start(fakeClient);

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

  @SonarLintTest
  void it_should_disable_telemetry(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    System.setProperty("sonarlint.internal.telemetry.initialDelay", "0");

    var fakeClient = harness.newFakeClient().build();
    when(fakeClient.getTelemetryLiveAttributes()).thenReturn(new TelemetryClientLiveAttributesResponse(emptyMap()));

    var backend = harness.newBackend()
      .withTelemetryEnabled(telemetryEndpointMock.baseUrl() + "/sonarlint-telemetry")
      .withEnabledLanguageInStandaloneMode(Language.JS)
      .start(fakeClient);

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

  @SonarLintTest
  void it_should_enable_disabled_telemetry(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    System.setProperty("sonarlint.internal.telemetry.initialDelay", "0");

    var fakeClient = harness.newFakeClient().build();
    when(fakeClient.getTelemetryLiveAttributes()).thenReturn(new TelemetryClientLiveAttributesResponse(emptyMap()));

    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .start(fakeClient);

    backend.getTelemetryService().disableTelemetry();
    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();

    backend.getTelemetryService().enableTelemetry();
    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isTrue();
    System.clearProperty("sonarlint.internal.telemetry.initialDelay");
  }

  @SonarLintTest
  void it_should_not_crash_when_cannot_build_payload(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    when(fakeClient.getTelemetryLiveAttributes()).thenThrow(new IllegalStateException("Unexpected error"));
    harness.newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .start(fakeClient);
    assertThat(telemetryEndpointMock.getAllServeEvents()).isEmpty();
  }

  @SonarLintTest
  void failed_upload_payload_should_log_if_debug(SonarLintTestHarness harness) {
    System.setProperty("sonarlint.internal.telemetry.initialDelay", "0");
    var originalValue = InternalDebug.isEnabled();
    InternalDebug.setEnabled(true);

    var fakeClient = harness.newFakeClient().build();
    when(fakeClient.getTelemetryLiveAttributes()).thenThrow(new IllegalStateException("Unexpected error"));
    harness.newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .start(fakeClient);

    assertThat(telemetryEndpointMock.getAllServeEvents()).isEmpty();
    await().untilAsserted(() -> assertThat(fakeClient.getLogs()).extracting(LogParams::getMessage).contains(("Failed to fetch telemetry payload")));
    InternalDebug.setEnabled(originalValue);
    System.clearProperty("sonarlint.internal.telemetry.initialDelay");
  }

  @SonarLintTest
  void should_increment_numDays_on_analysis_once_per_day(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().analysisDoneOnMultipleFiles();
    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().numUseDays()).isEqualTo(1));

    backend.getTelemetryService().analysisDoneOnMultipleFiles();
    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().numUseDays()).isEqualTo(1));
  }

  @SonarLintTest
  void it_should_accumulate_investigated_taint_vulnerabilities(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().taintVulnerabilitiesInvestigatedLocally();
    backend.getTelemetryService().taintVulnerabilitiesInvestigatedRemotely();
    await().untilAsserted(() -> assertThat(backend.telemetryFileContent())
      .extracting(TelemetryLocalStorage::taintVulnerabilitiesInvestigatedLocallyCount, TelemetryLocalStorage::taintVulnerabilitiesInvestigatedRemotelyCount)
      .containsExactly(1, 1));

    backend.getTelemetryService().taintVulnerabilitiesInvestigatedLocally();
    backend.getTelemetryService().taintVulnerabilitiesInvestigatedLocally();
    backend.getTelemetryService().taintVulnerabilitiesInvestigatedRemotely();

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent())
      .extracting(TelemetryLocalStorage::taintVulnerabilitiesInvestigatedLocallyCount, TelemetryLocalStorage::taintVulnerabilitiesInvestigatedRemotelyCount)
      .containsExactly(3, 2));
  }

  @SonarLintTest
  void it_should_accumulate_investigated_dependency_risks(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().dependencyRiskInvestigatedLocally();

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getDependencyRiskInvestigatedLocallyCount()).isEqualTo(1));

    backend.getTelemetryService().dependencyRiskInvestigatedLocally();
    backend.getTelemetryService().dependencyRiskInvestigatedLocally();

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getDependencyRiskInvestigatedLocallyCount()).isEqualTo(3));
  }

  @SonarLintTest
  void it_should_accumulate_clicked_dev_notifications(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);
    var notificationEvent = "myNotification";

    backend.getTelemetryService().devNotificationsClicked(new DevNotificationsClickedParams(notificationEvent));
    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().notifications())
      .extractingFromEntries(Map.Entry::getKey, e -> tuple(e.getValue().getDevNotificationsCount(), e.getValue().getDevNotificationsClicked()))
      .containsOnly(tuple("myNotification", tuple(0, 1))));

    backend.getTelemetryService().devNotificationsClicked(new DevNotificationsClickedParams(notificationEvent));
    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().notifications())
      .extractingFromEntries(Map.Entry::getKey, e -> tuple(e.getValue().getDevNotificationsCount(), e.getValue().getDevNotificationsClicked()))
      .containsOnly(tuple("myNotification", tuple(0, 2))));
  }

  @SonarLintTest
  void it_should_record_helpAndFeedbackLinkClicked(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().helpAndFeedbackLinkClicked(new HelpAndFeedbackClickedParams("itemId"));

    await()
      .untilAsserted(() -> assertThat(backend.telemetryFileContent().getHelpAndFeedbackLinkClickedCounter())
        .extractingFromEntries(Map.Entry::getKey, e -> e.getValue().getHelpAndFeedbackLinkClickedCount())
        .containsOnly(tuple("itemId", 1)));
  }

  @SonarLintTest
  void it_should_record_analysisReportingTriggered(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().analysisReportingTriggered(new AnalysisReportingTriggeredParams(AnalysisReportingType.ALL_FILES_ANALYSIS_TYPE));

    await().untilAsserted(
      () -> assertThat(backend.telemetryFileContent().getAnalysisReportingCountersByType())
        .extractingFromEntries(Map.Entry::getKey, e -> e.getValue().getAnalysisReportingCount())
        .containsOnly(tuple(AnalysisReportingType.ALL_FILES_ANALYSIS_TYPE, 1)));
  }

  @SonarLintTest
  void it_should_record_fixSuggestionResolved(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().fixSuggestionResolved(new FixSuggestionResolvedParams("suggestionId", FixSuggestionStatus.ACCEPTED, 0));
    backend.getTelemetryService().fixSuggestionResolved(new FixSuggestionResolvedParams("suggestionId2", FixSuggestionStatus.DECLINED, null));

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getFixSuggestionResolved())
      .extractingFromEntries(Map.Entry::getKey,
        e -> e.getValue().stream().map(status -> tuple(status.getFixSuggestionResolvedStatus(), status.getFixSuggestionResolvedSnippetIndex())).toList())
      .containsOnly(
        tuple("suggestionId", List.of(tuple(FixSuggestionStatus.ACCEPTED, 0))),
        tuple("suggestionId2", List.of(tuple(FixSuggestionStatus.DECLINED, null)))));
  }

  @SonarLintTest
  void it_should_record_addQuickFixAppliedForRule(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().addQuickFixAppliedForRule(new AddQuickFixAppliedForRuleParams("ruleKey"));

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getQuickFixesApplied()).isEqualTo(Set.of("ruleKey")));
  }

  @SonarLintTest
  void it_should_record_addReportedRules(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().addReportedRules(new AddReportedRulesParams(Set.of("ruleA")));

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getRaisedIssuesRules()).isEqualTo(Set.of("ruleA")));
  }

  @SonarLintTest
  void it_should_record_taintVulnerabilitiesInvestigatedRemotely(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().taintVulnerabilitiesInvestigatedRemotely();

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().taintVulnerabilitiesInvestigatedRemotelyCount()).isEqualTo(1));
  }

  @SonarLintTest
  void it_should_record_mcpIntegrationEnabled(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().mcpIntegrationEnabled();

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().isMcpIntegrationEnabled()).isTrue());
  }

  @SonarLintTest
  void it_should_record_mcpTransportModeUsed(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().mcpTransportModeUsed(new McpTransportModeUsedParams(McpTransportMode.STDIO));

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getMcpTransportModeUsed()).isEqualTo(McpTransportMode.STDIO));
  }

  @SonarLintTest
  void mcp_transport_mode_should_be_null_if_not_recorded(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getMcpTransportModeUsed()).isNull());
  }

  @SonarLintTest
  void it_should_record_toolCalled(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().toolCalled(new ToolCalledParams("tool_name", true));
    backend.getTelemetryService().toolCalled(new ToolCalledParams("tool_name", true));
    backend.getTelemetryService().toolCalled(new ToolCalledParams("tool_name", false));

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getCalledToolsByName())
      .extractingFromEntries(Map.Entry::getKey, e -> tuple(e.getValue().getSuccess(), e.getValue().getError()))
      .containsOnly(tuple("tool_name", tuple(2, 1))));
  }

  @SonarLintTest
  void it_should_record_taintVulnerabilitiesInvestigatedLocally(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().taintVulnerabilitiesInvestigatedLocally();

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().taintVulnerabilitiesInvestigatedLocallyCount()).isEqualTo(1));
  }

  @SonarLintTest
  void it_should_record_analysisDoneOnSingleLanguage(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().analysisDoneOnSingleLanguage(new AnalysisDoneOnSingleLanguageParams(Language.JAVA, 1000));

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().analyzers())
      .extractingFromEntries(Map.Entry::getKey, e -> tuple(e.getValue().analysisCount(), e.getValue().frequencies()))
      .containsOnly(tuple("java", tuple(1, Map.of(
        "0-300", 0,
        "300-500", 0,
        "500-1000", 0,
        "1000-2000", 1,
        "2000-4000", 0,
        "4000+", 0)))));
  }

  @SonarLintTest
  void it_should_record_analysisDoneOnMultipleFiles(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().analysisDoneOnMultipleFiles();

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().numUseDays()).isEqualTo(1));
  }

  @SonarLintTest
  void it_should_record_addedManualBindings(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().addedManualBindings();

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getManualAddedBindingsCount()).isEqualTo(1));
  }

  @SonarLintTest
  void it_should_record_addedImportedBindings(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().addedImportedBindings();

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getImportedAddedBindingsCount()).isEqualTo(1));
  }

  @SonarLintTest
  void it_should_record_addedAutomaticBindings(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().addedAutomaticBindings();

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getAutoAddedBindingsCount()).isEqualTo(1));
  }

  @SonarLintTest
  void it_should_accumulate_investigated_findings_count(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().issueInvestigatedLocally();
    backend.getTelemetryService().taintInvestigatedLocally();
    backend.getTelemetryService().taintInvestigatedRemotely();
    backend.getTelemetryService().hotspotInvestigatedLocally();
    backend.getTelemetryService().hotspotInvestigatedRemotely();

    backend.getTelemetryService().issueInvestigatedLocally();
    backend.getTelemetryService().taintInvestigatedRemotely();
    backend.getTelemetryService().hotspotInvestigatedLocally();
    backend.getTelemetryService().hotspotInvestigatedRemotely();

    backend.getTelemetryService().issueInvestigatedLocally();
    backend.getTelemetryService().hotspotInvestigatedLocally();
    backend.getTelemetryService().hotspotInvestigatedRemotely();

    backend.getTelemetryService().issueInvestigatedLocally();
    backend.getTelemetryService().hotspotInvestigatedRemotely();

    backend.getTelemetryService().issueInvestigatedLocally();

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent())
      .extracting(
        TelemetryLocalStorage::getIssueInvestigatedLocallyCount,
        TelemetryLocalStorage::getHotspotInvestigatedRemotelyCount,
        TelemetryLocalStorage::getHotspotInvestigatedLocallyCount,
        TelemetryLocalStorage::getTaintInvestigatedRemotelyCount,
        TelemetryLocalStorage::getTaintInvestigatedLocallyCount
      )
      .containsExactly(5, 4, 3, 2, 1));
  }

  @SonarLintTest
  void it_should_add_issue_uuid_when_ai_fixable(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.foo</groupId>
        <artifactId>bar</artifactId>
        <version>${pom.version}</version>
      </project>""");
    var fileUri = filePath.toUri();
    var fakeClient = harness.newFakeClient()
      .withInitialFs("configScope", baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), "configScope", false, null, filePath, null, null, true)))
      .build();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("organizationKey", organization -> organization
        .withProject("projectKey"))
      .start();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection("connectionId", "organizationKey", true, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main").withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MAJOR")))
        .withAiCodeFixSettings(aiCodeFix -> aiCodeFix
          .withSupportedRules(Set.of("xml:S3421"))
          .organizationEligible(true)
          .enabledForProjects("projectKey")))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .start(fakeClient);

    analyzeFileAndGetIssue(fileUri, fakeClient, backend, "configScope");

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent().getCountIssuesWithPossibleAiFixFromIde()).isEqualTo(1));
  }

  @SonarLintTest
  void it_should_apply_telemetry_migration(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var backend = harness.newBackend()
      .withTelemetryMigration(new TelemetryMigrationDto(OffsetDateTime.now(), 42, false))
      .withTelemetryEnabled()
      .start();

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();
  }

  @SonarLintTest
  void it_should_record_findingsFiltered(SonarLintTestHarness harness) {
    var backend = setupClientAndBackend(harness);

    backend.getTelemetryService().findingsFiltered(new FindingsFilteredParams("severity"));
    backend.getTelemetryService().findingsFiltered(new FindingsFilteredParams("severity"));
    backend.getTelemetryService().findingsFiltered(new FindingsFilteredParams("location"));
    backend.getTelemetryService().findingsFiltered(new FindingsFilteredParams("fix_availability"));
    backend.getTelemetryService().findingsFiltered(new FindingsFilteredParams("fix_availability"));
    backend.getTelemetryService().findingsFiltered(new FindingsFilteredParams("fix_availability"));

    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString()
      .contains("\"findingsFilteredCountersByType\":{\"severity\":{\"findingsFilteredCount\":2},\"location\":{\"findingsFilteredCount\":1},\"fix_availability\":{\"findingsFilteredCount\":3}}"));
  }

  @SonarLintTest
  void it_should_count_new_and_fixed_issues(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "sample.py", """
      def empty_function1():
        pass

      def empty_function2():
        pass
      """);

    var fileUri = filePath.toUri();
    var configScope = "configScope";
    var fakeClient = harness.newFakeClient()
      .withInitialFs(configScope, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), configScope, false, null, filePath, null, Language.PYTHON, true)))
      .build();
    var backend = harness.newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.PYTHON)
      .withUnboundConfigScope(configScope)
      .withTelemetryEnabled()
      .start(fakeClient);

    var issuesBefore = analyzeFileAndGetIssues(fileUri, fakeClient, backend, configScope);
    assertThat(issuesBefore).hasSize(2);

    var newContent = """
      def empty_function2():
        pass
      """;
    var updatedFile = new ClientFileDto(fileUri, baseDir.relativize(filePath), configScope, false, null, filePath, newContent, Language.PYTHON, true);
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(), List.of(updatedFile), List.of()));

    var issuesAfter = analyzeFileAndGetIssues(fileUri, fakeClient, backend, configScope);
    assertThat(issuesAfter).hasSize(1);

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent())
      .extracting(TelemetryLocalStorage::getNewIssuesFoundCount, TelemetryLocalStorage::getIssuesFixedCount)
      .containsExactly(2L, 1L));
  }

  private SonarLintTestRpcServer setupClientAndBackend(SonarLintTestHarness harness) {
    return harness.newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .start();
  }

  private static Path createFile(Path folderPath, String fileName, String content) {
    var filePath = folderPath.resolve(fileName);
    try {
      Files.writeString(filePath, content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return filePath;
  }

}
