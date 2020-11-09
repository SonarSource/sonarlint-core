/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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
package org.sonarsource.sonarlint.core.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.Proxy;

import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.util.ws.HttpConnector;

public class TelemetryHttpConnectorFactoryTest {

  @Test
  public void testFactory() {
    Proxy proxy = mock(Proxy.class);
    TelemetryClientConfig config = TelemetryClientConfig.builder()
      .proxy(proxy)
      .build();
    HttpConnector httpConnector = new TelemetryHttpConnectorFactory().buildClient(config);
    assertThat(httpConnector).isNotNull();
    assertThat(httpConnector.okHttpClient().proxy()).isEqualTo(proxy);

  }
}
