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
package org.sonarsource.sonarlint.core.analysis.container.analysis.sensor;

import org.junit.jupiter.api.Test;
import org.sonar.api.batch.sensor.code.NewSignificantCode;
import org.sonar.api.batch.sensor.coverage.NewCoverage;
import org.sonar.api.batch.sensor.cpd.NewCpdTokens;
import org.sonar.api.batch.sensor.highlighting.NewHighlighting;
import org.sonar.api.batch.sensor.issue.ExternalIssue;
import org.sonar.api.batch.sensor.measure.Measure;
import org.sonar.api.batch.sensor.symbol.NewSymbolTable;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SonarLintSensorStorageTests {

  private final SonarLintSensorStorage underTest = new SonarLintSensorStorage(null, null, null, null);

  @Test
  void store_Measure_doesnt_interact_with_its_param() {
    var measure = mock(Measure.class);
    underTest.store(measure);
    verifyNoInteractions(measure);
  }

  @Test
  void store_ExternalIssue_doesnt_interact_with_its_param() {
    var externalIssue = mock(ExternalIssue.class);
    underTest.store(externalIssue);
    verifyNoInteractions(externalIssue);
  }

  @Test
  void store_DefaultSignificantCode_doesnt_interact_with_its_param() {
    var significantCode = mock(NewSignificantCode.class);
    underTest.store(significantCode);
    verifyNoInteractions(significantCode);
  }

  @Test
  void store_DefaultHighlighting_doesnt_interact_with_its_param() {
    var highlighting = mock(NewHighlighting.class);
    underTest.store(highlighting);
    verifyNoInteractions(highlighting);
  }

  @Test
  void store_DefaultCoverage_doesnt_interact_with_its_param() {
    var coverage = mock(NewCoverage.class);
    underTest.store(coverage);
    verifyNoInteractions(coverage);
  }

  @Test
  void store_DefaultCpdTokens_doesnt_interact_with_its_param() {
    var cpdTokens = mock(NewCpdTokens.class);
    underTest.store(cpdTokens);
    verifyNoInteractions(cpdTokens);
  }

  @Test
  void store_DefaultSymbolTable_doesnt_interact_with_its_param() {
    var symbolTable = mock(NewSymbolTable.class);
    underTest.store(symbolTable);
    verifyNoInteractions(symbolTable);
  }
}
