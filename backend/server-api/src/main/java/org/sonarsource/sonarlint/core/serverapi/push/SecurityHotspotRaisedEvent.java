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
package org.sonarsource.sonarlint.core.serverapi.push;

import java.nio.file.Path;
import java.time.Instant;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

public class SecurityHotspotRaisedEvent implements ServerHotspotEvent {
  private final String hotspotKey;
  private final String projectKey;
  private final VulnerabilityProbability vulnerabilityProbability;
  private final HotspotReviewStatus status;
  private final Instant creationDate;
  private final String branch;
  private final TaintVulnerabilityRaisedEvent.Location mainLocation;
  private final String ruleKey;
  @Nullable
  private final String ruleDescriptionContextKey;
  @Nullable
  private final String assignee;

  public SecurityHotspotRaisedEvent(String hotspotKey, String projectKey, VulnerabilityProbability vulnerabilityProbability,
    HotspotReviewStatus status, Instant creationDate, String branch, TaintVulnerabilityRaisedEvent.Location mainLocation, String ruleKey,
    @Nullable String ruleDescriptionContextKey, @Nullable String assignee) {
    this.hotspotKey = hotspotKey;
    this.projectKey = projectKey;
    this.vulnerabilityProbability = vulnerabilityProbability;
    this.status = status;
    this.creationDate = creationDate;
    this.branch = branch;
    this.mainLocation = mainLocation;
    this.ruleKey = ruleKey;
    this.ruleDescriptionContextKey = ruleDescriptionContextKey;
    this.assignee = assignee;
  }

  public String getHotspotKey() {
    return hotspotKey;
  }

  @Override
  public String getProjectKey() {
    return projectKey;
  }

  public VulnerabilityProbability getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }

  public HotspotReviewStatus getStatus() {
    return status;
  }

  public Instant getCreationDate() {
    return creationDate;
  }

  public String getBranch() {
    return branch;
  }

  public TaintVulnerabilityRaisedEvent.Location getMainLocation() {
    return mainLocation;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  @Nullable
  public String getRuleDescriptionContextKey() {
    return ruleDescriptionContextKey;
  }

  @Override
  public Path getFilePath() {
    return mainLocation.getFilePath();
  }
}
