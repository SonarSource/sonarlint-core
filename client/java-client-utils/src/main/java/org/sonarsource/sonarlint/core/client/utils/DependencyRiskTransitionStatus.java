/*
 * SonarLint Core - Java Client Utils
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
package org.sonarsource.sonarlint.core.client.utils;

import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;

public enum DependencyRiskTransitionStatus {
  REOPEN("Open", "This finding has not yet been reviewed."),
  CONFIRM("Confirmed", "This finding has been reviewed and the risk is valid."),
  ACCEPT("Accepted", "This finding is valid, but it may not be fixed for a while."),
  SAFE("Safe", "This finding does not pose a risk. No fix is needed."),
  FIXED("Fixed", "This finding has been fixed.");

  private final String title;
  private final String description;

  DependencyRiskTransitionStatus(String title, String description) {
    this.title = title;
    this.description = description;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public static DependencyRiskTransitionStatus fromDto(DependencyRiskDto.Transition status) {
    switch (status) {
      case REOPEN:
        return REOPEN;
      case CONFIRM:
        return CONFIRM;
      case ACCEPT:
        return ACCEPT;
      case SAFE:
        return SAFE;
      case FIXED:
        return FIXED;
      default:
        throw new IllegalArgumentException("Unknown status: " + status);
    }
  }
}
