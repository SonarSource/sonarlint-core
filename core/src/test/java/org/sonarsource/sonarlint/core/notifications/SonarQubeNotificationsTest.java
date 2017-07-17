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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Timer;

import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.SonarQubeNotificationListener;

public class SonarQubeNotificationsTest {
  @Test
  public void testRegistration() {
    NotificationTimerTask timerTask = mock(NotificationTimerTask.class);
    NotificationConfiguration config = mock(NotificationConfiguration.class);
    Timer timer = mock(Timer.class);
    SonarQubeNotificationListener listener = mock(SonarQubeNotificationListener.class);

    when(config.listener()).thenReturn(listener);
    SonarQubeNotifications notifications = new SonarQubeNotifications(timer, timerTask);

    notifications.register(config);
    verify(timerTask).setProjects(argThat(c -> c.size() == 1));
    verifyNoMoreInteractions(timerTask);
  }
}
