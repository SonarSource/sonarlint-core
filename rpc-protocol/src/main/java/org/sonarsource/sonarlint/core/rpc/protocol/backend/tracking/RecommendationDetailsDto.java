/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking;

public class RecommendationDetailsDto {
  private final int impactScore;
  private final String impactDescription;
  private final boolean realIssue;
  private final String falsePositiveReason;
  private final boolean includesDev;
  private final boolean specificMethodsAffected;
  private final String specificMethodsDescription;
  private final boolean otherConditions;
  private final String otherConditionsDescription;
  private final boolean workaroundAvailable;
  private final String workaroundDescription;
  private final String visibility;

  private RecommendationDetailsDto(Builder builder) {
    this.impactScore = builder.impactScore;
    this.impactDescription = builder.impactDescription;
    this.realIssue = builder.realIssue;
    this.falsePositiveReason = builder.falsePositiveReason;
    this.includesDev = builder.includesDev;
    this.specificMethodsAffected = builder.specificMethodsAffected;
    this.specificMethodsDescription = builder.specificMethodsDescription;
    this.otherConditions = builder.otherConditions;
    this.otherConditionsDescription = builder.otherConditionsDescription;
    this.workaroundAvailable = builder.workaroundAvailable;
    this.workaroundDescription = builder.workaroundDescription;
    this.visibility = builder.visibility;
  }

  public static Builder builder() {
    return new Builder();
  }

  public int getImpactScore() {
    return impactScore;
  }

  public String getImpactDescription() {
    return impactDescription;
  }

  public boolean isRealIssue() {
    return realIssue;
  }

  public String getFalsePositiveReason() {
    return falsePositiveReason;
  }

  public boolean isIncludesDev() {
    return includesDev;
  }

  public boolean isSpecificMethodsAffected() {
    return specificMethodsAffected;
  }

  public String getSpecificMethodsDescription() {
    return specificMethodsDescription;
  }

  public boolean isOtherConditions() {
    return otherConditions;
  }

  public String getOtherConditionsDescription() {
    return otherConditionsDescription;
  }

  public boolean isWorkaroundAvailable() {
    return workaroundAvailable;
  }

  public String getWorkaroundDescription() {
    return workaroundDescription;
  }

  public String getVisibility() {
    return visibility;
  }

  public static class Builder {
    private int impactScore;
    private String impactDescription;
    private boolean realIssue;
    private String falsePositiveReason;
    private boolean includesDev;
    private boolean specificMethodsAffected;
    private String specificMethodsDescription;
    private boolean otherConditions;
    private String otherConditionsDescription;
    private boolean workaroundAvailable;
    private String workaroundDescription;
    private String visibility;

    public Builder impactScore(int impactScore) {
      this.impactScore = impactScore;
      return this;
    }

    public Builder impactDescription(String impactDescription) {
      this.impactDescription = impactDescription;
      return this;
    }

    public Builder realIssue(boolean realIssue) {
      this.realIssue = realIssue;
      return this;
    }

    public Builder falsePositiveReason(String falsePositiveReason) {
      this.falsePositiveReason = falsePositiveReason;
      return this;
    }

    public Builder includesDev(boolean includesDev) {
      this.includesDev = includesDev;
      return this;
    }

    public Builder specificMethodsAffected(boolean specificMethodsAffected) {
      this.specificMethodsAffected = specificMethodsAffected;
      return this;
    }

    public Builder specificMethodsDescription(String specificMethodsDescription) {
      this.specificMethodsDescription = specificMethodsDescription;
      return this;
    }

    public Builder otherConditions(boolean otherConditions) {
      this.otherConditions = otherConditions;
      return this;
    }

    public Builder otherConditionsDescription(String otherConditionsDescription) {
      this.otherConditionsDescription = otherConditionsDescription;
      return this;
    }

    public Builder workaroundAvailable(boolean workaroundAvailable) {
      this.workaroundAvailable = workaroundAvailable;
      return this;
    }

    public Builder workaroundDescription(String workaroundDescription) {
      this.workaroundDescription = workaroundDescription;
      return this;
    }

    public Builder visibility(String visibility) {
      this.visibility = visibility;
      return this;
    }

    public RecommendationDetailsDto build() {
      return new RecommendationDetailsDto(this);
    }
  }
}
