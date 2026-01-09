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
package mediumtest.promotion;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonarsource.sonarlint.core.commons.storage.local.FileStorageManager;
import org.sonarsource.sonarlint.core.promotion.campaign.CampaignConstants;
import org.sonarsource.sonarlint.core.promotion.campaign.storage.CampaignsLocalStorage;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageActionItem;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageRequestResponse;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorage;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorageManager;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SystemStubsExtension.class)
class CampaignMediumTests {

  private static final int MORE_THAN_TWO_WEEKS_AGO = 16;
  private static final String DEFAULT_KEY = "mediumTests";

  @SystemStub
  SystemProperties propertiesStubs;

  private Path userHome;

  @BeforeEach
  void setUp(@TempDir Path temp) {
    propertiesStubs.set("sonarlint.internal.promotion.initialDelay", "0");
    userHome = temp.resolve("sonarHome");
  }

  @SonarLintTest
  void it_should_initialize_campaigns_file(SonarLintTestHarness harness) {
    saveTelemetryInstallTime(DEFAULT_KEY, MORE_THAN_TWO_WEEKS_AGO);
    var client = harness.newFakeClient().build();

    baseBackend(harness)
      .start(client);

    var campaignsFile = getCampaignsPath(DEFAULT_KEY);
    await().until(() -> Files.exists(campaignsFile));
    assertThat(getCampaigns(campaignsFile))
      .hasSize(1)
      .contains(Map.entry(
        "feedback_2026_01",
        new CampaignsLocalStorage.Campaign("feedback_2026_01", LocalDate.now(), "IGNORE")));
  }

  @SonarLintTest
  void it_should_only_update_campaign_file_on_returning_null(SonarLintTestHarness harness) {
    saveTelemetryInstallTime(DEFAULT_KEY, MORE_THAN_TWO_WEEKS_AGO);
    var client = harness.newFakeClient().build();

    baseBackend(harness)
      .start(client);

    var campaignsFile = getCampaignsPath(DEFAULT_KEY);
    await().until(() -> Files.exists(campaignsFile));
    assertThat(getCampaigns(campaignsFile))
      .hasSize(1)
      .contains(Map.entry(
        "feedback_2026_01",
        new CampaignsLocalStorage.Campaign("feedback_2026_01", LocalDate.now(), "IGNORE")));
    verify(client, never()).openUrlInBrowser(any());
  }

  @SonarLintTest
  void it_should_only_update_campaign_file_on_ignore(SonarLintTestHarness harness) {
    saveTelemetryInstallTime(DEFAULT_KEY, MORE_THAN_TWO_WEEKS_AGO);
    var client = harness.newFakeClient().build();
    when(client.showMessageRequest(any(), any(), any()))
      .thenAnswer(new Answer<>() {
        @Override
        public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
          wait();
          return null;
        }
      });

    baseBackend(harness)
      .start(client);

    var campaignsFile = getCampaignsPath(DEFAULT_KEY);
    await().until(() -> Files.exists(campaignsFile));
    assertThat(getCampaigns(campaignsFile))
      .hasSize(1)
      .contains(Map.entry(
        "feedback_2026_01",
        new CampaignsLocalStorage.Campaign("feedback_2026_01", LocalDate.now(), "IGNORE")));
    verify(client, never()).openUrlInBrowser(any());
  }

  @SonarLintTest
  void it_should_update_campaigns_file_and_open_idea_url_on_love_it(SonarLintTestHarness harness) throws MalformedURLException {
    verifyOpenUrlOnResponse(harness,
      "LOVE_IT", "idea", "https://plugins.jetbrains.com/plugin/7973-sonarqube-for-ide/reviews");
  }

  @SonarLintTest
  void it_should_update_campaigns_file_and_open_vs_url_on_love_it(SonarLintTestHarness harness) throws MalformedURLException {
    verifyOpenUrlOnResponse(harness,
      "LOVE_IT", "visualstudio", "https://marketplace.visualstudio.com/items?itemName=SonarSource.SonarLintforVisualStudio2022&ssr=false#review-details");
  }

  @SonarLintTest
  void it_should_update_campaigns_file_and_open_vscode_url_on_love_it(SonarLintTestHarness harness) throws MalformedURLException {
    verifyOpenUrlOnResponse(harness,
      "LOVE_IT", "vscode", "https://marketplace.visualstudio.com/items?itemName=SonarSource.sonarlint-vscode&ssr=false#review-details");
  }

  @SonarLintTest
  void it_should_update_campaigns_file_and_open_vsx_url_on_love_it(SonarLintTestHarness harness) throws MalformedURLException {
    verifyOpenUrlOnResponse(harness,
      "LOVE_IT", "windsurf", "https://open-vsx.org/extension/SonarSource/sonarlint-vscode/reviews");
  }

  @SonarLintTest
  void it_should_update_campaigns_file_and_open_idea_google_form_url_on_share_feedback(SonarLintTestHarness harness) throws MalformedURLException {
    verifyOpenUrlOnResponse(harness,
      "SHARE_FEEDBACK", "idea", "https://forms.gle/kDyQ7sDyBfpPEBsy6");
  }

  @SonarLintTest
  void it_should_update_campaigns_file_and_open_visualstudio_google_form_url_on_share_feedback(SonarLintTestHarness harness) throws MalformedURLException {
    verifyOpenUrlOnResponse(harness,
      "SHARE_FEEDBACK", "visualstudio", "https://forms.gle/LjKGKWECDdJw1PmU7");
  }

  @SonarLintTest
  void it_should_update_campaigns_file_and_open_vscode_google_form_url_on_share_feedback(SonarLintTestHarness harness) throws MalformedURLException {
    verifyOpenUrlOnResponse(harness,
      "SHARE_FEEDBACK", "vscode", "https://forms.gle/TncKAVK4EWM7z4RV6");
  }

  @SonarLintTest
  void it_should_update_campaigns_file_and_open_vscode_google_form_url_on_share_feedback_for_forks(SonarLintTestHarness harness) throws MalformedURLException {
    verifyOpenUrlOnResponse(harness,
      "SHARE_FEEDBACK", "windsurf", "https://forms.gle/TncKAVK4EWM7z4RV6");
  }

  @SonarLintTest
  void it_should_only_update_campaign_file_on_maybe_later(SonarLintTestHarness harness) {
    saveTelemetryInstallTime(DEFAULT_KEY, MORE_THAN_TWO_WEEKS_AGO);
    var client = harness.newFakeClient().build();
    when(client.showMessageRequest(any(), any(), any()))
      .thenReturn(new ShowMessageRequestResponse("MAYBE_LATER"));

    baseBackend(harness)
      .start(client);

    var campaignsFile = getCampaignsPath(DEFAULT_KEY);
    await().until(() -> Files.exists(campaignsFile));
    assertThat(getCampaigns(campaignsFile))
      .hasSize(1)
      .contains(Map.entry(
        "feedback_2026_01",
        new CampaignsLocalStorage.Campaign("feedback_2026_01", LocalDate.now(), "MAYBE_LATER")));
    verify(client, never()).openUrlInBrowser(any());
  }

  @SonarLintTest
  void it_should_redirect_to_community_on_invalid_productKey(SonarLintTestHarness harness) {
    saveTelemetryInstallTime(DEFAULT_KEY, MORE_THAN_TWO_WEEKS_AGO);
    var client = harness.newFakeClient().build();
    when(client.showMessageRequest(any(), eq("Enjoying SonarQube for IDE? We'd love to hear what you think."), any()))
      .thenReturn(new ShowMessageRequestResponse("LOVE_IT"));
    when(client.showMessageRequest(any(), eq("Could not find feedback link for mediumTests. Please consider sharing your feedback directly on our community forum"), any()))
      .thenReturn(new ShowMessageRequestResponse("OPEN_COMMUNITY"));

    baseBackend(harness)
      .start(client);

    var campaignsFile = getCampaignsPath(DEFAULT_KEY);
    await().until(() -> Files.exists(campaignsFile));
    assertThat(getCampaigns(campaignsFile))
      .hasSize(1)
      .contains(Map.entry(
        "feedback_2026_01",
        new CampaignsLocalStorage.Campaign("feedback_2026_01", LocalDate.now(), "LOVE_IT")));

    ArgumentCaptor<List<MessageActionItem>> actionsArgumentCaptor = ArgumentCaptor.forClass(List.class);
    var messageTypeCaptor = ArgumentCaptor.forClass(MessageType.class);
    var messageCaptor = ArgumentCaptor.forClass(String.class);
    var openInBrowserCaptor = ArgumentCaptor.forClass(URL.class);

    verify(client, times(2)).showMessageRequest(messageTypeCaptor.capture(),
      messageCaptor.capture(),
      actionsArgumentCaptor.capture());
    // showMessageRequest will be called twice - once for the feedback, and second time for the community fallback
    assertThat(messageTypeCaptor.getAllValues().get(1)).isEqualTo(MessageType.INFO);
    assertThat(messageCaptor.getAllValues().get(1)).isEqualTo("Could not find feedback link for mediumTests. Please consider sharing your feedback directly on our community forum");
    assertThat(actionsArgumentCaptor.getAllValues().get(1)).hasSize(1);
    assertThat(actionsArgumentCaptor.getAllValues().get(1).get(0)).extracting(MessageActionItem::getKey, MessageActionItem::getDisplayText, MessageActionItem::isPrimaryAction)
      .containsExactly("OPEN_COMMUNITY", "Open Community Forum", true);
    verify(client, times(1)).openUrlInBrowser(openInBrowserCaptor.capture());
    assertThat(openInBrowserCaptor.getValue()).hasToString("https://community.sonarsource.com/c/sl/11");
  }

  @SonarLintTest
  void it_should_delay_the_notification_configured_amount_of_time(SonarLintTestHarness harness) {
    saveTelemetryInstallTime(DEFAULT_KEY, MORE_THAN_TWO_WEEKS_AGO);
    propertiesStubs.set("sonarlint.internal.promotion.initialDelay", "2");
    var client = harness.newFakeClient().build();
    when(client.showMessageRequest(any(), any(), any()))
      .thenReturn(new ShowMessageRequestResponse("LOVE_IT"));

    baseBackend(harness)
      .start(client);

    await().atLeast(2, TimeUnit.SECONDS)
      .untilAsserted(() -> verify(client).showMessageRequest(
        eq(MessageType.INFO),
        eq("Enjoying SonarQube for IDE? We'd love to hear what you think."),
        any()));
  }

  @SonarLintTest
  void it_should_not_trigger_notification_for_recent_installation(SonarLintTestHarness harness) {
    saveTelemetryInstallTime(DEFAULT_KEY, 5);
    var client = harness.newFakeClient().build();

    baseBackend(harness)
      .start(client);

    verify(client, never()).showMessageRequest(any(), any(), any());
  }

  @SonarLintTest
  void it_should_trigger_notification_again_after_week_for_maybe_later(SonarLintTestHarness harness) {
    saveFeedbackCampaign(LocalDate.now().minusDays(8), "MAYBE_LATER");
    var client = harness.newFakeClient().build();

    baseBackend(harness)
      .start(client);

    verify(client).showMessageRequest(
      eq(MessageType.INFO),
      eq("Enjoying SonarQube for IDE? We'd love to hear what you think."),
      any());
  }

  @SonarLintTest
  void it_should_not_trigger_notification_again_after_less_then_week_for_maybe_later(SonarLintTestHarness harness) {
    saveFeedbackCampaign(LocalDate.now().minusDays(6), "MAYBE_LATER");
    var client = harness.newFakeClient().build();

    baseBackend(harness)
      .start(client);

    verify(client, never()).showMessageRequest(any(), any(), any());
  }

  @SonarLintTest
  void it_should_trigger_notification_again_after_6_months_for_ignore(SonarLintTestHarness harness) {
    saveFeedbackCampaign(LocalDate.now().minusMonths(6).minusDays(1), "IGNORE");
    var client = harness.newFakeClient().build();

    baseBackend(harness)
      .start(client);

    verify(client).showMessageRequest(
      eq(MessageType.INFO),
      eq("Enjoying SonarQube for IDE? We'd love to hear what you think."),
      any());
  }

  @SonarLintTest
  void it_should_not_trigger_notification_again_after_less_then_6_months_for_ignore(SonarLintTestHarness harness) {
    saveFeedbackCampaign(LocalDate.now().minusMonths(6).plusDays(1), "IGNORE");
    var client = harness.newFakeClient().build();

    baseBackend(harness)
      .start(client);

    verify(client, never()).showMessageRequest(any(), any(), any());
  }

  @SonarLintTest
  void it_should_record_notification_shown_in_telemetry(SonarLintTestHarness harness) {
    // make a little delay as telemetry event listeners take some time to be seen by the context
    propertiesStubs.set("sonarlint.internal.promotion.initialDelay", "1");
    saveTelemetryInstallTime(DEFAULT_KEY, MORE_THAN_TWO_WEEKS_AGO);
    var backend = baseBackend(harness)
      .withTelemetryEnabled()
      .start();

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent())
      .extracting(TelemetryLocalStorage::getCampaignsShown)
      .extracting(campaigns -> campaigns.get(CampaignConstants.FEEDBACK_2026_01_CAMPAIGN))
      .isEqualTo(1));
  }

  @SonarLintTest
  void it_should_record_notification_responded_in_telemetry(SonarLintTestHarness harness) {
    // make a little delay as telemetry event listeners take some time to be seen by the context
    propertiesStubs.set("sonarlint.internal.promotion.initialDelay", "1");
    saveTelemetryInstallTime(DEFAULT_KEY, MORE_THAN_TWO_WEEKS_AGO);
    var client = harness.newFakeClient().build();
    when(client.showMessageRequest(any(), any(), any()))
      .thenReturn(new ShowMessageRequestResponse("LOVE_IT"));

    var backend = baseBackend(harness)
      .withTelemetryEnabled()
      .start(client);

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent())
      .extracting(TelemetryLocalStorage::getCampaignsResolutions)
      .extracting(campaigns -> campaigns.get(CampaignConstants.FEEDBACK_2026_01_CAMPAIGN))
      .isEqualTo("LOVE_IT"));
  }

  @SonarLintTest
  void it_should_not_trigger_notification_with_no_backend_capability(SonarLintTestHarness harness) {
    saveTelemetryInstallTime(DEFAULT_KEY, MORE_THAN_TWO_WEEKS_AGO);
    var client = harness.newFakeClient().build();

    harness.newBackend()
      .withUserHome(userHome)
      .start(client);

    verify(client, never()).showMessageRequest(any(), any(), any());
  }

  private void verifyOpenUrlOnResponse(SonarLintTestHarness harness, String response, String productKey, String expectedUrl) throws MalformedURLException {
    saveTelemetryInstallTime(productKey, MORE_THAN_TWO_WEEKS_AGO);
    var client = harness.newFakeClient()
      .build();
    when(client.showMessageRequest(any(), any(), any()))
      .thenReturn(new ShowMessageRequestResponse(response));

    baseBackend(harness)
      .withProductKey(productKey)
      .start(client);

    var campaignsFile = getCampaignsPath(productKey);
    await().untilAsserted(
      () -> assertThat(getCampaigns(campaignsFile))
        .hasSize(1)
        .contains(Map.entry(
          CampaignConstants.FEEDBACK_2026_01_CAMPAIGN,
          new CampaignsLocalStorage.Campaign(CampaignConstants.FEEDBACK_2026_01_CAMPAIGN, LocalDate.now(), response)))
    );
    verify(client).openUrlInBrowser(new URL(expectedUrl));
  }

  private void saveFeedbackCampaign(LocalDate lastShown, String lastResponse) {
    var campaignsStorage = new FileStorageManager<>(getCampaignsPath(CampaignMediumTests.DEFAULT_KEY), CampaignsLocalStorage::new, CampaignsLocalStorage.class);
    campaignsStorage.tryUpdateAtomically(data -> data.campaigns()
      .put(CampaignConstants.FEEDBACK_2026_01_CAMPAIGN,
        new CampaignsLocalStorage.Campaign(
          CampaignConstants.FEEDBACK_2026_01_CAMPAIGN,
          lastShown,
          lastResponse
        )));
  }

  private static Map<String, CampaignsLocalStorage.Campaign> getCampaigns(Path campaignsFile) {
    var campaignsStorage = new FileStorageManager<>(campaignsFile, CampaignsLocalStorage::new, CampaignsLocalStorage.class);
    return campaignsStorage.getStorage().campaigns();
  }

  private void saveTelemetryInstallTime(String productKey, int daysAgo) {
    var telemetryPath = getTelemetryPath(productKey);
    TelemetryLocalStorageManager telemetryStorageManager = new TelemetryLocalStorageManager(telemetryPath, mock(InitializeParams.class));
    telemetryStorageManager.tryUpdateAtomically(data ->
      data.setInstallTime(OffsetDateTime.now().minusDays(daysAgo)));
  }

  private Path getCampaignsPath(String productKey) {
    return userHome.resolve("campaigns").resolve(productKey).resolve("campaigns");
  }

  private Path getTelemetryPath(String productKey) {
    return userHome.resolve("telemetry").resolve(productKey).resolve("usage");
  }

  private SonarLintBackendFixture.SonarLintBackendBuilder baseBackend(SonarLintTestHarness harness) {
    return harness.newBackend()
      .withUserHome(userHome)
      .withBackendCapability(BackendCapability.PROMOTIONAL_CAMPAIGNS);
  }
}
