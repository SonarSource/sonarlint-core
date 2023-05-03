/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.backend.hotspot;

public enum HotspotStatus {
  TO_REVIEW("To Review", "This Security Hotspot needs to be reviewed to assess whether the code poses a risk."),
  SAFE("Safe", "The code has been reviewed and does not pose a risk. It does not need to be modified."),
  FIXED("Fixed", "The code has been modified to follow recommended secure coding practices."),
  ACKNOWLEDGED("Acknowledged", "The code has been reviewed and does pose a risk. A fix is required.");

  private final String title;
  private final String description;

  HotspotStatus(String title, String description) {
    this.title = title;
    this.description = description;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }
}
