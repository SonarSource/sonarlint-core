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

import java.util.Timer;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotificationListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ServerNotificationsRegistryTests {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  private NotificationTimerTask timerTask;
  private NotificationConfiguration config;
  private Timer timer;

  @BeforeEach
  public void setUp() {
    timerTask = mock(NotificationTimerTask.class);
    config = mock(NotificationConfiguration.class);
    timer = mock(Timer.class);
  }

  @Test
  void testRegistration() {
    ServerNotificationListener listener = mock(ServerNotificationListener.class);
    when(config.listener()).thenReturn(listener);
    ServerNotificationsRegistry notifications = new ServerNotificationsRegistry(timer, timerTask);

    notifications.register(config);
    notifications.remove(listener);

    verify(timerTask, times(2)).setProjects(anyCollection());

    verifyNoMoreInteractions(timerTask);
    verify(timer).scheduleAtFixedRate(timerTask, ServerNotificationsRegistry.DELAY, ServerNotificationsRegistry.DELAY);
  }

  @Test
  void testStop() {
    ServerNotificationsRegistry notifications = new ServerNotificationsRegistry(timer, timerTask);
    notifications.stop();
    verify(timer).cancel();
  }

  @Test
  void testIsSupported() {
    mockServer.addResponse("/api/developers/search_events?projects=&from=", new MockResponse());

    assertThat(ServerNotificationsRegistry.isSupported(mockServer.endpoint(), MockWebServerExtension.httpClient())).isTrue();
  }

}
