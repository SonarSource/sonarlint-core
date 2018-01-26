/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.notifications;

import java.util.List;
import java.util.Timer;
import java.util.concurrent.CopyOnWriteArrayList;

import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.SonarQubeNotificationListener;

public class SonarQubeNotifications {
  static final int DELAY = 60_000;
  private static final Object LOCK = new Object();

  private static SonarQubeNotifications singleton;

  private final List<NotificationConfiguration> configuredNotifications = new CopyOnWriteArrayList<>();

  private Timer timer;
  private NotificationTimerTask task;
  private final NotificationCheckerFactory checkerFactory;

  SonarQubeNotifications(Timer timer, NotificationTimerTask task, NotificationCheckerFactory checkerFactory) {
    this.timer = timer;
    this.task = task;
    this.checkerFactory = checkerFactory;
    this.timer.scheduleAtFixedRate(task, DELAY, DELAY);
  }
  
  public static SonarQubeNotifications get() {
    synchronized (LOCK) {
      if (singleton == null) {
        Timer timer = new Timer("Notifications timer", true);
        NotificationTimerTask timerTask = new NotificationTimerTask();
        singleton = new SonarQubeNotifications(timer, timerTask, new NotificationCheckerFactory());
      }
      return singleton;
    }
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
  public void remove(SonarQubeNotificationListener listener) {
    configuredNotifications.removeIf(p -> p.listener().equals(listener));
    task.setProjects(configuredNotifications);
  }

  /**
   * Checks if a server supports notifications
   */
  public boolean isSupported(ServerConfiguration serverConfig) {
    NotificationChecker checker = checkerFactory.create(serverConfig);
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
