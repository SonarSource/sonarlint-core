/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.api;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalAnalysisContainer;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleFileEventNotifier;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleRegistry;
import org.sonarsource.sonarlint.core.analysis.exceptions.SonarLintException;
import org.sonarsource.sonarlint.core.analysis.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.analysis.progress.ProgressWrapper;
import org.sonarsource.sonarlint.core.plugin.common.log.LogOutput;
import org.sonarsource.sonarlint.core.plugin.common.pico.ComponentContainer;

import static java.util.Objects.requireNonNull;

public class AnalysisEngine {

  private final ReadWriteLock rwl = new ReentrantReadWriteLock();
  private final LogOutput logOutput;
  private final AnalysisEngineConfiguration globalConfig;
  private GlobalAnalysisContainer globalContainer;

  public AnalysisEngine(AnalysisEngineConfiguration globalConfig) {
    this.logOutput = globalConfig.getLogOutput();
    this.globalConfig = globalConfig;
    start();
  }

  public void startModule(String moduleId) {
    withRwLock(() -> getModuleRegistry().registerModule(moduleId));
  }

  public void stopModule(String moduleId) {
    withRwLock(() -> {
      getModuleRegistry().unregisterModule(moduleId);
      return null;
    });
  }

  public void fireModuleFileEvent(String moduleId, ClientModuleFileEvent event) {
    withRwLock(() -> {
      ComponentContainer moduleContainer = getModuleRegistry().getContainerFor(moduleId);
      if (moduleContainer != null) {
        moduleContainer.getComponentByType(ModuleFileEventNotifier.class).fireModuleFileEvent(event);
      }
      return null;
    });
  }

  private <T> T withRwLock(Supplier<T> callable) {
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

  private void setLogging(@Nullable LogOutput logOutput) {
    if (logOutput != null) {
      Loggers.setTarget(logOutput);
    } else {
      Loggers.setTarget(this.logOutput);
    }
  }

  public GlobalAnalysisContainer getGlobalContainer() {
    return globalContainer;
  }

  private ModuleRegistry getModuleRegistry() {
    return getGlobalContainer().getModuleRegistry();
  }

  public void start() {
    setLogging(null);
    rwl.writeLock().lock();
    this.globalContainer = new GlobalAnalysisContainer(globalConfig);
    try {
      globalContainer.startComponents();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      rwl.writeLock().unlock();
    }
  }

  public AnalysisResults analyze(AnalysisConfiguration configuration, Consumer<Issue> issueListener, @Nullable LogOutput logOutput,
    @Nullable ProgressMonitor monitor) {
    requireNonNull(configuration);
    requireNonNull(issueListener);
    setLogging(logOutput);
    rwl.readLock().lock();
    try {
      ModuleContainer moduleContainer = getModuleContainer(configuration);
      SonarLintException originalException = null;
      try {
        return moduleContainer.analyze(configuration, issueListener, new ProgressWrapper(monitor));
      } catch (Throwable e) {
        originalException = SonarLintWrappedException.wrap(e);
        throw e;
      } finally {
        try {
          if (moduleContainer.isTranscient()) {
            moduleContainer.stopComponents();
          }
        } catch (Exception e) {
          if (originalException != null) {
            e.addSuppressed(originalException);
          }
          throw SonarLintWrappedException.wrap(e);
        }
      }
    } finally {
      rwl.readLock().unlock();
    }
  }

  ModuleContainer getModuleContainer(AnalysisConfiguration configuration) {
    String moduleId = configuration.moduleId();
    ModuleContainer moduleContainer = moduleId != null ? getModuleRegistry().getContainerFor(moduleId) : null;
    if (moduleContainer == null) {
      // if not found or moduleId is null, means we are outside of any module (e.g. single file analysis on VSCode)
      moduleContainer = getModuleRegistry().createTranscientContainer(configuration.inputFiles());
    }
    return moduleContainer;
  }

  public void stop() {
    setLogging(null);
    rwl.writeLock().lock();
    try {
      if (globalContainer == null) {
        return;
      }
      globalContainer.stopComponents(false);
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      this.globalContainer = null;
      rwl.writeLock().unlock();
    }
  }

}
