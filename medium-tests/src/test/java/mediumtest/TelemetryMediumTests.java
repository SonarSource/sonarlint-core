/*
 * SonarLint Core - Medium Tests
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
package mediumtest;

import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorageManager;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;

class TelemetryMediumTests {

  private SonarLintTestBackend backend;
  private String oldValue;


  @BeforeEach
  void saveTelemetryFlag() {
    oldValue = System.getProperty("sonarlint.telemetry.disabled");
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();

    if (oldValue == null) {
      System.clearProperty("sonarlint.telemetry.disabled");
    } else {
      System.setProperty("sonarlint.telemetry.disabled", oldValue);
    }
  }

  @Test
  void it_should_not_create_telemetry_file_if_telemetry_disabled_by_system_property() throws ExecutionException, InterruptedException {
    System.setProperty("sonarlint.telemetry.disabled", "true");
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build();

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();

    this.backend.getHotspotService().openHotspotInBrowser(new OpenHotspotInBrowserParams("scopeId", "master", "ab12ef45"));
    assertThat(backend.telemetryFilePath()).doesNotExist();
  }

  @Test
  void it_should_create_telemetry_file_if_telemetry_enabled() throws ExecutionException, InterruptedException {
    System.clearProperty("sonarlint.telemetry.disabled");
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build();

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isTrue();

    this.backend.getHotspotService().openHotspotInBrowser(new OpenHotspotInBrowserParams("scopeId", "master", "ab12ef45"));
    assertThat(backend.telemetryFilePath()).isNotEmptyFile();
  }

  @Test
  void it_should_consider_telemetry_status_in_file() throws ExecutionException, InterruptedException {
    System.clearProperty("sonarlint.telemetry.disabled");
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build();

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isTrue();

    // Trigger any telemetry event to initialize the file
    this.backend.getHotspotService().openHotspotInBrowser(new OpenHotspotInBrowserParams("scopeId", "master", "ab12ef45"));
    assertThat(backend.telemetryFilePath()).isNotEmptyFile();

    // Emulate another process has disabled telemetry
    var telemetryLocalStorageManager = new TelemetryLocalStorageManager(backend.telemetryFilePath());
    telemetryLocalStorageManager.tryUpdateAtomically(data -> {
      data.setEnabled(false);
    });

    assertThat(backend.getTelemetryService().getStatus().get().isEnabled()).isFalse();
  }

}
