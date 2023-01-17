/*
 * SonarLint Core - Plugin Commons
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
package org.sonar.api.utils.log;

import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * Overrides the behavior of loggers in the sonar API.
 * Basically the factory always returns {@link SonarApiLogger}, which uses {@link SonarLintLogger} to delegate
 * the logging to an {@link ClientLogOutput}.
 * The LogOutput can be set dynamically at any time, for the executing thread.
 */
public class Loggers {
  private static final SonarApiLogger logger = new SonarApiLogger();

  public static Logger get(Class<?> name) {
    return logger;
  }

  public static Logger get(String name) {
    return logger;
  }

  private Loggers() {
    // Only use get()
  }

}
