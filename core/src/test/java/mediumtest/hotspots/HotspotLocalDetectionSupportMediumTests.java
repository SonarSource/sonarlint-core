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

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import mediumtest.fixtures.StorageFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckLocalDetectionSupportedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckLocalDetectionSupportedResponse;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;

class HotspotLocalDetectionSupportMediumTests {
  @TempDir
  Path storageDir;

  private SonarLintBackendImpl backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void it_should_not_support_local_detection_in_standalone_mode() {
    backend = newBackend().build();

    var checkResponse = checkLocalDetectionSupported(null);

    assertThat(checkResponse)
      .extracting(CheckLocalDetectionSupportedResponse::isSupported, CheckLocalDetectionSupportedResponse::getReason)
      .containsExactly(false, "The project is not bound to SonarQube 9.7+");
  }

  @Test
  void it_should_not_support_local_detection_when_connected_to_sonarcloud() {
    backend = newBackend()
      .withSonarCloudConnection("connectionId", "orgKey")
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var checkResponse = checkLocalDetectionSupported("configScopeId");

    assertThat(checkResponse)
      .extracting(CheckLocalDetectionSupportedResponse::isSupported, CheckLocalDetectionSupportedResponse::getReason)
      .containsExactly(false, "The project is not bound to SonarQube 9.7+");
  }

  @Test
  void it_should_not_support_local_detection_when_connected_to_sonarqube_and_storage_is_missing() {
    bindToSonarQube("configScopeId", null);

    var checkResponse = checkLocalDetectionSupported("configScopeId");

    assertThat(checkResponse)
      .extracting(CheckLocalDetectionSupportedResponse::isSupported, CheckLocalDetectionSupportedResponse::getReason)
      .containsExactly(false, "The project is not bound to SonarQube 9.7+");
  }

  @Test
  void it_should_not_support_local_detection_when_connected_to_sonarqube_9_6_and_older() {
    bindToSonarQube("configScopeId", "9.6");

    var checkResponse = checkLocalDetectionSupported("configScopeId");

    assertThat(checkResponse)
      .extracting(CheckLocalDetectionSupportedResponse::isSupported, CheckLocalDetectionSupportedResponse::getReason)
      .containsExactly(false, "The project is not bound to SonarQube 9.7+");
  }

  @Test
  void it_should_support_local_detection_when_connected_to_sonarqube_9_7_plus() {
    bindToSonarQube("configScopeId", "9.7");

    var checkResponse = checkLocalDetectionSupported("configScopeId");

    assertThat(checkResponse)
      .extracting(CheckLocalDetectionSupportedResponse::isSupported, CheckLocalDetectionSupportedResponse::getReason)
      .containsExactly(true, null);
  }

  private void bindToSonarQube(String configScopeId, @Nullable String serverVersion) {
    backend = newBackend()
      .withSonarQubeConnection("connectionId", "serverUrl")
      .withBoundConfigScope(configScopeId, "connectionId", "projectKey")
      .withStorageRoot(storageDir.resolve("storage"))
      .build();
    StorageFixture.newStorage("connectionId")
      .withServerVersion(serverVersion)
      .withProject("projectKey")
      .create(storageDir);
  }

  private CheckLocalDetectionSupportedResponse checkLocalDetectionSupported(String configScopeId) {
    try {
      return backend.getHotspotService().checkLocalDetectionSupported(new CheckLocalDetectionSupportedParams(configScopeId)).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

}
