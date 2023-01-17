/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextPointer;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.DefaultTextPointer;
import testutils.TestInputFileBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class DefaultAnalysisErrorTests {
  private InputFile inputFile;
  private SensorStorage storage;
  private TextPointer textPointer;

  @BeforeEach
  void setUp() {
    inputFile = new TestInputFileBuilder("src/File.java").build();
    textPointer = new DefaultTextPointer(5, 2);
    storage = mock(SensorStorage.class);
  }

  @Test
  void test_analysis_error() {
    var analysisError = new DefaultAnalysisError(storage);
    analysisError.onFile(inputFile)
      .at(textPointer)
      .message("msg");

    assertThat(analysisError.location()).isEqualTo(textPointer);
    assertThat(analysisError.message()).isEqualTo("msg");
    assertThat(analysisError.inputFile()).isEqualTo(inputFile);
  }

  @Test
  void test_save() {
    var analysisError = new DefaultAnalysisError(storage);
    analysisError.onFile(inputFile).save();

    verify(storage).store(analysisError);
    verifyNoMoreInteractions(storage);
  }

  @Test
  void test_no_storage() {
    var analysisError = new DefaultAnalysisError();
    assertThrows(NullPointerException.class, () -> analysisError.onFile(inputFile).save());
  }

  @Test
  void test_validation() {
    assertThrows(IllegalArgumentException.class, () -> new DefaultAnalysisError(storage).onFile(null));
    assertThrows(IllegalStateException.class, () -> new DefaultAnalysisError(storage).onFile(inputFile).onFile(inputFile));
    assertThrows(IllegalStateException.class, () -> new DefaultAnalysisError(storage).at(textPointer).at(textPointer));
    assertThrows(NullPointerException.class, () -> new DefaultAnalysisError(storage).save());
  }
}
