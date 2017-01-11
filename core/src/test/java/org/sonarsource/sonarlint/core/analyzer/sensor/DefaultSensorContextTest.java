/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputModule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.Version;
import org.sonarsource.sonarlint.core.analyzer.sensor.noop.NoOpNewCpdTokens;
import org.sonarsource.sonarlint.core.analyzer.sensor.noop.NoOpNewHighlighting;
import org.sonarsource.sonarlint.core.analyzer.sensor.noop.NoOpNewSymbolTable;

public class DefaultSensorContextTest {
  @Mock
  private InputModule module;
  @Mock
  private Settings settings;
  @Mock
  private FileSystem fs;
  @Mock
  private ActiveRules activeRules;
  @Mock
  private SensorStorage sensorStorage;
  @Mock
  private SonarRuntime sqRuntime;

  private DefaultSensorContext ctx;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ctx = new DefaultSensorContext(module, settings, fs, activeRules, sensorStorage, sqRuntime);
  }

  @Test
  public void testGetters() {
    when(sqRuntime.getApiVersion()).thenReturn(Version.create(6, 1));

    assertThat(ctx.activeRules()).isEqualTo(activeRules);
    assertThat(ctx.settings()).isEqualTo(settings);
    assertThat(ctx.fileSystem()).isEqualTo(fs);
    assertThat(ctx.module()).isEqualTo(module);
    assertThat(ctx.runtime()).isEqualTo(sqRuntime);

    assertThat(ctx.getSonarQubeVersion()).isEqualTo(Version.create(6, 1));

    // no ops
    assertThat(ctx.newCpdTokens()).isInstanceOf(NoOpNewCpdTokens.class);
    assertThat(ctx.newSymbolTable()).isInstanceOf(NoOpNewSymbolTable.class);
    assertThat(ctx.newHighlighting()).isInstanceOf(NoOpNewHighlighting.class);
    ctx.addContextProperty(null, null);

    verify(sqRuntime).getApiVersion();

    verifyNoMoreInteractions(sqRuntime);
    verifyZeroInteractions(module);
    verifyZeroInteractions(settings);
    verifyZeroInteractions(fs);
    verifyZeroInteractions(activeRules);
    verifyZeroInteractions(sensorStorage);
  }
}
