/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.SonarQubeNotification;
import org.sonarsource.sonarlint.core.client.api.notifications.SonarQubeNotificationListener;
import org.sonarsource.sonarlint.core.client.api.notifications.LastNotificationTime;

public class NotificationTimerTaskTest {
  @Mock
  private SonarQubeNotificationListener listener;
  @Mock
  private NotificationChecker notificationChecker;
  @Mock
  private NotificationCheckerFactory notificationCheckerFactory;
  @Mock
  private LastNotificationTime notificationTime;

  private ZonedDateTime time = ZonedDateTime.now();
  private NotificationTimerTask timerTask;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
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
    SonarQubeNotification notif = mock(SonarQubeNotification.class);
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
      return time.isAfter(ZonedDateTime.now().minusHours(25)) && time.isBefore(ZonedDateTime.now().minusHours(23));
    }));

    verify(listener).handle(notif);
  }

  @Test
  public void testSingleProjectWithNotifications() {
    // return one notification for our project
    SonarQubeNotification notif = mock(SonarQubeNotification.class);
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

  private NotificationConfiguration createProject(String key) {
    return createProject(key, mock(ServerConfiguration.class));
  }

  private NotificationConfiguration createProject(String key, ServerConfiguration config) {
    NotificationConfiguration project = mock(NotificationConfiguration.class);

    when(project.listener()).thenReturn(listener);
    when(project.projectKey()).thenReturn(key);
    when(project.serverConfiguration()).thenReturn(config);
    when(project.lastNotificationTime()).thenReturn(notificationTime);
    return project;
  }
}
