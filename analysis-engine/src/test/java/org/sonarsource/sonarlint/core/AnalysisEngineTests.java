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

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.container.module.ModuleRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class AnalysisEngineTests {

  private static final String MODULE_KEY = "moduleKey";

  @Test
  void testThrowOnStop() {
    ModuleContainer mockedModuleContainerThatFailsOnStop = mock(ModuleContainer.class);
    IllegalStateException onStopException = new IllegalStateException("Exception during container stop");
    when(mockedModuleContainerThatFailsOnStop.stopComponents()).thenThrow(onStopException);

    AnalysisEngine underTest = prepareFakeEngine(mockedModuleContainerThatFailsOnStop);

    AnalysisConfiguration configuration = mock(AnalysisConfiguration.class);
    when(configuration.moduleKey()).thenReturn(MODULE_KEY);

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.withModule(configuration, c -> "Result"));

    assertThat(thrown).isEqualTo(onStopException).hasNoSuppressedExceptions();

  }

  @Test
  void dontLoseOriginalExceptionWhenErrorDuringModuleContainerStop() {
    ModuleContainer mockedModuleContainerThatFailsOnStop = mock(ModuleContainer.class);
    IllegalStateException onStopException = new IllegalStateException("Exception during container stop");
    when(mockedModuleContainerThatFailsOnStop.stopComponents()).thenThrow(onStopException);

    AnalysisEngine underTest = prepareFakeEngine(mockedModuleContainerThatFailsOnStop);

    AnalysisConfiguration configuration = mock(AnalysisConfiguration.class);
    when(configuration.moduleKey()).thenReturn(MODULE_KEY);

    IllegalStateException originalException = new IllegalStateException("Original exception");

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.withModule(configuration, c -> {
      throw originalException;
    }));

    assertThat(thrown).isEqualTo(onStopException).hasSuppressedException(originalException);

  }

  private AnalysisEngine prepareFakeEngine(ModuleContainer mockedModuleContainerThatFailsOnStop) {
    ModuleRegistry mockModuleRegistry = mock(ModuleRegistry.class);

    AnalysisEngine underTest = new AnalysisEngine(GlobalAnalysisConfiguration.builder().setLogOutput(mock(LogOutput.class)).build());
    underTest = spy(underTest);
    when(underTest.getModuleRegistry()).thenReturn(mockModuleRegistry);
    when(mockModuleRegistry.getContainerFor(MODULE_KEY)).thenReturn(null);
    when(mockModuleRegistry.createContainer(any())).thenReturn(mockedModuleContainerThatFailsOnStop);
    return underTest;
  }

}
