/*
 * SonarLint Core - Implementation
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
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorageManager;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;

class OpenHotspotInBrowserMediumTests {

  private SonarLintTestBackend backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void it_should_open_hotspot_in_sonarqube() {
    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", "http://localhost:12345")
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .build(fakeClient);
    var telemetryLocalStorageManager = new TelemetryLocalStorageManager(backend.getTelemetryFilePath());
    assertThat(telemetryLocalStorageManager.tryRead().openHotspotInBrowserCount()).isZero();

    this.backend.getHotspotService().openHotspotInBrowser(new OpenHotspotInBrowserParams("scopeId", "master", "ab12ef45"));

    assertThat(fakeClient.getUrlsToOpen()).containsExactly("http://localhost:12345/security_hotspots?id=projectKey&branch=master&hotspots=ab12ef45");

    assertThat(telemetryLocalStorageManager.tryRead().openHotspotInBrowserCount()).isEqualTo(1);
  }

  @Test
  void it_should_not_open_hotspot_if_unbound() {
    var fakeClient = newFakeClient().build();
    backend = newBackend()
      .withUnboundConfigScope("scopeId")
      .build(fakeClient);

    this.backend.getHotspotService().openHotspotInBrowser(new OpenHotspotInBrowserParams("scopeId", "master", "ab12ef45"));

    assertThat(fakeClient.getUrlsToOpen()).isEmpty();
  }

}
