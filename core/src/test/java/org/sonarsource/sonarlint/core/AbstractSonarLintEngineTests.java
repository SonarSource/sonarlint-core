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

import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.analysis.container.global.ModuleRegistry;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.client.api.common.AbstractAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractSonarLintEngineTests {

  private static final String MODULE_KEY = "moduleKey";

  @Test
  void testThrowOnStop() {
    ModuleContainer mockedModuleContainerThatFailsOnStop = mock(ModuleContainer.class);
    IllegalStateException onStopException = new IllegalStateException("Exception during container stop");
    when(mockedModuleContainerThatFailsOnStop.stopComponents()).thenThrow(onStopException);
    when(mockedModuleContainerThatFailsOnStop.isTranscient()).thenReturn(true);

    AbstractSonarLintEngine underTest = prepareFakeEngine(mockedModuleContainerThatFailsOnStop);

    AbstractAnalysisConfiguration configuration = mock(AbstractAnalysisConfiguration.class);
    when(configuration.moduleKey()).thenReturn(MODULE_KEY);

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.withModule(configuration, c -> "Result"));

    assertThat(thrown).isEqualTo(onStopException).hasNoSuppressedExceptions();

  }

  @Test
  void dontLoseOriginalExceptionWhenErrorDuringModuleContainerStop() {
    ModuleContainer mockedModuleContainerThatFailsOnStop = mock(ModuleContainer.class);
    IllegalStateException onStopException = new IllegalStateException("Exception during container stop");
    when(mockedModuleContainerThatFailsOnStop.stopComponents()).thenThrow(onStopException);
    when(mockedModuleContainerThatFailsOnStop.isTranscient()).thenReturn(true);

    AbstractSonarLintEngine underTest = prepareFakeEngine(mockedModuleContainerThatFailsOnStop);

    AbstractAnalysisConfiguration configuration = mock(AbstractAnalysisConfiguration.class);
    when(configuration.moduleKey()).thenReturn(MODULE_KEY);

    IllegalStateException originalException = new IllegalStateException("Original exception");

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.withModule(configuration, c -> {
      throw originalException;
    }));

    assertThat(thrown).isEqualTo(onStopException).hasSuppressedException(originalException);

  }

  private AbstractSonarLintEngine prepareFakeEngine(ModuleContainer mockedModuleContainerThatFailsOnStop) {
    ModuleRegistry mockModuleRegistry = mock(ModuleRegistry.class);

    AbstractSonarLintEngine underTest = new AbstractSonarLintEngine(mock(ClientLogOutput.class)) {

      @Override
      public Collection<PluginDetails> getPluginDetails() {
        return null;
      }

      @Override
      protected ModuleRegistry getModuleRegistry() {
        return mockModuleRegistry;
      }
    };
    when(mockModuleRegistry.getContainerFor(MODULE_KEY)).thenReturn(null);
    when(mockModuleRegistry.createTranscientContainer(any())).thenReturn(mockedModuleContainerThatFailsOnStop);
    return underTest;
  }

}
