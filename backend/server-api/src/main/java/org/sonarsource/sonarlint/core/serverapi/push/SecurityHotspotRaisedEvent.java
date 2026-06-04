/*
 * SonarLint Core - Server API
 * Copyright (C) SonarSource Sàrl
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

  private SecurityHotspotRaisedEvent(Builder builder) {
    this.hotspotKey = builder.hotspotKey;
    this.projectKey = builder.projectKey;
    this.vulnerabilityProbability = builder.vulnerabilityProbability;
    this.status = builder.status;
    this.creationDate = builder.creationDate;
    this.branch = builder.branch;
    this.mainLocation = builder.mainLocation;
    this.ruleKey = builder.ruleKey;
    this.ruleDescriptionContextKey = builder.ruleDescriptionContextKey;
    this.assignee = builder.assignee;
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

  public static class Builder {
    private String hotspotKey;
    private String projectKey;
    private VulnerabilityProbability vulnerabilityProbability;
    private HotspotReviewStatus status;
    private Instant creationDate;
    private String branch;
    private TaintVulnerabilityRaisedEvent.Location mainLocation;
    private String ruleKey;
    @Nullable
    private String ruleDescriptionContextKey;
    @Nullable
    private String assignee;

    public Builder setHotspotKey(String hotspotKey) {
      this.hotspotKey = hotspotKey;
      return this;
    }

    public Builder setProjectKey(String projectKey) {
      this.projectKey = projectKey;
      return this;
    }

    public Builder setVulnerabilityProbability(VulnerabilityProbability vulnerabilityProbability) {
      this.vulnerabilityProbability = vulnerabilityProbability;
      return this;
    }

    public Builder setStatus(HotspotReviewStatus status) {
      this.status = status;
      return this;
    }

    public Builder setCreationDate(Instant creationDate) {
      this.creationDate = creationDate;
      return this;
    }

    public Builder setBranch(String branch) {
      this.branch = branch;
      return this;
    }

    public Builder setMainLocation(TaintVulnerabilityRaisedEvent.Location mainLocation) {
      this.mainLocation = mainLocation;
      return this;
    }

    public Builder setRuleKey(String ruleKey) {
      this.ruleKey = ruleKey;
      return this;
    }

    public Builder setRuleDescriptionContextKey(@Nullable String ruleDescriptionContextKey) {
      this.ruleDescriptionContextKey = ruleDescriptionContextKey;
      return this;
    }

    public Builder setAssignee(@Nullable String assignee) {
      this.assignee = assignee;
      return this;
    }

    public SecurityHotspotRaisedEvent build() {
      return new SecurityHotspotRaisedEvent(this);
    }
  }
}
