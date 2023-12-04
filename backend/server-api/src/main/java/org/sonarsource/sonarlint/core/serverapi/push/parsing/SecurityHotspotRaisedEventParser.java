/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2023 SonarSource SA
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
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotRaisedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.common.LocationPayload;

import static org.sonarsource.sonarlint.core.serverapi.push.parsing.TaintVulnerabilityRaisedEventParser.adapt;
import static org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils.isBlank;

public class SecurityHotspotRaisedEventParser implements EventParser<SecurityHotspotRaisedEvent> {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Gson gson = new Gson();

  @Override
  public Optional<SecurityHotspotRaisedEvent> parse(String jsonData) {
    var payload = gson.fromJson(jsonData, HotspotRaisedEventPayload.class);
    if (payload.isInvalid()) {
      LOG.error("Invalid payload for 'SecurityHotspotRaised' event: {}", jsonData);
      return Optional.empty();
    }
    return Optional.of(new SecurityHotspotRaisedEvent(
      payload.key,
      payload.projectKey,
      VulnerabilityProbability.valueOf(payload.vulnerabilityProbability),
      HotspotReviewStatus.valueOf(payload.status),
      Instant.ofEpochMilli(payload.creationDate),
      payload.branch,
      adapt(payload.mainLocation),
      payload.ruleKey,
      payload.ruleDescriptionContextKey, payload.assignee));
  }

  private static class HotspotRaisedEventPayload {
    private String key;
    private String projectKey;
    private String status;
    private String branch;
    private String vulnerabilityProbability;
    private long creationDate;
    private String ruleKey;
    private LocationPayload mainLocation;
    @Nullable
    private String ruleDescriptionContextKey;
    @Nullable
    private String assignee;

    private boolean isInvalid() {
      return isBlank(key) || isBlank(projectKey) || isBlank(vulnerabilityProbability) || creationDate == 0L || isBlank(branch) || isBlank(ruleKey)
        || mainLocation.isInvalid();
    }
  }

}
