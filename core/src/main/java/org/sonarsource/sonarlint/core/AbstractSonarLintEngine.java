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

import com.google.common.collect.Streams;
import java.util.function.Function;
import java.util.stream.Stream;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.client.api.common.AbstractAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.common.ClientFileSystem;
import org.sonarsource.sonarlint.core.client.api.common.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.client.api.common.ModuleFileEventNotifier;
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.module.ModuleRegistry;

public abstract class AbstractSonarLintEngine implements SonarLintEngine {
  protected abstract ModuleRegistry getModuleRegistry();

  @Override
  public void declareModule(ModuleInfo module) {
    getModuleRegistry().registerModule(module);
  }

  @Override
  public void stopModule(Object moduleKey) {
    getModuleRegistry().unregisterModule(moduleKey);
  }

  @Override
  public void fireModuleFileEvent(Object moduleKey, ClientModuleFileEvent event) {
    ComponentContainer moduleContainer = getModuleRegistry().getContainerFor(moduleKey);
    if (moduleContainer != null) {
      moduleContainer.getComponentByType(ModuleFileEventNotifier.class).fireModuleFileEvent(event);
    }
  }

  protected <T> T withModule(AbstractAnalysisConfiguration configuration, Function<ComponentContainer, T> consumer) {
    boolean deleteModuleAfterAnalysis = false;
    Object moduleKey = configuration.moduleKey();
    ComponentContainer moduleContainer = getModuleRegistry().getContainerFor(moduleKey);
    if (moduleContainer == null) {
      // if not found, means we are outside of any module (e.g. single file analysis on VSCode)
      moduleContainer = getModuleRegistry().createContainer(new ModuleInfo(moduleKey, new AnalysisScopeFileSystem(configuration.inputFiles())));
      deleteModuleAfterAnalysis = true;
    }
    try {
      return consumer.apply(moduleContainer);
    } finally {
      if (deleteModuleAfterAnalysis) {
        moduleContainer.stopComponents();
      }
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
