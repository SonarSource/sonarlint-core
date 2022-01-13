/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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

import com.google.common.collect.Streams;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.AbstractAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.common.ClientFileSystem;
import org.sonarsource.sonarlint.core.client.api.common.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.ModuleFileEventNotifier;
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.module.ModuleRegistry;

public abstract class AbstractSonarLintEngine implements SonarLintEngine {
  protected final ReadWriteLock rwl = new ReentrantReadWriteLock();
  protected abstract ModuleRegistry getModuleRegistry();
  private final LogOutput logOutput;

  protected AbstractSonarLintEngine(@Nullable LogOutput logOutput) {
    this.logOutput = logOutput;
  }

  @Override
  public void declareModule(ModuleInfo module) {
    withRwLock(() -> getModuleRegistry().registerModule(module));
  }

  @Override
  public void stopModule(Object moduleKey) {
    withRwLock(() -> {
      getModuleRegistry().unregisterModule(moduleKey);
      return null;
    });
  }

  @Override
  public void fireModuleFileEvent(Object moduleKey, ClientModuleFileEvent event) {
    withRwLock(() -> {
      ComponentContainer moduleContainer = getModuleRegistry().getContainerFor(moduleKey);
      if (moduleContainer != null) {
        moduleContainer.getComponentByType(ModuleFileEventNotifier.class).fireModuleFileEvent(event);
      }
      return null;
    });
  }

  protected <T> T withModule(AbstractAnalysisConfiguration configuration, Function<ComponentContainer, T> consumer) {
    var deleteModuleAfterAnalysis = false;
    Object moduleKey = configuration.moduleKey();
    ComponentContainer moduleContainer = getModuleRegistry().getContainerFor(moduleKey);
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
      return Streams.stream(filesToAnalyze);
    }
  }
}
