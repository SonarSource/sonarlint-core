/*
 * SonarLint Core - Java Client Utils
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
package org.sonarsource.sonarlint.core.client.utils;

import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;

public enum IssueResolutionStatus {
  ACCEPT("Accepted", "The issue is valid but will not be fixed now. It represents accepted technical debt."),
  WONT_FIX("Won't Fix", "The issue is valid but does not need fixing. It represents accepted technical debt."),
  FALSE_POSITIVE("False Positive", "The issue is raised unexpectedly on code that should not trigger an issue.");

  private final String title;
  private final String description;

  IssueResolutionStatus(String title, String description) {
    this.title = title;
    this.description = description;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public static IssueResolutionStatus fromDto(ResolutionStatus status) {
    switch (status) {
      case ACCEPT:
        return ACCEPT;
      case WONT_FIX:
        return WONT_FIX;
      case FALSE_POSITIVE:
        return FALSE_POSITIVE;
      default:
        throw new IllegalArgumentException("Unknown status: " + status);
    }
  }
}
