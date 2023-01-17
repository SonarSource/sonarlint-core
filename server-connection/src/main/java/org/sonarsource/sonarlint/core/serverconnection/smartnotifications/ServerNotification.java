/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.smartnotifications;

import java.time.ZonedDateTime;

public class ServerNotification {
  private final String category;
  private final String message;
  private final String link;
  private final String projectKey;
  private final ZonedDateTime time;

  public ServerNotification(String category, String message, String link, String projectKey, ZonedDateTime time) {
    this.category = category;
    this.message = message;
    this.link = link;
    this.projectKey = projectKey;
    this.time = time;
  }

  public String category() {
    return category;
  }

  public String message() {
    return message;
  }

  public String link() {
    return link;
  }

  public String projectKey() {
    return projectKey;
  }

  public ZonedDateTime time() {
    return time;
  }

}
