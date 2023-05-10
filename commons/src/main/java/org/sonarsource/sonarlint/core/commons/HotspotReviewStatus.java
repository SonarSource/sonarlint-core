/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.sonarsource.sonarlint.core.commons.ConnectionKind.SONARCLOUD;
import static org.sonarsource.sonarlint.core.commons.ConnectionKind.SONARQUBE;

public enum HotspotReviewStatus {
  TO_REVIEW(Set.of(SONARQUBE, SONARCLOUD)),
  SAFE(Set.of(SONARQUBE, SONARCLOUD)),
  FIXED(Set.of(SONARQUBE, SONARCLOUD)),
  ACKNOWLEDGED(Set.of(SONARQUBE));
  private final Set<ConnectionKind> allowedConnectionKinds;

  HotspotReviewStatus(Set<ConnectionKind> allowedConnectionKinds) {
    this.allowedConnectionKinds = allowedConnectionKinds;
  }

  public boolean isReviewed() {
    return !equals(TO_REVIEW);
  }

  public boolean isResolved() {
    // ACKNOWLEDGED is considered as non-resolved because the hotspot is confirmed
    return equals(SAFE) || equals(FIXED);
  }

  private boolean isAllowedOn(ConnectionKind kind) {
    return allowedConnectionKinds.contains(kind);
  }

  public static List<HotspotReviewStatus> allowedStatusesOn(ConnectionKind kind) {
    return Arrays.stream(HotspotReviewStatus.values()).filter(status -> status.isAllowedOn(kind))
      .collect(Collectors.toList());
  }
}
