/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.notifications;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.LastNotificationTime;
import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotification;
import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotificationListener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class NotificationTimerTaskTest {
  private final ServerNotificationListener listener = mock(ServerNotificationListener.class);
  private final NotificationChecker notificationChecker = mock(NotificationChecker.class);
  private final NotificationCheckerFactory notificationCheckerFactory = mock(NotificationCheckerFactory.class);
  private final LastNotificationTime notificationTime = mock(LastNotificationTime.class);

  private final ZonedDateTime time = ZonedDateTime.now();
  private NotificationTimerTask timerTask;

  @Before
  public void setup() {
    when(notificationTime.get()).thenReturn(time);
    when(notificationCheckerFactory.create(any())).thenReturn(notificationChecker);
    timerTask = new NotificationTimerTask(notificationCheckerFactory);
  }

  @Test
  public void testRunEmpty() {
    timerTask.run();
    verifyZeroInteractions(notificationCheckerFactory);
  }

  @Test
  public void testSingleProjectWithoutNotifications() {
    NotificationConfiguration project = createProject("myproject");
    timerTask.setProjects(Collections.singleton(project));
    timerTask.run();
    verify(notificationCheckerFactory, times(1)).create(any(ServerConfiguration.class));
    verify(notificationChecker).request(Collections.singletonMap("myproject", time));
    verifyZeroInteractions(listener);
  }

  @Test
  public void testErrorParsing() {
    when(notificationChecker.request(anyMap())).thenThrow(new IllegalStateException());
    NotificationConfiguration project = createProject("myproject");
    timerTask.setProjects(Collections.singleton(project));
    timerTask.run();

    verify(notificationCheckerFactory, times(1)).create(any(ServerConfiguration.class));
    verify(notificationChecker).request(Collections.singletonMap("myproject", time));
    verifyZeroInteractions(listener);
  }

  @Test
  public void testLimit24h() {
    when(notificationTime.get()).thenReturn(ZonedDateTime.now().minusDays(30));

    // return one notification for our project
    ServerNotification notif = mock(ServerNotification.class);
    when(notif.projectKey()).thenReturn("myproject");
    when(notificationChecker.request(anyMap())).thenReturn(Collections.singletonList(notif));

    // execute with one project
    NotificationConfiguration project = createProject("myproject");
    timerTask.setProjects(Collections.singleton(project));
    timerTask.run();

    // verify checker used once and notification was returned through the listener
    verify(notificationCheckerFactory, times(1)).create(any(ServerConfiguration.class));
    verify(notificationChecker).request(ArgumentMatchers.argThat(map -> {
      ZonedDateTime time = map.values().iterator().next();
      return ChronoUnit.MINUTES.between(ZonedDateTime.now().minusDays(1), time) == 0;
    }));

    verify(listener).handle(notif);
  }

  @Test
  public void testSingleProjectWithNotifications() {
    // return one notification for our project
    ServerNotification notif = mock(ServerNotification.class);
    when(notif.projectKey()).thenReturn("myproject");
    when(notificationChecker.request(Collections.singletonMap("myproject", time))).thenReturn(Collections.singletonList(notif));

    // execute with one project
    NotificationConfiguration project = createProject("myproject");
    timerTask.setProjects(Collections.singleton(project));
    timerTask.run();

    // verify checker used once and notification was returned through the listener
    verify(notificationCheckerFactory, times(1)).create(any(ServerConfiguration.class));
    verify(notificationChecker).request(Collections.singletonMap("myproject", time));

    verify(listener).handle(notif);
  }

  @Test
  public void testRepeatedProject() {
    // return one notification for our project
    ServerNotification notif = mock(ServerNotification.class);
    when(notif.projectKey()).thenReturn("myproject");
    when(notificationChecker.request(Collections.singletonMap("myproject", time))).thenReturn(Collections.singletonList(notif));

    // execute with one project
    NotificationConfiguration project = createProject("myproject");
    NotificationConfiguration project2 = createProject("myproject");

    LastNotificationTime notificationTime = mock(LastNotificationTime.class);
    when(notificationTime.get()).thenReturn(ZonedDateTime.now().minusHours(2));
    when(project2.lastNotificationTime()).thenReturn(notificationTime);

    timerTask.setProjects(Collections.singleton(project));
    timerTask.run();

    // verify checker used once and notification was returned through the listener
    verify(notificationCheckerFactory, times(1)).create(any(ServerConfiguration.class));

    // should use the most recent time
    verify(notificationChecker).request(Collections.singletonMap("myproject", time));

    verify(listener).handle(notif);
  }

  private NotificationConfiguration createProject(String key) {
    return createProject(key, mock(ServerConfiguration.class));
  }

  private NotificationConfiguration createProject(String key, ServerConfiguration config) {
    NotificationConfiguration project = mock(NotificationConfiguration.class);

    when(project.listener()).thenReturn(listener);
    when(project.projectKey()).thenReturn(key);
    when(project.serverConfiguration()).thenReturn(() -> config);
    when(project.lastNotificationTime()).thenReturn(notificationTime);
    return project;
  }
}
