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
package org.sonarsource.sonarlint.core.serverapi.developers;

import java.time.ZonedDateTime;

public class Event {
  private final String category;
  private final String message;
  private final String link;
  private final String projectKey;
  private final ZonedDateTime time;

  public Event(String category, String message, String link, String projectKey, ZonedDateTime time) {
    this.category = category;
    this.message = message;
    this.link = link;
    this.projectKey = projectKey;
    this.time = time;
  }

  public String getCategory() {
    return category;
  }

  public String getMessage() {
    return message;
  }

  public String getLink() {
    return link;
  }

  public String getProjectKey() {
    return projectKey;
  }

  public ZonedDateTime getTime() {
    return time;
  }
}
