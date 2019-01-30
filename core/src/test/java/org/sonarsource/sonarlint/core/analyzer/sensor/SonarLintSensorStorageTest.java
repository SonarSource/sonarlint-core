/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarsource.sonarlint.core.analyzer.sensor;

import org.junit.Test;
import org.sonar.api.batch.sensor.code.internal.DefaultSignificantCode;
import org.sonar.api.batch.sensor.coverage.internal.DefaultCoverage;
import org.sonar.api.batch.sensor.cpd.internal.DefaultCpdTokens;
import org.sonar.api.batch.sensor.highlighting.internal.DefaultHighlighting;
import org.sonar.api.batch.sensor.issue.internal.DefaultExternalIssue;
import org.sonar.api.batch.sensor.measure.Measure;
import org.sonar.api.batch.sensor.symbol.internal.DefaultSymbolTable;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

public class SonarLintSensorStorageTest {

  private final SonarLintSensorStorage underTest = new SonarLintSensorStorage(null, null, null, null, null);

  @Test
  public void store_Measure_doesnt_interact_with_its_param() {
    Measure measure = mock(Measure.class);
    underTest.store(measure);
    verifyZeroInteractions(measure);
  }

  @Test
  public void store_ExternalIssue_doesnt_interact_with_its_param() {
    DefaultExternalIssue externalIssue = mock(DefaultExternalIssue.class);
    underTest.store(externalIssue);
    verifyZeroInteractions(externalIssue);
  }

  @Test
  public void store_DefaultSignificantCode_doesnt_interact_with_its_param() {
    DefaultSignificantCode significantCode = mock(DefaultSignificantCode.class);
    underTest.store(significantCode);
    verifyZeroInteractions(significantCode);
  }

  @Test
  public void store_DefaultHighlighting_doesnt_interact_with_its_param() {
    DefaultHighlighting highlighting = mock(DefaultHighlighting.class);
    underTest.store(highlighting);
    verifyZeroInteractions(highlighting);
  }

  @Test
  public void store_DefaultCoverage_doesnt_interact_with_its_param() {
    DefaultCoverage coverage = mock(DefaultCoverage.class);
    underTest.store(coverage);
    verifyZeroInteractions(coverage);
  }

  @Test
  public void store_DefaultCpdTokens_doesnt_interact_with_its_param() {
    DefaultCpdTokens cpdTokens = mock(DefaultCpdTokens.class);
    underTest.store(cpdTokens);
    verifyZeroInteractions(cpdTokens);
  }

  @Test
  public void store_DefaultSymbolTable_doesnt_interact_with_its_param() {
    DefaultSymbolTable symbolTable = mock(DefaultSymbolTable.class);
    underTest.store(symbolTable);
    verifyZeroInteractions(symbolTable);
  }
}
