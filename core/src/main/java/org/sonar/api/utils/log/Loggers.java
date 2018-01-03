/*
 * SonarLint Core - Implementation
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
package org.sonar.api.utils.log;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.log.LogOutputDelegator;
import org.sonarsource.sonarlint.core.log.SonarLintLoggerFactory;
import org.sonarsource.sonarlint.core.util.LoggedErrorHandler;

/**
 * Overrides the behavior of loggers in the sonar API.
 * Basically the factory always returns {@link SonarLintLogger}, which uses {@link LogOutputDelegator} to delegate
 * the logging to an {@link LogOutput}.
 * The LogOutput can be set dynamically at any time, for the executing thread. 
 */
public abstract class Loggers {
  private static final LogOutputDelegator logOutputDelegator = new LogOutputDelegator();
  private static final SonarLintLogger logger = new SonarLintLogger(logOutputDelegator);
  private static final SonarLintLoggerFactory factory = new SonarLintLoggerFactory(logger);

  public static Logger get(Class<?> name) {
    return logger;
  }

  public static Logger get(String name) {
    return logger;
  }

  static Loggers getFactory() {
    return factory;
  }

  protected abstract Logger newInstance(String name);

  protected abstract LoggerLevel getLevel();

  protected abstract void setLevel(LoggerLevel level);

  public static void setTarget(@Nullable LogOutput output) {
    logOutputDelegator.setTarget(output);
  }

  public static void setErrorHandler(LoggedErrorHandler errorHandler) {
    logOutputDelegator.setErrorHandler(errorHandler);
  }

}
