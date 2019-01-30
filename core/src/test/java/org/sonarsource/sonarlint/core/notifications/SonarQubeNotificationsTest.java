/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Timer;

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.SonarQubeNotificationListener;

public class SonarQubeNotificationsTest {
  private NotificationCheckerFactory checkerFactory;
  private NotificationChecker checker;
  private NotificationTimerTask timerTask;
  private NotificationConfiguration config;
  private Timer timer;

  @Before
  public void setUp() {
    checkerFactory = mock(NotificationCheckerFactory.class);
    checker = mock(NotificationChecker.class);
    timerTask = mock(NotificationTimerTask.class);
    config = mock(NotificationConfiguration.class);
    timer = mock(Timer.class);
  }
  
  @Test
  public void testSingleton() {
    assertThat(SonarQubeNotifications.get()).isEqualTo(SonarQubeNotifications.get());
    SonarQubeNotifications.get().stop();
  }

  @Test
  public void testRegistration() {
    SonarQubeNotificationListener listener = mock(SonarQubeNotificationListener.class);
    when(config.listener()).thenReturn(listener);
    SonarQubeNotifications notifications = new SonarQubeNotifications(timer, timerTask, checkerFactory);

    notifications.register(config);
    notifications.remove(listener);

    verify(timerTask, times(2)).setProjects(anyCollection());

    verifyNoMoreInteractions(timerTask);
    verify(timer).scheduleAtFixedRate(timerTask, SonarQubeNotifications.DELAY, SonarQubeNotifications.DELAY);
  }

  @Test
  public void testStop() {
    SonarQubeNotifications notifications = new SonarQubeNotifications(timer, timerTask, checkerFactory);
    notifications.stop();
    verify(timer).cancel();
  }

  @Test
  public void testIsSupported() {
    ServerConfiguration serverConfig = mock(ServerConfiguration.class);
    when(checkerFactory.create(serverConfig)).thenReturn(checker);
    when(checker.isSupported()).thenReturn(true);

    SonarQubeNotifications notifications = new SonarQubeNotifications(timer, timerTask, checkerFactory);
    assertThat(notifications.isSupported(serverConfig)).isTrue();
  }

}
