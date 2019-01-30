/*
 * SonarLint Language Server
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
package org.sonarlint.languageserver;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

/**
 * @see EngineCache
 */
public class DefaultEngineCache implements EngineCache {

  private final Map<String, ConnectedSonarLintEngine> cache = new HashMap<>();

  private final StandaloneEngineFactory standaloneEngineFactory;
  private final ConnectedEngineFactory connectedEngineFactory;

  private StandaloneSonarLintEngine standaloneEngine = null;

  DefaultEngineCache(StandaloneEngineFactory standaloneEngineFactory, ConnectedEngineFactory connectedEngineFactory) {
    this.standaloneEngineFactory = standaloneEngineFactory;
    this.connectedEngineFactory = connectedEngineFactory;
  }

  @Override
  public StandaloneSonarLintEngine getOrCreateStandaloneEngine() {
    if (standaloneEngine == null) {
      standaloneEngine = standaloneEngineFactory.create();
    }
    return standaloneEngine;
  }

  @Override
  public void stopStandaloneEngine() {
    if (standaloneEngine != null) {
      standaloneEngine.stop();
    }
  }

  @CheckForNull
  @Override
  public ConnectedSonarLintEngine getOrCreateConnectedEngine(ServerInfo serverInfo) {
    ConnectedSonarLintEngine engine = cache.get(serverInfo.serverId);
    if (engine == null) {
      engine = connectedEngineFactory.create(serverInfo);
      if (engine != null) {
        cache.put(serverInfo.serverId, engine);
      }
    }
    return engine;
  }

  @Override
  public void putExtraProperty(String name, String value) {
    standaloneEngineFactory.putExtraProperty(name, value);
    connectedEngineFactory.putExtraProperty(name, value);
  }

  @Override
  public void clearConnectedEngines() {
    cache.values().forEach(engine -> engine.stop(false));
    cache.clear();
  }
}
