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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import javax.annotation.PostConstruct;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.promotion.campaign.storage.CampaignsLocalStorage;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.telemetry.LocalDateAdapter;
import org.springframework.beans.factory.annotation.Qualifier;

public class CampaignService {

  private final SonarLintLogger logger = SonarLintLogger.get();

  private final Path campaignsPath;
  private final SonarLintRpcClient client;
  private final Gson gson;
  private CampaignsLocalStorage campaigns;

  public CampaignService(@Qualifier("campaignsPath") Path campaignsPath, SonarLintRpcClient client) {
    this.campaignsPath = campaignsPath;
    this.client = client;
    this.gson = new GsonBuilder()
      .registerTypeAdapter(LocalDate.class, new LocalDateAdapter().nullSafe())
      .create();
  }

  @PostConstruct
  public void initCampaigns() {
    this.campaigns = tryReadCampaigns();

  }

  private CampaignsLocalStorage tryReadCampaigns() {
    var readCampaigns = new CampaignsLocalStorage();
    if (Files.exists(campaignsPath)) {
      try {
        var json = Files.readString(campaignsPath);
        readCampaigns = gson.fromJson(json, CampaignsLocalStorage.class);
      } catch (IOException | JsonParseException e) {
        logger.warn("Unable to read campaigns from campaigns file", e);
      }
    }
    return readCampaigns;
  }
}
