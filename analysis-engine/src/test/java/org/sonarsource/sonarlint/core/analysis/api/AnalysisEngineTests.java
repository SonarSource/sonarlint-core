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

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.analysis.exceptions.SonarLintException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static testutils.TestUtils.noOpIssueListener;

class AnalysisEngineTests {

  private static final String MODULE_KEY = "moduleKey";

  private static IllegalStateException onStopException = new IllegalStateException("Exception during container stop");
  private static IllegalStateException originalException = new IllegalStateException("Original exception");

  @Test
  void testThrowOnStop() {
    AnalysisEngine underTest = prepareFakeEngine(false);

    SonarLintException thrown = assertThrows(SonarLintException.class, () -> underTest.analyze(mock(AnalysisConfiguration.class), noOpIssueListener(), null, null));

    assertThat(thrown).hasMessage(onStopException.getMessage()).hasNoSuppressedExceptions();

  }

  @Test
  void dontLoseOriginalExceptionWhenErrorDuringModuleContainerStop() {
    AnalysisEngine underTest = prepareFakeEngine(true);

    SonarLintException thrown = assertThrows(SonarLintException.class, () -> underTest.analyze(mock(AnalysisConfiguration.class), noOpIssueListener(), null, null));

    assertThat(thrown).hasMessage(onStopException.getMessage());
    assertThat(thrown.getSuppressed()[0]).hasMessage(originalException.getMessage());

  }

  private AnalysisEngine prepareFakeEngine(boolean throwDuringAnalyze) {
    ModuleContainer mockedModuleContainerThatFailsOnStop = mock(ModuleContainer.class);
    if (throwDuringAnalyze) {
      when(mockedModuleContainerThatFailsOnStop.analyze(any(), any(), any())).thenThrow(originalException);
    }

    // Create transcient container to force the engine to call stop immediately
    when(mockedModuleContainerThatFailsOnStop.isTranscient()).thenReturn(true);
    when(mockedModuleContainerThatFailsOnStop.stopComponents()).thenThrow(onStopException);

    AnalysisEngine underTest = new AnalysisEngine(GlobalAnalysisConfiguration.builder().setLogOutput(mock(LogOutput.class)).build());
    underTest = spy(underTest);
    doReturn(mockedModuleContainerThatFailsOnStop).when(underTest).getModuleContainer(any());
    return underTest;
  }

}
