/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons;

import javax.annotation.CheckForNull;

/**
 * Represents Issue resolution status. Not the status of the issue itself.
 */
public enum IssueStatus {
  ACCEPT,
  WONT_FIX,
  FALSE_POSITIVE;

  @CheckForNull
  public static IssueStatus parse(String stringRepresentation) {
    return switch (stringRepresentation) {
      // ACCEPTED transition leads to WONTFIX status on server so we are not making difference between them.
      case "WONTFIX", "ACCEPT" -> IssueStatus.ACCEPT;
      case "FALSE-POSITIVE" -> IssueStatus.FALSE_POSITIVE;
      default -> null;
    };
  }
}
