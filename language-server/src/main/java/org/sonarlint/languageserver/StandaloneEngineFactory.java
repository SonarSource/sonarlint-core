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

import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

/**
 * Create and start standalone engines, configured with loggers and extra properties.
 */
class StandaloneEngineFactory {
  private final Collection<URL> analyzers;
  private final LogOutput logOutput;
  private final Logger logger;

  private final Map<String, String> extraProperties = new HashMap<>();

  StandaloneEngineFactory(Collection<URL> analyzers, LogOutput logOutput, Logger logger) {
    this.analyzers = analyzers;
    this.logOutput = logOutput;
    this.logger = logger;
  }

  StandaloneSonarLintEngine create() {
    logger.info("Starting standalone SonarLint engine...");
    logger.info("Using " + analyzers.size() + " analyzers");

    try {
      StandaloneGlobalConfiguration configuration = StandaloneGlobalConfiguration.builder()
        .setLogOutput(logOutput)
        .setExtraProperties(extraProperties)
        .addPlugins(analyzers.toArray(new URL[0]))
        .build();

      StandaloneSonarLintEngine engine = new StandaloneSonarLintEngineImpl(configuration);
      logger.info("Standalone SonarLint engine started");
      return engine;
    } catch (Exception e) {
      logger.error("Error starting standalone SonarLint engine", e);
      throw new IllegalStateException(e);
    }
  }

  void putExtraProperty(String name, String value) {
    extraProperties.put(name, value);
  }
}
