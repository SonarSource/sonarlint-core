/*
 * SonarLint Language Server
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
package org.sonarlint.languageserver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonarsource.sonarlint.core.telemetry.TelemetryClient;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class SonarLintTelemetryTest {
  private SonarLintTelemetry telemetry;
  private TelemetryManager telemetryManager = mock(TelemetryManager.class);

  @Rule
  public final EnvironmentVariables env = new EnvironmentVariables();

  @Rule
  public LogTester logTester = new LogTester();

  @Before
  public void setUp() {
    this.telemetry = createTelemetry();
  }

  @After
  public void after() {
    System.clearProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY);
  }

  private SonarLintTelemetry createTelemetry() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    SonarLintTelemetry telemetry = new SonarLintTelemetry() {
      @Override
      TelemetryManager newTelemetryManager(Path path, TelemetryClient client, Supplier<Boolean> usesConnectedMode, Supplier<Boolean> usesSonarCloud) {
        return telemetryManager;
      }
    };
    telemetry.init(Paths.get("dummy"), "product", "version", "ideVersion", () -> true, () -> true);
    return telemetry;
  }

  @Test
  public void disable_property_should_disable_telemetry() throws Exception {
    assertThat(createTelemetry().enabled()).isTrue();

    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    assertThat(createTelemetry().enabled()).isFalse();
  }

  @Test
  public void log_failure_create_task_if_debug_enabled() {
    env.set("SONARLINT_INTERNAL_DEBUG", "true");
    telemetry = new SonarLintTelemetry(() -> {
      throw new IllegalStateException("error");
    });
    telemetry.init(Paths.get("dummy"), "product", "version", "ideVersion", () -> true, () -> true);

    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("Failed scheduling period telemetry job");
  }

  @Test
  public void stop_should_trigger_stop_telemetry() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.stop();
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).stop();
  }

  @Test
  public void test_scheduler() {
    assertThat(telemetry.scheduledFuture).isNotNull();
    assertThat(telemetry.scheduledFuture.getDelay(TimeUnit.MINUTES)).isBetween(0L, 1L);
    telemetry.stop();
    assertThat(telemetry.scheduledFuture).isNull();
  }

  @Test
  public void create_telemetry_manager() {
    assertThat(telemetry.newTelemetryManager(Paths.get(""), mock(TelemetryClient.class), () -> true, () -> true)).isNotNull();
  }

  @Test
  public void optOut_should_trigger_disable_telemetry() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.optOut(true);
    verify(telemetryManager).disable();
    telemetry.stop();
  }

  @Test
  public void should_not_opt_out_twice() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.optOut(true);
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  public void optIn_should_trigger_enable_telemetry() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.optOut(false);
    verify(telemetryManager).enable();
  }

  @Test
  public void upload_should_trigger_upload_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.upload();
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).uploadLazily();
  }

  @Test
  public void upload_should_not_trigger_upload_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.upload();
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  public void analysisDoneOnMultipleFiles_should_trigger_analysisDoneOnMultipleFiles_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.analysisDoneOnMultipleFiles();
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).analysisDoneOnMultipleFiles();
  }

  @Test
  public void analysisDoneOnMultipleFiles_should_not_trigger_analysisDoneOnMultipleFiles_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.analysisDoneOnMultipleFiles();
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  public void analysisDoneOnSingleFile_should_trigger_analysisDoneOnSingleFile_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.analysisDoneOnSingleFile("java", 1000);
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).analysisDoneOnSingleFile("java", 1000);
  }

  @Test
  public void analysisDoneOnSingleFile_should_not_trigger_analysisDoneOnSingleFile_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.analysisDoneOnSingleFile("java", 1000);
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  public void should_start_disabled_when_storagePath_null() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    SonarLintTelemetry telemetry = new SonarLintTelemetry() {
      @Override
      TelemetryManager newTelemetryManager(Path path, TelemetryClient client, Supplier<Boolean> usesConnectedMode, Supplier<Boolean> usesSonarCloud) {
        return telemetryManager;
      }
    };
    telemetry.init(null, "product", "version", "ideVersion", () -> true, () -> true);
    assertThat(telemetry.enabled()).isFalse();
  }
}
