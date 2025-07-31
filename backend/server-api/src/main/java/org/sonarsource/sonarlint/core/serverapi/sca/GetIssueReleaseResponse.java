/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.sca;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public record GetIssueReleaseResponse(UUID key, Severity severity, SoftwareQuality quality, Release release, Type type, Vulnerability vulnerability) {
  public enum Severity {
    INFO, LOW, MEDIUM, HIGH, BLOCKER
  }

  public enum SoftwareQuality {
    MAINTAINABILITY,
    RELIABILITY,
    SECURITY
  }

  public record Release(String packageName, String version) {
  }

  public enum Type {
    VULNERABILITY, PROHIBITED_LICENSE
  }

  public record Vulnerability(String vulnerabilityId, String description, List<AffectedPackage> affectedPackages) {
  }

  public record AffectedPackage(
    String purl,
    String recommendation,
    @Nullable RecommendationDetails recommendationDetails) {
  }

  public record RecommendationDetails(
    int impactScore,
    String impactDescription,
    boolean realIssue,
    String falsePositiveReason,
    boolean includesDev,
    boolean specificMethodsAffected,
    String specificMethodsDescription,
    boolean otherConditions,
    String otherConditionsDescription,
    boolean workaroundAvailable,
    String workaroundDescription,
    String visibility) {
  }
}
