/*
 * SonarLint Language Server
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
package org.sonarlint.languageserver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.telemetry.TelemetryClient;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class SonarLintTelemetryTest {
  private SonarLintTelemetry telemetry;
  private TelemetryManager engine = mock(TelemetryManager.class);

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Before
  public void start() throws Exception {
    Path path = tmp.newFile().toPath();
    this.telemetry = createTelemetry();
  }

  @After
  public void after() {
    System.clearProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY);
  }

  private SonarLintTelemetry createTelemetry() {
    when(engine.isEnabled()).thenReturn(true);
    SonarLintTelemetry telemetry = new SonarLintTelemetry() {
      @Override
      TelemetryManager newTelemetryManager(Path path, TelemetryClient client) {
        return engine;
      }
    };
    telemetry.init(Paths.get("dummy"), "product", "version");
    return telemetry;
  }

  @Test
  public void disable_should_disable_telemetry() throws Exception {
    assertThat(createTelemetry().enabled()).isTrue();

    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    assertThat(createTelemetry().enabled()).isFalse();
  }

  @Test
  public void stop_should_trigger_stop_telemetry() {
    when(engine.isEnabled()).thenReturn(true);
    telemetry.stop();
    verify(engine).isEnabled();
    verify(engine).stop();
  }

  @Test
  public void test_scheduler() {
    assertThat(telemetry.scheduledFuture).isNotNull();
    assertThat(telemetry.scheduledFuture.getDelay(TimeUnit.MINUTES)).isBetween(0L, 1L);
    telemetry.stop();
    assertThat(telemetry.scheduledFuture).isNull();
  }

  @Test
  public void optOut_should_trigger_disable_telemetry() {
    when(engine.isEnabled()).thenReturn(true);
    telemetry.optOut(true);
    verify(engine).disable();
    telemetry.stop();
  }

  @Test
  public void should_not_opt_out_twice() {
    when(engine.isEnabled()).thenReturn(false);
    telemetry.optOut(true);
    verify(engine).isEnabled();
    verifyNoMoreInteractions(engine);
  }

  @Test
  public void optIn_should_trigger_enable_telemetry() {
    when(engine.isEnabled()).thenReturn(false);
    telemetry.optOut(false);
    verify(engine).enable();
  }

  @Test
  public void upload_should_trigger_upload_when_enabled() {
    when(engine.isEnabled()).thenReturn(true);
    telemetry.upload();
    verify(engine).isEnabled();
    verify(engine).uploadLazily();
  }

  @Test
  public void upload_should_not_trigger_upload_when_disabled() {
    when(engine.isEnabled()).thenReturn(false);
    telemetry.upload();
    verify(engine).isEnabled();
    verifyNoMoreInteractions(engine);
  }

  @Test
  public void analysisSubmitted_should_trigger_usedAnalysis_when_enabled() {
    when(engine.isEnabled()).thenReturn(true);
    telemetry.analysisSubmitted();
    verify(engine).isEnabled();
    verify(engine).usedAnalysis();
  }

  @Test
  public void analysisSubmitted_should_not_trigger_usedAnalysis_when_disabled() {
    when(engine.isEnabled()).thenReturn(false);
    telemetry.analysisSubmitted();
    verify(engine).isEnabled();
    verifyNoMoreInteractions(engine);
  }
}
