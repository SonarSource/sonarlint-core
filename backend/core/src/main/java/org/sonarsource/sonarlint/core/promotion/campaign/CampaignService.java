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
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.EnumUtils;
import org.sonarsource.sonarlint.core.commons.storage.local.FileStorageManager;
import org.sonarsource.sonarlint.core.promotion.campaign.storage.CampaignsLocalStorage;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageRequestParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageRequestResponse;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.sonarsource.sonarlint.core.promotion.campaign.FeedbackNotificationActionItem.*;
import static org.sonarsource.sonarlint.core.promotion.campaign.storage.CampaignsLocalStorage.Campaign;

public class CampaignService {

  private static final Set<FeedbackNotificationActionItem> RESPONSES_TO_OPEN_URL = EnumSet.of(LOVE_IT, SHARE_FEEDBACK);

  private final String productKey;
  private final SonarLintRpcClient client;
  private final FileStorageManager<CampaignsLocalStorage> fileStorageManager;

  public CampaignService(@Qualifier("campaignsPath") Path campaignsPath, SonarLintRpcClient client, InitializeParams initializeParams) {
    this.productKey = initializeParams.getTelemetryConstantAttributes().getProductKey();
    this.client = client;
    this.fileStorageManager = new FileStorageManager<>(campaignsPath, CampaignsLocalStorage::new, CampaignsLocalStorage.class);
  }

  @PostConstruct
  public void initCampaigns() {
    // Scheduling and conditional logic in scope of https://sonarsource.atlassian.net/browse/SLCORE-1897
    // todo make the schedule configurable for test purposes.
    fileStorageManager.tryUpdateAtomically(storage ->
      storage.campaigns()
        .put(CampaignConstants.FEEDBACK_2025_12_CAMPAIGN,
          new Campaign(CampaignConstants.FEEDBACK_2025_12_CAMPAIGN, LocalDate.now(), "IGNORE")));
    var userChoice = client.showMessageRequest(new ShowMessageRequestParams(
      MessageType.INFO,
      "Enjoying SonarQube for IDE? We'd love to hear what you think.",
      Stream.of(values())
        .map(FeedbackNotificationActionItem::toMessageActionItem)
        .toList()
    ));
    userChoice.thenAccept(this::handleResponse);
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
