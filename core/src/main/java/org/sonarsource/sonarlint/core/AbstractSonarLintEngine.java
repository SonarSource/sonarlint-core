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
package org.sonarsource.sonarlint.core;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.plugin.common.log.LogOutput;

public abstract class AbstractSonarLintEngine implements SonarLintEngine {
  protected final ReadWriteLock rwl = new ReentrantReadWriteLock();
  private final LogOutput logOutput;
  protected AnalysisEngine analysisEngine;

  protected AbstractSonarLintEngine(@Nullable LogOutput logOutput) {
    this.logOutput = logOutput;
  }

  protected void startAnalysisEngine(AnalysisEngineConfiguration analysisGlobalConfig) {
    analysisEngine = new AnalysisEngine(analysisGlobalConfig);
  }

  @Override
  public void startModule(String moduleId) {
    analysisEngine.startModule(moduleId);
  }

  @Override
  public void stopModule(String moduleId) {
    analysisEngine.stopModule(moduleId);
  }

  @Override
  public void fireModuleFileEvent(String moduleId, ClientModuleFileEvent event) {
    analysisEngine.fireModuleFileEvent(moduleId, event);
  }

  protected <T> T withRwLock(Supplier<T> callable) {
    setLogging(null);
    rwl.writeLock().lock();
    try {
      return callable.get();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      rwl.writeLock().unlock();
    }
  }

  protected void setLogging(@Nullable LogOutput logOutput) {
    if (logOutput != null) {
      Loggers.setTarget(logOutput);
    } else {
      Loggers.setTarget(this.logOutput);
    }
  }

}
