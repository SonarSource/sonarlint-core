/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.push.parsing;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.Optional;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotChangedEvent;

import static org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils.isBlank;

public class SecurityHotspotChangedEventParser implements EventParser<SecurityHotspotChangedEvent> {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Gson gson = new Gson();

  @Override
  public Optional<SecurityHotspotChangedEvent> parse(String jsonData) {
    var payload = gson.fromJson(jsonData, HotspotChangedEventPayload.class);
    if (payload.isInvalid()) {
      LOG.error("Invalid payload for 'SecurityHotspotChanged' event: {}", jsonData);
      return Optional.empty();
    }
    return Optional.of(new SecurityHotspotChangedEvent(
      payload.key,
      payload.projectKey,
      Instant.ofEpochMilli(payload.updateDate),
      HotspotReviewStatus.fromStatusAndResolution(payload.status, payload.resolution),
      payload.assignee,
      payload.filePath));
  }

  private static class HotspotChangedEventPayload {
    private String key;
    private String projectKey;
    private String status;
    private String resolution;
    private long updateDate;
    private String assignee;
    private String filePath;

    public String getKey() {
      return key;
    }

    public String getProjectKey() {
      return projectKey;
    }

    public String getStatus() {
      return status;
    }

    public String getResolution() {
      return resolution;
    }

    public long getUpdateDate() {
      return updateDate;
    }

    public String getAssignee() {
      return assignee;
    }

    private boolean isInvalid() {
      return isBlank(key) || isBlank(projectKey) || updateDate == 0L || isBlank(filePath);
    }
  }
}
