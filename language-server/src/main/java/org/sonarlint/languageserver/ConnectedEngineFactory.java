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

import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;

/**
 * Create and start connected engines based on server info, configured with loggers and extra properties.
 */
class ConnectedEngineFactory {

  private final LogOutput logOutput;
  private final Logger logger;

  private final Map<String, String> extraProperties = new HashMap<>();

  ConnectedEngineFactory(LogOutput logOutput, Logger logger) {
    this.logOutput = logOutput;
    this.logger = logger;
  }

  @CheckForNull
  ConnectedSonarLintEngine create(ServerInfo serverInfo) {
    String serverId = serverInfo.serverId;
    logger.info("Starting connected SonarLint engine for " + serverId + "...");

    try {
      ConnectedGlobalConfiguration configuration = ConnectedGlobalConfiguration.builder()
        .setLogOutput(logOutput)
        .setServerId(serverId)
        .setExtraProperties(extraProperties)
        .build();

      ConnectedSonarLintEngineImpl engine = new ConnectedSonarLintEngineImpl(configuration);

      logger.info("Connected SonarLint engine started for " + serverId);

      return engine;
    } catch (Exception e) {
      logger.error("Error starting connected SonarLint engine for " + serverId, e);
    }
    return null;
  }

  void putExtraProperty(String name, String value) {
    extraProperties.put(name, value);
  }
}
