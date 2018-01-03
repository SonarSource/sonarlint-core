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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.SonarQubeNotification;

class NotificationTimerTask extends TimerTask {
  // merge with most recent time
  private static final BinaryOperator<ZonedDateTime> MERGE_TIMES = (t1, t2) -> t1.toInstant().compareTo(t2.toInstant()) > 0 ? t1 : t2;
  private static final Logger LOG = Loggers.get(NotificationTimerTask.class);
  private final NotificationCheckerFactory checkerFactory;
  private Collection<NotificationConfiguration> configuredProjects = Collections.emptyList();

  public NotificationTimerTask() {
    this(new NotificationCheckerFactory());
  }

  public NotificationTimerTask(NotificationCheckerFactory checkerFactory) {
    this.checkerFactory = checkerFactory;
  }

  public void setProjects(Collection<NotificationConfiguration> configurations) {
    this.configuredProjects = new ArrayList<>(configurations);
  }

  @Override
  public void run() {
    Map<ServerConfiguration, List<NotificationConfiguration>> mapByServer = groupByServer();

    for (Map.Entry<ServerConfiguration, List<NotificationConfiguration>> entry : mapByServer.entrySet()) {
      requestForServer(entry.getKey(), entry.getValue());
    }
  }

  private static ZonedDateTime getLastNotificationTime(NotificationConfiguration config) {
    ZonedDateTime lastTime = config.lastNotificationTime().get();
    ZonedDateTime oneDayAgo = ZonedDateTime.now().minusDays(1);
    return lastTime.isAfter(oneDayAgo) ? lastTime : oneDayAgo;
  }

  private void requestForServer(ServerConfiguration serverConfiguration, List<NotificationConfiguration> configs) {
    try {
      Map<String, ZonedDateTime> request = configs.stream()
        .collect(Collectors.toMap(NotificationConfiguration::projectKey, NotificationTimerTask::getLastNotificationTime, MERGE_TIMES));

      NotificationChecker notificationChecker = checkerFactory.create(serverConfiguration);
      List<SonarQubeNotification> notifications = notificationChecker.request(request);

      for (SonarQubeNotification n : notifications) {
        Stream<NotificationConfiguration> matchingConfStream = configs.stream();
        if (n.projectKey() != null) {
          matchingConfStream = matchingConfStream.filter(c -> c.projectKey().equals(n.projectKey()));
        }

        matchingConfStream.forEach(c -> {
          c.listener().handle(n);
          c.lastNotificationTime().set(n.time());
        });
      }
    } catch (Exception e) {
      LOG.warn("Failed to request SonarQube events to " + serverConfiguration.getUrl(), e);
    }
  }

  private Map<ServerConfiguration, List<NotificationConfiguration>> groupByServer() {
    return configuredProjects.stream().collect(Collectors.groupingBy(NotificationConfiguration::serverConfiguration));
  }

}
