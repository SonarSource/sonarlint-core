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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.telemetry.Telemetry;
import org.sonarsource.sonarlint.core.telemetry.TelemetryClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarLintTelemetryTest {
  private SonarLintTelemetry telemetry;
  private Telemetry engine;
  private TelemetryClient client;

  @Before
  public void start() throws Exception {
    this.telemetry = createTelemetry();
  }

  @After
  public void after() {
    System.clearProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY);
  }

  private SonarLintTelemetry createTelemetry() throws Exception {
    engine = mock(Telemetry.class);
    client = mock(TelemetryClient.class);
    when(engine.getClient()).thenReturn(client);
    SonarLintTelemetry sonarLintTelemetry = new SonarLintTelemetry() {
      protected Telemetry newTelemetry(java.nio.file.Path storagePath, String productName, String productVersion) throws Exception {
        return engine;
      };
    };
    sonarLintTelemetry.init(null, null, null);
    return sonarLintTelemetry;
  }

  @Test
  public void disable() throws Exception {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry = createTelemetry();
    assertThat(telemetry.enabled()).isFalse();
  }

  @Test
  public void testSaveData() {
    telemetry.stop();
  }

  @Test
  public void testScheduler() throws IOException {
    assertThat(telemetry.scheduledFuture).isNotNull();
    assertThat(telemetry.scheduledFuture.getDelay(TimeUnit.MINUTES)).isBetween(0L, 1L);
    telemetry.stop();
    assertThat(telemetry.scheduledFuture).isNull();
  }

  @Test
  public void testOptOut() throws Exception {
    when(engine.enabled()).thenReturn(true);
    telemetry.optOut(true);
    verify(engine).enable(false);
    verify(client).optOut(any(TelemetryClientConfig.class), anyBoolean());
    telemetry.stop();
  }

  @Test
  public void testDontOptOutAgain() {
    when(engine.enabled()).thenReturn(false);
    telemetry.optOut(true);
    verify(engine).enabled();
    verifyNoMoreInteractions(engine);
    verifyZeroInteractions(client);
  }
}
