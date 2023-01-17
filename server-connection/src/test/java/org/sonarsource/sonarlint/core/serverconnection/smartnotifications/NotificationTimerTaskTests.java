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
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationTimerTaskTests {
  private final ServerNotificationListener listener = mock(ServerNotificationListener.class);
  private final NotificationChecker notificationChecker = mock(NotificationChecker.class);
  private final LastNotificationTime notificationTime = mock(LastNotificationTime.class);

  private final ZonedDateTime time = ZonedDateTime.now();
  private NotificationTimerTask timerTask;

  @BeforeEach
  void setup() {
    when(notificationTime.get()).thenReturn(time);
    timerTask = new NotificationTimerTask(c -> notificationChecker);
  }

  @Test
  void testRunEmpty() {
    timerTask.run();
    verifyNoInteractions(notificationChecker);
  }

  @Test
  void testSingleProjectWithoutNotifications() {
    var project = createProject("myproject");
    timerTask.setProjects(Collections.singleton(project));
    timerTask.run();
    verify(notificationChecker).request(Collections.singletonMap("myproject", time));
    verifyNoInteractions(listener);
  }

  @Test
  void testErrorParsing() {
    when(notificationChecker.request(anyMap())).thenThrow(new IllegalStateException());
    var project = createProject("myproject");
    timerTask.setProjects(Collections.singleton(project));
    timerTask.run();

    verify(notificationChecker).request(Collections.singletonMap("myproject", time));
    verifyNoInteractions(listener);
  }

  @Test
  void testLimit24h() {
    when(notificationTime.get()).thenReturn(ZonedDateTime.now().minusDays(30));

    // return one notification for our project
    var notif = mock(ServerNotification.class);
    when(notif.projectKey()).thenReturn("myproject");
    when(notificationChecker.request(anyMap())).thenReturn(Collections.singletonList(notif));

    // execute with one project
    var project = createProject("myproject");
    timerTask.setProjects(Collections.singleton(project));
    timerTask.run();

    // verify checker used once and notification was returned through the listener
    verify(notificationChecker).request(ArgumentMatchers.argThat(map -> {
      var time = map.values().iterator().next();
      return ChronoUnit.MINUTES.between(ZonedDateTime.now().minusDays(1), time) == 0;
    }));

    verify(listener).handle(notif);
  }

  @Test
  void testSingleProjectWithNotifications() {
    // return one notification for our project
    var notif = mock(ServerNotification.class);
    when(notif.projectKey()).thenReturn("myproject");
    when(notificationChecker.request(Collections.singletonMap("myproject", time))).thenReturn(Collections.singletonList(notif));

    // execute with one project
    var project = createProject("myproject");
    timerTask.setProjects(Collections.singleton(project));
    timerTask.run();

    // verify checker used once and notification was returned through the listener
    verify(notificationChecker).request(Collections.singletonMap("myproject", time));

    verify(listener).handle(notif);
  }

  @Test
  void testRepeatedProject() {
    // return one notification for our project
    var notif = mock(ServerNotification.class);
    when(notif.projectKey()).thenReturn("myproject");
    when(notificationChecker.request(Collections.singletonMap("myproject", time))).thenReturn(Collections.singletonList(notif));

    // execute with one project
    var project = createProject("myproject");
    var project2 = createProject("myproject");

    var notificationTime = mock(LastNotificationTime.class);
    when(notificationTime.get()).thenReturn(ZonedDateTime.now().minusHours(2));
    when(project2.lastNotificationTime()).thenReturn(notificationTime);

    timerTask.setProjects(Collections.singleton(project));
    timerTask.run();

    // verify checker used once and notification was returned through the listener
    // should use the most recent time
    verify(notificationChecker).request(Collections.singletonMap("myproject", time));

    verify(listener).handle(notif);
  }

  private NotificationConfiguration createProject(String key) {
    return createProject(key, mock(EndpointParams.class));
  }

  private NotificationConfiguration createProject(String key, EndpointParams endpoint) {
    var project = mock(NotificationConfiguration.class);

    when(project.listener()).thenReturn(listener);
    when(project.projectKey()).thenReturn(key);
    when(project.endpoint()).thenReturn(() -> endpoint);
    when(project.client()).thenReturn(() -> null);
    when(project.lastNotificationTime()).thenReturn(notificationTime);
    return project;
  }
}
