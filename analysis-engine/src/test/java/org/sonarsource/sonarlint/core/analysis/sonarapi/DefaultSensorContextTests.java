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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.Version;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputProject;
import org.sonarsource.sonarlint.core.analysis.sonarapi.noop.NoOpNewCoverage;
import org.sonarsource.sonarlint.core.analysis.sonarapi.noop.NoOpNewCpdTokens;
import org.sonarsource.sonarlint.core.analysis.sonarapi.noop.NoOpNewHighlighting;
import org.sonarsource.sonarlint.core.analysis.sonarapi.noop.NoOpNewMeasure;
import org.sonarsource.sonarlint.core.analysis.sonarapi.noop.NoOpNewSignificantCode;
import org.sonarsource.sonarlint.core.analysis.sonarapi.noop.NoOpNewSymbolTable;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSensorContextTests {
  @Mock
  private SonarLintInputProject module;
  @Mock
  private Settings settings;
  @Mock
  private Configuration config;
  @Mock
  private FileSystem fs;
  @Mock
  private ActiveRules activeRules;
  @Mock
  private SensorStorage sensorStorage;
  @Mock
  private SonarRuntime sqRuntime;

  private DefaultSensorContext ctx;
  private ProgressMonitor progress;

  @BeforeEach
  void setUp() {
    progress = new ProgressMonitor(null);
    ctx = new DefaultSensorContext(module, settings, config, fs, activeRules, sensorStorage, sqRuntime, progress);
  }

  @Test
  void testGetters() {
    when(sqRuntime.getApiVersion()).thenReturn(Version.create(6, 1));

    assertThat(ctx.activeRules()).isEqualTo(activeRules);
    assertThat(ctx.settings()).isEqualTo(settings);
    assertThat(ctx.config()).isEqualTo(config);
    assertThat(ctx.fileSystem()).isEqualTo(fs);
    assertThat(ctx.module()).isEqualTo(module);
    assertThat(ctx.runtime()).isEqualTo(sqRuntime);

    assertThat(ctx.getSonarQubeVersion()).isEqualTo(Version.create(6, 1));
    assertThat(ctx.isCancelled()).isFalse();

    // no ops
    assertThat(ctx.newCpdTokens()).isInstanceOf(NoOpNewCpdTokens.class);
    assertThat(ctx.newSymbolTable()).isInstanceOf(NoOpNewSymbolTable.class);
    assertThat(ctx.newHighlighting()).isInstanceOf(NoOpNewHighlighting.class);
    assertThat(ctx.newMeasure()).isInstanceOf(NoOpNewMeasure.class);
    assertThat(ctx.newCoverage()).isInstanceOf(NoOpNewCoverage.class);
    assertThat(ctx.newSignificantCode()).isInstanceOf(NoOpNewSignificantCode.class);
    ctx.addContextProperty(null, null);
    ctx.markForPublishing(null);
    assertThat(ctx.canSkipUnchangedFiles()).isFalse();
    assertThat(ctx.isCacheEnabled()).isFalse();
    assertThrows(UnsupportedOperationException.class, () -> ctx.newExternalIssue());
    assertThrows(UnsupportedOperationException.class, () -> ctx.previousCache());
    assertThrows(UnsupportedOperationException.class, () -> ctx.nextCache());

    verify(sqRuntime).getApiVersion();

    verifyNoMoreInteractions(sqRuntime);
    verifyNoInteractions(module);
    verifyNoInteractions(settings);
    verifyNoInteractions(fs);
    verifyNoInteractions(activeRules);
    verifyNoInteractions(sensorStorage);
  }

  @Test
  void testCancellation() {
    assertThat(ctx.isCancelled()).isFalse();
    progress.cancel();
    assertThat(ctx.isCancelled()).isTrue();
  }
}
