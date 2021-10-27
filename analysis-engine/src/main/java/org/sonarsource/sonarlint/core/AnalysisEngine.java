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
package org.sonarsource.sonarlint.core;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.ClientFileSystem;
import org.sonarsource.sonarlint.core.client.api.common.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.ModuleFileEventNotifier;
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.global.GlobalAnalysisContainer;
import org.sonarsource.sonarlint.core.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.container.module.ModuleRegistry;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static java.util.Objects.requireNonNull;

public class AnalysisEngine {

  private final ReadWriteLock rwl = new ReentrantReadWriteLock();
  private final LogOutput logOutput;
  private final GlobalAnalysisConfiguration globalConfig;
  private GlobalAnalysisContainer globalContainer;

  public AnalysisEngine(GlobalAnalysisConfiguration globalConfig) {
    this.logOutput = globalConfig.getLogOutput();
    this.globalConfig = globalConfig;
    start();
  }

  public void declareModule(ModuleInfo module) {
    withRwLock(() -> getModuleRegistry().registerModule(module));
  }

  public void stopModule(Object moduleKey) {
    withRwLock(() -> {
      getModuleRegistry().unregisterModule(moduleKey);
      return null;
    });
  }

  public void fireModuleFileEvent(Object moduleKey, ClientModuleFileEvent event) {
    withRwLock(() -> {
      ComponentContainer moduleContainer = getModuleRegistry().getContainerFor(moduleKey);
      if (moduleContainer != null) {
        moduleContainer.getComponentByType(ModuleFileEventNotifier.class).fireModuleFileEvent(event);
      }
      return null;
    });
  }

  <T> T withModule(AnalysisConfiguration configuration, Function<ModuleContainer, T> consumer) {
    boolean deleteModuleAfterAnalysis = false;
    Object moduleKey = configuration.moduleKey();
    ModuleContainer moduleContainer = getModuleRegistry().getContainerFor(moduleKey);
    if (moduleContainer == null) {
      // if not found, means we are outside of any module (e.g. single file analysis on VSCode)
      moduleContainer = getModuleRegistry().createContainer(new ModuleInfo(moduleKey, new AnalysisScopeFileSystem(configuration.inputFiles())));
      deleteModuleAfterAnalysis = true;
    }
    Throwable originalException = null;
    try {
      return consumer.apply(moduleContainer);
    } catch (Throwable e) {
      originalException = e;
      throw e;
    } finally {
      try {
        if (deleteModuleAfterAnalysis) {
          moduleContainer.stopComponents();
        }
      } catch (Exception e) {
        if (originalException != null) {
          e.addSuppressed(originalException);
        }
        throw e;
      }
    }
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

  private static class AnalysisScopeFileSystem implements ClientFileSystem {

    private final Iterable<ClientInputFile> filesToAnalyze;

    private AnalysisScopeFileSystem(Iterable<ClientInputFile> filesToAnalyze) {
      this.filesToAnalyze = filesToAnalyze;
    }

    @Override
    public Stream<ClientInputFile> files(String suffix, InputFile.Type type) {
      return files()
        .filter(file -> file.relativePath().endsWith(suffix))
        .filter(file -> file.isTest() == (type == InputFile.Type.TEST));
    }

    @Override
    public Stream<ClientInputFile> files() {
      return StreamSupport.stream(filesToAnalyze.spliterator(), false);
    }
  }

  public GlobalAnalysisContainer getGlobalContainer() {
    return globalContainer;
  }

  ModuleRegistry getModuleRegistry() {
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

  public AnalysisResults analyze(AnalysisConfiguration configuration, IssueListener issueListener, @Nullable LogOutput logOutput, @Nullable ProgressMonitor monitor) {
    requireNonNull(configuration);
    requireNonNull(issueListener);
    setLogging(logOutput);
    rwl.readLock().lock();
    return withModule(configuration, moduleContainer -> {
      try {
        return moduleContainer.analyze(configuration, issueListener, new ProgressWrapper(monitor));
      } catch (RuntimeException e) {
        throw SonarLintWrappedException.wrap(e);
      } finally {
        rwl.readLock().unlock();
      }
    });
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
