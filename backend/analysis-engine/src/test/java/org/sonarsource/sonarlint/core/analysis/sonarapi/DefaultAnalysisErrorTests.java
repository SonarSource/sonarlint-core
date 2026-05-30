/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) SonarSource Sàrl
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
    var error = analysisError.onFile(inputFile);
    assertThrows(NullPointerException.class, () -> error.save());
  }

  @Test
  void test_validation() {
    var error1 = new DefaultAnalysisError(storage);
    assertThrows(IllegalArgumentException.class, () -> error1.onFile(null));

    var error2 = new DefaultAnalysisError(storage);
    error2.onFile(inputFile);
    assertThrows(IllegalStateException.class, () -> error2.onFile(inputFile));

    var error3 = new DefaultAnalysisError(storage);
    error3.at(textPointer);
    assertThrows(IllegalStateException.class, () -> error3.at(textPointer));

    var error4 = new DefaultAnalysisError(storage);
    assertThrows(NullPointerException.class, error4::save);
  }
}
