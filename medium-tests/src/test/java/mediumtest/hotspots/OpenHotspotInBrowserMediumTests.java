/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource SA
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
package mediumtest.hotspots;

import java.net.MalformedURLException;
import java.net.URL;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.OpenHotspotInBrowserParams;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class OpenHotspotInBrowserMediumTests {

  @SonarLintTest
  void it_should_open_hotspot_in_sonarqube(SonarLintTestHarness harness) throws MalformedURLException {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", "http://localhost:12345", storage -> storage.withProject("projectKey", project -> project.withMainBranch("master")))
      .withBoundConfigScope("scopeId", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .build(fakeClient);

    backend.getHotspotService().openHotspotInBrowser(new OpenHotspotInBrowserParams("scopeId", "ab12ef45"));

    verify(fakeClient, timeout(5000)).openUrlInBrowser(new URL("http://localhost:12345/security_hotspots?id=projectKey&branch=master&hotspots=ab12ef45"));

    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"openHotspotInBrowserCount\":1"));
  }

  @SonarLintTest
  void it_should_not_open_hotspot_if_unbound(SonarLintTestHarness harness) throws InterruptedException {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withUnboundConfigScope("scopeId")
      .build(fakeClient);

    backend.getHotspotService().openHotspotInBrowser(new OpenHotspotInBrowserParams("scopeId", "ab12ef45"));

    Thread.sleep(100);

    verify(fakeClient, never()).openUrlInBrowser(any());
  }

}
