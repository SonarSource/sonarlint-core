/*
 * SonarLint Language Server
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
package org.sonarlint.languageserver;

import org.junit.*;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class DefaultEngineCacheTest {

  @Test
  public void get_or_create_standalone_engine_with_extra_properties() {
    StandaloneEngineFactory standaloneEngineFactory = mock(StandaloneEngineFactory.class);
    StandaloneSonarLintEngine standaloneEngine = mock(StandaloneSonarLintEngine.class);

    DefaultEngineCache engineCache = new DefaultEngineCache(standaloneEngineFactory, mock(ConnectedEngineFactory.class));
    engineCache.putExtraProperty("foo", "bar");
    verify(standaloneEngineFactory).putExtraProperty(anyString(), anyString());

    when(standaloneEngineFactory.create()).thenReturn(standaloneEngine);
    assertThat(engineCache.getOrCreateStandaloneEngine()).isEqualTo(standaloneEngine);

    when(standaloneEngineFactory.create()).thenReturn(mock(StandaloneSonarLintEngine.class));
    // should get instance from cache
    assertThat(engineCache.getOrCreateStandaloneEngine()).isEqualTo(standaloneEngine);
  }

  @Test
  public void get_or_create_connected_engine_with_extra_properties() {
    ServerInfo serverInfo = new ServerInfo("serverId", "serverUrl", "token", null);

    ConnectedEngineFactory connectedEngineFactory = mock(ConnectedEngineFactory.class);
    ConnectedSonarLintEngine connectedEngine = mock(ConnectedSonarLintEngine.class);

    DefaultEngineCache engineCache = new DefaultEngineCache(mock(StandaloneEngineFactory.class), connectedEngineFactory);
    engineCache.putExtraProperty("foo", "bar");
    verify(connectedEngineFactory).putExtraProperty(anyString(), anyString());

    when(connectedEngineFactory.create(eq(serverInfo))).thenReturn(connectedEngine);
    assertThat(engineCache.getOrCreateConnectedEngine(serverInfo)).isEqualTo(connectedEngine);

    // should get instance from cache
    when(connectedEngineFactory.create(eq(serverInfo))).thenReturn(mock(ConnectedSonarLintEngine.class));
    assertThat(engineCache.getOrCreateConnectedEngine(serverInfo)).isEqualTo(connectedEngine);
  }
}
