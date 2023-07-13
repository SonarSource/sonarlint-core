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
package mediumtest.hotspots;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.ChangeHotspotStatusParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.hotspot.HotspotStatusChangeException;

import static mediumtest.fixtures.ServerFixture.ServerStatus.DOWN;
import static mediumtest.fixtures.ServerFixture.newSonarCloudServer;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;

class HotspotStatusChangeMediumTests {
  @TempDir
  Path storageDir;

  private SonarLintTestBackend backend;
  private ServerFixture.Server server;
  private String oldSonarCloudUrl;

  @BeforeEach
  void prepare() {
    oldSonarCloudUrl = System.getProperty("sonarlint.internal.sonarcloud.url");
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (server != null) {
      server.shutdown();
      server = null;
    }

    if (oldSonarCloudUrl == null) {
      System.clearProperty("sonarlint.internal.sonarcloud.url");
    } else {
      System.setProperty("sonarlint.internal.sonarcloud.url", oldSonarCloudUrl);
    }
  }

  @Test
  void it_should_fail_the_future_when_the_server_returns_an_error() {
    server = newSonarQubeServer().withStatus(DOWN).start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = setStatusToSafe("configScopeId", "hotspotKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(HotspotStatusChangeException.class)
      .withMessage("Cannot change status on the hotspot");
  }

  @Test
  void it_should_do_nothing_when_the_configuration_scope_is_unknown() {
    backend = newBackend().build();

    var response = setStatusToSafe("configScopeId", "hotspotKey");

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
  }

  @Test
  void it_should_do_nothing_when_the_configuration_scope_bound_connection_is_unknown() {
    backend = newBackend()
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = setStatusToSafe("configScopeId", "hotspotKey");

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
  }

  @Test
  void it_should_update_the_status_on_sonarcloud_through_the_web_api() {
    server = newSonarCloudServer().start();
    System.setProperty("sonarlint.internal.sonarcloud.url", server.baseUrl());

    backend = newBackend()
      .withSonarCloudConnection("connectionId", "orgKey")
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = setStatusToSafe("configScopeId", "hotspotKey");

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var lastRequest = server.lastRequest();
    assertThat(lastRequest.getPath()).isEqualTo("/api/hotspots/change_status");
    assertThat(lastRequest.getHeader("Content-Type")).isEqualTo("application/x-www-form-urlencoded");
    assertThat(lastRequest.getBody().readString(StandardCharsets.UTF_8)).isEqualTo("hotspot=hotspotKey&status=REVIEWED&resolution=SAFE");
  }

  @Test
  void it_should_update_the_status_on_sonarqube_through_the_web_api() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = setStatusToSafe("configScopeId", "hotspotKey");

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var lastRequest = server.lastRequest();
    assertThat(lastRequest.getPath()).isEqualTo("/api/hotspots/change_status");
    assertThat(lastRequest.getHeader("Content-Type")).isEqualTo("application/x-www-form-urlencoded");
    assertThat(lastRequest.getBody().readString(StandardCharsets.UTF_8)).isEqualTo("hotspot=hotspotKey&status=REVIEWED&resolution=SAFE");
  }

  @Test
  @Disabled("TODO")
  void it_should_update_the_hotspot_status_in_the_storage() {
//    newStorage("connectionId").withProject("projectKey", project -> project.withHotspot("hotspotKey")).create(storageDir);
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = setStatusToSafe("configScopeId", "hotspotKey");

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var lastRequest = server.lastRequest();
    assertThat(lastRequest.getPath()).isEqualTo("/api/hotspots/change_status");
    assertThat(lastRequest.getHeader("Content-Type")).isEqualTo("application/x-www-form-urlencoded");
    assertThat(lastRequest.getBody().readString(StandardCharsets.UTF_8)).isEqualTo("hotspot=hotspotKey&status=REVIEWED&resolution=SAFE");
  }

  @Test
  void it_should_count_status_change_in_telemetry() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = setStatusToSafe("configScopeId", "hotspotKey");

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"hotspotStatusChangedCount\":1");
  }

  private CompletableFuture<Void> setStatusToSafe(String configScopeId, String hotspotKey) {
    return backend.getHotspotService().changeStatus(new ChangeHotspotStatusParams(configScopeId, hotspotKey, HotspotStatus.SAFE));
  }
}
