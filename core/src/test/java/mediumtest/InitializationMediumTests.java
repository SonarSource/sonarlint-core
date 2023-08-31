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

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.ClientInfoDto;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class InitializationMediumTests {

  private SonarLintBackendImpl backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void it_should_fail_to_initialize_the_backend_twice() {
    backend = newBackend()
      .build();

    var future = backend
      .initialize(new InitializeParams(new ClientInfoDto("name", "productKey", "userAgent"), new FeatureFlagsDto(false, false, false, false, false, false),
        Path.of("unused"), Path.of("unused"),
        emptySet(), emptyMap(), emptySet(), emptySet(),
        emptyList(), emptyList(), "home", emptyMap()));

    assertThat(future)
      .failsWithin(Duration.ofSeconds(1))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(UnsupportedOperationException.class)
      .withMessage("Already initialized");
  }

  @Test
  void it_should_fail_to_use_services_if_the_backend_is_not_initialized() {
    backend = new SonarLintBackendImpl(newFakeClient().build());

    var error = catchThrowable(() -> backend.getConnectionService());

    assertThat(error)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Backend is not initialized");
  }

  @Test
  void it_should_silently_shutdown_the_backend_if_it_was_not_initialized() {
    backend = new SonarLintBackendImpl(newFakeClient().build());

    var future = backend.shutdown();

    assertThat(future)
      .succeedsWithin(Duration.ofSeconds(1));
  }
}
