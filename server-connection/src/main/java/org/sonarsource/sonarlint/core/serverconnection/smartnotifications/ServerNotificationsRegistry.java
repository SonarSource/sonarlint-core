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

import java.util.List;
import java.util.Timer;
import java.util.concurrent.CopyOnWriteArrayList;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

public class ServerNotificationsRegistry {
  static final int DELAY = 60_000;

  private final List<NotificationConfiguration> configuredNotifications = new CopyOnWriteArrayList<>();

  private Timer timer;
  private NotificationTimerTask task;

  public ServerNotificationsRegistry() {
    this(new Timer("Notifications timer", true), new NotificationTimerTask());
  }

  ServerNotificationsRegistry(Timer timer, NotificationTimerTask task) {
    this.timer = timer;
    this.task = task;
    this.timer.scheduleAtFixedRate(task, DELAY, DELAY);
  }

  /**
   * Register a project to receive notifications about it.
   */
  public void register(NotificationConfiguration configuration) {
    configuredNotifications.add(configuration);
    task.setProjects(configuredNotifications);
  }

  /**
   * Removes any previously registered projects attached to a listener
   */
  public void remove(ServerNotificationListener listener) {
    configuredNotifications.removeIf(p -> p.listener().equals(listener));
    task.setProjects(configuredNotifications);
  }

  /**
   * Checks if a server supports notifications
   */
  public static boolean isSupported(EndpointParams endpoint, HttpClient client) {
    var checker = new NotificationChecker(new ServerApiHelper(endpoint, client));
    return checker.isSupported();
  }

  /**
   * Stops notifications.
   */
  public void stop() {
    timer.cancel();
    timer = null;
    task = null;
    configuredNotifications.clear();
  }
}
