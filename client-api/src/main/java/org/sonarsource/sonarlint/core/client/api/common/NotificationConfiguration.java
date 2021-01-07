/*
 * SonarLint Core - Client API
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.common;

import java.util.function.Supplier;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.LastNotificationTime;
import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotificationListener;

public class NotificationConfiguration {
  private final ServerNotificationListener listener;
  private final LastNotificationTime lastNotificationTime;
  private final String projectKey;
  private final Supplier<ServerConfiguration> serverConfiguration;

  public NotificationConfiguration(ServerNotificationListener listener, LastNotificationTime lastNotificationTime,
    String projectKey, Supplier<ServerConfiguration> serverConfiguration) {
    this.listener = listener;
    this.lastNotificationTime = lastNotificationTime;
    this.projectKey = projectKey;
    this.serverConfiguration = serverConfiguration;
  }

  public ServerNotificationListener listener() {
    return listener;
  }

  public LastNotificationTime lastNotificationTime() {
    return lastNotificationTime;
  }

  public String projectKey() {
    return projectKey;
  }

  public Supplier<ServerConfiguration> serverConfiguration() {
    return serverConfiguration;
  }
}
