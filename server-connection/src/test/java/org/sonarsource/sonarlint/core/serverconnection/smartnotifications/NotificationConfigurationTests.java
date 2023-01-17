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

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationConfigurationTests {
  @Test
  void testGetters() {
    var listener = mock(ServerNotificationListener.class);
    var lastNotificationTime = mock(LastNotificationTime.class);
    var projectKey = "key";
    var endpoint = mock(EndpointParams.class);
    var client = mock(HttpClient.class);
    var configuration = new NotificationConfiguration(listener, lastNotificationTime, projectKey, () -> endpoint, () -> client);

    assertThat(configuration.lastNotificationTime()).isEqualTo(lastNotificationTime);
    assertThat(configuration.listener()).isEqualTo(listener);
    assertThat(configuration.projectKey()).isEqualTo(projectKey);
    assertThat(configuration.endpoint().get()).isEqualTo(endpoint);
    assertThat(configuration.client().get()).isEqualTo(client);
  }
}
