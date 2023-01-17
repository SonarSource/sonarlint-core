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

import java.util.function.Supplier;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;

public class NotificationConfiguration {
  private final ServerNotificationListener listener;
  private final LastNotificationTime lastNotificationTime;
  private final String projectKey;
  private final Supplier<EndpointParams> endpoint;
  private final Supplier<HttpClient> client;

  public NotificationConfiguration(ServerNotificationListener listener, LastNotificationTime lastNotificationTime,
    String projectKey, Supplier<EndpointParams> endpoint, Supplier<HttpClient> client) {
    this.listener = listener;
    this.lastNotificationTime = lastNotificationTime;
    this.projectKey = projectKey;
    this.endpoint = endpoint;
    this.client = client;
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

  public Supplier<EndpointParams> endpoint() {
    return endpoint;
  }

  public Supplier<HttpClient> client() {
    return client;
  }
}
