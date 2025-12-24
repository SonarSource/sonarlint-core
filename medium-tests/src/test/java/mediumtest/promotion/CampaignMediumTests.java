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
import java.util.Map;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonarsource.sonarlint.core.commons.storage.local.FileStorageManager;
import org.sonarsource.sonarlint.core.promotion.campaign.storage.CampaignsLocalStorage;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageRequestResponse;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampaignMediumTests {

  @SonarLintTest
  void it_should_initialize_campaigns_file(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();

    var backend = harness.newBackend()
      .start(client);

    var campaignsFile = getCampaignsFile(backend, "mediumTests");
    await().until(() -> Files.exists(campaignsFile));
    assertThat(getCampaigns(campaignsFile))
      .hasSize(1)
      .contains(Map.entry(
        "feedback_2025_12",
        new CampaignsLocalStorage.Campaign("feedback_2025_12", LocalDate.now(), "IGNORE")));
  }

  @SonarLintTest
  void it_should_only_update_campaign_file_on_returning_null(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();

    var backend = harness.newBackend()
      .start(client);

    var campaignsFile = getCampaignsFile(backend, "mediumTests");
    await().until(() -> Files.exists(campaignsFile));
    assertThat(getCampaigns(campaignsFile))
      .hasSize(1)
      .contains(Map.entry(
        "feedback_2025_12",
        new CampaignsLocalStorage.Campaign("feedback_2025_12", LocalDate.now(), "IGNORE")));
    verify(client, never()).openUrlInBrowser(any());
  }

  @SonarLintTest
  void it_should_only_update_campaign_file_on_ignore(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    when(client.showMessageRequest(any(), any(), any()))
      .thenAnswer(new Answer<>() {
        @Override
        public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
          wait();
          return null;
        }
      });

    var backend = harness.newBackend()
      .start(client);

    var campaignsFile = getCampaignsFile(backend, "mediumTests");
    await().until(() -> Files.exists(campaignsFile));
    assertThat(getCampaigns(campaignsFile))
      .hasSize(1)
      .contains(Map.entry(
        "feedback_2025_12",
        new CampaignsLocalStorage.Campaign("feedback_2025_12", LocalDate.now(), "IGNORE")));
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

  // fixme Google form is a stub for now, we need to make sure we put the right one before going live!
  @SonarLintTest
  void it_should_update_campaigns_file_and_open_idea_google_form_url_on_share_feedback(SonarLintTestHarness harness) throws MalformedURLException {
    verifyOpenUrlOnResponse(harness,
      "SHARE_FEEDBACK", "idea", "https://docs.google.com/forms/d/1ch2YxyF3n62JN3eiWHeMQOH6S7R6LHO6JWnLNdPWRYE/preview");
  }

  @SonarLintTest
  void it_should_update_campaigns_file_and_open_visualstudio_google_form_url_on_share_feedback(SonarLintTestHarness harness) throws MalformedURLException {
    verifyOpenUrlOnResponse(harness,
      "SHARE_FEEDBACK", "visualstudio", "https://docs.google.com/forms/d/1ch2YxyF3n62JN3eiWHeMQOH6S7R6LHO6JWnLNdPWRYE/preview");
  }

  @SonarLintTest
  void it_should_update_campaigns_file_and_open_vscode_google_form_url_on_share_feedback(SonarLintTestHarness harness) throws MalformedURLException {
    verifyOpenUrlOnResponse(harness,
      "SHARE_FEEDBACK", "vscode", "https://docs.google.com/forms/d/1ch2YxyF3n62JN3eiWHeMQOH6S7R6LHO6JWnLNdPWRYE/preview");
  }

  @SonarLintTest
  void it_should_only_update_campaign_file_on_maybe_later(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    when(client.showMessageRequest(any(), any(), any()))
      .thenReturn(new ShowMessageRequestResponse("MAYBE_LATER"));

    var backend = harness.newBackend()
      .start(client);

    var campaignsFile = getCampaignsFile(backend, "mediumTests");
    await().until(() -> Files.exists(campaignsFile));
    assertThat(getCampaigns(campaignsFile))
      .hasSize(1)
      .contains(Map.entry(
        "feedback_2025_12",
        new CampaignsLocalStorage.Campaign("feedback_2025_12", LocalDate.now(), "MAYBE_LATER")));
    verify(client, never()).openUrlInBrowser(any());
  }

  @SonarLintTest
  void it_should_show_message_on_invalid_projectKey(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    when(client.showMessageRequest(any(), any(), any()))
      .thenReturn(new ShowMessageRequestResponse("LOVE_IT"));

    var backend = harness.newBackend()
      .start(client);

    var campaignsFile = getCampaignsFile(backend, "mediumTests");
    await().until(() -> Files.exists(campaignsFile));
    assertThat(getCampaigns(campaignsFile))
      .hasSize(1)
      .contains(Map.entry(
        "feedback_2025_12",
        new CampaignsLocalStorage.Campaign("feedback_2025_12", LocalDate.now(), "LOVE_IT")));
    verify(client, never()).openUrlInBrowser(any());
    verify(client).showMessage(MessageType.ERROR,
      "Wasn't able to find a marketplace link for mediumTests. Please report it here: https://community.sonarsource.com/");
  }

  private static void verifyOpenUrlOnResponse(SonarLintTestHarness harness, String response, String productKey, String expectedUrl) throws MalformedURLException {
    var client = harness.newFakeClient()
      .build();
    when(client.showMessageRequest(any(), any(), any()))
      .thenReturn(new ShowMessageRequestResponse(response));

    var backend = harness.newBackend()
      .withProductKey(productKey)
      .start(client);

    var campaignsFile = getCampaignsFile(backend, productKey);
    await().untilAsserted(
      () -> assertThat(getCampaigns(campaignsFile))
        .hasSize(1)
        .contains(Map.entry(
          "feedback_2025_12",
          new CampaignsLocalStorage.Campaign("feedback_2025_12", LocalDate.now(), response)))
    );
    verify(client).openUrlInBrowser(new URL(expectedUrl));
  }

  private static Map<String, CampaignsLocalStorage.Campaign> getCampaigns(Path campaignsFile) {
    var campaignsStorage = new FileStorageManager<>(campaignsFile, CampaignsLocalStorage::new, CampaignsLocalStorage.class);
    return campaignsStorage.getStorage().campaigns();
  }

  private static Path getCampaignsFile(SonarLintTestRpcServer backend, String productKey) {
    return backend.getUserHome().resolve("campaigns/" + productKey + "/campaigns");
  }
}
