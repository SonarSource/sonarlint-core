/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.promotion.campaign;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.sonarsource.sonarlint.core.commons.storage.local.FileStorageManager;
import org.sonarsource.sonarlint.core.commons.util.FailSafeExecutors;
import org.sonarsource.sonarlint.core.promotion.campaign.storage.CampaignsLocalStorage;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageActionItem;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageRequestParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageRequestResponse;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;
import org.springframework.beans.factory.annotation.Qualifier;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.sonarsource.sonarlint.core.promotion.campaign.FeedbackNotificationActionItem.LOVE_IT;
import static org.sonarsource.sonarlint.core.promotion.campaign.FeedbackNotificationActionItem.MAYBE_LATER;
import static org.sonarsource.sonarlint.core.promotion.campaign.FeedbackNotificationActionItem.SHARE_FEEDBACK;
import static org.sonarsource.sonarlint.core.promotion.campaign.FeedbackNotificationActionItem.values;
import static org.sonarsource.sonarlint.core.promotion.campaign.storage.CampaignsLocalStorage.Campaign;

public class CampaignService {

  private static final Set<FeedbackNotificationActionItem> RESPONSES_TO_OPEN_URL = EnumSet.of(LOVE_IT, SHARE_FEEDBACK);
  private static final Map<String, Period> POSTPONE_PERIODS = Map.of(
    MAYBE_LATER.name(), Period.ofWeeks(1),
    "IGNORE", Period.ofMonths(6)
  );
  private static final String SIX_MINUTES_OF_SECONDS = "360";
  private static final int TWO_WEEKS = 14;

  private final String productKey;
  private final SonarLintRpcClient client;
  private final TelemetryService telemetryService;
  private final FileStorageManager<CampaignsLocalStorage> fileStorageManager;
  private final ScheduledExecutorService scheduledExecutor;

  public CampaignService(@Qualifier("campaignsPath") Path campaignsPath, SonarLintRpcClient client, InitializeParams initializeParams, TelemetryService telemetryService) {
    this.productKey = initializeParams.getTelemetryConstantAttributes().getProductKey();
    this.client = client;
    this.telemetryService = telemetryService;
    this.fileStorageManager = new FileStorageManager<>(campaignsPath, CampaignsLocalStorage::new, CampaignsLocalStorage.class);
    this.scheduledExecutor = FailSafeExecutors.newSingleThreadScheduledExecutor("SonarLint Telemetry");
  }

  @PostConstruct
  public void checkCampaigns() {
    if (shouldShowFeedbackNotification()) {
      var initialDelayProperty = System.getProperty("sonarlint.internal.promotion.initialDelay", SIX_MINUTES_OF_SECONDS);
      var initialDelay = NumberUtils.toInt(initialDelayProperty, 360);
      scheduledExecutor.schedule(this::showMessage, initialDelay, SECONDS);
    }
  }

  private boolean shouldShowFeedbackNotification() {
    var campaigns = fileStorageManager.getStorage().campaigns();
    var feedbackCampaign = campaigns.get(CampaignConstants.FEEDBACK_2025_12_CAMPAIGN);
    if (feedbackCampaign != null) {
      var lastResponse = feedbackCampaign.lastUserResponse();
      return isPostponeResponse(lastResponse)
        && postponeTimePassed(lastResponse, feedbackCampaign);
    } else {
      return isInstalledLongEnough();
    }
  }

  private static boolean isPostponeResponse(String lastResponse) {
    return POSTPONE_PERIODS.containsKey(lastResponse);
  }

  private boolean isInstalledLongEnough() {
    return OffsetDateTime.now().minusDays(TWO_WEEKS).isAfter(telemetryService.installTime());
  }

  private static boolean postponeTimePassed(String lastResponse, Campaign feedbackCampaign) {
    var postpone = POSTPONE_PERIODS.get(lastResponse);
    var lastShown = feedbackCampaign.lastNotificationShownOn();
    return lastShown.plus(postpone).isBefore(LocalDate.now());
  }

  private void showMessage() {
    fileStorageManager.tryUpdateAtomically(storage ->
      storage.campaigns()
        .put(CampaignConstants.FEEDBACK_2025_12_CAMPAIGN,
          new Campaign(CampaignConstants.FEEDBACK_2025_12_CAMPAIGN, LocalDate.now(), "IGNORE")));
    var userChoice = client.showMessageRequest(new ShowMessageRequestParams(
      MessageType.INFO,
      "Enjoying SonarQube for IDE? We'd love to hear what you think.",
      getActions()
    ));
    userChoice.thenAccept(this::handleResponse);
  }

  private static List<MessageActionItem> getActions() {
    return Stream.of(values())
      .map(FeedbackNotificationActionItem::toMessageActionItem)
      .toList();
  }

  private void handleResponse(ShowMessageRequestResponse response) {
    Optional.ofNullable(response)
      .map(ShowMessageRequestResponse::getSelectedKey)
      .ifPresent(this::handleResponse);
  }

  private void handleResponse(String responseOption) {
    fileStorageManager.tryUpdateAtomically(storage -> storage.campaigns().put(
      CampaignConstants.FEEDBACK_2025_12_CAMPAIGN,
      new Campaign(CampaignConstants.FEEDBACK_2025_12_CAMPAIGN, LocalDate.now(), responseOption)));

    var response = EnumUtils.getEnum(FeedbackNotificationActionItem.class, responseOption);
    if (RESPONSES_TO_OPEN_URL.contains(response)) {
      var url = CampaignConstants.urlToOpen(response, productKey);
      if (url != null) {
        client.openUrlInBrowser(new OpenUrlInBrowserParams(url));
      } else {
        client.showMessage(new ShowMessageParams(MessageType.ERROR,
          "Wasn't able to find a marketplace link for " + productKey + ". Please report it here: https://community.sonarsource.com/"));
      }
    }
  }
}
