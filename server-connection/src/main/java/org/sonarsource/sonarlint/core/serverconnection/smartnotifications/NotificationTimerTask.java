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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

class NotificationTimerTask extends TimerTask {
  // merge with most recent time
  private static final BinaryOperator<ZonedDateTime> MERGE_TIMES = (t1, t2) -> t1.toInstant().compareTo(t2.toInstant()) > 0 ? t1 : t2;
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private Collection<NotificationConfiguration> configuredProjects = Collections.emptyList();
  private final Function<ServerApiHelper, NotificationChecker> notificationCheckerFactory;

  public NotificationTimerTask() {
    this(NotificationChecker::new);
  }

  NotificationTimerTask(Function<ServerApiHelper, NotificationChecker> notificationCheckerFactory) {
    this.notificationCheckerFactory = notificationCheckerFactory;
  }

  public void setProjects(Collection<NotificationConfiguration> configurations) {
    this.configuredProjects = new ArrayList<>(configurations);
  }

  @Override
  public void run() {
    var mapByServer = groupByServer();

    for (Map.Entry<ServerApiHelper, List<NotificationConfiguration>> entry : mapByServer.entrySet()) {
      requestForServer(entry.getKey(), entry.getValue());
    }
  }

  private static ZonedDateTime getLastNotificationTime(NotificationConfiguration config) {
    var lastTime = config.lastNotificationTime().get();
    var oneDayAgo = ZonedDateTime.now().minusDays(1);
    return lastTime.isAfter(oneDayAgo) ? lastTime : oneDayAgo;
  }

  private void requestForServer(ServerApiHelper serverApiHelper, List<NotificationConfiguration> configs) {
    try {
      Map<String, ZonedDateTime> request = configs.stream()
        .collect(Collectors.toMap(NotificationConfiguration::projectKey, NotificationTimerTask::getLastNotificationTime, MERGE_TIMES));

      var notificationChecker = notificationCheckerFactory.apply(serverApiHelper);
      var notifications = notificationChecker.request(request);

      for (var n : notifications) {
        var matchingConfStream = configs.stream();
        if (n.projectKey() != null) {
          matchingConfStream = matchingConfStream.filter(c -> c.projectKey().equals(n.projectKey()));
        }

        matchingConfStream.forEach(c -> {
          c.listener().handle(n);
          c.lastNotificationTime().set(n.time());
        });
      }
    } catch (Exception e) {
      LOG.warn("Failed to request SonarLint notifications", e);
    }
  }

  private Map<ServerApiHelper, List<NotificationConfiguration>> groupByServer() {
    return configuredProjects.stream().collect(Collectors.groupingBy(n -> new ServerApiHelper(n.endpoint().get(), n.client().get())));
  }

}
